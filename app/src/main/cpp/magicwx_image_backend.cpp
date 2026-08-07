// magicwx_image_backend - MagicWX 隔离式端侧生图后端进程（全管线版）
//
// 本文件是 local-dream (https://github.com/xororz/local-dream) main.cpp 的
// MagicWX 全量对齐版：sd15cpu / sd15npu / sdxl / anima 四种固定模型格式 +
// upscaler_mode 纯超分进程 + convert 模式，端点契约、参数解析、文件布局校验、
// 管线构造、embeddings/safety_checker 消费均与参照 main.cpp 逐项一致。
// QNN 部分（sd15npu/sdxl/anima 管线、QNN 超分、--version/--log_level 的 QNN
// 实现）用 MAGICWX_WITH_QNN 条件编译：CMake 依据 QNN_SDK_ROOT 存在性门控；
// SDK 缺席的 CPU-only 构建中 QNN 类型以退出码 6 明确报错，优雅降级。
//
// 模型格式与文件布局（与参照一致，--type 决定一切）：
//   sd15cpu: tokenizer.json clip_v2.mnn pos_emb.bin token_emb.bin
//            unet.mnn vae_encoder.mnn vae_decoder.mnn
//   sd15npu: tokenizer.json clip_v2.mnn pos_emb.bin token_emb.bin
//            unet.bin vae_encoder.bin vae_decoder.bin [+分辨率 patch]
//   sdxl:    tokenizer.json clip.mnn pos_emb.bin token_emb.bin clip_2.mnn
//            pos_emb_2.bin token_emb_2.bin unet.bin vae_encoder.bin
//            vae_decoder.bin
//   anima:   tokenizer.json tokenizer_t5.json token_emb.bin clip.bin
//            unet_part1.bin unet_part2.bin vae_decoder.bin
//            [vae_encoder.bin]（可选；启用 img2img/inpaint）
// SD15/SDXL CLIP 走 MNN (CPU)；Anima 的 CLIP (clip.bin) 走 QNN/HTP
// （C++ 侧仍做 qwen token_emb 查表 -> input_embedding）。
//
// 端点契约（与 local-dream 一致）：
// - GET  /health   → 200 OK
// - POST /generate → text/event-stream；progress{step,total_steps,image?,format?}
//                    / complete{image(base64),format,seed,width,height,channels,
//                               generation_time_ms,first_step_time_ms}
//                    / error{message}
// - POST /tokenize → {count,max_length,overflow_offset}
// - POST /upscale  → image/jpeg；二进制 RGB + X-Image-* 头部；
//                    .mnn 走 MNN 超分，其余扩展名走 QNN 超分（仅 QNN 构建）

#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "httplib.h"   // 单文件 HTTP/SSE 服务
#include "json.hpp"    // nlohmann::json 请求解析

#include "Config.hpp"           // anima_text_seq_len 等全局常量
#include "MnnUtils.hpp"         // safety checker 用 MNN interpreter/session 工具
#include "Pipeline.hpp"         // 管线基类（generate/setSafetyChecker）
#include "PipelineSd15Cpu.hpp"  // SD1.5 CPU（MNN）管线
#include "RequestParser.hpp"    // /generate 请求解析
#include "SDUtils.hpp"          // base64 / 图像编解码 / resize
#include "SafeTensor2MNN.hpp"   // --convert：safetensors → MNN 转换
#include "TextEncoder.hpp"      // CLIP/T5 分词 + 嵌入表 + textual inversion
#include "Upscaler.hpp"         // /upscale：MNN/QNN 4x 分块超分

#ifdef MAGICWX_WITH_QNN
// QNN 构建专属：QNN 管线与运行时（CPU-only 构建不可编译，整体隔离）
#include "PipelineAnima.hpp"
#include "PipelineSd15Npu.hpp"
#include "PipelineSdxl.hpp"
#include "QnnRuntime.hpp"
// QNN SDK SampleApp：--version 构建号与 --log_level 解析
#include "BuildId.hpp"
#include "QnnSampleAppUtils.hpp"
#endif

namespace {

constexpr const char *kTag = "MagicWxImageBackend";

// 服务选项：与参照 ServerOptions 逐字段一致
struct ServerOptions {
  enum class ModelType { kSd15Cpu, kSd15Npu, kSdxl, kAnima };

  int port = 8081;
  std::string listen_address = "127.0.0.1";
  ModelType type = ModelType::kSd15Npu;  // 与参照默认值一致（Kotlin 侧必显式传 --type）
  std::string model_dir;
  std::string lib_dir;
  std::string patch_path;
  std::string safety_checker_path;
  float nsfw_threshold = 0.5f;
  bool use_v_pred = false;
  bool no_img2img = false;        // 完全跳过 VAE encoder
  bool lowram = false;            // (sdxl/anima) 按阶段加载/释放模型
  bool anima_seq_dit = false;     // (anima+lowram) 两半 DiT 绝不同时驻留
  bool upscaler_mode = false;     // 纯超分进程，不加载扩散模型
  bool convert_mode = false;      // safetensors → MNN 转换后退出
  bool convert_clip_skip_2 = false;

  bool isSdxl() const { return type == ModelType::kSdxl; }
  bool isAnima() const { return type == ModelType::kAnima; }
  bool isMnn() const { return type == ModelType::kSd15Cpu; }
};

// 帮助文本：与参照 showHelp 一致
void showHelp() {
  std::cout
      << "Usage:\n"
         "  stable_diffusion_core --type <sd15cpu|sd15npu|sdxl> "
         "--model_dir <dir> [--lib_dir <dir>] [options]\n"
         "  stable_diffusion_core --upscaler_mode [--lib_dir <dir>] "
         "[options]\n"
         "  stable_diffusion_core --convert <dir> [--clip_skip_2]\n"
         "\n"
         "Modes:\n"
         "  --type <type>          Model format: sd15cpu (MNN), sd15npu "
         "(QNN), sdxl (QNN), anima (QNN)\n"
         "  --upscaler_mode        Upscale-only server, no diffusion model\n"
         "  --convert <dir>        Convert model.safetensors in <dir> to MNN "
         "and exit\n"
         "\n"
         "Paths:\n"
         "  --model_dir <dir>      Directory with the fixed per-type model "
         "files\n"
         "  --lib_dir <dir>        Directory with libQnnHtp.so / "
         "libQnnSystem.so (QNN types)\n"
         "  --patch <file>         zstd resolution patch for unet.bin "
         "(sd15npu)\n"
         "  --safety_checker <f>   NSFW checker MNN model\n"
         "\n"
         "Options:\n"
         "  --port <n>             HTTP port (default 8081)\n"
         "  --listen_all           Listen on 0.0.0.0 instead of 127.0.0.1\n"
         "  --no_img2img           Do not load the VAE encoder\n"
         "  --use_v_pred           v-prediction model\n"
         "  --lowram               (sdxl/anima) load/release models per stage\n"
         "  --anima_seq_dit        (anima+lowram) never keep both DiT halves "
         "resident; for 12GB devices\n"
         "  --clip_skip_2          (convert) export CLIP with skip 2\n"
         "  --log_level <n>        QNN log level\n"
         "  --version              Print QNN SDK build id\n"
         "  --help                 Show this help\n";
}

// 参数错误：打印错误 + 帮助后以 EXIT_FAILURE 退出（与参照一致）
[[noreturn]] void showHelpAndExit(std::string &&error) {
  std::cerr << "ERROR: " << error << "\n";
  showHelp();
  std::exit(EXIT_FAILURE);
}

#ifndef MAGICWX_WITH_QNN
// CPU-only 构建：请求 QNN 管线/能力时打印明确错误并以退出码 6 退出
[[noreturn]] void exitQnnNotBuilt(const char *what) {
  __android_log_print(ANDROID_LOG_ERROR, kTag,
                      "%s 不可用：本构建未含 QNN 支持，需 QNN SDK 重编"
                      "（CMake MAGICWX_WITH_QNN + QNN_SDK_ROOT）",
                      what);
  std::cerr << "ERROR: " << what
            << " 不可用：本构建未含 QNN 支持，需 QNN SDK 重编"
               "（CMake MAGICWX_WITH_QNN + QNN_SDK_ROOT）\n";
  std::exit(6);
}
#endif

// 长选项定义表：与参照 OPTIONS 枚举 + s_longOptions 逐项对齐
struct LongOption {
  const char *name;  // 选项名（不含 -- 前缀）
  bool needs_arg;    // true = pal::required_argument
};
constexpr LongOption kLongOptions[] = {
    {"help", false},          {"version", false},
    {"type", true},           {"model_dir", true},
    {"lib_dir", true},        {"port", true},
    {"listen_all", false},    {"no_img2img", false},
    {"use_v_pred", false},    {"safety_checker", true},
    {"convert", true},        {"clip_skip_2", false},
    {"patch", true},          {"upscaler_mode", false},
    {"lowram", false},        {"anima_seq_dit", false},
    {"log_level", true},
};

// 命令行解析：行为与参照 pal::getOptLongOnly 流程逐项对齐——
// 支持 "--opt value" 与 "--opt=value" 两种形式；未知选项报错退出
//（替换 Phase 1 的静默忽略）；getopt_long 默认 permute 行为对非选项
// 位置参数静默忽略，此处同样忽略。CPU-only 构建没有 QNN SDK 的
// PAL/GetOpt.hpp，故手写等价解析，选项消费逻辑与参照完全一致。
ServerOptions processCommandLine(int argc, char **argv) {
  ServerOptions opts;
  std::string typeStr;

  for (int i = 1; i < argc; ++i) {
    const std::string arg = argv[i];
    // 非 -- 开头的位置参数：与 getopt_long permute 默认行为一致，忽略
    if (arg.rfind("--", 0) != 0) continue;

    // 拆分 "--name[=value]"
    std::string name = arg.substr(2);
    std::string value;
    bool has_inline_value = false;
    const auto eq = name.find('=');
    if (eq != std::string::npos) {
      value = name.substr(eq + 1);
      name = name.substr(0, eq);
      has_inline_value = true;
    }

    // 查选项表：未知选项 → 与参照 default 分支一致报错退出
    const LongOption *spec = nullptr;
    for (const auto &o : kLongOptions) {
      if (name == o.name) {
        spec = &o;
        break;
      }
    }
    if (!spec) showHelpAndExit("Invalid argument passed.");

    // 必需参数缺失 → getopt_long 同样走错误分支
    if (spec->needs_arg && !has_inline_value) {
      if (i + 1 >= argc) showHelpAndExit("Invalid argument passed.");
      value = argv[++i];
    }

    // ---- 选项消费（逐项对照参照 switch）----
    if (name == "help") {
      showHelp();
      std::exit(EXIT_SUCCESS);
    } else if (name == "version") {
#ifdef MAGICWX_WITH_QNN
      // 与参照一致：打印 QNN SDK 构建号
      std::cout << "QNN SDK " << qnn::tools::getBuildId() << "\n";
#else
      // CPU-only：无 QNN SDK，打印构建形态后正常退出
      std::cout << "QNN SDK 未包含在本构建中（MagicWX CPU-only）\n";
#endif
      std::exit(EXIT_SUCCESS);
    } else if (name == "type") {
      typeStr = value;
    } else if (name == "model_dir") {
      opts.model_dir = value;
    } else if (name == "lib_dir") {
      opts.lib_dir = value;
    } else if (name == "port") {
      opts.port = std::stoi(value);
    } else if (name == "listen_all") {
      opts.listen_address = "0.0.0.0";
    } else if (name == "no_img2img") {
      opts.no_img2img = true;
    } else if (name == "use_v_pred") {
      opts.use_v_pred = true;
    } else if (name == "safety_checker") {
      opts.safety_checker_path = value;
    } else if (name == "convert") {
      opts.convert_mode = true;
      opts.model_dir = value;
    } else if (name == "clip_skip_2") {
      opts.convert_clip_skip_2 = true;
    } else if (name == "patch") {
      opts.patch_path = value;
    } else if (name == "upscaler_mode") {
      opts.upscaler_mode = true;
    } else if (name == "lowram") {
      opts.lowram = true;
    } else if (name == "anima_seq_dit") {
      opts.anima_seq_dit = true;
    } else if (name == "log_level") {
#ifdef MAGICWX_WITH_QNN
      // 与参照一致：解析并设置 QNN 日志级别；非法级别 parseLogLevel 返回
      // QNN_LOG_LEVEL_MAX，参照行为为静默跳过
      QnnLog_Level_t logLevel = qnn::tools::sample_app::parseLogLevel(value);
      if (logLevel != QNN_LOG_LEVEL_MAX) {
        if (!qnn::log::setLogLevel(logLevel))
          showHelpAndExit("Unable to set log level.");
      }
#else
      // CPU-only：无 QNN 日志器；与参照"非法级别静默忽略"语义一致，
      // 此处对任意值仅告警不生效
      QNN_WARN("CPU-only 构建：--log_level %s 已忽略（无 QNN 日志器）",
               value.c_str());
#endif
    }
  }

  // 与参照一致：upscaler/convert 模式不需要 --type/--model_dir 校验
  if (opts.upscaler_mode || opts.convert_mode) return opts;

  // --type 校验与映射（与参照逐项一致）
  if (typeStr == "sd15cpu")
    opts.type = ServerOptions::ModelType::kSd15Cpu;
  else if (typeStr == "sdxl")
    opts.type = ServerOptions::ModelType::kSdxl;
  else if (typeStr == "sd15npu")
    opts.type = ServerOptions::ModelType::kSd15Npu;
  else if (typeStr == "anima")
    opts.type = ServerOptions::ModelType::kAnima;
  else
    showHelpAndExit(typeStr.empty() ? "Missing --type"
                                    : "Invalid --type: " + typeStr);
  if (opts.model_dir.empty()) showHelpAndExit("Missing --model_dir");
  return opts;
}

// --convert 模式：把模型目录内 model.safetensors（及旁边的 lora.N.safetensors）
// 转换为 MNN 格式后退出（与参照 runConvertMode 逐项一致）
void runConvertMode(const ServerOptions &opts) {
  if (!std::filesystem::exists(opts.model_dir)) {
    showHelpAndExit("Model directory does not exist: " + opts.model_dir);
  }
  std::string model_name = "model.safetensors";
  auto model_path = std::filesystem::path(opts.model_dir) / model_name;
  if (!std::filesystem::exists(model_path)) {
    showHelpAndExit("Model file does not exist");
  }

  // 收集 lora.N.safetensors 与配套权重文件 lora.N.weight（缺省权重 1.0）
  std::vector<std::string> loras;
  std::vector<float> lora_weights;
  for (int i = 1;; ++i) {
    std::string lora_filename = "lora." + std::to_string(i) + ".safetensors";
    auto lora_path = std::filesystem::path(opts.model_dir) / lora_filename;
    if (!std::filesystem::exists(lora_path)) {
      break;
    }
    loras.push_back(lora_filename);

    std::string weight_filename = "lora." + std::to_string(i) + ".weight";
    auto weight_path = std::filesystem::path(opts.model_dir) / weight_filename;
    float weight = 1.0f;

    if (std::filesystem::exists(weight_path)) {
      std::ifstream weight_file(weight_path);
      if (weight_file.is_open()) {
        weight_file >> weight;
        weight_file.close();
      }
    }
    lora_weights.push_back(weight);
  }

  generateMNNModels(opts.model_dir, model_name, opts.convert_clip_skip_2, loras,
                    lora_weights);
}

// 校验 --type 对应的固定文件布局并构造对应管线（未初始化）。
// 与参照 createPipeline 逐项一致；CPU-only 构建对 QNN 类型以退出码 6 报错。
std::unique_ptr<Pipeline> createPipeline(const ServerOptions &opts,
                                         TextEncoder &text_encoder) {
#ifndef MAGICWX_WITH_QNN
  // CPU-only：QNN 类型（sd15npu/sdxl/anima）在布局校验前统一退出码 6，
  // 保证报错信息明确而不是误报"缺少 .bin 文件"
  if (!opts.isMnn()) exitQnnNotBuilt("--type sd15npu/sdxl/anima");
#endif

  const std::filesystem::path dir(opts.model_dir);
  const bool sdxl = opts.isSdxl();
  const bool anima = opts.isAnima();

  // Anima: Qwen "CLIP" (clip.bin, QNN) + 分裂 DiT (unet_part1/2.bin) + 16ch
  // VAE。Qwen 文本编码器内部使用 RoPE，因此没有 pos_emb.bin。
  if (anima) {
#ifdef MAGICWX_WITH_QNN
    std::string clip_path = (dir / "clip.bin").string();
    std::string unet_part1_path = (dir / "unet_part1.bin").string();
    std::string unet_part2_path = (dir / "unet_part2.bin").string();
    std::string vae_decoder_path = (dir / "vae_decoder.bin").string();
    std::string vae_encoder_path =
        opts.no_img2img ? "" : (dir / "vae_encoder.bin").string();

    std::vector<std::string> required = {
        (dir / "tokenizer.json").string(),
        (dir / "tokenizer_t5.json").string(),
        clip_path,
        unet_part1_path,
        unet_part2_path,
        vae_decoder_path,
        (dir / "token_emb.bin").string(),
    };
    if (!vae_encoder_path.empty()) required.push_back(vae_encoder_path);
    for (const auto &p : required) {
      if (!std::filesystem::exists(p)) showHelpAndExit("File not found: " + p);
    }
    return std::make_unique<PipelineAnima>(
        text_encoder, opts.model_dir, clip_path, unet_part1_path,
        unet_part2_path, vae_decoder_path, vae_encoder_path, opts.lowram,
        opts.anima_seq_dit);
#else
    exitQnnNotBuilt("--type anima");  // 防御分支：上方已统一拦截
#endif
  }

  // sd15cpu 扩展名 .mnn，QNN 类型扩展名 .bin（与参照一致）
  const std::string ext = opts.isMnn() ? ".mnn" : ".bin";
  std::string clip_path = (dir / (sdxl ? "clip.mnn" : "clip_v2.mnn")).string();
  std::string clip2_path = sdxl ? (dir / "clip_2.mnn").string() : "";
  std::string unet_path = (dir / ("unet" + ext)).string();
  std::string vae_decoder_path = (dir / ("vae_decoder" + ext)).string();
  std::string vae_encoder_path =
      opts.no_img2img ? "" : (dir / ("vae_encoder" + ext)).string();

  std::vector<std::string> required = {
      (dir / "tokenizer.json").string(),
      clip_path,
      unet_path,
      vae_decoder_path,
      (dir / "pos_emb.bin").string(),
      (dir / "token_emb.bin").string(),
  };
  if (!vae_encoder_path.empty()) required.push_back(vae_encoder_path);
  if (sdxl) {
    required.push_back(clip2_path);
    required.push_back((dir / "pos_emb_2.bin").string());
    required.push_back((dir / "token_emb_2.bin").string());
  }
  for (const auto &p : required) {
    if (!std::filesystem::exists(p)) showHelpAndExit("File not found: " + p);
  }

  switch (opts.type) {
    case ServerOptions::ModelType::kSd15Cpu:
      return std::make_unique<PipelineSd15Cpu>(
          text_encoder, opts.model_dir, clip_path, unet_path, vae_decoder_path,
          vae_encoder_path, opts.use_v_pred);
#ifdef MAGICWX_WITH_QNN
    case ServerOptions::ModelType::kSd15Npu:
      return std::make_unique<PipelineSd15Npu>(
          text_encoder, opts.model_dir, clip_path, unet_path, vae_decoder_path,
          vae_encoder_path, opts.patch_path, opts.use_v_pred);
    case ServerOptions::ModelType::kSdxl:
    default:
      return std::make_unique<PipelineSdxl>(
          text_encoder, opts.model_dir, clip_path, clip2_path, unet_path,
          vae_decoder_path, vae_encoder_path, opts.use_v_pred, opts.lowram);
#else
    case ServerOptions::ModelType::kSd15Npu:
    case ServerOptions::ModelType::kSdxl:
    default:
      exitQnnNotBuilt("--type sd15npu/sdxl");  // 防御分支：上方已统一拦截
#endif
  }
}

// 按请求的 wire format 编码最终图像并 base64 包装（与参照 encodeResultImage 一致）
std::string encodeResultImage(const GenerationResult &result,
                              const std::string &format) {
  if (format == "jpeg") {
    auto jpeg = encodeJPEG(result.image_data, result.width, result.height, 95);
    return base64_encode(std::string(jpeg.begin(), jpeg.end()));
  }
  if (format == "png") {
    auto png = encodePNG(result.image_data, result.width, result.height);
    return base64_encode(std::string(png.begin(), png.end()));
  }
  // raw：直接 RGB 字节 base64
  return base64_encode(
      std::string(result.image_data.begin(), result.image_data.end()));
}

// 串行化生成：管线共享 MNN session 与全局 IO 维度，两个请求绝不能并发
// generate（如中止中的请求尚未收尾时新请求到达）——与参照 g_generation_mutex 一致
std::mutex g_generation_mutex;

// 注册 /generate 端点：SSE progress/complete/error，与参照逐字段对齐
void registerGenerateEndpoint(httplib::Server &svr, Pipeline *pipeline) {
  svr.Post("/generate", [pipeline](const httplib::Request &request,
                                   httplib::Response &res) {
    try {
      auto json = nlohmann::json::parse(request.body);
      auto req = std::make_shared<GenerationRequest>(parseGenerationRequest(
          json, pipeline->isSdxl(), pipeline->isAnima(),
          pipeline->supportsImg2Img(), pipeline->supportsUltrafix()));

      std::cout << "Req Rcvd: P:" << req->prompt
                << " NP:" << req->negative_prompt << " S:" << req->steps
                << " CFG:" << req->cfg << " Seed:" << req->seed
                << " Size:" << req->width << "x" << req->height
                << " Img2Img:" << req->img2img << " Mask:" << req->has_mask
                << " Ultrafix:" << req->ultrafix
                << " Denoise:" << req->denoise_strength
                << " ShowProcess:" << req->show_diffusion_process
                << " Stride:" << req->show_diffusion_stride << std::endl;
      res.set_header("Content-Type", "text/event-stream");
      res.set_header("Cache-Control", "no-cache");
      res.set_header("Connection", "keep-alive");
      res.set_chunked_content_provider(
          "text/event-stream",
          [pipeline, req](intptr_t, httplib::DataSink &sink) -> bool {
            try {
              std::lock_guard<std::mutex> generation_lock(g_generation_mutex);
              auto result = pipeline->generate(
                  *req, [&sink, &req](int s, int t, const std::string &img) {
                    nlohmann::json p = {
                        {"type", "progress"}, {"step", s}, {"total_steps", t}};
                    if (!img.empty()) {
                      p["image"] = img;
                      p["format"] = req->preview_format;
                    }
                    std::string ev =
                        "event: progress\ndata: " + p.dump() + "\n\n";
                    // 写入失败 = 客户端已断开（取消）。立即中止生成，
                    // 不把 NPU/CPU 烧在没人接收的结果上。
                    if (!sink.is_writable() ||
                        !sink.write(ev.c_str(), ev.size()))
                      throw std::runtime_error(
                          "Client disconnected, generation aborted");
                  });
              auto enc_start = std::chrono::high_resolution_clock::now();
              std::string enc_img =
                  encodeResultImage(result, req->output_format);
              auto enc_end = std::chrono::high_resolution_clock::now();
              std::cout
                  << "Enc time: "
                  << std::chrono::duration_cast<std::chrono::milliseconds>(
                         enc_end - enc_start)
                         .count()
                  << "ms\n";
              nlohmann::json c = {
                  {"type", "complete"},
                  {"image", enc_img},
                  {"format", req->output_format},
                  {"seed", req->seed},
                  {"width", result.width},
                  {"height", result.height},
                  {"channels", result.channels},
                  {"generation_time_ms", result.generation_time_ms},
                  {"first_step_time_ms", result.first_step_time_ms}};
              std::string ev = "event: complete\ndata: " + c.dump() + "\n\n";
              auto send_start = std::chrono::high_resolution_clock::now();
              sink.write(ev.c_str(), ev.size());
              auto send_end = std::chrono::high_resolution_clock::now();
              std::cout
                  << "Image send time: "
                  << std::chrono::duration_cast<std::chrono::milliseconds>(
                         send_end - send_start)
                         .count()
                  << "ms, size: " << ev.size() << " bytes\n";
              sink.done();
              return true;
            } catch (const std::exception &e) {
              nlohmann::json err = {{"type", "error"}, {"message", e.what()}};
              std::string ev = "event: error\ndata: " + err.dump() + "\n\n";
              sink.write(ev.c_str(), ev.size());
              sink.done();
              return false;
            }
          });
    } catch (const nlohmann::json::parse_error &e) {
      nlohmann::json err = {
          {"error",
           {{"message", "Invalid JSON: " + std::string(e.what())},
            {"type", "request_error"}}}};
      res.status = 400;
      res.set_content(err.dump(), "application/json");
    } catch (const std::invalid_argument &e) {
      nlohmann::json err = {
          {"error",
           {{"message", "Invalid Arg: " + std::string(e.what())},
            {"type", "request_error"}}}};
      res.status = 400;
      res.set_content(err.dump(), "application/json");
    } catch (const std::exception &e) {
      nlohmann::json err = {
          {"error",
           {{"message", "Server Err: " + std::string(e.what())},
            {"type", "server_error"}}}};
      res.status = 500;
      res.set_content(err.dump(), "application/json");
    }
  });
}

// 注册 /upscale 端点：二进制协议，性能优先（与参照 registerUpscaleEndpoint 一致）。
// .mnn → MNN 超分；其余扩展名 → QNN 超分（仅 QNN 构建；CPU-only 返回 400）。
void registerUpscaleEndpoint(httplib::Server &svr) {
  svr.Post("/upscale", [](const httplib::Request &req, httplib::Response &res) {
    std::unique_ptr<QnnModel> tempUpscalerApp = nullptr;

    try {
      if (!req.has_header("X-Image-Width")) {
        throw std::invalid_argument("Missing 'X-Image-Width' header");
      }
      if (!req.has_header("X-Image-Height")) {
        throw std::invalid_argument("Missing 'X-Image-Height' header");
      }
      if (!req.has_header("X-Upscaler-Path")) {
        throw std::invalid_argument("Missing 'X-Upscaler-Path' header");
      }

      int original_width = std::stoi(req.get_header_value("X-Image-Width"));
      int original_height = std::stoi(req.get_header_value("X-Image-Height"));
      std::string upscaler_path = req.get_header_value("X-Upscaler-Path");

      // X-Use-OpenCL 头存在则表示 MNN 模型走 OpenCL
      bool use_opencl = false;
      if (req.has_header("X-Use-OpenCL")) {
        std::string opencl_str = req.get_header_value("X-Use-OpenCL");
        use_opencl = (opencl_str == "true" || opencl_str == "1");
      }

      // 按文件扩展名判定模型类型（与参照一致：末 4 字符小写比较）
      bool is_mnn_model = false;
      if (upscaler_path.size() >= 4) {
        std::string ext = upscaler_path.substr(upscaler_path.size() - 4);
        std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
        is_mnn_model = (ext == ".mnn");
      }

      QNN_INFO("Binary upscale request: %dx%d, upscaler: %s, type: %s%s",
               original_width, original_height, upscaler_path.c_str(),
               is_mnn_model ? "MNN" : "QNN",
               is_mnn_model && use_opencl ? " (OpenCL)" : "");

      std::vector<uint8_t> image_data(req.body.begin(), req.body.end());

      if (image_data.size() != (size_t)original_width * original_height * 3) {
        throw std::invalid_argument(
            "Image data size mismatch. Expected " +
            std::to_string(original_width * original_height * 3) +
            " bytes, got " + std::to_string(image_data.size()) + " bytes");
      }

      // 预处理：最短边 < 192 时先放大到 192（tile 最小尺寸）
      const int min_size = 192;
      int process_width = original_width;
      int process_height = original_height;
      std::vector<uint8_t> process_image = image_data;

      if (std::min(original_width, original_height) < min_size) {
        QNN_INFO("Image too small (%dx%d), resizing to min edge %d",
                 original_width, original_height, min_size);
        process_image =
            resizeImageToMinSize(image_data, original_width, original_height,
                                 min_size, process_width, process_height);
        QNN_INFO("Resized to %dx%d for processing", process_width,
                 process_height);
      }

      auto start_time = std::chrono::high_resolution_clock::now();

      xt::xarray<uint8_t> upscaled;

      if (is_mnn_model) {
        // MNN 超分：两种构建均可用
        upscaled =
            upscaler::upscaleWithMnn(process_image, process_width,
                                     process_height, upscaler_path, use_opencl);
      } else {
#ifdef MAGICWX_WITH_QNN
        // QNN 超分：真 QnnModel（dlopen upscaler .bin/.so 上下文二进制）
        tempUpscalerApp = qnn_runtime::createModel(upscaler_path, "upscaler");
        if (!tempUpscalerApp) {
          throw std::runtime_error("Failed to create upscaler model from: " +
                                   upscaler_path);
        }

        auto status = qnn_runtime::initializeApp("Upscaler", tempUpscalerApp);
        if (status != EXIT_SUCCESS) {
          throw std::runtime_error("Failed to initialize upscaler model");
        }

        upscaled = upscaler::upscaleWithQnn(process_image, process_width,
                                            process_height, tempUpscalerApp);
#else
        // CPU-only：非 .mnn 超分模型不可用，返回 400 明确错误
        throw std::invalid_argument(
            "Non-.mnn upscaler requires QNN support; this build is CPU-only "
            "(本构建未含 QNN 支持，仅支持 .mnn 超分模型，需 QNN SDK 重编)");
#endif
      }

      auto end_time = std::chrono::high_resolution_clock::now();
      int duration = std::chrono::duration_cast<std::chrono::milliseconds>(
                         end_time - start_time)
                         .count();

      int upscaled_width = process_width * 4;
      int upscaled_height = process_height * 4;

      // 后处理：若输入曾被预放大，把输出回缩到原始宽高 4x
      int final_width = original_width * 4;
      int final_height = original_height * 4;
      std::vector<uint8_t> final_rgb(upscaled.begin(), upscaled.end());

      if (upscaled_width != final_width || upscaled_height != final_height) {
        QNN_INFO("Resizing output from %dx%d to %dx%d", upscaled_width,
                 upscaled_height, final_width, final_height);
        final_rgb =
            resizeImageToTarget(final_rgb, upscaled_width, upscaled_height,
                                final_width, final_height);
      }

      auto encode_start = std::chrono::high_resolution_clock::now();
      std::vector<uint8_t> output_jpeg =
          encodeJPEG(final_rgb, final_width, final_height, 95);
      auto encode_end = std::chrono::high_resolution_clock::now();
      int encode_duration =
          std::chrono::duration_cast<std::chrono::milliseconds>(encode_end -
                                                                encode_start)
              .count();

      QNN_INFO("Upscaling completed in %d ms: %dx%d -> %dx%d", duration,
               original_width, original_height, final_width, final_height);
      QNN_INFO("JPEG encoding time: %d ms, size: %zu KB", encode_duration,
               output_jpeg.size() / 1024);

      res.status = 200;
      res.set_content(std::string(output_jpeg.begin(), output_jpeg.end()),
                      "image/jpeg");
      res.set_header("X-Output-Width", std::to_string(final_width));
      res.set_header("X-Output-Height", std::to_string(final_height));
      res.set_header("X-Duration-Ms", std::to_string(duration));
      res.set_header("Access-Control-Expose-Headers",
                     "X-Output-Width,X-Output-Height,X-Duration-Ms");

      if (tempUpscalerApp) {
        tempUpscalerApp.reset();
        QNN_INFO("Upscaler model released");
      }

    } catch (const std::invalid_argument &e) {
      tempUpscalerApp.reset();
      nlohmann::json err = {
          {"error",
           {{"message", "Invalid Arg: " + std::string(e.what())},
            {"type", "request_error"}}}};
      res.status = 400;
      res.set_content(err.dump(), "application/json");
    } catch (const std::exception &e) {
      tempUpscalerApp.reset();
      nlohmann::json err = {
          {"error",
           {{"message", "Server Err: " + std::string(e.what())},
            {"type", "server_error"}}}};
      res.status = 500;
      res.set_content(err.dump(), "application/json");
    }
  });
}

// 注册 /tokenize 端点：返回 token 计数与溢出偏移（与参照一致）
void registerTokenizeEndpoint(httplib::Server &svr, TextEncoder *text_encoder) {
  svr.Post("/tokenize", [text_encoder](const httplib::Request &req,
                                       httplib::Response &res) {
    try {
      auto json = nlohmann::json::parse(req.body);
      std::string text = json.value("prompt", std::string());
      // Anima 用 T5 分词器按上下文长度 512 计数，远长于 CLIP 的 77
      const int max_len = text_encoder->isAnima() ? anima_text_seq_len : 77;

      TokenizeInfo info = text_encoder->tokenizeInfo(text, max_len);

      nlohmann::json resp = {{"count", info.count},
                             {"max_length", max_len},
                             {"overflow_offset", info.overflow_offset}};
      res.status = 200;
      res.set_content(resp.dump(), "application/json");
    } catch (const std::exception &e) {
      nlohmann::json err = {
          {"error",
           {{"message", std::string(e.what())}, {"type", "tokenize_error"}}}};
      res.status = 400;
      res.set_content(err.dump(), "application/json");
    }
  });
}

}  // namespace

// 进程入口：与参照 main 逐项对齐——
// 初始化日志 → 解析参数 → convert 模式短路 → upscaler 轻量进程 / 完整管线 →
// HTTP 服务 → 清理
int main(int argc, char **argv) {
#ifdef MAGICWX_WITH_QNN
  // QNN 日志子系统初始化（CPU-only 构建使用 logcat 桩，无需初始化）
  if (!qnn::log::initializeLogging()) {
    std::cerr << "ERROR: Init logging failed!\n";
    return EXIT_FAILURE;
  }
#endif
  ServerOptions opts = processCommandLine(argc, argv);

  // convert 模式：转换完成即退出，不启动服务
  if (opts.convert_mode) {
    runConvertMode(opts);
    return EXIT_SUCCESS;
  }

  std::unique_ptr<TextEncoder> text_encoder;
  std::unique_ptr<Pipeline> pipeline;
  MNN::Interpreter *safety_interpreter = nullptr;
  MNN::Session *safety_session = nullptr;

  if (opts.upscaler_mode) {
    // 纯超分轻量进程：跳过扩散模型加载，只注册 /upscale
    QNN_INFO("Upscaler mode - skipping MNN and QNN model initialization");
#ifdef MAGICWX_WITH_QNN
    // QNN 超分需要 --lib_dir；MNN-only 超分无需
    if (!opts.lib_dir.empty() && !qnn_runtime::init(opts.lib_dir))
      showHelpAndExit("Failed get QNN system func ptrs.");
#else
    if (!opts.lib_dir.empty())
      QNN_WARN("CPU-only 构建：--lib_dir 已忽略（无 QNN），仅 .mnn 超分可用");
#endif
  } else {
    // 文本编码器：按类型选择 CLIP/双 CLIP/Qwen 路径
    text_encoder = std::make_unique<TextEncoder>(opts.isSdxl(), opts.isAnima());
    try {
      const std::filesystem::path mdir(opts.model_dir);
      text_encoder->loadTokenizer((mdir / "tokenizer.json").string());
      // Anima 的 LLM 适配器由第二个 (T5) 分词器喂入
      if (opts.isAnima())
        text_encoder->loadT5Tokenizer((mdir / "tokenizer_t5.json").string());
    } catch (const std::exception &e) {
      std::cerr << "Failed load tokenizer: " << e.what() << std::endl;
      return EXIT_FAILURE;
    }

    pipeline = createPipeline(opts, *text_encoder);
    text_encoder->loadEmbeddingTables(opts.model_dir);

    // Textual-inversion embeddings 位于模型目录上两级（审计项 F6，与参照同路径约定）
    std::filesystem::path embeddingsPath =
        std::filesystem::path(opts.model_dir).parent_path().parent_path() /
        "embeddings";
    if (std::filesystem::exists(embeddingsPath)) {
      try {
        text_encoder->loadTextualInversions(embeddingsPath.string());
        QNN_INFO("Loaded %zu embeddings (SDXL=%d) from %s",
                 text_encoder->embeddingCount(), opts.isSdxl() ? 1 : 0,
                 embeddingsPath.string().c_str());
      } catch (const std::exception &e) {
        QNN_WARN("Failed to load embeddings: %s", e.what());
      }
    } else {
      QNN_INFO("Embeddings directory not found: %s",
               embeddingsPath.string().c_str());
    }

    // safety checker：--safety_checker 指定 MNN NSFW 模型（审计项 F7）
    if (!opts.safety_checker_path.empty()) {
      safety_interpreter =
          createMnnInterpreterMmap(opts.safety_checker_path.c_str());
      if (!safety_interpreter)
        showHelpAndExit("Failed load Safety MNN: " + opts.safety_checker_path);
      MnnSessionOptions safety_opts;
      safety_opts.num_threads = 1;
      safety_session = createMnnSession(safety_interpreter, safety_opts);
      if (!safety_session) {
        QNN_ERROR("Failed create persistent MNN Safety session!");
      } else {
        QNN_INFO("Persistent MNN Safety session created.");
        auto input =
            safety_interpreter->getSessionInput(safety_session, nullptr);
        safety_interpreter->resizeTensor(input, {1, 224, 224, 3});
        safety_interpreter->resizeSession(safety_session);
        safety_interpreter->releaseModel();
      }
      pipeline->setSafetyChecker(safety_interpreter, safety_session,
                                 opts.nsfw_threshold);
    }

    // QNN 类型：初始化 QNN 运行时（libQnnHtp.so / libQnnSystem.so）
    if (!opts.isMnn()) {
#ifdef MAGICWX_WITH_QNN
      if (opts.lib_dir.empty()) showHelpAndExit("Missing --lib_dir for QNN");
      if (!qnn_runtime::init(opts.lib_dir))
        showHelpAndExit("Failed get QNN system func ptrs.");
#else
      exitQnnNotBuilt("--type sd15npu/sdxl/anima");  // 防御分支：createPipeline 已拦截
#endif
    }

    if (!pipeline->initialize()) {
      std::cerr << "ERROR: Pipeline initialization failed!\n";
      return EXIT_FAILURE;
    }
  }

  // --- HTTP 服务（与参照一致的默认头/CORS/端点注册）---
  httplib::Server svr;
  svr.set_default_headers({
      {"Access-Control-Allow-Origin", "*"},
      {"Access-Control-Allow-Methods", "GET, POST, OPTIONS"},
      {"Access-Control-Allow-Headers", "Content-Type, Authorization"},
      {"Access-Control-Max-Age", "86400"},
  });
  svr.Options(R"(.*)", [](const httplib::Request &, httplib::Response &res) {
    res.status = 204;
  });
  svr.Get("/health", [](const httplib::Request &, httplib::Response &res) {
    res.status = 200;
  });

  // upscaler 轻量进程只注册 /upscale；完整进程三者齐备
  if (pipeline) registerGenerateEndpoint(svr, pipeline.get());
  registerUpscaleEndpoint(svr);
  if (text_encoder) registerTokenizeEndpoint(svr, text_encoder.get());

  std::cout << "Server listening on " << opts.listen_address << ":" << opts.port
            << std::endl;
  __android_log_print(ANDROID_LOG_INFO, kTag, "图片后端已启动 %s:%d",
                      opts.listen_address.c_str(), opts.port);
  svr.listen(opts.listen_address.c_str(), opts.port);

  // --- 清理（与参照一致）---
  pipeline.reset();
  if (safety_session) safety_interpreter->releaseSession(safety_session);
  delete safety_interpreter;

  return EXIT_SUCCESS;
}

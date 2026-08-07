#ifndef MNNUTILS_HPP
#define MNNUTILS_HPP

#include <MNN/MNNDefine.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <MNN/Interpreter.hpp>
#include <filesystem>
#include <string>
#include <system_error>

// Returns "{model_dir}/cache", creating it if needed. Returns "" when
// model_dir is empty or directory creation fails; callers must treat that
// as "caching disabled for this run".
inline std::string ensureCacheDir(const std::string &model_dir) {
  if (model_dir.empty()) return "";
  std::filesystem::path p = std::filesystem::path(model_dir) / "cache";
  std::error_code ec;
  std::filesystem::create_directories(p, ec);
  if (ec) return "";
  return p.string();
}

// Load an MNN model via mmap + createFromBuffer instead of createFromFile.
// createFromFile reads the whole .mnn in 4 KB chunks and then merges them into
// one contiguous buffer, transiently holding ~2x the model size in anonymous
// (non-reclaimable) memory. Mapping the file read-only keeps that source as
// clean, file-backed pages the kernel can reclaim under pressure, so the peak
// anonymous footprint during load drops to the single owned buffer MNN copies
// into. createFromBuffer copies the bytes, so the mapping can be released right
// away. Falls back to createFromFile on any mmap-path failure.
inline MNN::Interpreter *createMnnInterpreterMmap(const char *path) {
  int fd = open(path, O_RDONLY);
  if (fd < 0) {
    return MNN::Interpreter::createFromFile(path);
  }
  struct stat st{};
  if (0 != fstat(fd, &st) || st.st_size <= 0) {
    close(fd);
    return MNN::Interpreter::createFromFile(path);
  }
  size_t size = static_cast<size_t>(st.st_size);
  void *mapped = mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
  // The mapping holds its own file reference, so the fd can be closed now.
  close(fd);
  if (MAP_FAILED == mapped) {
    return MNN::Interpreter::createFromFile(path);
  }
  // MNN copies the whole buffer once, sequentially; hint readahead to match.
  madvise(mapped, size, MADV_SEQUENTIAL);
  MNN::Interpreter *interpreter =
      MNN::Interpreter::createFromBuffer(mapped, size);
  munmap(mapped, size);
  if (interpreter) {
    // createFromFile sets a default external weight path; createFromBuffer does
    // not. Mirror it so models that store weights in a companion ".weight" file
    // still resolve them at session creation. Harmless when no such file
    // exists.
    interpreter->setExternalFile((std::string(path) + ".weight").c_str());
  }
  return interpreter;
}

// Session creation options shared by every MNN model in the pipeline.
// CPU: 4 threads, low-memory, high-power. OpenCL: fast tuning + buffer mode
// with low precision, plus an on-disk tuning cache when cache_file is set.
struct MnnSessionOptions {
  bool use_opencl = false;
  std::string cache_file;  // OpenCL tuning cache path ("" = no cache file)
  int num_threads = 4;
  // MagicWX：CPU 是否走 fp16（Precision_Low，MNN_ARM82 已静态链入）。
  // UNet 耗时占比 >95% 且实测多步 latent 轨迹与 fp32 一致（数值耐受），保持 fp16；
  // VAE 解码在 SM-A566E + AnythingV5 实测：cfg 放大后 latent absmax≈9 时 fp16
  // 数值崩塌 → 输出纯黑图，同参 fp32 出图完美，故 VAE 阶段调用方置 false。
  bool cpu_low_precision = true;
};

// Creates a session with the standard pipeline configuration. The interpreter
// keeps no reference to the local configs after createSession returns.
inline MNN::Session *createMnnSession(MNN::Interpreter *interpreter,
                                      const MnnSessionOptions &opts) {
  MNN::ScheduleConfig config;
  MNN::BackendConfig backendConfig;
  if (opts.use_opencl) {
    if (!opts.cache_file.empty()) {
      interpreter->setCacheFile(opts.cache_file.c_str());
    }
    config.type = MNN_FORWARD_OPENCL;
    config.mode = MNN_GPU_MEMORY_BUFFER | MNN_GPU_TUNING_FAST;
    backendConfig.precision = MNN::BackendConfig::Precision_Low;
  } else {
    config.type = MNN_FORWARD_CPU;
    config.numThread = opts.num_threads;
    backendConfig.memory = MNN::BackendConfig::Memory_Low;
    // MagicWX：CPU fp16（MNN_ARM82 已静态链入）仅对 UNet 开启——UNet 占 >95% 耗时且
    // 实测多步 latent 轨迹与 fp32 一致。VAE 编/解码与 CLIP 在 latent absmax≈9 时 fp16
    // 数值崩塌（SM-A566E + AnythingV5 实测纯黑图，同参 fp32 出图完美），调用方置
    // cpu_low_precision=false 走 fp32。
    if (opts.cpu_low_precision) {
      backendConfig.precision = MNN::BackendConfig::Precision_Low;
    }
  }
  backendConfig.power = MNN::BackendConfig::Power_High;
  config.backendConfig = &backendConfig;
  return interpreter->createSession(config);
}

#endif  // MNNUTILS_HPP

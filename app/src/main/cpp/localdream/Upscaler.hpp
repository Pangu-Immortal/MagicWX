#ifndef UPSCALER_HPP
#define UPSCALER_HPP

#include <MNN/Interpreter.hpp>
#include <functional>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include <xtensor/xadapt.hpp>
#include <xtensor/xarray.hpp>
#include <xtensor/xbuilder.hpp>
#include <xtensor/xmanipulation.hpp>
#include <xtensor/xmath.hpp>
#include <xtensor/xview.hpp>

#include "Logger.hpp"
#include "MnnUtils.hpp"
// QnnModel 来源宏切换：
// - QNN 构建（MAGICWX_WITH_QNN，CMake 依据 QNN_SDK_ROOT 门控）用真 QnnModel.hpp，
//   upscaleWithQnn 走真实 QNN/HTP 图执行（与 local-dream 参照一致）；
// - CPU-only 构建用桩头 QnnModelStub.hpp：upscaleWithQnn 编译通过但恒不可用
//   （executeUpscalerGraphs 恒返回 FAILURE），仅 upscaleWithMnn 被实际使用。
#ifdef MAGICWX_WITH_QNN
#include "QnnModel.hpp"
#else
#include "QnnModelStub.hpp"
#endif
#include "Tiling.hpp"

namespace upscaler {

constexpr int kTileSize = 192;
constexpr int kOutputTileSize = 768;
constexpr int kMinOverlap = 12;
constexpr int kScaleFactor = 4;

// Shared 4x tiled upscale driver. `run_tile` receives one CHW float tile
// (1,3,192,192) and must fill the (1,3,768,768) output tile.
inline xt::xarray<uint8_t> upscaleTiled(
    const std::vector<uint8_t> &input_image, int width, int height,
    const std::function<void(const std::vector<float> &in,
                             std::vector<float> &out)> &run_tile) {
  auto x_coords = calculate_tile_positions(width, kTileSize, kMinOverlap);
  auto y_coords = calculate_tile_positions(height, kTileSize, kMinOverlap);
  int num_tiles_w = x_coords.size();
  int num_tiles_h = y_coords.size();

  int output_w = width * kScaleFactor;
  int output_h = height * kScaleFactor;

  std::vector<int> input_shape = {1, height, width, 3};
  xt::xarray<uint8_t> input_hwc_u8 = xt::adapt(input_image, input_shape);
  xt::xarray<float> input_hwc_f32 = xt::cast<float>(input_hwc_u8) / 255.0f;
  xt::xarray<float> input_chw =
      xt::transpose(input_hwc_f32, {0, 3, 1, 2});  // (1, 3, H, W)

  std::vector<int> output_shape = {1, 3, output_h, output_w};
  xt::xarray<float> accumulated_output = xt::zeros<float>(output_shape);
  xt::xarray<float> weight_map = xt::zeros<float>({output_h, output_w});

  // Upscaler tiles fade on all four edges (image borders are handled by the
  // weight normalization), unlike the VAE blend which keeps borders solid.
  int output_overlap = kMinOverlap * kScaleFactor;
  int fade_size = output_overlap / 2;
  xt::xarray<float> tile_weight =
      xt::ones<float>({kOutputTileSize, kOutputTileSize});

  if (fade_size > 0) {
    for (int i = 0; i < fade_size; ++i) {
      float alpha = static_cast<float>(i + 1) / fade_size;
      xt::view(tile_weight, i, xt::all()) *= alpha;
      xt::view(tile_weight, kOutputTileSize - 1 - i, xt::all()) *= alpha;
      xt::view(tile_weight, xt::all(), i) *= alpha;
      xt::view(tile_weight, xt::all(), kOutputTileSize - 1 - i) *= alpha;
    }
  }

  int tile_count = 0;
  for (int y : y_coords) {
    for (int x : x_coords) {
      xt::xarray<float> input_tile =
          xt::view(input_chw, 0, xt::all(), xt::range(y, y + kTileSize),
                   xt::range(x, x + kTileSize));

      std::vector<float> tile_input_vec(input_tile.begin(), input_tile.end());
      std::vector<float> tile_output_vec((size_t)1 * 3 * kOutputTileSize *
                                         kOutputTileSize);

      run_tile(tile_input_vec, tile_output_vec);

      std::vector<int> tile_output_shape = {1, 3, kOutputTileSize,
                                            kOutputTileSize};
      xt::xarray<float> output_tile =
          xt::adapt(tile_output_vec, tile_output_shape);

      int out_x = x * kScaleFactor;
      int out_y = y * kScaleFactor;

      for (int c = 0; c < 3; ++c) {
        auto acc_slice = xt::view(accumulated_output, 0, c,
                                  xt::range(out_y, out_y + kOutputTileSize),
                                  xt::range(out_x, out_x + kOutputTileSize));
        auto tile_slice = xt::view(output_tile, 0, c, xt::all(), xt::all());
        acc_slice += tile_slice * tile_weight;
      }

      auto weight_slice =
          xt::view(weight_map, xt::range(out_y, out_y + kOutputTileSize),
                   xt::range(out_x, out_x + kOutputTileSize));
      weight_slice += tile_weight;

      tile_count++;
      std::cout << "Processed tile " << tile_count << "/"
                << (num_tiles_w * num_tiles_h) << std::endl;
    }
  }

  weight_map = xt::maximum(weight_map, 1e-8f);
  xt::xarray<float> weight_expanded =
      xt::reshape_view(weight_map, {1, 1, output_h, output_w});

  xt::xarray<float> normalized_output = accumulated_output / weight_expanded;

  auto output_hwc = xt::transpose(normalized_output, {0, 2, 3, 1});
  auto output_clamped = xt::clip(output_hwc, 0.0f, 1.0f);
  auto output_normalized = output_clamped * 255.0f;
  xt::xarray<uint8_t> output_uint8 = xt::cast<uint8_t>(output_normalized);

  return output_uint8;
}

inline xt::xarray<uint8_t> upscaleWithQnn(
    const std::vector<uint8_t> &input_image, int width, int height,
    std::unique_ptr<QnnModel> &upscaler_model) {
  if (!upscaler_model) {
    throw std::runtime_error("Upscaler model not provided");
  }

  QNN_INFO("Upscaling %dx%d to %dx%d using %s (variable overlap)", width,
           height, width * kScaleFactor, height * kScaleFactor, "QNN");

  return upscaleTiled(
      input_image, width, height,
      [&](const std::vector<float> &in, std::vector<float> &out) {
        if (StatusCode::SUCCESS !=
            upscaler_model->executeUpscalerGraphs(
                const_cast<float *>(in.data()), out.data())) {
          throw std::runtime_error("Upscaler execution failed for tile");
        }
      });
}

inline xt::xarray<uint8_t> upscaleWithMnn(
    const std::vector<uint8_t> &input_image, int width, int height,
    const std::string &model_path, bool use_opencl) {
  auto interpreter = std::shared_ptr<MNN::Interpreter>(
      createMnnInterpreterMmap(model_path.c_str()));
  if (!interpreter) {
    throw std::runtime_error("Failed to create MNN interpreter from: " +
                             model_path);
  }

  MnnSessionOptions opts;
  // MagicWX：超分模型强制 fp32——MnnSessionOptions 默认 cpu_low_precision=true 会让
  // ESRGAN 类深卷积栈走 fp16，已有 VAE fp16 数值崩塌出黑图的实测教训，超分未做
  // fp16 耐受验证，保守锁定 fp32（与参照 Precision_Normal 行为一致）。
  opts.cpu_low_precision = false;
  opts.use_opencl = use_opencl;
  if (use_opencl) {
    auto cache_dir = ensureCacheDir(
        std::filesystem::path(model_path).parent_path().string());
    opts.cache_file =
        (cache_dir.empty()
             ? model_path
             : cache_dir + "/" +
                   std::filesystem::path(model_path).filename().string()) +
        ".mnnc";
  }

  auto session = createMnnSession(interpreter.get(), opts);
  if (!session) {
    throw std::runtime_error("Failed to create MNN session");
  }

  QNN_INFO("Upscaling %dx%d to %dx%d using MNN (%s)", width, height,
           width * kScaleFactor, height * kScaleFactor,
           use_opencl ? "OpenCL" : "CPU");

  auto input_tensor = interpreter->getSessionInput(session, nullptr);
  auto output_tensor = interpreter->getSessionOutput(session, nullptr);

  return upscaleTiled(
      input_image, width, height,
      [&](const std::vector<float> &in, std::vector<float> &out) {
        std::vector<int> dims = {1, 3, kTileSize, kTileSize};
        interpreter->resizeTensor(input_tensor, dims);
        interpreter->resizeSession(session);

        auto host_tensor = MNN::Tensor::create<float>(
            dims, const_cast<float *>(in.data()), MNN::Tensor::CAFFE);
        input_tensor->copyFromHostTensor(host_tensor);
        delete host_tensor;

        if (interpreter->runSession(session) != 0) {
          throw std::runtime_error("MNN inference failed for tile");
        }

        auto output_host =
            MNN::Tensor::create<float>({1, 3, kOutputTileSize, kOutputTileSize},
                                       nullptr, MNN::Tensor::CAFFE);
        output_tensor->copyToHostTensor(output_host);
        memcpy(out.data(), output_host->host<float>(),
               out.size() * sizeof(float));
        delete output_host;
      });
}

}  // namespace upscaler

#endif  // UPSCALER_HPP

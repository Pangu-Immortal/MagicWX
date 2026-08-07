#ifndef TILING_HPP
#define TILING_HPP

#include <algorithm>
#include <cmath>
#include <stdexcept>
#include <tuple>
#include <utility>
#include <vector>
#include <xtensor/xarray.hpp>
#include <xtensor/xbuilder.hpp>
#include <xtensor/xeval.hpp>
#include <xtensor/xmanipulation.hpp>
#include <xtensor/xmath.hpp>
#include <xtensor/xrandom.hpp>
#include <xtensor/xview.hpp>

#include "Config.hpp"

// Separable Gaussian blur over the spatial dims of a [1, C, H, W] latent,
// with edge replication at the borders. Used to split a latent into its
// low-frequency (blurred) and high-frequency (residual) bands. `radius` is
// in latent cells; `sigma <= 0` derives a sigma from the radius.
inline xt::xarray<float> gaussian_blur_latent(const xt::xarray<float> &input,
                                              int radius, float sigma = -1.0f) {
  if (radius < 1) return input;
  const int channels = static_cast<int>(input.shape()[1]);
  const int height = static_cast<int>(input.shape()[2]);
  const int width = static_cast<int>(input.shape()[3]);
  if (sigma <= 0.0f) sigma = static_cast<float>(radius) / 2.0f;

  std::vector<float> kernel(2 * radius + 1);
  float ksum = 0.0f;
  for (int i = -radius; i <= radius; ++i) {
    float v = std::exp(-static_cast<float>(i * i) / (2.0f * sigma * sigma));
    kernel[i + radius] = v;
    ksum += v;
  }
  for (float &v : kernel) v /= ksum;

  xt::xarray<float> tmp = xt::zeros<float>({1, channels, height, width});
  xt::xarray<float> out = xt::zeros<float>({1, channels, height, width});
  const float *src = input.data();
  float *mid = tmp.data();
  float *dst = out.data();

  auto clampi = [](int v, int lo, int hi) {
    return v < lo ? lo : (v > hi ? hi : v);
  };

  for (int c = 0; c < channels; ++c) {
    const size_t plane = static_cast<size_t>(c) * height * width;
    // Horizontal pass: src -> mid.
    for (int y = 0; y < height; ++y) {
      const float *row = src + plane + static_cast<size_t>(y) * width;
      float *row_out = mid + plane + static_cast<size_t>(y) * width;
      for (int x = 0; x < width; ++x) {
        float acc = 0.0f;
        for (int i = -radius; i <= radius; ++i) {
          acc += kernel[i + radius] * row[clampi(x + i, 0, width - 1)];
        }
        row_out[x] = acc;
      }
    }
    // Vertical pass: mid -> dst.
    for (int y = 0; y < height; ++y) {
      float *row_out = dst + plane + static_cast<size_t>(y) * width;
      for (int x = 0; x < width; ++x) {
        float acc = 0.0f;
        for (int i = -radius; i <= radius; ++i) {
          acc += kernel[i + radius] *
                 mid[plane +
                     static_cast<size_t>(clampi(y + i, 0, height - 1)) * width +
                     x];
        }
        row_out[x] = acc;
      }
    }
  }
  return out;
}

// QnnModel graph execution sizes its IO from the global output_/sample_
// dimensions in Config.hpp. Tiled VAE passes run the fixed-size tile graph
// while the request-level dimensions describe the full image, so the globals
// must be temporarily swapped to tile size. This guard restores them on every
// exit path.
struct ScopedSizeOverride {
  ScopedSizeOverride(int new_output_w, int new_output_h, int new_sample_w,
                     int new_sample_h)
      : output_w_(output_width),
        output_h_(output_height),
        sample_w_(sample_width),
        sample_h_(sample_height) {
    output_width = new_output_w;
    output_height = new_output_h;
    sample_width = new_sample_w;
    sample_height = new_sample_h;
  }
  ~ScopedSizeOverride() {
    output_width = output_w_;
    output_height = output_h_;
    sample_width = sample_w_;
    sample_height = sample_h_;
  }
  ScopedSizeOverride(const ScopedSizeOverride &) = delete;
  ScopedSizeOverride &operator=(const ScopedSizeOverride &) = delete;

 private:
  int output_w_, output_h_, sample_w_, sample_h_;
};

// Evenly spreads tiles of `tile_size` across `dimension` with at least
// `min_overlap` between neighbours; the last tile is clamped to the edge.
inline std::vector<int> calculate_tile_positions(int dimension, int tile_size,
                                                 int min_overlap) {
  if (dimension <= tile_size) {
    return {0};
  }

  int num_tiles = 1;
  int effective_tile_size = tile_size - min_overlap;
  if (dimension > tile_size) {
    num_tiles +=
        (dimension - tile_size + effective_tile_size - 1) / effective_tile_size;
  }

  std::vector<int> positions;
  positions.reserve(num_tiles);
  positions.push_back(0);

  if (num_tiles == 1) {
    return positions;
  }

  int total_distance = dimension - tile_size;
  int num_strides = num_tiles - 1;

  int base_stride = total_distance / num_strides;
  int remainder = total_distance % num_strides;

  int current_pos = 0;
  for (int i = 0; i < num_strides; ++i) {
    int stride = base_stride + (i < remainder ? 1 : 0);
    current_pos += stride;
    positions.push_back(current_pos);
  }

  positions.back() = dimension - tile_size;

  return positions;
}

// Calculate tile positions and overlaps for VAE encoder/decoder.
// `vae_tile_size` is the fixed graph size in pixel space (512 for SD1.5 NPU,
// 1024 for SDXL); the minimum overlap is a quarter tile in latent space.
// The grid is computed in latent space and scaled up: latent coordinates are
// exact (image dimensions are 8-aligned), so the pixel tile fed to the
// encoder and the latent slot its output is blended into always describe the
// same region. Spreading in pixel space first and dividing would truncate
// and desync the two grids by up to 7px.
// Returns: {pixel_positions, latent_positions, pixel_overlap_x,
// pixel_overlap_y, latent_overlap_x, latent_overlap_y}
inline std::tuple<std::vector<std::pair<int, int>>,
                  std::vector<std::pair<int, int>>, int, int, int, int>
calculate_vae_tile_positions(int pixel_width, int pixel_height,
                             int vae_tile_size) {
  const int scale_factor = 8;  // VAE pixel/latent scale
  const int vae_latent_tile_size = vae_tile_size / scale_factor;
  const int min_latent_overlap = vae_latent_tile_size / 4;

  auto latent_x_coords = calculate_tile_positions(
      pixel_width / scale_factor, vae_latent_tile_size, min_latent_overlap);
  auto latent_y_coords = calculate_tile_positions(
      pixel_height / scale_factor, vae_latent_tile_size, min_latent_overlap);

  std::vector<std::pair<int, int>> pixel_positions;
  std::vector<std::pair<int, int>> latent_positions;
  pixel_positions.reserve(latent_x_coords.size() * latent_y_coords.size());
  latent_positions.reserve(latent_x_coords.size() * latent_y_coords.size());

  for (int ly : latent_y_coords) {
    for (int lx : latent_x_coords) {
      latent_positions.push_back({lx, ly});
      pixel_positions.push_back({lx * scale_factor, ly * scale_factor});
    }
  }

  int latent_overlap_x = 0;
  int latent_overlap_y = 0;
  if (latent_x_coords.size() > 1) {
    latent_overlap_x =
        vae_latent_tile_size - (latent_x_coords[1] - latent_x_coords[0]);
  }
  if (latent_y_coords.size() > 1) {
    latent_overlap_y =
        vae_latent_tile_size - (latent_y_coords[1] - latent_y_coords[0]);
  }

  return {pixel_positions,
          latent_positions,
          latent_overlap_x * scale_factor,
          latent_overlap_y * scale_factor,
          latent_overlap_x,
          latent_overlap_y};
}

// Row-major (x, y) tile grid over a latent plane plus the per-axis overlap
// of the first tile pair. Used for tiled UNet steps where the graph runs at
// a fixed latent tile size.
//
// Positions are snapped down to multiples of `align`, the UNet's internal
// downsampling factor: neighbouring tiles then see the overlap content at
// the same feature-grid phase. With unaligned strides (e.g. a 3072px-wide
// SDXL image spreads to strides 86/85) the two tiles' noise predictions are
// systematically phase-shifted against each other and their blend ghosts.
// The first/last positions stay clamped to the edges so coverage never
// depends on the dimension itself being aligned.
inline std::tuple<std::vector<std::pair<int, int>>, int, int>
calculate_latent_tile_grid(int latent_w, int latent_h, int tile_size,
                           int min_overlap, int align) {
  auto aligned_positions = [&](int dimension) {
    auto coords = calculate_tile_positions(dimension, tile_size, min_overlap);
    for (auto &c : coords) c -= c % align;
    coords.back() = dimension <= tile_size ? 0 : dimension - tile_size;
    coords.erase(std::unique(coords.begin(), coords.end()), coords.end());
    return coords;
  };
  auto xs = aligned_positions(latent_w);
  auto ys = aligned_positions(latent_h);
  std::vector<std::pair<int, int>> positions;
  positions.reserve(xs.size() * ys.size());
  for (int y : ys) {
    for (int x : xs) positions.push_back({x, y});
  }
  int overlap_x = xs.size() > 1 ? tile_size - (xs[1] - xs[0]) : 0;
  int overlap_y = ys.size() > 1 ? tile_size - (ys[1] - ys[0]) : 0;
  return {positions, overlap_x, overlap_y};
}

// Per-tile blend weight: linear fade-in over half the overlap on every edge
// that has a neighbouring tile (interior edges only; image borders stay 1.0).
inline xt::xarray<float> make_tile_fade_weight(int tile_size, int x, int y,
                                               int extent_w, int extent_h,
                                               int fade_size_x,
                                               int fade_size_y) {
  xt::xarray<float> tile_weight = xt::ones<float>({tile_size, tile_size});

  if (fade_size_y > 0) {
    if (y > 0) {
      for (int i = 0; i < fade_size_y; ++i) {
        float alpha = (float)(i + 1) / fade_size_y;
        xt::view(tile_weight, i, xt::all()) *= alpha;
      }
    }
    if (y + tile_size < extent_h) {
      for (int i = 0; i < fade_size_y; ++i) {
        float alpha = (float)(i + 1) / fade_size_y;
        xt::view(tile_weight, tile_size - 1 - i, xt::all()) *= alpha;
      }
    }
  }

  if (fade_size_x > 0) {
    if (x > 0) {
      for (int i = 0; i < fade_size_x; ++i) {
        float alpha = (float)(i + 1) / fade_size_x;
        xt::view(tile_weight, xt::all(), i) *= alpha;
      }
    }
    if (x + tile_size < extent_w) {
      for (int i = 0; i < fade_size_x; ++i) {
        float alpha = (float)(i + 1) / fade_size_x;
        xt::view(tile_weight, xt::all(), tile_size - 1 - i) *= alpha;
      }
    }
  }

  return tile_weight;
}

inline xt::xarray<float> blend_vae_encoder_tiles(
    const std::vector<std::pair<xt::xarray<float>, xt::xarray<float>>>
        &tiles_mean_std,
    const std::vector<std::pair<int, int>> &positions, int latent_h,
    int latent_w, int tile_size, int overlap_x, int overlap_y) {
  if (tiles_mean_std.empty()) {
    throw std::runtime_error(
        "Tile list cannot be empty for VAE encoder blending.");
  }

  std::vector<int> accumulated_shape = {1, 4, latent_h, latent_w};
  xt::xarray<float> accumulated_mean = xt::zeros<float>(accumulated_shape);
  xt::xarray<float> accumulated_std = xt::zeros<float>(accumulated_shape);
  xt::xarray<float> weight_map = xt::zeros<float>({latent_h, latent_w});

  int fade_size_x = overlap_x / 2;
  int fade_size_y = overlap_y / 2;

  for (size_t idx = 0; idx < tiles_mean_std.size(); ++idx) {
    int x = positions[idx].first;
    int y = positions[idx].second;

    xt::xarray<float> tile_weight = make_tile_fade_weight(
        tile_size, x, y, latent_w, latent_h, fade_size_x, fade_size_y);

    const auto &mean_tile =
        tiles_mean_std[idx].first;  // (1, 4, tile_size, tile_size)
    const auto &std_tile =
        tiles_mean_std[idx].second;  // (1, 4, tile_size, tile_size)

    for (int c = 0; c < 4; ++c) {
      auto acc_mean_slice =
          xt::view(accumulated_mean, 0, c, xt::range(y, y + tile_size),
                   xt::range(x, x + tile_size));
      auto mean_slice = xt::view(mean_tile, 0, c, xt::all(), xt::all());
      acc_mean_slice += mean_slice * tile_weight;

      auto acc_std_slice =
          xt::view(accumulated_std, 0, c, xt::range(y, y + tile_size),
                   xt::range(x, x + tile_size));
      auto std_slice = xt::view(std_tile, 0, c, xt::all(), xt::all());
      acc_std_slice += std_slice * tile_weight;
    }

    auto weight_slice = xt::view(weight_map, xt::range(y, y + tile_size),
                                 xt::range(x, x + tile_size));
    weight_slice += tile_weight;
  }

  weight_map = xt::maximum(weight_map, 1e-8f);
  xt::xarray<float> weight_expanded =
      xt::reshape_view(weight_map, {1, 1, latent_h, latent_w});

  xt::xarray<float> final_mean = accumulated_mean / weight_expanded;
  xt::xarray<float> final_std = accumulated_std / weight_expanded;

  xt::xarray<float> noise =
      xt::random::randn<float>({1, 4, latent_h, latent_w});
  xt::xarray<float> latent = xt::eval(final_mean + final_std * noise);

  return latent;
}

// Weighted blend of [1, C, tile, tile] tiles into a [1, C, H, W] plane.
// Used for both decoded VAE pixel tiles (C=3) and per-step UNet noise
// prediction tiles (C=4).
inline xt::xarray<float> blend_output_tiles(
    const std::vector<xt::xarray<float>> &tiles,
    const std::vector<std::pair<int, int>> &positions, int output_h,
    int output_w, int tile_size, int overlap_x, int overlap_y) {
  if (tiles.empty()) {
    throw std::runtime_error("Tile list cannot be empty for tile blending.");
  }

  const int channels = (int)tiles[0].shape()[1];
  std::vector<int> accumulated_shape = {1, channels, output_h, output_w};
  xt::xarray<float> accumulated = xt::zeros<float>(accumulated_shape);
  xt::xarray<float> weight_map = xt::zeros<float>({output_h, output_w});

  int fade_size_x = overlap_x / 2;
  int fade_size_y = overlap_y / 2;

  for (size_t idx = 0; idx < tiles.size(); ++idx) {
    int x = positions[idx].first;
    int y = positions[idx].second;

    xt::xarray<float> tile_weight = make_tile_fade_weight(
        tile_size, x, y, output_w, output_h, fade_size_x, fade_size_y);

    for (int c = 0; c < channels; ++c) {
      auto acc_slice = xt::view(accumulated, 0, c, xt::range(y, y + tile_size),
                                xt::range(x, x + tile_size));
      auto tile_slice = xt::view(tiles[idx], 0, c, xt::all(), xt::all());
      acc_slice += tile_slice * tile_weight;
    }

    auto weight_slice = xt::view(weight_map, xt::range(y, y + tile_size),
                                 xt::range(x, x + tile_size));
    weight_slice += tile_weight;
  }

  weight_map = xt::maximum(weight_map, 1e-8f);
  xt::xarray<float> weight_expanded =
      xt::reshape_view(weight_map, {1, 1, output_h, output_w});

  return accumulated / weight_expanded;
}

#endif  // TILING_HPP

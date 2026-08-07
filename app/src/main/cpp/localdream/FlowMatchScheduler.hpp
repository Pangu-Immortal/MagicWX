// FlowMatchScheduler — rectified-flow euler-ancestral sampler for Anima.
//
// Port of ComfyUI's `ModelSamplingDiscreteFlow(shift=3.0, multiplier=1.0)` +
// `sample_euler_ancestral_RF`, both wired through the CONST (rectified-flow)
// model parameterization used by Anima (see anima_torch/sampler.py).
//
// Differences from the DDPM-style schedulers in this tree:
//   * the "timestep" handed to the model IS the sigma (multiplier = 1.0), a
//     float in (0, 1]; the model output is the flow velocity v.
//   * the denoised x0 estimate is `x - v * sigma` (CONST), not the
//     epsilon/v-prediction formulas.
//   * sigmas come from the SNR-shifted flow schedule, not from betas.
// The Scheduler interface still takes `int timestep`, but this scheduler
// ignores that value and drives everything off its own internal step index
// (begin_index for img2img), so the shared generate() loop is unchanged.
#ifndef FLOWMATCHSCHEDULER_HPP
#define FLOWMATCHSCHEDULER_HPP

#include <algorithm>
#include <cmath>
#include <optional>
#include <string>
#include <vector>
#include <xtensor/xadapt.hpp>
#include <xtensor/xarray.hpp>
#include <xtensor/xmanipulation.hpp>
#include <xtensor/xrandom.hpp>

#include "Scheduler.hpp"

class FlowMatchScheduler : public Scheduler {
 public:
  // eta = 1.0 -> stochastic ancestral euler ("euler_ancestral"); eta = 0.0 ->
  // deterministic euler. Anima's reference pipeline defaults to ancestral.
  explicit FlowMatchScheduler(float shift = 3.0f, float multiplier = 1.0f,
                              float eta = 1.0f, float s_noise = 1.0f)
      : shift_(shift), multiplier_(multiplier), eta_(eta), s_noise_(s_noise) {}

  void set_timesteps(int num_inference_steps) override {
    num_inference_steps_ = num_inference_steps;
    auto sigs = build_sigmas(num_inference_steps);
    sigmas_ = xt::adapt(sigs);  // length N+1, trailing 0
    // The model timestep equals sigma (multiplier = 1.0). The loop iterates
    // over timesteps_, one entry per non-terminal sigma.
    std::vector<float> ts(sigs.begin(), sigs.end() - 1);
    timesteps_ = xt::adapt(ts);
    step_index_ = std::nullopt;
    begin_index_ = std::nullopt;
  }

  // CONST parameterization: model input is the raw latent (identity scaling).
  xt::xarray<float> scale_model_input(const xt::xarray<float> &sample,
                                      int /*timestep*/) override {
    if (!step_index_.has_value()) init_step_index();
    return sample;
  }

  // `model_output` is the flow velocity v (CFG-combined upstream). `sample` is
  // the current latent x. Returns the next latent.
  SchedulerOutput step(const xt::xarray<float> &model_output, int /*timestep*/,
                       const xt::xarray<float> &sample) override {
    if (!num_inference_steps_.has_value())
      throw std::runtime_error("set_timesteps must be called before stepping");
    if (!step_index_.has_value()) init_step_index();

    const int idx = step_index_.value();
    const float sigma_i = sigmas_(idx);
    const float sigma_ip1 = sigmas_(idx + 1);

    // CONST denoised x0 estimate.
    xt::xarray<float> denoised = sample - model_output * sigma_i;

    xt::xarray<float> prev_sample;
    if (sigma_ip1 == 0.0f) {
      prev_sample = denoised;
    } else {
      const float downstep_ratio = 1.0f + (sigma_ip1 / sigma_i - 1.0f) * eta_;
      const float sigma_down = sigma_ip1 * downstep_ratio;
      const float alpha_ip1 = 1.0f - sigma_ip1;
      const float alpha_down = 1.0f - sigma_down;
      const float renoise = std::sqrt(std::max(
          0.0f, sigma_ip1 * sigma_ip1 - sigma_down * sigma_down * alpha_ip1 *
                                            alpha_ip1 /
                                            (alpha_down * alpha_down)));
      const float sigma_down_i = sigma_down / sigma_i;

      prev_sample = sigma_down_i * sample + (1.0f - sigma_down_i) * denoised;
      if (eta_ > 0.0f) {
        xt::xarray<float> noise = xt::random::randn<float>(sample.shape());
        prev_sample = (alpha_ip1 / alpha_down) * prev_sample +
                      noise * (s_noise_ * renoise);
      }
    }

    step_index_ = idx + 1;
    return {prev_sample, denoised};
  }

  // Rectified-flow forward process: x_t = (1 - sigma) * x0 + sigma * noise.
  // Only used for img2img/inpaint; sigma is taken from the current step index.
  xt::xarray<float> add_noise(const xt::xarray<float> &original_samples,
                              const xt::xarray<float> &noise,
                              const xt::xarray<int> &timesteps) const override {
    int idx = step_index_.value_or(begin_index_.value_or(0));
    idx = std::clamp(idx, 0, int(sigmas_.size()) - 1);
    const float sigma = sigmas_(idx);
    (void)timesteps;
    return (1.0f - sigma) * original_samples + sigma * noise;
  }

  void set_begin_index(int begin_index) override { begin_index_ = begin_index; }
  void set_prediction_type(const std::string &) override {}  // always CONST
  const xt::xarray<float> &get_timesteps() const override { return timesteps_; }
  size_t get_step_index() const override { return step_index_.value_or(0); }
  float get_current_sigma() const override {
    int idx = step_index_.value_or(0);
    return sigmas_(std::clamp(idx, 0, int(sigmas_.size()) - 1));
  }
  // x starts as plain unit-variance noise (CONST noise_scaling at sigma=1).
  float get_init_noise_sigma() const override { return 1.0f; }

 private:
  static float time_snr_shift(float alpha, float t) {
    if (alpha == 1.0f) return t;
    return alpha * t / (1.0f + (alpha - 1.0f) * t);
  }

  // Mirrors anima_torch.sampler.build_sigmas (ComfyUI normal_scheduler for
  // flow-matching). Returns length steps+1 (descending sigmas, trailing 0).
  std::vector<float> build_sigmas(int steps) const {
    // sigma(ts) = time_snr_shift(shift, ts/multiplier); timestep(sigma)=sigma.
    auto sigma_of = [&](float ts) {
      return time_snr_shift(shift_, ts / multiplier_);
    };
    const float sigma_min = time_snr_shift(shift_, (1.0f / 1000.0f));
    const float sigma_max = time_snr_shift(shift_, 1.0f);
    const float start = sigma_max * multiplier_;
    const float end = sigma_min * multiplier_;

    int n = steps;
    bool append_zero = true;
    if (std::fabs(sigma_of(end)) < 1e-5f) {
      n += 1;
      append_zero = false;
    }
    std::vector<float> sigs;
    sigs.reserve(n + 1);
    for (int i = 0; i < n; ++i) {
      float t =
          (n == 1) ? start : start + (end - start) * float(i) / float(n - 1);
      sigs.push_back(sigma_of(t));
    }
    if (append_zero) sigs.push_back(0.0f);
    return sigs;
  }

  void init_step_index() { step_index_ = begin_index_.value_or(0); }

  float shift_;
  float multiplier_;
  float eta_;
  float s_noise_;

  std::optional<int> num_inference_steps_;
  xt::xarray<float> sigmas_;     // length N+1
  xt::xarray<float> timesteps_;  // length N (== sigmas_[0..N-1])
  std::optional<int> step_index_;
  std::optional<int> begin_index_;
};

#endif  // FLOWMATCHSCHEDULER_HPP

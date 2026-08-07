// self-implemented DPMSolverMultistepScheduler class
#include <cmath>
#include <optional>
#include <string>
#include <vector>
#include <xtensor/xadapt.hpp>
#include <xtensor/xarray.hpp>
#include <xtensor/xio.hpp>
#include <xtensor/xmanipulation.hpp>
#include <xtensor/xmath.hpp>
#include <xtensor/xrandom.hpp>
#include <xtensor/xview.hpp>

#include "Scheduler.hpp"

class DPMSolverMultistepScheduler : public Scheduler {
 public:
  DPMSolverMultistepScheduler(int num_train_timesteps, float beta_start,
                              float beta_end, const std::string &beta_schedule,
                              int solver_order,
                              const std::string &prediction_type,
                              const std::string &timestep_spacing,
                              bool use_karras_sigmas = false,
                              const std::string &algorithm_type = "dpmsolver++")
      : num_train_timesteps_(num_train_timesteps),
        beta_start_(beta_start),
        beta_end_(beta_end),
        beta_schedule_(beta_schedule),
        solver_order_(solver_order),
        prediction_type_(prediction_type),
        timestep_spacing_(timestep_spacing),
        use_karras_sigmas_(use_karras_sigmas),
        algorithm_type_(algorithm_type),
        lower_order_final_(true) {
    if (algorithm_type_ != "dpmsolver++" &&
        algorithm_type_ != "sde-dpmsolver++") {
      throw std::runtime_error("Unsupported algorithm_type: " +
                               algorithm_type_);
    }
    if (beta_schedule == "scaled_linear") {
      float beta_start_sqrt = std::sqrt(beta_start_);
      float beta_end_sqrt = std::sqrt(beta_end_);
      betas_ = xt::pow(xt::linspace<float>(beta_start_sqrt, beta_end_sqrt,
                                           num_train_timesteps),
                       2.0f);
    } else {
      throw std::runtime_error(beta_schedule + " is not implemented");
    }

    alphas_ = 1.0f - betas_;
    alphas_cumprod_ = xt::cumprod(alphas_);

    alpha_t_ = xt::sqrt(alphas_cumprod_);
    sigma_t_ = xt::sqrt(1.0f - alphas_cumprod_);
    lambda_t_ = xt::log(alpha_t_) - xt::log(sigma_t_);
    sigmas_ = xt::pow((1.0f - alphas_cumprod_) / alphas_cumprod_, 0.5f);

    model_outputs_.resize(solver_order_);
    std::fill(model_outputs_.begin(), model_outputs_.end(),
              xt::xarray<float>());

    lower_order_nums_ = 0;
    step_index_ = std::nullopt;
    begin_index_ = std::nullopt;
  }

  void set_timesteps(int num_inference_steps) override {
    num_inference_steps_ = num_inference_steps;

    // Base sigmas from alphas_cumprod (ascending order)
    auto base_sigmas_vec = std::vector<float>(num_train_timesteps_);
    for (int i = 0; i < num_train_timesteps_; ++i) {
      float alpha_cumprod = alphas_cumprod_(i);
      base_sigmas_vec[i] = std::sqrt((1.0f - alpha_cumprod) / alpha_cumprod);
    }

    if (use_karras_sigmas_) {
      std::vector<float> reversed(base_sigmas_vec.rbegin(),
                                  base_sigmas_vec.rend());
      auto karras_sigmas =
          _convert_to_karras_vec(reversed, num_inference_steps);

      std::vector<float> log_sigmas(num_train_timesteps_);
      for (int i = 0; i < num_train_timesteps_; ++i) {
        log_sigmas[i] = std::log(std::max(base_sigmas_vec[i], 1e-10f));
      }

      auto timesteps_vec = std::vector<float>(num_inference_steps);
      for (int i = 0; i < num_inference_steps; ++i) {
        timesteps_vec[i] =
            std::round(_sigma_to_t(karras_sigmas[i], log_sigmas));
      }
      timesteps_ = xt::adapt(timesteps_vec);

      auto sigmas_vec = std::vector<float>(num_inference_steps + 1);
      for (int i = 0; i < num_inference_steps; ++i) {
        sigmas_vec[i] = karras_sigmas[i];
      }
      sigmas_vec[num_inference_steps] = 0.0f;
      sigmas_ = xt::adapt(sigmas_vec);
    } else {
      if (timestep_spacing_ == "linspace") {
        // np.linspace(0, N-1, M+1).round()[::-1][:-1]
        auto timesteps_vec = std::vector<float>(num_inference_steps);
        for (int i = 0; i < num_inference_steps; ++i) {
          timesteps_vec[i] = std::round(float(num_inference_steps - i) *
                                        float(num_train_timesteps_ - 1) /
                                        float(num_inference_steps));
        }
        timesteps_ = xt::adapt(timesteps_vec);
      } else if (timestep_spacing_ == "leading") {
        int step_ratio = num_train_timesteps_ / (num_inference_steps + 1);
        auto timesteps_vec = std::vector<float>(num_inference_steps);
        for (int i = 0; i < num_inference_steps; ++i) {
          timesteps_vec[i] = float((num_inference_steps - i) * step_ratio);
        }
        timesteps_ = xt::adapt(timesteps_vec);
      } else if (timestep_spacing_ == "trailing") {
        float step_ratio =
            float(num_train_timesteps_) / float(num_inference_steps);
        auto timesteps_vec = std::vector<float>(num_inference_steps);
        for (int i = 0; i < num_inference_steps; ++i) {
          timesteps_vec[i] =
              std::round(float(num_train_timesteps_) - float(i) * step_ratio) -
              1.0f;
        }
        timesteps_ = xt::adapt(timesteps_vec);
      } else {
        throw std::runtime_error(timestep_spacing_ + " is not supported");
      }

      // Interpolate sigmas using np.interp logic; final sigma is 0
      // (final_sigmas_type="zero").
      auto sigmas_vec = std::vector<float>(num_inference_steps + 1);
      for (int i = 0; i < num_inference_steps; ++i) {
        float t = timesteps_(i);
        if (t <= 0.0f) {
          sigmas_vec[i] = base_sigmas_vec[0];
        } else if (t >= float(num_train_timesteps_ - 1)) {
          sigmas_vec[i] = base_sigmas_vec[num_train_timesteps_ - 1];
        } else {
          int t_floor = int(std::floor(t));
          int t_ceil = int(std::ceil(t));
          float weight = t - float(t_floor);
          sigmas_vec[i] = base_sigmas_vec[t_floor] * (1.0f - weight) +
                          base_sigmas_vec[t_ceil] * weight;
        }
      }
      sigmas_vec[num_inference_steps] = 0.0f;
      sigmas_ = xt::adapt(sigmas_vec);
    }

    model_outputs_.clear();
    model_outputs_.resize(solver_order_);
    std::fill(model_outputs_.begin(), model_outputs_.end(),
              xt::xarray<float>());

    lower_order_nums_ = 0;
    step_index_ = std::nullopt;
    begin_index_ = std::nullopt;
  }

  std::vector<float> _convert_to_karras_vec(const std::vector<float> &in_sigmas,
                                            int num_inference_steps) const {
    float sigma_min = in_sigmas.back();
    float sigma_max = in_sigmas.front();

    const float rho = 7.0f;
    float min_inv_rho = std::pow(sigma_min, 1.0f / rho);
    float max_inv_rho = std::pow(sigma_max, 1.0f / rho);

    std::vector<float> out(num_inference_steps);
    for (int i = 0; i < num_inference_steps; ++i) {
      float ramp = (num_inference_steps == 1)
                       ? 0.0f
                       : float(i) / float(num_inference_steps - 1);
      float v = max_inv_rho + ramp * (min_inv_rho - max_inv_rho);
      out[i] = std::pow(v, rho);
    }
    return out;
  }

  float _sigma_to_t(float sigma, const std::vector<float> &log_sigmas) const {
    float log_sigma = std::log(std::max(sigma, 1e-10f));
    int n = int(log_sigmas.size());

    int low_idx = 0;
    for (int i = 0; i < n; ++i) {
      if (log_sigmas[i] <= log_sigma) {
        low_idx = i;
      }
    }
    if (low_idx > n - 2) low_idx = n - 2;
    int high_idx = low_idx + 1;

    float low = log_sigmas[low_idx];
    float high = log_sigmas[high_idx];

    float w = (low - log_sigma) / (low - high);
    if (w < 0.0f) w = 0.0f;
    if (w > 1.0f) w = 1.0f;

    return (1.0f - w) * float(low_idx) + w * float(high_idx);
  }

  std::tuple<float, float> _sigma_to_alpha_sigma_t(float sigma) const {
    float alpha_t = 1.0f / std::sqrt(sigma * sigma + 1.0f);
    float sigma_t = sigma * alpha_t;
    return {alpha_t, sigma_t};
  }

  void set_prediction_type(const std::string &prediction_type) override {
    prediction_type_ = prediction_type;
  }

  xt::xarray<float> scale_model_input(const xt::xarray<float> &sample,
                                      int timestep) override {
    // DPM solver does not require input scaling
    return sample;
  }

  xt::xarray<float> convert_model_output(const xt::xarray<float> &model_output,
                                         const xt::xarray<float> &sample) {
    float sigma = sigmas_(step_index_.value());
    auto [alpha_t, sigma_t_val] = _sigma_to_alpha_sigma_t(sigma);
    if (prediction_type_ == "epsilon") {
      return (sample - sigma_t_val * model_output) / alpha_t;
    } else if (prediction_type_ == "v_prediction") {
      return alpha_t * sample - sigma_t_val * model_output;
    } else if (prediction_type_ == "sample") {
      return model_output;
    } else {
      throw std::runtime_error(
          prediction_type_ +
          " is not implemented for DPMSolverMultistepScheduler");
    }
  }

  xt::xarray<float> dpm_solver_first_order_update(
      const xt::xarray<float> &model_output, const xt::xarray<float> &sample) {
    float sigma_next = sigmas_(step_index_.value() + 1);
    float sigma_curr = sigmas_(step_index_.value());
    auto [alpha_t, sigma_t_val] = _sigma_to_alpha_sigma_t(sigma_next);
    auto [alpha_s, sigma_s_val] = _sigma_to_alpha_sigma_t(sigma_curr);

    float lambda_t = std::log(alpha_t) - std::log(sigma_t_val);
    float lambda_s = std::log(alpha_s) - std::log(sigma_s_val);
    float h = lambda_t - lambda_s;

    if (algorithm_type_ == "sde-dpmsolver++") {
      float one_minus_exp_neg_2h = 1.0f - std::exp(-2.0f * h);
      xt::xarray<float> noise =
          xt::random::randn<float>(model_output.shape(), 0.0f, 1.0f,
                                   xt::random::get_default_random_engine());
      return (sigma_t_val / sigma_s_val * std::exp(-h)) * sample +
             (alpha_t * one_minus_exp_neg_2h) * model_output +
             (sigma_t_val * std::sqrt(std::max(one_minus_exp_neg_2h, 0.0f))) *
                 noise;
    }

    return (sigma_t_val / sigma_s_val) * sample -
           alpha_t * (std::exp(-h) - 1.0f) * model_output;
  }

  xt::xarray<float> multistep_dpm_solver_second_order_update(
      const std::vector<xt::xarray<float>> &model_output_list,
      const xt::xarray<float> &sample) {
    float sigma_next = sigmas_(step_index_.value() + 1);
    float sigma_s0 = sigmas_(step_index_.value());
    float sigma_s1 = sigmas_(step_index_.value() - 1);

    auto [alpha_t, sigma_t_val] = _sigma_to_alpha_sigma_t(sigma_next);
    auto [alpha_s0, sigma_s0_val] = _sigma_to_alpha_sigma_t(sigma_s0);
    auto [alpha_s1, sigma_s1_val] = _sigma_to_alpha_sigma_t(sigma_s1);

    float lambda_t = std::log(alpha_t) - std::log(sigma_t_val);
    float lambda_s0_ = std::log(alpha_s0) - std::log(sigma_s0_val);
    float lambda_s1_ = std::log(alpha_s1) - std::log(sigma_s1_val);

    const auto &m0 = model_output_list.back();
    const auto &m1 = model_output_list[model_output_list.size() - 2];

    float h = lambda_t - lambda_s0_;
    float h_0 = lambda_s0_ - lambda_s1_;
    float r0 = h_0 / h;

    xt::xarray<float> D0 = m0;
    xt::xarray<float> D1 = (1.0f / r0) * (m0 - m1);

    if (algorithm_type_ == "sde-dpmsolver++") {
      float one_minus_exp_neg_2h = 1.0f - std::exp(-2.0f * h);
      xt::xarray<float> noise = xt::random::randn<float>(
          sample.shape(), 0.0f, 1.0f, xt::random::get_default_random_engine());
      return (sigma_t_val / sigma_s0_val * std::exp(-h)) * sample +
             (alpha_t * one_minus_exp_neg_2h) * D0 +
             (0.5f * alpha_t * one_minus_exp_neg_2h) * D1 +
             (sigma_t_val * std::sqrt(std::max(one_minus_exp_neg_2h, 0.0f))) *
                 noise;
    }

    return (sigma_t_val / sigma_s0_val) * sample -
           (alpha_t * (std::exp(-h) - 1.0f)) * D0 -
           0.5f * (alpha_t * (std::exp(-h) - 1.0f)) * D1;
  }

  xt::xarray<float> multistep_dpm_solver_third_order_update(
      const std::vector<xt::xarray<float>> &model_output_list,
      const xt::xarray<float> &sample) {
    float sigma_next = sigmas_(step_index_.value() + 1);
    float sigma_s0 = sigmas_(step_index_.value());
    float sigma_s1 = sigmas_(step_index_.value() - 1);
    float sigma_s2 = sigmas_(step_index_.value() - 2);

    auto [alpha_t, sigma_t_val] = _sigma_to_alpha_sigma_t(sigma_next);
    auto [alpha_s0, sigma_s0_val] = _sigma_to_alpha_sigma_t(sigma_s0);
    auto [alpha_s1, sigma_s1_val] = _sigma_to_alpha_sigma_t(sigma_s1);
    auto [alpha_s2, sigma_s2_val] = _sigma_to_alpha_sigma_t(sigma_s2);

    float lambda_t = std::log(alpha_t) - std::log(sigma_t_val);
    float lambda_s0_ = std::log(alpha_s0) - std::log(sigma_s0_val);
    float lambda_s1_ = std::log(alpha_s1) - std::log(sigma_s1_val);
    float lambda_s2_ = std::log(alpha_s2) - std::log(sigma_s2_val);

    const auto &m0 = model_output_list.back();
    const auto &m1 = model_output_list[model_output_list.size() - 2];
    const auto &m2 = model_output_list[model_output_list.size() - 3];

    float h = lambda_t - lambda_s0_;
    float h_0 = lambda_s0_ - lambda_s1_;
    float h_1 = lambda_s1_ - lambda_s2_;
    float r0 = h_0 / h;
    float r1 = h_1 / h;

    xt::xarray<float> D0 = m0;
    xt::xarray<float> D1_0 = (1.0f / r0) * (m0 - m1);
    xt::xarray<float> D1_1 = (1.0f / r1) * (m1 - m2);
    xt::xarray<float> D1 = D1_0 + (r0 / (r0 + r1)) * (D1_0 - D1_1);
    xt::xarray<float> D2 = (1.0f / (r0 + r1)) * (D1_0 - D1_1);

    return (sigma_t_val / sigma_s0_val) * sample -
           (alpha_t * (std::exp(-h) - 1.0f)) * D0 +
           (alpha_t * ((std::exp(-h) - 1.0f) / h + 1.0f)) * D1 -
           (alpha_t * ((std::exp(-h) - 1.0f + h) / (h * h) - 0.5f)) * D2;
  }

  int index_for_timestep(int timestep) const {
    std::vector<size_t> indices;
    for (size_t i = 0; i < timesteps_.size(); ++i) {
      if (int(timesteps_(i)) == timestep) {
        indices.push_back(i);
      }
    }
    if (indices.empty()) {
      return int(timesteps_.size()) - 1;
    } else if (indices.size() > 1) {
      return int(indices[1]);
    } else {
      return int(indices[0]);
    }
  }

  SchedulerOutput step(const xt::xarray<float> &model_output, int timestep,
                       const xt::xarray<float> &sample) override {
    if (!num_inference_steps_) {
      throw std::runtime_error("set_timesteps must be called before stepping");
    }

    if (!step_index_) {
      step_index_ = index_for_timestep(timestep);
    }

    xt::xarray<float> converted_output =
        convert_model_output(model_output, sample);

    for (int i = 0; i < solver_order_ - 1; ++i) {
      model_outputs_[i] = std::move(model_outputs_[i + 1]);
    }
    model_outputs_.back() = std::move(converted_output);

    // Match diffusers: at the last step always fall back to first-order,
    // because our sigmas array ends in 0 (equivalent to
    // final_sigmas_type="zero"). The previous `||` form was a bug — when
    // timesteps.size() < 15 it silently degraded *every* step to first-order.
    bool is_last_step = (step_index_.value() == int(timesteps_.size()) - 1);
    bool lower_order_final = is_last_step;
    bool lower_order_second =
        (step_index_.value() == int(timesteps_.size()) - 2) &&
        lower_order_final_ && timesteps_.size() < 15;

    xt::xarray<float> prev_sample;
    if (solver_order_ == 1 || lower_order_nums_ < 1 || lower_order_final) {
      prev_sample =
          dpm_solver_first_order_update(model_outputs_.back(), sample);
    } else if (solver_order_ == 2 || lower_order_nums_ < 2 ||
               lower_order_second) {
      prev_sample =
          multistep_dpm_solver_second_order_update(model_outputs_, sample);
    } else {
      prev_sample =
          multistep_dpm_solver_third_order_update(model_outputs_, sample);
    }

    if (lower_order_nums_ < solver_order_) {
      lower_order_nums_++;
    }

    step_index_ = step_index_.value() + 1;
    return {prev_sample, xt::xarray<float>()};
  }

  void set_begin_index(int begin_index) override { begin_index_ = begin_index; }

  xt::xarray<float> add_noise(const xt::xarray<float> &original_samples,
                              const xt::xarray<float> &noise,
                              const xt::xarray<int> &timesteps) const override {
    std::vector<int> step_indices;

    if (!begin_index_) {
      for (size_t i = 0; i < timesteps.size(); ++i) {
        step_indices.push_back(index_for_timestep(timesteps(i)));
      }
    } else if (step_index_) {
      step_indices.resize(timesteps.size(), step_index_.value());
    } else {
      step_indices.resize(timesteps.size(), begin_index_.value());
    }

    xt::xarray<float> sigma = xt::zeros<float>({step_indices.size()});
    for (size_t i = 0; i < step_indices.size(); ++i) {
      sigma(i) = sigmas_(step_indices[i]);
    }

    std::vector<size_t> new_shape = {sigma.size(), 1, 1, 1};
    auto reshaped_sigma = xt::reshape_view(sigma, new_shape);

    xt::xarray<float> alpha_t =
        xt::ones_like(reshaped_sigma) /
        xt::sqrt(reshaped_sigma * reshaped_sigma + 1.0f);
    xt::xarray<float> sigma_t = reshaped_sigma * alpha_t;

    return alpha_t * original_samples + sigma_t * noise;
  }

  const xt::xarray<float> &get_timesteps() const override { return timesteps_; }
  size_t get_step_index() const override { return step_index_.value_or(0); }

  const xt::xarray<float> &get_betas() const { return betas_; }
  const xt::xarray<float> &get_alphas() const { return alphas_; }
  const xt::xarray<float> &get_alphas_cumprod() const {
    return alphas_cumprod_;
  }
  const xt::xarray<float> &get_alpha_t() const { return alpha_t_; }
  const xt::xarray<float> &get_sigma_t() const { return sigma_t_; }
  const xt::xarray<float> &get_lambda_t() const { return lambda_t_; }
  const xt::xarray<float> &get_sigmas() const { return sigmas_; }

  float get_current_sigma() const override {
    if (!step_index_) {
      return sigmas_(0);
    }
    return sigmas_(std::min<int>(step_index_.value(), int(sigmas_.size()) - 1));
  }

  float get_init_noise_sigma() const override {
    // DPM solver does not require special initial noise scaling
    return 1.0f;
  }

 private:
  int num_train_timesteps_;
  float beta_start_;
  float beta_end_;
  std::string beta_schedule_;
  int solver_order_;
  std::string prediction_type_;
  std::string timestep_spacing_;
  bool use_karras_sigmas_;
  std::string algorithm_type_;
  bool lower_order_final_;

  xt::xarray<float> betas_;
  xt::xarray<float> alphas_;
  xt::xarray<float> alphas_cumprod_;
  xt::xarray<float> alpha_t_;
  xt::xarray<float> sigma_t_;
  xt::xarray<float> lambda_t_;
  xt::xarray<float> sigmas_;

  std::optional<int> num_inference_steps_;
  xt::xarray<float> timesteps_;
  std::vector<xt::xarray<float>> model_outputs_;
  int lower_order_nums_;
  std::optional<int> step_index_;
  std::optional<int> begin_index_;
};

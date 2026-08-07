#ifndef PIPELINESD15NPU_HPP
#define PIPELINESD15NPU_HPP

#include <MNN/Interpreter.hpp>
#include <memory>
#include <stdexcept>
#include <string>

#include "Config.hpp"
#include "MnnUtils.hpp"
#include "PipelineQnn.hpp"

// sd15npu: UNet and VAE run as persistent QNN (HTP) contexts; CLIP runs on a
// persistent MNN CPU session. Supports zstd resolution patches for unet.bin
// and tiled VAE for outputs above 512px.
class PipelineSd15Npu : public PipelineQnn {
 public:
  PipelineSd15Npu(TextEncoder &text_encoder, const std::string &model_dir,
                  std::string clip_path, std::string unet_path,
                  std::string vae_decoder_path, std::string vae_encoder_path,
                  std::string patch_path, bool use_v_pred)
      : PipelineQnn(text_encoder, model_dir, /*sdxl=*/false, use_v_pred),
        clip_path_(std::move(clip_path)),
        unet_path_(std::move(unet_path)),
        vae_decoder_path_(std::move(vae_decoder_path)),
        vae_encoder_path_(std::move(vae_encoder_path)),
        patch_path_(std::move(patch_path)) {}

  ~PipelineSd15Npu() override {
    if (clip_session_) clip_interpreter_->releaseSession(clip_session_);
    delete clip_interpreter_;
  }

  bool initialize() override {
    clip_interpreter_ = createMnnInterpreterMmap(clip_path_.c_str());
    if (!clip_interpreter_) {
      QNN_ERROR("Failed load CLIP MNN: %s", clip_path_.c_str());
      return false;
    }
    clip_session_ = createMnnSession(clip_interpreter_, MnnSessionOptions{});
    if (!clip_session_) {
      QNN_ERROR("Failed create persistent MNN CLIP session!");
      return false;
    }
    QNN_INFO("Persistent MNN CLIP session created.");
    auto input =
        clip_interpreter_->getSessionInput(clip_session_, "input_embedding");
    clip_interpreter_->resizeTensor(input, {1, 77, text_embedding_size});
    clip_interpreter_->resizeSession(clip_session_);
    clip_interpreter_->releaseModel();

    // Optional zstd resolution patch, applied in memory against the base
    // unet.bin used as dictionary.
    std::unique_ptr<qnn_runtime::PatchedModelBuffer> patched;
    if (!patch_path_.empty()) {
      QNN_INFO("Applying patch to unet model in memory...");
      patched = qnn_runtime::applyZstdPatchToBuffer(unet_path_, patch_path_);
      if (!patched) {
        QNN_ERROR("Failed to apply patch to unet model buffer");
        return false;
      }
      QNN_INFO("Patch applied successfully to buffer (size: %llu bytes)",
               patched->size);
      qnn_runtime::cleanupOldPatchedFiles(patch_path_);
    }

    unet_ = qnn_runtime::createModel(unet_path_, "unet");
    if (!unet_) {
      QNN_ERROR("Failed create QNN UNET model.");
      return false;
    }
    vae_decoder_ = qnn_runtime::createModel(vae_decoder_path_, "vae_decoder");
    if (!vae_decoder_) {
      QNN_ERROR("Failed create QNN VAE Decoder model.");
      return false;
    }
    if (!vae_encoder_path_.empty()) {
      vae_encoder_ = qnn_runtime::createModel(vae_encoder_path_, "vae_encoder");
      if (!vae_encoder_) QNN_WARN("Failed create QNN VAE Enc model.");
    } else {
      QNN_INFO("img2img disabled: VAE encoder not loaded");
    }

    int status;
    if (patched && patched->buffer) {
      status = qnn_runtime::initializeApp("UNET", unet_, patched->buffer.get(),
                                          patched->size);
    } else {
      status = qnn_runtime::initializeApp("UNET", unet_);
    }
    if (status != EXIT_SUCCESS) return false;
    if (patched) {
      QNN_INFO("Releasing unet patch buffer to free memory");
      patched.reset();
    }

    if (qnn_runtime::initializeApp("VAEDecoder", vae_decoder_) != EXIT_SUCCESS)
      return false;
    if (vae_encoder_ &&
        qnn_runtime::initializeApp("VAEEncoder", vae_encoder_) != EXIT_SUCCESS)
      return false;
    return true;
  }

  bool supportsImg2Img() const override { return vae_encoder_ != nullptr; }

 protected:
  bool previewSupported() const override { return true; }
  bool vaeTilingSupported() const override { return true; }

  void encodeText(const ProcessedPromptPair &prompts, bool need_negative,
                  bool need_positive, Conditioning &cond) override {
    if (!clip_interpreter_ || !clip_session_)
      throw std::runtime_error("MNN CLIP missing");

    auto input =
        clip_interpreter_->getSessionInput(clip_session_, "input_embedding");
    clip_interpreter_->resizeTensor(input, {1, 77, text_embedding_size});
    clip_interpreter_->resizeSession(clip_session_);

    auto run_side = [&](const std::vector<float> &embeddings, float *dst) {
      memcpy(input->host<float>(), embeddings.data(),
             77 * text_embedding_size * sizeof(float));
      clip_interpreter_->runSession(clip_session_);
      auto out = clip_interpreter_->getSessionOutput(clip_session_,
                                                     "last_hidden_state");
      memcpy(dst, out->host<float>(), 77 * text_embedding_size * sizeof(float));
    };

    if (need_negative) run_side(prompts.negative_embeddings, cond.negHidden());
    if (need_positive) run_side(prompts.positive_embeddings, cond.posHidden());
  }

  void vaeEncode(const GenerationRequest &, const float *image, float *mean,
                 float *std_dev) override {
    if (!vae_encoder_) throw std::runtime_error("QNN VAE Enc missing");
    if (StatusCode::SUCCESS != vae_encoder_->executeVaeEncoderGraphs(
                                   const_cast<float *>(image), mean, std_dev))
      throw std::runtime_error("QNN VAE enc exec failed");
  }

  void runUnetStep(const GenerationRequest &, const float *latents_batch2,
                   float timestep, bool skip_uncond, Conditioning &cond,
                   float *out_batch2) override {
    if (!unet_) throw std::runtime_error("QNN UNET missing");

    const int single_latent_size = 1 * 4 * sample_width * sample_height;
    const int ts = static_cast<int>(timestep);
    float *latents_in = const_cast<float *>(latents_batch2);

    if (!skip_uncond && StatusCode::SUCCESS !=
                            unet_->executeUnetGraphs(
                                latents_in, ts, cond.negHidden(), out_batch2))
      throw std::runtime_error("QNN UNET exec failed (uncond)");

    if (StatusCode::SUCCESS !=
        unet_->executeUnetGraphs(latents_in + single_latent_size, ts,
                                 cond.posHidden(),
                                 out_batch2 + single_latent_size))
      throw std::runtime_error("QNN UNET exec failed (cond)");
  }

  void vaeDecode(const GenerationRequest &, const float *latents,
                 float *pixels) override {
    if (!vae_decoder_) throw std::runtime_error("QNN VAE Dec missing");
    if (StatusCode::SUCCESS != vae_decoder_->executeVaeDecoderGraphs(
                                   const_cast<float *>(latents), pixels))
      throw std::runtime_error("QNN VAE dec exec failed");
  }

 private:
  const std::string clip_path_;
  const std::string unet_path_;
  const std::string vae_decoder_path_;
  const std::string vae_encoder_path_;
  const std::string patch_path_;

  MNN::Interpreter *clip_interpreter_ = nullptr;
  MNN::Session *clip_session_ = nullptr;
};

#endif  // PIPELINESD15NPU_HPP

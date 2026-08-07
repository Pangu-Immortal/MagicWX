#ifndef PIPELINEQNN_HPP
#define PIPELINEQNN_HPP

#include <memory>
#include <string>

#include "Pipeline.hpp"
#include "QnnModel.hpp"
#include "QnnRuntime.hpp"

// Shared base for the QNN-backed formats (sd15npu, sdxl): owns the three
// stage models and the cfg=1 uncond-skip capability common to per-half UNet
// execution.
class PipelineQnn : public Pipeline {
 public:
  using Pipeline::Pipeline;

 protected:
  bool canSkipUncond() const override { return true; }

  std::unique_ptr<QnnModel> unet_;
  std::unique_ptr<QnnModel> vae_decoder_;
  std::unique_ptr<QnnModel> vae_encoder_;
};

#endif  // PIPELINEQNN_HPP

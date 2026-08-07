#ifndef CONFIG_HPP
#define CONFIG_HPP

#include <cstddef>

inline int sample_width = 64;
inline int sample_height = 64;
// CLIP hidden sizes are fixed: 768 for SD1.5 / SDXL encoder 1 (CLIP-L),
// 1280 for SDXL encoder 2 (CLIP-G).
inline constexpr int text_embedding_size = 768;
inline constexpr int text_embedding_size_2 = 1280;
inline int output_width = 512;
inline int output_height = 512;

// ---- Anima (DiT + Qwen) constants ------------------------------------------
// Anima latents are 16-channel (Wan 2.1 / Qwen-Image VAE) instead of SD's 4.
inline constexpr int anima_latent_channels = 16;
// Qwen3-0.6B text encoder: 512-token input, 1024-dim hidden states (no pooled
// output, no learned positional table — RoPE is internal to the model). The
// merged text encoder (Qwen + LLM adapter) re-grids onto the T5 token sequence,
// so its OUTPUT context — what the UNet's encoder_hidden_states consumes — is
// also 512 long. Two distinct lengths (both 512, kept separate so the Qwen and
// T5 sides can diverge if a future export changes one):
//   anima_qwen_seq_len : qwen input_embedding + qwen_mask
//   anima_text_seq_len : t5_ids/t5_mask, clip context output,
//                        Conditioning hidden, UNet encoder_hidden_states
inline constexpr int anima_qwen_seq_len = 512;
inline constexpr int anima_text_seq_len = 512;
inline constexpr int anima_text_embedding_size = 1024;
// Wan 2.1 per-channel latent normalization (16 channels). The diffusion model
// works in the normalized space; the VAE consumes/produces the de-normalized
// latent: vae_latent = model_latent * std + mean  (process_out), and the
// inverse for encoding: model_latent = (vae_latent - mean) / std.
inline constexpr float anima_latent_mean[16] = {
    -0.7571f, -0.7089f, -0.9113f, 0.1075f,  -0.1745f, 0.9653f,
    -0.1517f, 1.5508f,  0.4134f,  -0.0715f, 0.5517f,  -0.3632f,
    -0.1922f, -0.9497f, 0.2503f,  -0.2921f};
inline constexpr float anima_latent_std[16] = {
    2.8184f, 1.4541f, 2.3275f, 2.6558f, 1.2196f, 1.7708f, 2.6052f, 2.0743f,
    3.2687f, 2.1526f, 2.8652f, 1.5579f, 1.6382f, 1.1253f, 2.8251f, 1.9160f};

#endif  // CONFIG_HPP
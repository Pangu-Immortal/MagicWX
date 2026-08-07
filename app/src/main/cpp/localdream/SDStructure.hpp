#include <string>
#include <vector>

std::vector<std::vector<std::string>> clip_structure = {
    {"cond_stage_model.transformer.text_model.encoder.layers.0.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.11.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.final_layer_norm.weight", "fp32"},
    {"cond_stage_model.transformer.text_model.final_layer_norm.bias", "fp32"}};

std::vector<std::vector<std::string>> clip_skip_2_structure = {
    {"cond_stage_model.transformer.text_model.encoder.layers.0.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.0.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.1.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.2.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.3.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.4.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.5.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.6.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.7.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.8.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.9.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.layer_norm1."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.layer_norm1."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.q_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.q_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.k_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.k_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.v_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.v_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.out_"
     "proj.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.self_attn.out_"
     "proj.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.layer_norm2."
     "weight",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.layer_norm2."
     "bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.mlp.fc1.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.mlp.fc1.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.mlp.fc2.weight",
     "fp16"},
    {"cond_stage_model.transformer.text_model.encoder.layers.10.mlp.fc2.bias",
     "fp32"},
    {"cond_stage_model.transformer.text_model.final_layer_norm.weight", "fp32"},
    {"cond_stage_model.transformer.text_model.final_layer_norm.bias", "fp32"}};

std::vector<std::vector<std::string>> unet_structure = {
    {"model.diffusion_model.input_blocks.0.0.weight", "block_quant",
     "320x4x3x3"},
    {"model.diffusion_model.input_blocks.0.0.bias", "fp32"},
    {"model.diffusion_model.input_blocks.1.0.in_layers.2.weight", "block_quant",
     "320x320x3x3"},
    {"model.diffusion_model.input_blocks.1.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.time_embed.0.weight", "block_quant",
     "1280x320x1x1"},
    {"model.diffusion_model.time_embed.0.bias", "fp32"},
    {"model.diffusion_model.time_embed.2.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.time_embed.2.bias", "fp32"},
    {"model.diffusion_model.input_blocks.1.0.emb_layers.1.weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.input_blocks.1.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.input_blocks.1.0.out_layers.3.weight",
     "block_quant", "320x320x3x3"},
    {"model.diffusion_model.input_blocks.1.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.input_blocks.1.1.proj_in.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.input_blocks.1.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.norm1.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_0", "const", "320"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_1", "const", "320"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_2", "const", "320"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn1.to_out."
     "0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn1.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.norm2.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_3", "const", "320"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_4", "const", "320"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_5", "const", "320"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn2.to_out."
     "0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.attn2.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.norm3.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "2560x320x1x1"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.input_blocks.1.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.1.1.proj_out.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.input_blocks.1.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.input_blocks.2.0.in_layers.2.weight", "block_quant",
     "320x320x3x3"},
    {"model.diffusion_model.input_blocks.2.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.input_blocks.2.0.emb_layers.1.weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.input_blocks.2.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.input_blocks.2.0.out_layers.3.weight",
     "block_quant", "320x320x3x3"},
    {"model.diffusion_model.input_blocks.2.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.input_blocks.2.1.proj_in.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.input_blocks.2.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.norm1.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_6", "const", "320"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_7", "const", "320"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_8", "const", "320"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn1.to_out."
     "0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn1.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.norm2.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_9", "const", "320"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_10", "const", "320"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_11", "const", "320"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn2.to_out."
     "0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.attn2.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.norm3.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "2560x320x1x1"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.input_blocks.2.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.2.1.proj_out.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.input_blocks.2.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.input_blocks.3.0.op.weight", "block_quant",
     "320x320x3x3"},
    {"model.diffusion_model.input_blocks.3.0.op.bias", "fp32"},
    {"model.diffusion_model.input_blocks.4.0.skip_connection.weight",
     "block_quant", "640x320x1x1"},
    {"model.diffusion_model.input_blocks.4.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.input_blocks.4.0.in_layers.2.weight", "block_quant",
     "640x320x3x3"},
    {"model.diffusion_model.input_blocks.4.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.input_blocks.4.0.emb_layers.1.weight",
     "block_quant", "640x1280x1x1"},
    {"model.diffusion_model.input_blocks.4.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.input_blocks.4.0.out_layers.3.weight",
     "block_quant", "640x640x3x3"},
    {"model.diffusion_model.input_blocks.4.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.input_blocks.4.1.proj_in.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.input_blocks.4.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.norm1.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_12", "const", "640"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_13", "const", "640"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_14", "const", "640"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn1.to_out."
     "0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn1.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.norm2.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_15", "const", "640"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_16", "const", "640"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_17", "const", "640"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn2.to_out."
     "0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.attn2.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.norm3.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "5120x640x1x1"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "640x2560x1x1"},
    {"model.diffusion_model.input_blocks.4.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.4.1.proj_out.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.input_blocks.4.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.input_blocks.5.0.in_layers.2.weight", "block_quant",
     "640x640x3x3"},
    {"model.diffusion_model.input_blocks.5.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.input_blocks.5.0.emb_layers.1.weight",
     "block_quant", "640x1280x1x1"},
    {"model.diffusion_model.input_blocks.5.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.input_blocks.5.0.out_layers.3.weight",
     "block_quant", "640x640x3x3"},
    {"model.diffusion_model.input_blocks.5.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.input_blocks.5.1.proj_in.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.input_blocks.5.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.norm1.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_18", "const", "640"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_19", "const", "640"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_20", "const", "640"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn1.to_out."
     "0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn1.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.norm2.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_21", "const", "640"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_22", "const", "640"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_23", "const", "640"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn2.to_out."
     "0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.attn2.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.norm3.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "5120x640x1x1"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "640x2560x1x1"},
    {"model.diffusion_model.input_blocks.5.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.5.1.proj_out.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.input_blocks.5.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.input_blocks.6.0.op.weight", "block_quant",
     "640x640x3x3"},
    {"model.diffusion_model.input_blocks.6.0.op.bias", "fp32"},
    {"model.diffusion_model.input_blocks.7.0.skip_connection.weight",
     "block_quant", "1280x640x1x1"},
    {"model.diffusion_model.input_blocks.7.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.input_blocks.7.0.in_layers.2.weight", "block_quant",
     "1280x640x3x3"},
    {"model.diffusion_model.input_blocks.7.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.input_blocks.7.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.7.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.input_blocks.7.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.input_blocks.7.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.input_blocks.7.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.input_blocks.7.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.input_blocks.7.1.norm.weight", "fp32"},
    {"model.diffusion_model.input_blocks.7.1.norm.bias", "fp32"},
    {"model.diffusion_model.input_blocks.7.1.proj_in.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.7.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.norm1.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_24", "const", "1280"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_25", "const", "1280"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_26", "const", "1280"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn1.to_out."
     "0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn1.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.norm2.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_27", "const", "1280"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_28", "const", "1280"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_29", "const", "1280"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn2.to_out."
     "0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.attn2.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.norm3.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "10240x1280x1x1"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "1280x5120x1x1"},
    {"model.diffusion_model.input_blocks.7.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.7.1.proj_out.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.7.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.input_blocks.8.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.input_blocks.8.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.input_blocks.8.0.in_layers.2.weight", "block_quant",
     "1280x1280x3x3"},
    {"model.diffusion_model.input_blocks.8.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.input_blocks.8.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.8.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.input_blocks.8.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.input_blocks.8.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.input_blocks.8.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.input_blocks.8.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.input_blocks.8.1.norm.weight", "fp32"},
    {"model.diffusion_model.input_blocks.8.1.norm.bias", "fp32"},
    {"model.diffusion_model.input_blocks.8.1.proj_in.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.8.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.norm1.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_30", "const", "1280"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_31", "const", "1280"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_32", "const", "1280"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn1.to_out."
     "0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn1.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.norm2.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_33", "const", "1280"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_34", "const", "1280"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_35", "const", "1280"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn2.to_out."
     "0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.attn2.to_out."
     "0.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.norm3.weight",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "10240x1280x1x1"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "1280x5120x1x1"},
    {"model.diffusion_model.input_blocks.8.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.input_blocks.8.1.proj_out.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.8.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.input_blocks.9.0.op.weight", "block_quant",
     "1280x1280x3x3"},
    {"model.diffusion_model.input_blocks.9.0.op.bias", "fp32"},
    {"model.diffusion_model.input_blocks.10.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.input_blocks.10.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.input_blocks.10.0.in_layers.2.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.input_blocks.10.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.input_blocks.10.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.10.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.input_blocks.10.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.input_blocks.10.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.input_blocks.10.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.input_blocks.10.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.input_blocks.11.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.input_blocks.11.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.input_blocks.11.0.in_layers.2.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.input_blocks.11.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.input_blocks.11.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.input_blocks.11.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.input_blocks.11.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.input_blocks.11.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.input_blocks.11.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.input_blocks.11.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.middle_block.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.middle_block.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.middle_block.0.in_layers.2.weight", "block_quant",
     "1280x1280x3x3"},
    {"model.diffusion_model.middle_block.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.middle_block.0.emb_layers.1.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.middle_block.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.middle_block.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.middle_block.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.middle_block.0.out_layers.3.weight", "block_quant",
     "1280x1280x3x3"},
    {"model.diffusion_model.middle_block.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.middle_block.1.norm.weight", "fp32"},
    {"model.diffusion_model.middle_block.1.norm.bias", "fp32"},
    {"model.diffusion_model.middle_block.1.proj_in.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.middle_block.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.norm1.weight",
     "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_36", "const", "1280"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_37", "const", "1280"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_38", "const", "1280"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn1.to_out.0."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn1.to_out.0."
     "bias",
     "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.norm2.weight",
     "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_39", "const", "1280"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_40", "const", "1280"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_41", "const", "1280"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn2.to_out.0."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.attn2.to_out.0."
     "bias",
     "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.norm3.weight",
     "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.ff.net.0.proj."
     "weight",
     "block_quant", "10240x1280x1x1"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.ff.net.0.proj."
     "bias",
     "fp32"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "1280x5120x1x1"},
    {"model.diffusion_model.middle_block.1.transformer_blocks.0.ff.net.2.bias",
     "fp32"},
    {"model.diffusion_model.middle_block.1.proj_out.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.middle_block.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.middle_block.2.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.middle_block.2.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.middle_block.2.in_layers.2.weight", "block_quant",
     "1280x1280x3x3"},
    {"model.diffusion_model.middle_block.2.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.middle_block.2.emb_layers.1.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.middle_block.2.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.middle_block.2.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.middle_block.2.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.middle_block.2.out_layers.3.weight", "block_quant",
     "1280x1280x3x3"},
    {"model.diffusion_model.middle_block.2.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.0.0.skip_connection.weight",
     "block_quant", "1280x2560x1x1"},
    {"model.diffusion_model.output_blocks.0.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.0.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.0.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.0.0.in_layers.2.weight",
     "block_quant", "1280x2560x3x3"},
    {"model.diffusion_model.output_blocks.0.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.0.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.0.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.0.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.0.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.0.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.output_blocks.0.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.1.0.skip_connection.weight",
     "block_quant", "1280x2560x1x1"},
    {"model.diffusion_model.output_blocks.1.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.1.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.1.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.1.0.in_layers.2.weight",
     "block_quant", "1280x2560x3x3"},
    {"model.diffusion_model.output_blocks.1.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.1.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.1.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.1.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.1.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.1.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.output_blocks.1.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.2.0.skip_connection.weight",
     "block_quant", "1280x2560x1x1"},
    {"model.diffusion_model.output_blocks.2.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.2.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.2.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.2.0.in_layers.2.weight",
     "block_quant", "1280x2560x3x3"},
    {"model.diffusion_model.output_blocks.2.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.2.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.2.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.2.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.2.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.2.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.output_blocks.2.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.2.1.conv.weight", "block_quant",
     "1280x1280x3x3"},
    {"model.diffusion_model.output_blocks.2.1.conv.bias", "fp32"},
    {"model.diffusion_model.output_blocks.3.0.skip_connection.weight",
     "block_quant", "1280x2560x1x1"},
    {"model.diffusion_model.output_blocks.3.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.3.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.3.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.3.0.in_layers.2.weight",
     "block_quant", "1280x2560x3x3"},
    {"model.diffusion_model.output_blocks.3.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.3.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.3.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.3.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.3.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.3.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.output_blocks.3.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.3.1.norm.weight", "fp32"},
    {"model.diffusion_model.output_blocks.3.1.norm.bias", "fp32"},
    {"model.diffusion_model.output_blocks.3.1.proj_in.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.3.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.norm1."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_42", "const", "1280"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_43", "const", "1280"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_44", "const", "1280"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn1.to_"
     "out.0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn1.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.norm2."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_45", "const", "1280"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_46", "const", "1280"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_47", "const", "1280"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn2.to_"
     "out.0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.attn2.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.norm3."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "10240x1280x1x1"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "1280x5120x1x1"},
    {"model.diffusion_model.output_blocks.3.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.3.1.proj_out.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.3.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.output_blocks.4.0.skip_connection.weight",
     "block_quant", "1280x2560x1x1"},
    {"model.diffusion_model.output_blocks.4.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.4.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.4.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.4.0.in_layers.2.weight",
     "block_quant", "1280x2560x3x3"},
    {"model.diffusion_model.output_blocks.4.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.4.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.4.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.4.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.4.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.4.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.output_blocks.4.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.4.1.norm.weight", "fp32"},
    {"model.diffusion_model.output_blocks.4.1.norm.bias", "fp32"},
    {"model.diffusion_model.output_blocks.4.1.proj_in.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.4.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.norm1."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_48", "const", "1280"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_49", "const", "1280"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_50", "const", "1280"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn1.to_"
     "out.0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn1.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.norm2."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_51", "const", "1280"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_52", "const", "1280"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_53", "const", "1280"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn2.to_"
     "out.0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.attn2.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.norm3."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "10240x1280x1x1"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "1280x5120x1x1"},
    {"model.diffusion_model.output_blocks.4.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.4.1.proj_out.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.4.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.0.skip_connection.weight",
     "block_quant", "1280x1920x1x1"},
    {"model.diffusion_model.output_blocks.5.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.5.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.0.in_layers.2.weight",
     "block_quant", "1280x1920x3x3"},
    {"model.diffusion_model.output_blocks.5.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.0.emb_layers.1.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.5.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.0.out_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.5.0.out_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.0.out_layers.3.weight",
     "block_quant", "1280x1280x3x3"},
    {"model.diffusion_model.output_blocks.5.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.1.norm.weight", "fp32"},
    {"model.diffusion_model.output_blocks.5.1.norm.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.1.proj_in.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.5.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.norm1."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_54", "const", "1280"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_55", "const", "1280"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_56", "const", "1280"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn1.to_"
     "out.0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn1.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.norm2."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "1280x1280x1x1"},
    {"const_57", "const", "1280"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_58", "const", "1280"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "1280x768x1x1"},
    {"const_59", "const", "1280"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn2.to_"
     "out.0.weight",
     "block_quant", "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.attn2.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.norm3."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "10240x1280x1x1"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "1280x5120x1x1"},
    {"model.diffusion_model.output_blocks.5.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.5.1.proj_out.weight", "block_quant",
     "1280x1280x1x1"},
    {"model.diffusion_model.output_blocks.5.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.output_blocks.5.2.conv.weight", "block_quant",
     "1280x1280x3x3"},
    {"model.diffusion_model.output_blocks.5.2.conv.bias", "fp32"},
    {"model.diffusion_model.output_blocks.6.0.skip_connection.weight",
     "block_quant", "640x1920x1x1"},
    {"model.diffusion_model.output_blocks.6.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.6.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.6.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.6.0.in_layers.2.weight",
     "block_quant", "640x1920x3x3"},
    {"model.diffusion_model.output_blocks.6.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.6.0.emb_layers.1.weight",
     "block_quant", "640x1280x1x1"},
    {"model.diffusion_model.output_blocks.6.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.6.0.out_layers.3.weight",
     "block_quant", "640x640x3x3"},
    {"model.diffusion_model.output_blocks.6.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.6.1.proj_in.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.output_blocks.6.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.norm1."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_60", "const", "640"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_61", "const", "640"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_62", "const", "640"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn1.to_"
     "out.0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn1.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.norm2."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_63", "const", "640"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_64", "const", "640"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_65", "const", "640"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn2.to_"
     "out.0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.attn2.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.norm3."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "5120x640x1x1"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "640x2560x1x1"},
    {"model.diffusion_model.output_blocks.6.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.6.1.proj_out.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.output_blocks.6.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.output_blocks.7.0.skip_connection.weight",
     "block_quant", "640x1280x1x1"},
    {"model.diffusion_model.output_blocks.7.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.7.0.in_layers.0.weight", "fp32"},
    {"model.diffusion_model.output_blocks.7.0.in_layers.0.bias", "fp32"},
    {"model.diffusion_model.output_blocks.7.0.in_layers.2.weight",
     "block_quant", "640x1280x3x3"},
    {"model.diffusion_model.output_blocks.7.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.7.0.emb_layers.1.weight",
     "block_quant", "640x1280x1x1"},
    {"model.diffusion_model.output_blocks.7.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.7.0.out_layers.3.weight",
     "block_quant", "640x640x3x3"},
    {"model.diffusion_model.output_blocks.7.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.7.1.proj_in.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.output_blocks.7.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.norm1."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_66", "const", "640"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_67", "const", "640"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_68", "const", "640"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn1.to_"
     "out.0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn1.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.norm2."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_69", "const", "640"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_70", "const", "640"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_71", "const", "640"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn2.to_"
     "out.0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.attn2.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.norm3."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "5120x640x1x1"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "640x2560x1x1"},
    {"model.diffusion_model.output_blocks.7.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.7.1.proj_out.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.output_blocks.7.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.output_blocks.8.0.skip_connection.weight",
     "block_quant", "640x960x1x1"},
    {"model.diffusion_model.output_blocks.8.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.8.0.in_layers.2.weight",
     "block_quant", "640x960x3x3"},
    {"model.diffusion_model.output_blocks.8.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.8.0.emb_layers.1.weight",
     "block_quant", "640x1280x1x1"},
    {"model.diffusion_model.output_blocks.8.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.8.0.out_layers.3.weight",
     "block_quant", "640x640x3x3"},
    {"model.diffusion_model.output_blocks.8.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.8.1.proj_in.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.output_blocks.8.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.norm1."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_72", "const", "640"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_73", "const", "640"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_74", "const", "640"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn1.to_"
     "out.0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn1.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.norm2."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "640x640x1x1"},
    {"const_75", "const", "640"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_76", "const", "640"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "640x768x1x1"},
    {"const_77", "const", "640"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn2.to_"
     "out.0.weight",
     "block_quant", "640x640x1x1"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.attn2.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.norm3."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "5120x640x1x1"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "640x2560x1x1"},
    {"model.diffusion_model.output_blocks.8.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.8.1.proj_out.weight", "block_quant",
     "640x640x1x1"},
    {"model.diffusion_model.output_blocks.8.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.output_blocks.8.2.conv.weight", "block_quant",
     "640x640x3x3"},
    {"model.diffusion_model.output_blocks.8.2.conv.bias", "fp32"},
    {"model.diffusion_model.output_blocks.9.0.skip_connection.weight",
     "block_quant", "320x960x1x1"},
    {"model.diffusion_model.output_blocks.9.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.9.0.in_layers.2.weight",
     "block_quant", "320x960x3x3"},
    {"model.diffusion_model.output_blocks.9.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.9.0.emb_layers.1.weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.output_blocks.9.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.9.0.out_layers.3.weight",
     "block_quant", "320x320x3x3"},
    {"model.diffusion_model.output_blocks.9.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.9.1.proj_in.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.output_blocks.9.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.norm1."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_78", "const", "320"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_79", "const", "320"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_80", "const", "320"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn1.to_"
     "out.0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn1.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.norm2."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_81", "const", "320"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_82", "const", "320"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_83", "const", "320"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn2.to_"
     "out.0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.attn2.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.norm3."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "2560x320x1x1"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.output_blocks.9.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.9.1.proj_out.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.output_blocks.9.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.output_blocks.10.0.skip_connection.weight",
     "block_quant", "320x640x1x1"},
    {"model.diffusion_model.output_blocks.10.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.10.0.in_layers.2.weight",
     "block_quant", "320x640x3x3"},
    {"model.diffusion_model.output_blocks.10.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.10.0.emb_layers.1.weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.output_blocks.10.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.10.0.out_layers.3.weight",
     "block_quant", "320x320x3x3"},
    {"model.diffusion_model.output_blocks.10.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.10.1.proj_in.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.output_blocks.10.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.norm1."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_84", "const", "320"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_85", "const", "320"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_86", "const", "320"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn1.to_"
     "out.0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn1.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.norm2."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_87", "const", "320"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_88", "const", "320"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_89", "const", "320"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn2.to_"
     "out.0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.attn2.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.norm3."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "2560x320x1x1"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.output_blocks.10.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.10.1.proj_out.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.output_blocks.10.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.output_blocks.11.0.skip_connection.weight",
     "block_quant", "320x640x1x1"},
    {"model.diffusion_model.output_blocks.11.0.skip_connection.bias", "fp32"},
    {"model.diffusion_model.output_blocks.11.0.in_layers.2.weight",
     "block_quant", "320x640x3x3"},
    {"model.diffusion_model.output_blocks.11.0.in_layers.2.bias", "fp32"},
    {"model.diffusion_model.output_blocks.11.0.emb_layers.1.weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.output_blocks.11.0.emb_layers.1.bias", "fp32"},
    {"model.diffusion_model.output_blocks.11.0.out_layers.3.weight",
     "block_quant", "320x320x3x3"},
    {"model.diffusion_model.output_blocks.11.0.out_layers.3.bias", "fp32"},
    {"model.diffusion_model.output_blocks.11.1.proj_in.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.output_blocks.11.1.proj_in.bias", "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.norm1."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.norm1.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn1.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_90", "const", "320"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn1.to_k."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_91", "const", "320"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn1.to_v."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_92", "const", "320"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn1.to_"
     "out.0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn1.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.norm2."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.norm2.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn2.to_q."
     "weight",
     "block_quant", "320x320x1x1"},
    {"const_93", "const", "320"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn2.to_k."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_94", "const", "320"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn2.to_v."
     "weight",
     "block_quant", "320x768x1x1"},
    {"const_95", "const", "320"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn2.to_"
     "out.0.weight",
     "block_quant", "320x320x1x1"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.attn2.to_"
     "out.0.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.norm3."
     "weight",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.norm3.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.ff.net.0."
     "proj.weight",
     "block_quant", "2560x320x1x1"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.ff.net.0."
     "proj.bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.ff.net.2."
     "weight",
     "block_quant", "320x1280x1x1"},
    {"model.diffusion_model.output_blocks.11.1.transformer_blocks.0.ff.net.2."
     "bias",
     "fp32"},
    {"model.diffusion_model.output_blocks.11.1.proj_out.weight", "block_quant",
     "320x320x1x1"},
    {"model.diffusion_model.output_blocks.11.1.proj_out.bias", "fp32"},
    {"model.diffusion_model.out.2.weight", "block_quant", "4x320x3x3"},
    {"model.diffusion_model.out.2.bias", "fp32"}};

std::vector<std::vector<std::string>> vae_decoder_structure = {
    {"first_stage_model.post_quant_conv.weight", "fp16"},
    {"first_stage_model.post_quant_conv.bias", "fp32"},
    {"first_stage_model.decoder.conv_in.weight", "fp16"},
    {"first_stage_model.decoder.conv_in.bias", "fp32"},
    {"first_stage_model.decoder.mid.block_1.conv1.weight", "fp16"},
    {"first_stage_model.decoder.mid.block_1.conv1.bias", "fp32"},
    {"first_stage_model.decoder.mid.block_1.conv2.weight", "fp16"},
    {"first_stage_model.decoder.mid.block_1.conv2.bias", "fp32"},
    {"first_stage_model.decoder.mid.attn_1.q.weight", "fp16"},
    {"first_stage_model.decoder.mid.attn_1.q.bias", "fp32"},
    {"first_stage_model.decoder.mid.attn_1.k.weight", "fp16"},
    {"first_stage_model.decoder.mid.attn_1.k.bias", "fp32"},
    {"first_stage_model.decoder.mid.attn_1.v.weight", "fp16"},
    {"first_stage_model.decoder.mid.attn_1.v.bias", "fp32"},
    {"first_stage_model.decoder.mid.attn_1.proj_out.weight", "fp16"},
    {"first_stage_model.decoder.mid.attn_1.proj_out.bias", "fp32"},
    {"first_stage_model.decoder.mid.block_2.conv1.weight", "fp16"},
    {"first_stage_model.decoder.mid.block_2.conv1.bias", "fp32"},
    {"first_stage_model.decoder.mid.block_2.conv2.weight", "fp16"},
    {"first_stage_model.decoder.mid.block_2.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.3.block.0.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.3.block.0.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.3.block.0.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.3.block.0.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.3.block.1.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.3.block.1.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.3.block.1.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.3.block.1.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.3.block.2.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.3.block.2.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.3.block.2.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.3.block.2.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.3.upsample.conv.weight", "fp16"},
    {"first_stage_model.decoder.up.3.upsample.conv.bias", "fp32"},
    {"first_stage_model.decoder.up.2.block.0.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.2.block.0.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.2.block.0.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.2.block.0.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.2.block.1.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.2.block.1.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.2.block.1.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.2.block.1.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.2.block.2.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.2.block.2.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.2.block.2.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.2.block.2.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.2.upsample.conv.weight", "fp16"},
    {"first_stage_model.decoder.up.2.upsample.conv.bias", "fp32"},
    {"first_stage_model.decoder.up.1.block.0.nin_shortcut.weight", "fp16"},
    {"first_stage_model.decoder.up.1.block.0.nin_shortcut.bias", "fp32"},
    {"first_stage_model.decoder.up.1.block.0.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.1.block.0.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.1.block.0.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.1.block.0.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.1.block.1.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.1.block.1.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.1.block.1.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.1.block.1.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.1.block.2.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.1.block.2.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.1.block.2.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.1.block.2.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.1.upsample.conv.weight", "fp16"},
    {"first_stage_model.decoder.up.1.upsample.conv.bias", "fp32"},
    {"first_stage_model.decoder.up.0.block.0.nin_shortcut.weight", "fp16"},
    {"first_stage_model.decoder.up.0.block.0.nin_shortcut.bias", "fp32"},
    {"first_stage_model.decoder.up.0.block.0.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.0.block.0.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.0.block.0.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.0.block.0.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.0.block.1.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.0.block.1.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.0.block.1.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.0.block.1.conv2.bias", "fp32"},
    {"first_stage_model.decoder.up.0.block.2.conv1.weight", "fp16"},
    {"first_stage_model.decoder.up.0.block.2.conv1.bias", "fp32"},
    {"first_stage_model.decoder.up.0.block.2.conv2.weight", "fp16"},
    {"first_stage_model.decoder.up.0.block.2.conv2.bias", "fp32"},
    {"first_stage_model.decoder.conv_out.weight", "fp16"},
    {"first_stage_model.decoder.conv_out.bias", "fp32"}};

std::vector<std::vector<std::string>> vae_encoder_structure = {
    {"first_stage_model.encoder.conv_in.weight", "fp16"},
    {"first_stage_model.encoder.conv_in.bias", "fp32"},
    {"first_stage_model.encoder.down.0.block.0.conv1.weight", "fp16"},
    {"first_stage_model.encoder.down.0.block.0.conv1.bias", "fp32"},
    {"first_stage_model.encoder.down.0.block.0.conv2.weight", "fp16"},
    {"first_stage_model.encoder.down.0.block.0.conv2.bias", "fp32"},
    {"first_stage_model.encoder.down.0.block.1.conv1.weight", "fp16"},
    {"first_stage_model.encoder.down.0.block.1.conv1.bias", "fp32"},
    {"first_stage_model.encoder.down.0.block.1.conv2.weight", "fp16"},
    {"first_stage_model.encoder.down.0.block.1.conv2.bias", "fp32"},
    {"first_stage_model.encoder.down.0.downsample.conv.weight", "fp16"},
    {"first_stage_model.encoder.down.0.downsample.conv.bias", "fp32"},
    {"first_stage_model.encoder.down.1.block.0.nin_shortcut.weight", "fp16"},
    {"first_stage_model.encoder.down.1.block.0.nin_shortcut.bias", "fp32"},
    {"first_stage_model.encoder.down.1.block.0.conv1.weight", "fp16"},
    {"first_stage_model.encoder.down.1.block.0.conv1.bias", "fp32"},
    {"first_stage_model.encoder.down.1.block.0.conv2.weight", "fp16"},
    {"first_stage_model.encoder.down.1.block.0.conv2.bias", "fp32"},
    {"first_stage_model.encoder.down.1.block.1.conv1.weight", "fp16"},
    {"first_stage_model.encoder.down.1.block.1.conv1.bias", "fp32"},
    {"first_stage_model.encoder.down.1.block.1.conv2.weight", "fp16"},
    {"first_stage_model.encoder.down.1.block.1.conv2.bias", "fp32"},
    {"first_stage_model.encoder.down.1.downsample.conv.weight", "fp16"},
    {"first_stage_model.encoder.down.1.downsample.conv.bias", "fp32"},
    {"first_stage_model.encoder.down.2.block.0.nin_shortcut.weight", "fp16"},
    {"first_stage_model.encoder.down.2.block.0.nin_shortcut.bias", "fp32"},
    {"first_stage_model.encoder.down.2.block.0.conv1.weight", "fp16"},
    {"first_stage_model.encoder.down.2.block.0.conv1.bias", "fp32"},
    {"first_stage_model.encoder.down.2.block.0.conv2.weight", "fp16"},
    {"first_stage_model.encoder.down.2.block.0.conv2.bias", "fp32"},
    {"first_stage_model.encoder.down.2.block.1.conv1.weight", "fp16"},
    {"first_stage_model.encoder.down.2.block.1.conv1.bias", "fp32"},
    {"first_stage_model.encoder.down.2.block.1.conv2.weight", "fp16"},
    {"first_stage_model.encoder.down.2.block.1.conv2.bias", "fp32"},
    {"first_stage_model.encoder.down.2.downsample.conv.weight", "fp16"},
    {"first_stage_model.encoder.down.2.downsample.conv.bias", "fp32"},
    {"first_stage_model.encoder.down.3.block.0.conv1.weight", "fp16"},
    {"first_stage_model.encoder.down.3.block.0.conv1.bias", "fp32"},
    {"first_stage_model.encoder.down.3.block.0.conv2.weight", "fp16"},
    {"first_stage_model.encoder.down.3.block.0.conv2.bias", "fp32"},
    {"first_stage_model.encoder.down.3.block.1.conv1.weight", "fp16"},
    {"first_stage_model.encoder.down.3.block.1.conv1.bias", "fp32"},
    {"first_stage_model.encoder.down.3.block.1.conv2.weight", "fp16"},
    {"first_stage_model.encoder.down.3.block.1.conv2.bias", "fp32"},
    {"first_stage_model.encoder.mid.block_1.conv1.weight", "fp16"},
    {"first_stage_model.encoder.mid.block_1.conv1.bias", "fp32"},
    {"first_stage_model.encoder.mid.block_1.conv2.weight", "fp16"},
    {"first_stage_model.encoder.mid.block_1.conv2.bias", "fp32"},
    {"first_stage_model.encoder.mid.attn_1.q.weight", "fp16"},
    {"first_stage_model.encoder.mid.attn_1.q.bias", "fp32"},
    {"first_stage_model.encoder.mid.attn_1.k.weight", "fp16"},
    {"first_stage_model.encoder.mid.attn_1.k.bias", "fp32"},
    {"first_stage_model.encoder.mid.attn_1.v.weight", "fp16"},
    {"first_stage_model.encoder.mid.attn_1.v.bias", "fp32"},
    {"first_stage_model.encoder.mid.attn_1.proj_out.weight", "fp16"},
    {"first_stage_model.encoder.mid.attn_1.proj_out.bias", "fp32"},
    {"first_stage_model.encoder.mid.block_2.conv1.weight", "fp16"},
    {"first_stage_model.encoder.mid.block_2.conv1.bias", "fp32"},
    {"first_stage_model.encoder.mid.block_2.conv2.weight", "fp16"},
    {"first_stage_model.encoder.mid.block_2.conv2.bias", "fp32"},
    {"first_stage_model.encoder.conv_out.weight", "fp16"},
    {"first_stage_model.encoder.conv_out.bias", "fp32"},
    {"first_stage_model.quant_conv.weight", "fp16"},
    {"first_stage_model.quant_conv.bias", "fp32"}};

std::unordered_map<std::string, int> vae_encoder_small_weights = {
    {"first_stage_model.encoder.down.0.block.0.norm1.bias", 119516},
    {"first_stage_model.encoder.down.0.block.0.norm1.weight", 120008},
    {"first_stage_model.encoder.down.0.block.0.norm2.bias", 117180},
    {"first_stage_model.encoder.down.0.block.0.norm2.weight", 117648},
    {"first_stage_model.encoder.down.0.block.1.norm1.bias", 114768},
    {"first_stage_model.encoder.down.0.block.1.norm1.weight", 115236},
    {"first_stage_model.encoder.down.0.block.1.norm2.bias", 112476},
    {"first_stage_model.encoder.down.0.block.1.norm2.weight", 112944},
    {"first_stage_model.encoder.down.1.block.0.norm1.bias", 109116},
    {"first_stage_model.encoder.down.1.block.0.norm1.weight", 109584},
    {"first_stage_model.encoder.down.1.block.0.norm2.bias", 106296},
    {"first_stage_model.encoder.down.1.block.0.norm2.weight", 107020},
    {"first_stage_model.encoder.down.1.block.1.norm1.bias", 103356},
    {"first_stage_model.encoder.down.1.block.1.norm1.weight", 104080},
    {"first_stage_model.encoder.down.1.block.1.norm2.bias", 100536},
    {"first_stage_model.encoder.down.1.block.1.norm2.weight", 101260},
    {"first_stage_model.encoder.down.2.block.0.norm1.bias", 96904},
    {"first_stage_model.encoder.down.2.block.0.norm1.weight", 97628},
    {"first_stage_model.encoder.down.2.block.0.norm2.bias", 93064},
    {"first_stage_model.encoder.down.2.block.0.norm2.weight", 94300},
    {"first_stage_model.encoder.down.2.block.1.norm1.bias", 89100},
    {"first_stage_model.encoder.down.2.block.1.norm1.weight", 90336},
    {"first_stage_model.encoder.down.2.block.1.norm2.bias", 85256},
    {"first_stage_model.encoder.down.2.block.1.norm2.weight", 86492},
    {"first_stage_model.encoder.down.3.block.0.norm1.bias", 80892},
    {"first_stage_model.encoder.down.3.block.0.norm1.weight", 82128},
    {"first_stage_model.encoder.down.3.block.0.norm2.bias", 77048},
    {"first_stage_model.encoder.down.3.block.0.norm2.weight", 78284},
    {"first_stage_model.encoder.down.3.block.1.norm1.bias", 73084},
    {"first_stage_model.encoder.down.3.block.1.norm1.weight", 74320},
    {"first_stage_model.encoder.down.3.block.1.norm2.bias", 69240},
    {"first_stage_model.encoder.down.3.block.1.norm2.weight", 70476},
    {"first_stage_model.encoder.mid.attn_1.norm.bias", 53608},
    {"first_stage_model.encoder.mid.attn_1.norm.weight", 54844},
    {"first_stage_model.encoder.mid.block_1.norm1.bias", 65296},
    {"first_stage_model.encoder.mid.block_1.norm1.weight", 66528},
    {"first_stage_model.encoder.mid.block_1.norm2.bias", 61496},
    {"first_stage_model.encoder.mid.block_1.norm2.weight", 62728},
    {"first_stage_model.encoder.mid.block_2.norm1.bias", 38524},
    {"first_stage_model.encoder.mid.block_2.norm1.weight", 39756},
    {"first_stage_model.encoder.mid.block_2.norm2.bias", 34720},
    {"first_stage_model.encoder.mid.block_2.norm2.weight", 35952},
    {"first_stage_model.encoder.norm_out.bias", 30796},
    {"first_stage_model.encoder.norm_out.weight", 32016}};

std::unordered_map<std::string, int> vae_decoder_small_weights = {
    {"first_stage_model.decoder.mid.attn_1.norm.bias", 137824},
    {"first_stage_model.decoder.mid.attn_1.norm.weight", 139060},
    {"first_stage_model.decoder.mid.block_1.norm1.bias", 149480},
    {"first_stage_model.decoder.mid.block_1.norm1.weight", 150736},
    {"first_stage_model.decoder.mid.block_1.norm2.bias", 145684},
    {"first_stage_model.decoder.mid.block_1.norm2.weight", 146916},
    {"first_stage_model.decoder.mid.block_2.norm1.bias", 122756},
    {"first_stage_model.decoder.mid.block_2.norm1.weight", 123988},
    {"first_stage_model.decoder.mid.block_2.norm2.bias", 118952},
    {"first_stage_model.decoder.mid.block_2.norm2.weight", 120184},
    {"first_stage_model.decoder.norm_out.bias", 34796},
    {"first_stage_model.decoder.norm_out.weight", 35248},
    {"first_stage_model.decoder.up.0.block.0.norm1.bias", 48792},
    {"first_stage_model.decoder.up.0.block.0.norm1.weight", 49516},
    {"first_stage_model.decoder.up.0.block.0.norm2.bias", 46504},
    {"first_stage_model.decoder.up.0.block.0.norm2.weight", 46972},
    {"first_stage_model.decoder.up.0.block.1.norm1.bias", 44104},
    {"first_stage_model.decoder.up.0.block.1.norm1.weight", 44572},
    {"first_stage_model.decoder.up.0.block.1.norm2.bias", 41816},
    {"first_stage_model.decoder.up.0.block.1.norm2.weight", 42284},
    {"first_stage_model.decoder.up.0.block.2.norm1.bias", 39416},
    {"first_stage_model.decoder.up.0.block.2.norm1.weight", 39884},
    {"first_stage_model.decoder.up.0.block.2.norm2.bias", 37128},
    {"first_stage_model.decoder.up.0.block.2.norm2.weight", 37596},
    {"first_stage_model.decoder.up.1.block.0.norm1.bias", 66648},
    {"first_stage_model.decoder.up.1.block.0.norm1.weight", 67884},
    {"first_stage_model.decoder.up.1.block.0.norm2.bias", 63848},
    {"first_stage_model.decoder.up.1.block.0.norm2.weight", 64572},
    {"first_stage_model.decoder.up.1.block.1.norm1.bias", 60936},
    {"first_stage_model.decoder.up.1.block.1.norm1.weight", 61660},
    {"first_stage_model.decoder.up.1.block.1.norm2.bias", 58136},
    {"first_stage_model.decoder.up.1.block.1.norm2.weight", 58860},
    {"first_stage_model.decoder.up.1.block.2.norm1.bias", 55224},
    {"first_stage_model.decoder.up.1.block.2.norm1.weight", 55948},
    {"first_stage_model.decoder.up.1.block.2.norm2.bias", 52424},
    {"first_stage_model.decoder.up.1.block.2.norm2.weight", 53148},
    {"first_stage_model.decoder.up.2.block.0.norm1.bias", 90644},
    {"first_stage_model.decoder.up.2.block.0.norm1.weight", 91880},
    {"first_stage_model.decoder.up.2.block.0.norm2.bias", 86824},
    {"first_stage_model.decoder.up.2.block.0.norm2.weight", 88060},
    {"first_stage_model.decoder.up.2.block.1.norm1.bias", 82888},
    {"first_stage_model.decoder.up.2.block.1.norm1.weight", 84124},
    {"first_stage_model.decoder.up.2.block.1.norm2.bias", 79064},
    {"first_stage_model.decoder.up.2.block.1.norm2.weight", 80300},
    {"first_stage_model.decoder.up.2.block.2.norm1.bias", 75128},
    {"first_stage_model.decoder.up.2.block.2.norm1.weight", 76364},
    {"first_stage_model.decoder.up.2.block.2.norm2.bias", 71304},
    {"first_stage_model.decoder.up.2.block.2.norm2.weight", 72540},
    {"first_stage_model.decoder.up.3.block.0.norm1.bias", 114960},
    {"first_stage_model.decoder.up.3.block.0.norm1.weight", 116196},
    {"first_stage_model.decoder.up.3.block.0.norm2.bias", 111136},
    {"first_stage_model.decoder.up.3.block.0.norm2.weight", 112372},
    {"first_stage_model.decoder.up.3.block.1.norm1.bias", 107132},
    {"first_stage_model.decoder.up.3.block.1.norm1.weight", 108368},
    {"first_stage_model.decoder.up.3.block.1.norm2.bias", 103312},
    {"first_stage_model.decoder.up.3.block.1.norm2.weight", 104548},
    {"first_stage_model.decoder.up.3.block.2.norm1.bias", 99308},
    {"first_stage_model.decoder.up.3.block.2.norm1.weight", 100544},
    {"first_stage_model.decoder.up.3.block.2.norm2.bias", 95488},
    {"first_stage_model.decoder.up.3.block.2.norm2.weight", 96724}};

std::unordered_map<std::string, int> unet_small_weights = {
    {"model.diffusion_model.input_blocks.1.0.in_layers.0.bias", 1102828},
    {"model.diffusion_model.input_blocks.1.0.in_layers.0.weight", 1104344},
    {"model.diffusion_model.input_blocks.1.0.out_layers.0.bias", 1089404},
    {"model.diffusion_model.input_blocks.1.0.out_layers.0.weight", 1090888},
    {"model.diffusion_model.input_blocks.1.1.norm.bias", 1085032},
    {"model.diffusion_model.input_blocks.1.1.norm.weight", 1086520},
    {"model.diffusion_model.input_blocks.2.0.in_layers.0.bias", 1046396},
    {"model.diffusion_model.input_blocks.2.0.in_layers.0.weight", 1047880},
    {"model.diffusion_model.input_blocks.2.0.out_layers.0.bias", 1040948},
    {"model.diffusion_model.input_blocks.2.0.out_layers.0.weight", 1042432},
    {"model.diffusion_model.input_blocks.2.1.norm.bias", 1036576},
    {"model.diffusion_model.input_blocks.2.1.norm.weight", 1038064},
    {"model.diffusion_model.input_blocks.4.0.in_layers.0.bias", 999384},
    {"model.diffusion_model.input_blocks.4.0.in_layers.0.weight", 1000868},
    {"model.diffusion_model.input_blocks.4.0.out_layers.0.bias", 991288},
    {"model.diffusion_model.input_blocks.4.0.out_layers.0.weight", 994052},
    {"model.diffusion_model.input_blocks.4.1.norm.bias", 984360},
    {"model.diffusion_model.input_blocks.4.1.norm.weight", 987128},
    {"model.diffusion_model.input_blocks.5.0.in_layers.0.bias", 944788},
    {"model.diffusion_model.input_blocks.5.0.in_layers.0.weight", 947552},
    {"model.diffusion_model.input_blocks.5.0.out_layers.0.bias", 936780},
    {"model.diffusion_model.input_blocks.5.0.out_layers.0.weight", 939544},
    {"model.diffusion_model.input_blocks.5.1.norm.bias", 929848},
    {"model.diffusion_model.input_blocks.5.1.norm.weight", 932616},
    {"model.diffusion_model.input_blocks.7.0.in_layers.0.bias", 889896},
    {"model.diffusion_model.input_blocks.7.0.in_layers.0.weight", 892660},
    {"model.diffusion_model.out.0.bias", 316440},
    {"model.diffusion_model.out.0.weight", 317908},
    {"model.diffusion_model.output_blocks.10.0.in_layers.0.bias", 411920},
    {"model.diffusion_model.output_blocks.10.0.in_layers.0.weight", 414684},
    {"model.diffusion_model.output_blocks.10.0.out_layers.0.bias", 406508},
    {"model.diffusion_model.output_blocks.10.0.out_layers.0.weight", 407992},
    {"model.diffusion_model.output_blocks.10.1.norm.bias", 402156},
    {"model.diffusion_model.output_blocks.10.1.norm.weight", 403640},
    {"model.diffusion_model.output_blocks.11.0.in_layers.0.bias", 362680},
    {"model.diffusion_model.output_blocks.11.0.in_layers.0.weight", 365444},
    {"model.diffusion_model.output_blocks.11.0.out_layers.0.bias", 357268},
    {"model.diffusion_model.output_blocks.11.0.out_layers.0.weight", 358752},
    {"model.diffusion_model.output_blocks.11.1.norm.bias", 352916},
    {"model.diffusion_model.output_blocks.11.1.norm.weight", 354400},
    {"model.diffusion_model.output_blocks.6.0.out_layers.0.bias", 616732},
    {"model.diffusion_model.output_blocks.6.0.out_layers.0.weight", 619496},
    {"model.diffusion_model.output_blocks.6.1.norm.bias", 609820},
    {"model.diffusion_model.output_blocks.6.1.norm.weight", 612584},
    {"model.diffusion_model.output_blocks.7.0.out_layers.0.bias", 567452},
    {"model.diffusion_model.output_blocks.7.0.out_layers.0.weight", 570216},
    {"model.diffusion_model.output_blocks.7.1.norm.bias", 560540},
    {"model.diffusion_model.output_blocks.7.1.norm.weight", 563304},
    {"model.diffusion_model.output_blocks.8.0.in_layers.0.bias", 518504},
    {"model.diffusion_model.output_blocks.8.0.in_layers.0.weight", 522548},
    {"model.diffusion_model.output_blocks.8.0.out_layers.0.bias", 510532},
    {"model.diffusion_model.output_blocks.8.0.out_layers.0.weight", 513296},
    {"model.diffusion_model.output_blocks.8.1.norm.bias", 503620},
    {"model.diffusion_model.output_blocks.8.1.norm.weight", 506384},
    {"model.diffusion_model.output_blocks.9.0.in_layers.0.bias", 461160},
    {"model.diffusion_model.output_blocks.9.0.in_layers.0.weight", 465204},
    {"model.diffusion_model.output_blocks.9.0.out_layers.0.bias", 455748},
    {"model.diffusion_model.output_blocks.9.0.out_layers.0.weight", 457232},
    {"model.diffusion_model.output_blocks.9.1.norm.bias", 451396},
    {"model.diffusion_model.output_blocks.9.1.norm.weight", 452880}};

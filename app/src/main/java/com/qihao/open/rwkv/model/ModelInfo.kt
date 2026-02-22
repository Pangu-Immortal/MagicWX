/**
 * ModelInfo - 多模型注册表
 *
 * 功能：
 * - ModelArch: 模型架构枚举（RWKV / Transformer）
 * - ModelInfo: 单个模型的元信息（名称、URL、大小、架构、分词器等）
 * - ModelRegistry: 10 个可在手机运行的 ONNX 模型注册表
 * - 所有下载链接均为国内可访问地址（hf-mirror.com / GitHub）
 */
package com.qihao.open.rwkv.model

/** 模型架构类型 */
enum class ModelArch {
    RWKV,        // RWKV 架构（RNN 状态传递，完全支持）
    TRANSFORMER  // Transformer 架构（KV-cache，实验性支持）
}

/**
 * 模型元信息
 * @param id 模型唯一标识（用作目录名）
 * @param name 模型显示名称
 * @param description 模型简要描述
 * @param arch 模型架构类型
 * @param paramSize 参数量描述（如 "0.4B"）
 * @param quantization 量化方式（如 "INT8"、"INT4"）
 * @param downloadUrl 模型下载地址（国内可访问）
 * @param fileSizeMB 预估文件大小（MB）
 * @param isFullySupported 是否完全支持（RWKV 模型为 true）
 * @param tokenizerUrl 分词器 tokenizer.json 下载地址（Transformer 模型必需，RWKV 为 null）
 * @param chatTemplate 聊天模板类型（Transformer 模型使用）
 */
data class ModelInfo(
    val id: String,              // 唯一标识
    val name: String,            // 显示名称
    val description: String,     // 简要描述
    val arch: ModelArch,         // 架构类型
    val paramSize: String,       // 参数量
    val quantization: String,    // 量化方式
    val downloadUrl: String,     // 下载地址
    val fileSizeMB: Int,         // 预估大小 MB
    val isFullySupported: Boolean, // 是否完全支持
    val tokenizerUrl: String? = null,      // 分词器下载地址（仅 Transformer 模型）
    val chatTemplate: ChatTemplate = ChatTemplate.CHATML // 聊天模板类型
)

/**
 * 模型注册表 - 包含 10 个可在手机运行的 ONNX 模型
 *
 * 支持等级说明：
 * - 完全支持（isFullySupported=true）：RWKV 模型，使用内置分词器和推理引擎
 * - 实验性（isFullySupported=false）：Transformer 模型，使用 HuggingFace 分词器和 KV-cache 推理
 */
object ModelRegistry {

    /**
     * 所有可用模型列表（每个厂家仅保留 1 个模型）
     *
     * 厂家清单：
     * 1. RWKV Foundation - RWKV-7 World 0.4B
     * 2. 深度求索 - DeepSeek-R1 1.5B
     * 3. 阿里巴巴 - Qwen3 0.6B
     * 4. Google - Gemma 3 1B
     * 5. 微软 - Phi-3 Mini 4K
     * 6. Meta - Llama 3.2 1B
     * 7. HuggingFace - SmolLM2 360M
     * 8. TinyLlama - TinyLlama 1.1B
     * 9. Stability AI - StableLM 2 1.6B
     * 10. 面壁智能 - MiniCPM 2B
     */
    val models: List<ModelInfo> = listOf(
        // 1. RWKV Foundation（完全支持，使用内置 vocab.json 分词器）
        ModelInfo(
            id = "rwkv7-world-0.4b",
            name = "RWKV-7 World 0.4B",
            description = "RWKV-7 架构，支持100+语言，手机端流畅运行",
            arch = ModelArch.RWKV,
            paramSize = "0.4B",
            quantization = "FP32",
            downloadUrl = "https://github.com/Pangu-Immortal/MagicWX/releases/download/1.0.1/model.onnx",
            fileSizeMB = 1572,
            isFullySupported = true,
            tokenizerUrl = null                          // RWKV 使用内置 vocab.json
        ),
        // 2. 深度求索（model_q4.onnx 自包含，无需 _data 文件）
        ModelInfo(
            id = "deepseek-r1-1.5b",
            name = "DeepSeek-R1 1.5B",
            description = "深度求索 R1 蒸馏版，推理能力强",
            arch = ModelArch.TRANSFORMER,
            paramSize = "1.5B",
            quantization = "INT4",
            downloadUrl = "https://hf-mirror.com/onnx-community/DeepSeek-R1-Distill-Qwen-1.5B-ONNX/resolve/main/onnx/model_q4.onnx",
            fileSizeMB = 1830,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/DeepSeek-R1-Distill-Qwen-1.5B-ONNX/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.CHATML
        ),
        // 3. 阿里巴巴（仅保留最新 Qwen3）
        ModelInfo(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B",
            description = "通义千问 3 最新轻量版，中英文能力全面升级",
            arch = ModelArch.TRANSFORMER,
            paramSize = "0.6B",
            quantization = "Q4F16",
            downloadUrl = "https://hf-mirror.com/onnx-community/Qwen3-0.6B-ONNX/resolve/main/onnx/model_q4f16.onnx",
            fileSizeMB = 300,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/Qwen3-0.6B-ONNX/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.CHATML
        ),
        // 4. Google
        ModelInfo(
            id = "gemma3-1b",
            name = "Gemma 3 1B",
            description = "Google Gemma 3 轻量版，多语言支持",
            arch = ModelArch.TRANSFORMER,
            paramSize = "1B",
            quantization = "INT4",
            downloadUrl = "https://hf-mirror.com/onnx-community/gemma-3-1b-it-ONNX/resolve/main/onnx/model_q4.onnx",
            fileSizeMB = 1569,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/gemma-3-1b-it-ONNX/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.GEMMA
        ),
        // 5. 微软
        ModelInfo(
            id = "phi3-mini",
            name = "Phi-3 Mini 4K",
            description = "微软 Phi-3 迷你版，强大的推理能力",
            arch = ModelArch.TRANSFORMER,
            paramSize = "3.8B",
            quantization = "INT4",
            downloadUrl = "https://hf-mirror.com/onnx-community/Phi-3-mini-4k-instruct-onnx/resolve/main/onnx/model_q4.onnx",
            fileSizeMB = 2200,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/Phi-3-mini-4k-instruct-onnx/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.CHATML
        ),
        // 6. Meta（使用 INT8 自包含文件，避免 model_q4.onnx_data 外部依赖）
        ModelInfo(
            id = "llama32-1b",
            name = "Llama 3.2 1B",
            description = "Meta Llama 3.2 移动端专用，轻量高效",
            arch = ModelArch.TRANSFORMER,
            paramSize = "1B",
            quantization = "INT8",
            downloadUrl = "https://hf-mirror.com/onnx-community/Llama-3.2-1B-Instruct-ONNX/resolve/main/onnx/model_int8.onnx",
            fileSizeMB = 1150,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/Llama-3.2-1B-Instruct-ONNX/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.LLAMA3
        ),
        // 7. HuggingFace（SmolLM2 替代 SmolLM v1 因后者为 gated 仓库）
        ModelInfo(
            id = "smollm2-360m",
            name = "SmolLM2 360M",
            description = "HuggingFace 超轻量模型，适合低配设备",
            arch = ModelArch.TRANSFORMER,
            paramSize = "360M",
            quantization = "Q4F16",
            downloadUrl = "https://hf-mirror.com/onnx-community/SmolLM2-360M-Instruct-ONNX/resolve/main/onnx/model_q4f16.onnx",
            fileSizeMB = 260,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/SmolLM2-360M-Instruct-ONNX/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.CHATML
        ),
        // 8. TinyLlama
        ModelInfo(
            id = "tinyllama-1.1b",
            name = "TinyLlama 1.1B",
            description = "TinyLlama 聊天版，Llama 架构精简实现",
            arch = ModelArch.TRANSFORMER,
            paramSize = "1.1B",
            quantization = "INT4",
            downloadUrl = "https://hf-mirror.com/onnx-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/onnx/model_q4.onnx",
            fileSizeMB = 600,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.CHATML
        ),
        // 9. Stability AI
        ModelInfo(
            id = "stablelm2-1.6b",
            name = "StableLM 2 1.6B",
            description = "Stability AI 轻量语言模型，多语言对话",
            arch = ModelArch.TRANSFORMER,
            paramSize = "1.6B",
            quantization = "INT4",
            downloadUrl = "https://hf-mirror.com/onnx-community/stablelm-2-zephyr-1_6b-ONNX/resolve/main/onnx/model_q4.onnx",
            fileSizeMB = 980,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/stablelm-2-zephyr-1_6b-ONNX/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.CHATML
        ),
        // 10. 面壁智能
        ModelInfo(
            id = "minicpm-2b",
            name = "MiniCPM 2B",
            description = "面壁智能端侧模型，中文能力突出",
            arch = ModelArch.TRANSFORMER,
            paramSize = "2B",
            quantization = "INT4",
            downloadUrl = "https://hf-mirror.com/onnx-community/MiniCPM-2B-sft-bf16-ONNX/resolve/main/onnx/model_q4.onnx",
            fileSizeMB = 1200,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/MiniCPM-2B-sft-bf16-ONNX/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.CHATML
        )
    )

    /** 根据 ID 查找模型 */
    fun findById(id: String): ModelInfo? = models.find { it.id == id }

    /** 获取默认模型（RWKV-7） */
    fun getDefault(): ModelInfo = models.first()

    /** 获取完全支持的模型列表 */
    fun getFullySupported(): List<ModelInfo> = models.filter { it.isFullySupported }

    /** 获取实验性模型列表 */
    fun getExperimental(): List<ModelInfo> = models.filter { !it.isFullySupported }
}

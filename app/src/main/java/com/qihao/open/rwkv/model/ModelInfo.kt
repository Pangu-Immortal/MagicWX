/**
 * ModelInfo - 多模型注册表
 *
 * 功能：
 * - ModelArch: 模型架构枚举（内置 / RWKV / Transformer）
 * - ModelCapability: 模型能力枚举（文本、语音、视觉、图像生成等）
 * - RuntimeAdapterType: 运行时 adapter 类型枚举
 * - ModelAsset: 单个模型包资产元信息
 * - ModelInfo: 单个模型的完整元信息（能力、adapter、资产、验证状态）
 * - ModelRegistry: 已验证模型、候选模型、隐藏模型的统一注册表
 */
package com.qihao.open.rwkv.model

import com.qihao.open.rwkv.model.image.DeviceSocCapability
import com.qihao.open.rwkv.model.image.LocalDreamDefaults

/** 模型架构类型 */
enum class ModelArch {
    BUILTIN,     // 内置体验模型（无需下载，用于首次打开快速体验）
    RWKV,        // RWKV 架构（RNN 状态传递，完全支持）
    TRANSFORMER, // Transformer 架构（KV-cache，本地运行）
    STABLE_DIFFUSION // Stable Diffusion / SDXL / Anima 图片生成模型
}

/** 模型可提供的能力类型 */
enum class ModelCapability {
    TEXT_CHAT,            // 文本对话
    ASR,                  // 语音识别
    VAD,                  // 语音活动检测
    TTS,                  // 文本转语音
    IMAGE_CLASSIFICATION, // 图像分类
    OBJECT_DETECTION,     // 目标检测
    IMAGE_SEGMENTATION,   // 图像分割
    POSE_ESTIMATION,      // 姿态识别
    FACE_ANALYSIS,        // 人脸检测或关键点
    IMAGE_TEXT,           // 图像到文本 / VLM
    IMAGE_GENERATION,     // 文生图 / 图像生成
    IMAGE_UPSCALING       // 图片超分辨率
}

/** 模型运行时 adapter 类型 */
enum class RuntimeAdapterType {
    BUILTIN_TEXT,          // 内置文本体验 adapter
    ONNX_TEXT_GENERATION,  // ONNX Runtime 文本生成 adapter
    ONNX_ASR,              // ONNX 语音识别 adapter（待接入）
    ONNX_TTS,              // ONNX TTS adapter（待接入）
    ONNX_VISION,           // ONNX 视觉 adapter（待接入）
    LITERT_LM,             // Google LiteRT-LM adapter（已接入文本对话链路）
    MEDIAPIPE_TASK,        // MediaPipe Tasks adapter（待接入）
    MEDIAPIPE_IMAGE_GENERATION, // MediaPipe Image Generator adapter（已接入端侧文生图）
    QNN_IMAGE_GENERATION,  // Qualcomm QNN / Snapdragon NPU 生图 adapter（待真机接入）
    WHISPER_CPP,           // whisper.cpp adapter（待接入）
    PIPER,                 // Piper TTS adapter（待接入）
    SHERPA_ONNX,           // sherpa-onnx adapter（待接入）
    LLAMA_CPP,             // llama.cpp / GGUF adapter（已接入文本对话链路）
    MLC_LLM,               // MLC LLM adapter（待接入）
    MNN_LLM,               // MNN LLM adapter（待接入）
    VOSK,                  // Vosk ASR adapter（待接入）
    NCNN,                  // NCNN adapter（待接入）
    MNN,                   // MNN adapter（待接入）
    UNSUPPORTED            // 明确不接入或已下线
}

/** 模型在 App 中的可见性 */
enum class ModelVisibility {
    VERIFIED,   // 已真机验证，允许显示在首页
    CANDIDATE,  // 候选池，仅用于后续适配，不显示在首页
    HIDDEN      // 已失败或下线，不显示在首页
}

/** 模型包资产类型 */
enum class ModelAssetKind {
    MODEL,         // 主模型权重或图
    TOKENIZER,     // tokenizer.json 或等价分词器
    EXTERNAL_DATA, // ONNX 外部数据文件
    CONFIG,        // config.json / runtime 配置
    VOCAB,         // 词表
    VOICE,         // TTS 声音包
    PROCESSOR,     // processor / preprocessor
    LABELS,        // 标签文件
    TASK,          // MediaPipe / LiteRT .task 包
    ARCHIVE,       // zip 等多文件模型包，下载后需解压为运行时目录
    ENCODER,       // 编码器
    DECODER,       // 解码器
    OTHER          // 其它资产
}

/** 单个模型包资产声明 */
data class ModelAsset(
    val filename: String,                                  // 本地保存文件名
    val url: String,                                       // 下载地址
    val mirrorUrls: List<String> = emptyList(),             // 同一资产的备用下载地址，失败时按顺序切换
    val required: Boolean = true,                          // 是否为加载必需资产
    val kind: ModelAssetKind = ModelAssetKind.OTHER        // 资产类型
)

/**
 * 模型元信息
 * @param id 模型唯一标识（用作目录名）
 * @param name 模型显示名称
 * @param description 模型简要描述
 * @param arch 模型架构类型
 * @param paramSize 参数量描述（如 "0.4B"）
 * @param quantization 量化方式（如 "INT8"、"INT4"）
 * @param downloadUrl 模型下载地址（国内可访问）
 * @param mirrorUrls 模型主文件备用下载地址，按顺序失败切换
 * @param fileSizeMB 预估文件大小（MB）
 * @param isFullySupported 是否完全支持（当前仅内置体验模型为 true）
 * @param tokenizerUrl 分词器 tokenizer.json 下载地址（Transformer 模型必需，RWKV 为 null）
 * @param chatTemplate 聊天模板类型（Transformer 模型使用）
 * @param capability 模型能力类型
 * @param adapterType 运行时 adapter 类型
 * @param visibility 模型可见性
 * @param assets 显式模型包资产列表；为空时从旧 downloadUrl/tokenizerUrl 自动兼容生成
 * @param imageBackendType 图片后端类型；用于区分 sd15cpu / sd15npu / sdxl / anima / upscaler
 * @param requiredRuntimeFiles 解压或下载后必须存在的运行时文件清单
 * @param verifiedOnDevice 是否已有真机验证证据
 * @param adapterAvailable 当前 App 是否已经实现该 adapter
 * @param unavailableReason 候选或下线原因
 * @param defaultPrompt 模型专属默认正向提示词（对齐参照 codeDefaults.prompt；null 走全局默认）
 * @param defaultNegativePrompt 模型专属默认负向提示词（对齐参照 codeDefaults.negativePrompt；null 走全局默认）
 */
data class ModelInfo(
    val id: String,              // 唯一标识
    val name: String,            // 显示名称
    val description: String,     // 简要描述
    val arch: ModelArch,         // 架构类型
    val paramSize: String,       // 参数量
    val quantization: String,    // 量化方式
    val downloadUrl: String,     // 下载地址
    val mirrorUrls: List<String> = emptyList(), // 备用下载地址
    val fileSizeMB: Int,         // 预估大小 MB
    val isFullySupported: Boolean, // 是否完全支持
    val tokenizerUrl: String? = null,      // 分词器下载地址（仅 Transformer 模型）
    val chatTemplate: ChatTemplate = ChatTemplate.CHATML, // 聊天模板类型
    val capability: ModelCapability = ModelCapability.TEXT_CHAT, // 模型能力
    val adapterType: RuntimeAdapterType = RuntimeAdapterType.ONNX_TEXT_GENERATION, // runtime adapter
    val visibility: ModelVisibility = ModelVisibility.VERIFIED, // 首页可见性
    val assets: List<ModelAsset> = emptyList(),                 // 显式资产清单
    val imageBackendType: String = "",                          // localdream 图片后端类型
    val requiredRuntimeFiles: List<String> = emptyList(),        // 多文件模型包完整性门禁
    val verifiedOnDevice: Boolean = false,                      // 真机验证状态
    val adapterAvailable: Boolean = true,                       // adapter 是否已接入
    val unavailableReason: String = "",                         // 不可用原因
    val defaultPrompt: String? = null,                          // 模型专属默认正向提示词
    val defaultNegativePrompt: String? = null,                  // 模型专属默认负向提示词
    // ---- 以下字段对齐 local-dream Model.kt 数据类 ----
    val generationSize: Int = 512,                              // 最大生图分辨率（512/1024），对齐 local-dream Model.generationSize
    val approximateSize: String = "",                            // 人可读模型大小（"1.1GB"/"4.2GB"/"自定义"），对齐 local-dream Model.approximateSize
    val runOnCpu: Boolean = false,                              // 是否 CPU 后端运行，对齐 local-dream Model.runOnCpu
    val isSdxl: Boolean = false,                                // 是否 SDXL 架构，对齐 local-dream Model.isSdxl
    val isAnima: Boolean = false,                               // 是否 Anima 架构，对齐 local-dream Model.isAnima
    val isCustom: Boolean = false,                              // 是否自定义导入模型，对齐 local-dream Model.isCustom
    val needsUpgrade: Boolean = false,                          // 是否需要升级格式（v3 marker），对齐 local-dream Model.needsUpgrade
) {
    /** SDXL 和 Anima 均为固定 1024 画布，对齐 local-dream Model.usesFixedCanvas */
    val usesFixedCanvas: Boolean
        get() = isSdxl || isAnima
    /**
     * QNN 生图族设备门禁标记：QNN adapter 的可用性由设备 SoC 物理能力决定
     * （DeviceSocCapability 判定），不可用时属于"本机不支持"而非排期上的"即将支持"。
     * UI 层据此渲染"本机不支持"角标并阻断下载入口。
     */
    val isDeviceGatedQnnUnavailable: Boolean
        get() = adapterType == RuntimeAdapterType.QNN_IMAGE_GENERATION && !adapterAvailable
    /** 返回完整资产清单；兼容旧字段，避免一次性重写所有调用点 */
    fun resolvedAssets(): List<ModelAsset> {
        if (assets.isNotEmpty()) return assets                  // 显式清单优先，适配多资产模型包
        val resolved = mutableListOf<ModelAsset>()              // 旧模型字段转换结果
        if (downloadUrl.isNotBlank()) {
            resolved += ModelAsset(
                filename = filenameFromUrl(downloadUrl, "model.onnx"),
                url = downloadUrl,
                mirrorUrls = mirrorUrls,
                kind = if (downloadUrl.endsWith(".task")) ModelAssetKind.TASK else ModelAssetKind.MODEL
            )
        }
        if (!tokenizerUrl.isNullOrBlank()) {
            resolved += ModelAsset(
                filename = "tokenizer.json",
                url = tokenizerUrl,
                kind = ModelAssetKind.TOKENIZER
            )
        }
        return resolved
    }
}

/** 从 URL 中提取本地文件名，失败时使用 fallback */
private fun filenameFromUrl(url: String, fallback: String): String {
    val name = url.substringBefore("?").substringAfterLast("/")
    return if (name.isNotBlank() && name.contains(".")) name else fallback
}

/**
 * 模型注册表 - 同时维护可见模型、候选池和隐藏模型
 *
 * 支持等级说明：
 * - 完全支持（isFullySupported=true）：内置体验模型，无需下载外部权重
 * - 已验证可用（isFullySupported=false）：已验证可下载、可加载、可完成基础对话或受运行时门禁保护的外部模型
 * - 候选模型（visibility=CANDIDATE）：已进入工程清单；图片目录可展示下载校验态，生成仍由 adapter 门禁阻断
 * - 隐藏模型（visibility=HIDDEN）：已有失败证据或暂时不可控，不在首页展示
 */
object ModelRegistry {

    /**
     * 对用户展示的可用模型列表
     *
     * 当前保留条件：
     * 1. 清数据后可从应用内完成下载或无需下载
     * 2. 可正常加载进入对话页
     * 3. 使用基础测试词 hello 可输出自然回复，或按当前设备运行时门禁阻断不安全推理
     *
     * 已下线待重新验证：DeepSeek-R1、Gemma 3 ONNX、Phi-3、Llama 3.2 ONNX、
     * TinyLlama ONNX、StableLM 2、MiniCPM、Qwen2 0.5B、SmolLM2 135M、SmolLM2 135M MHA。
     * 原因是下载不可控、输出异常、无输出、链接失效或尚未取得完整真机通过证据。
     */
    val allModels: List<ModelInfo> = listOf(
        // 0. MagicWX 内置体验模型（无需下载权重，确保首次打开可立即对话）
        ModelInfo(
            id = "magicwx-builtin-demo",
            name = "MagicWX 内置体验模型",
            description = "古风客栈体验模型：无需下载，立刻用“小女子/客官”口吻试聊。",
            arch = ModelArch.BUILTIN,
            paramSize = "内置",
            quantization = "N/A",
            downloadUrl = "",
            fileSizeMB = 0,
            isFullySupported = true,
            tokenizerUrl = null,
            adapterType = RuntimeAdapterType.BUILTIN_TEXT,
            verifiedOnDevice = true
        ),
        // 1. RWKV Foundation（用户亲测效果最佳，使用内置 vocab.json 分词器）
        ModelInfo(
            id = "rwkv7-world-0.4b",
            name = "RWKV-7 World 0.4B",
            description = "多语言轻量长聊：RWKV 架构低内存连续对话，适合手机端日常聊天。",
            arch = ModelArch.RWKV,
            paramSize = "0.4B",
            quantization = "FP32",
            downloadUrl = "https://huggingface.co/TIEMING/rwkv-world-0.4B-onnx/resolve/main/model.onnx",
            mirrorUrls = listOf(
                "https://hf-mirror.com/TIEMING/rwkv-world-0.4B-onnx/resolve/main/model.onnx",
                "https://github.com/Pangu-Immortal/MagicWX/releases/download/1.0.1/model.onnx"
            ),
            fileSizeMB = 1572,
            isFullySupported = true,
            tokenizerUrl = null,
            adapterType = RuntimeAdapterType.ONNX_TEXT_GENERATION,
            visibility = ModelVisibility.VERIFIED,
            verifiedOnDevice = true,
            adapterAvailable = true
        ),
        // 2. RWKV7 G1A 0.1B GGUF（本地验证：下载、llama.cpp 加载、hello 回复通过）
        ModelInfo(
            id = "rwkv7-g1a-0.1b-gguf",
            name = "RWKV7 G1A 0.1B GGUF",
            description = "极小体积 RWKV7：约 203MB，适合低配手机快速下载和即时试聊。",
            arch = ModelArch.RWKV,
            paramSize = "0.1B",
            quantization = "Q8",
            downloadUrl = "https://huggingface.co/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1a-0.1b-20250728-ctx4096-Q8_0.gguf",
            mirrorUrls = listOf(
                "https://hf-mirror.com/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1a-0.1b-20250728-ctx4096-Q8_0.gguf"
            ),
            fileSizeMB = 203,
            isFullySupported = false,
            tokenizerUrl = null,
            chatTemplate = ChatTemplate.RWKV_USER_ASSISTANT,
            capability = ModelCapability.TEXT_CHAT,
            adapterType = RuntimeAdapterType.LLAMA_CPP,
            assets = listOf(
                ModelAsset(
                    filename = "rwkv7-g1a-0.1b-20250728-ctx4096-Q8_0.gguf",
                    url = "https://huggingface.co/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1a-0.1b-20250728-ctx4096-Q8_0.gguf",
                    mirrorUrls = listOf(
                        "https://hf-mirror.com/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1a-0.1b-20250728-ctx4096-Q8_0.gguf"
                    ),
                    kind = ModelAssetKind.MODEL
                )
            ),
            verifiedOnDevice = true,
            adapterAvailable = true
        ),
        // 3. RWKV7 MiSS Roleplay 1.5B GGUF（本地验证：下载、llama.cpp 加载、hello 回复通过）
        ModelInfo(
            id = "rwkv7-miss-roleplay-1.5b-gguf",
            name = "RWKV7 MiSS Roleplay 1.5B",
            description = "RWKV 角色扮演写作模型：1.5B 量化包，适合人设对话和长篇续写。",
            arch = ModelArch.RWKV,
            paramSize = "1.5B",
            quantization = "Q4_K_M",
            downloadUrl = "https://huggingface.co/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1c-1.5B-MiSS-Roleplay-260116-ctx8192-q4_k_m.gguf",
            mirrorUrls = listOf(
                "https://hf-mirror.com/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1c-1.5B-MiSS-Roleplay-260116-ctx8192-q4_k_m.gguf"
            ),
            fileSizeMB = 944,
            isFullySupported = false,
            tokenizerUrl = null,
            chatTemplate = ChatTemplate.RWKV_USER_ASSISTANT,
            capability = ModelCapability.TEXT_CHAT,
            adapterType = RuntimeAdapterType.LLAMA_CPP,
            assets = listOf(
                ModelAsset(
                    filename = "rwkv7-g1c-1.5B-MiSS-Roleplay-260116-ctx8192-q4_k_m.gguf",
                    url = "https://huggingface.co/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1c-1.5B-MiSS-Roleplay-260116-ctx8192-q4_k_m.gguf",
                    mirrorUrls = listOf(
                        "https://hf-mirror.com/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1c-1.5B-MiSS-Roleplay-260116-ctx8192-q4_k_m.gguf"
                    ),
                    kind = ModelAssetKind.MODEL
                )
            ),
            visibility = ModelVisibility.VERIFIED,
            verifiedOnDevice = true,
            adapterAvailable = true
        ),
        // 4. 阿里巴巴 Qwen3（已真机验证：下载、加载、hello 回复通过）
        ModelInfo(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B",
            description = "中文综合能力强：适合问答、总结、翻译和日常助手场景。",
            arch = ModelArch.TRANSFORMER,
            paramSize = "0.6B",
            quantization = "Q4F16",
            downloadUrl = "https://hf-mirror.com/onnx-community/Qwen3-0.6B-ONNX/resolve/main/onnx/model_q4f16.onnx",
            fileSizeMB = 544,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/Qwen3-0.6B-ONNX/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.QWEN3,
            verifiedOnDevice = true
        ),
        // 5. 阿里巴巴 Qwen2.5 0.5B（已真机验证：下载、加载、hello 回复通过）
        ModelInfo(
            id = "qwen25-0.5b",
            name = "Qwen2.5 0.5B",
            description = "小体积中文指令模型：下载更快，适合简短问答和轻量写作。",
            arch = ModelArch.TRANSFORMER,
            paramSize = "0.5B",
            quantization = "Q4F16",
            downloadUrl = "https://hf-mirror.com/onnx-community/Qwen2.5-0.5B-Instruct/resolve/main/onnx/model_q4f16.onnx",
            fileSizeMB = 461,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/Qwen2.5-0.5B-Instruct/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.CHATML,
            verifiedOnDevice = true
        ),
        // 6. HuggingFace SmolLM2 360M（已真机验证：下载、加载、hello 回复通过）
        ModelInfo(
            id = "smollm2-360m",
            name = "SmolLM2 360M",
            description = "超轻量英文/通用对话：低配设备优先，适合快速响应。",
            arch = ModelArch.TRANSFORMER,
            paramSize = "360M",
            quantization = "Q4F16",
            downloadUrl = "https://hf-mirror.com/onnx-community/SmolLM2-360M-Instruct-ONNX/resolve/main/onnx/model_q4f16.onnx",
            fileSizeMB = 260,
            isFullySupported = false,
            tokenizerUrl = "https://hf-mirror.com/onnx-community/SmolLM2-360M-Instruct-ONNX/resolve/main/tokenizer.json",
            chatTemplate = ChatTemplate.CHATML,
            verifiedOnDevice = true
        ),
        // 7. TinyLlama 1.1B Chat LiteRT（本轮非 Qwen 真机验证目标，使用 MediaPipe/LiteRT 独立 runtime）
        ModelInfo(
            id = "tinyllama-1.1b-task",
            name = "TinyLlama 1.1B Chat LiteRT",
            description = "LiteRT 独立运行时模型：适合验证 MediaPipe 端侧推理链路。",
            arch = ModelArch.TRANSFORMER,
            paramSize = "1.1B",
            quantization = "Q8",
            downloadUrl = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
            mirrorUrls = listOf(
                "https://hf-mirror.com/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task"
            ),
            fileSizeMB = 1096,
            isFullySupported = false,
            tokenizerUrl = null,
            capability = ModelCapability.TEXT_CHAT,
            adapterType = RuntimeAdapterType.LITERT_LM,
            assets = listOf(
                ModelAsset(
                    filename = "TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
                    url = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
                    mirrorUrls = listOf(
                        "https://hf-mirror.com/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task"
                    ),
                    kind = ModelAssetKind.TASK
                )
            ),
            verifiedOnDevice = true,
            adapterAvailable = true
        ),
        // 8. Creative Writing RP 1B GGUF（标称 creative / roleplay / uncensored，使用 llama.cpp / GGUF 独立 runtime）
        ModelInfo(
            id = "creative-rp-1b-gguf",
            name = "Creative Writing RP 1B GGUF",
            description = "创意写作与角色扮演：GGUF/llama.cpp 本地运行，适合故事续写和人设对话。",
            arch = ModelArch.TRANSFORMER,
            paramSize = "1B",
            quantization = "Q4",
            downloadUrl = "https://huggingface.co/Novaciano/Uncensored-1b-Creative_Writing_RP-GGUF/resolve/main/Uncensored-1b-Creative_Writing_RP.gguf",
            mirrorUrls = listOf(
                "https://hf-mirror.com/Novaciano/Uncensored-1b-Creative_Writing_RP-GGUF/resolve/main/Uncensored-1b-Creative_Writing_RP.gguf"
            ),
            fileSizeMB = 709,
            isFullySupported = false,
            tokenizerUrl = null,
            chatTemplate = ChatTemplate.LLAMA3,
            capability = ModelCapability.TEXT_CHAT,
            adapterType = RuntimeAdapterType.LLAMA_CPP,
            assets = listOf(
                ModelAsset(
                    filename = "Uncensored-1b-Creative_Writing_RP.gguf",
                    url = "https://huggingface.co/Novaciano/Uncensored-1b-Creative_Writing_RP-GGUF/resolve/main/Uncensored-1b-Creative_Writing_RP.gguf",
                    mirrorUrls = listOf(
                        "https://hf-mirror.com/Novaciano/Uncensored-1b-Creative_Writing_RP-GGUF/resolve/main/Uncensored-1b-Creative_Writing_RP.gguf"
                    ),
                    kind = ModelAssetKind.MODEL
                )
            ),
            verifiedOnDevice = true,
            adapterAvailable = true
        ),
        // 9. Triangulum 1B Roleplay NSFW GGUF（本地验证：下载、llama.cpp 加载、hello 回复通过）
        ModelInfo(
            id = "triangulum-rp-1b-gguf",
            name = "Triangulum 1B Roleplay NSFW",
            description = "NSFW 角色扮演小模型：1B GGUF 量化包，适合本地人设对话和创意续写。",
            arch = ModelArch.TRANSFORMER,
            paramSize = "1B",
            quantization = "Q4",
            downloadUrl = "https://huggingface.co/Novaciano/Triangulum-1B-DPO_Roleplay_NSFW-GGUF/resolve/main/Triangulum-1B-DPO_Roleplay_NSFW.gguf",
            mirrorUrls = listOf(
                "https://hf-mirror.com/Novaciano/Triangulum-1B-DPO_Roleplay_NSFW-GGUF/resolve/main/Triangulum-1B-DPO_Roleplay_NSFW.gguf"
            ),
            fileSizeMB = 770,
            isFullySupported = false,
            tokenizerUrl = null,
            chatTemplate = ChatTemplate.LLAMA3,
            capability = ModelCapability.TEXT_CHAT,
            adapterType = RuntimeAdapterType.LLAMA_CPP,
            assets = listOf(
                ModelAsset(
                    filename = "Triangulum-1B-DPO_Roleplay_NSFW.gguf",
                    url = "https://huggingface.co/Novaciano/Triangulum-1B-DPO_Roleplay_NSFW-GGUF/resolve/main/Triangulum-1B-DPO_Roleplay_NSFW.gguf",
                    mirrorUrls = listOf(
                        "https://hf-mirror.com/Novaciano/Triangulum-1B-DPO_Roleplay_NSFW-GGUF/resolve/main/Triangulum-1B-DPO_Roleplay_NSFW.gguf"
                    ),
                    kind = ModelAssetKind.MODEL
                )
            ),
            visibility = ModelVisibility.VERIFIED,
            verifiedOnDevice = true,
            adapterAvailable = true
        ),
        ModelInfo(
            id = "rwkv7-disha-roleplay-1.5b-gguf",
            name = "RWKV7 Disha Roleplay 1.5B",
            description = "RWKV 角色写作候选：同为 1.5B Q4_K_M，等待完整下载和真机 hello 验证。",
            arch = ModelArch.RWKV,
            paramSize = "1.5B",
            quantization = "Q4_K_M",
            downloadUrl = "https://huggingface.co/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1a3-1.5B-disha-roleplay-ctx8192-251107-q4_k_m.gguf",
            mirrorUrls = listOf(
                "https://hf-mirror.com/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1a3-1.5B-disha-roleplay-ctx8192-251107-q4_k_m.gguf"
            ),
            fileSizeMB = 944,
            isFullySupported = false,
            tokenizerUrl = null,
            chatTemplate = ChatTemplate.RWKV_USER_ASSISTANT,
            capability = ModelCapability.TEXT_CHAT,
            adapterType = RuntimeAdapterType.LLAMA_CPP,
            visibility = ModelVisibility.CANDIDATE,
            assets = listOf(
                ModelAsset(
                    filename = "rwkv7-g1a3-1.5B-disha-roleplay-ctx8192-251107-q4_k_m.gguf",
                    url = "https://huggingface.co/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1a3-1.5B-disha-roleplay-ctx8192-251107-q4_k_m.gguf",
                    mirrorUrls = listOf(
                        "https://hf-mirror.com/mollysama/rwkv-mobile-models/resolve/main/gguf/rwkv7-g1a3-1.5B-disha-roleplay-ctx8192-251107-q4_k_m.gguf"
                    ),
                    kind = ModelAssetKind.MODEL
                )
            ),
            verifiedOnDevice = false,
            adapterAvailable = false,
            unavailableReason = "候选模型：下载速度慢，本轮未完成完整文件和真机 hello 验证"
        ),
        ModelInfo(
            id = "rwkv52-3b-nsfw-role-iq1s-gguf",
            name = "RWKV-5.2 3B NSFW Role IQ1_S",
            description = "RWKV NSFW Role 候选：3B 低量化包体更小，但许可证和真机质量仍需复核。",
            arch = ModelArch.RWKV,
            paramSize = "3B",
            quantization = "IQ1_S",
            downloadUrl = "https://huggingface.co/mzwing/RWKV-5.2-3B-NSFW-Role-16k-GGUF/resolve/main/RWKV-5.2-3B-NSFW-Role-16k.IQ1_S.gguf",
            mirrorUrls = listOf(
                "https://hf-mirror.com/mzwing/RWKV-5.2-3B-NSFW-Role-16k-GGUF/resolve/main/RWKV-5.2-3B-NSFW-Role-16k.IQ1_S.gguf"
            ),
            fileSizeMB = 817,
            isFullySupported = false,
            tokenizerUrl = null,
            chatTemplate = ChatTemplate.RWKV_USER_ASSISTANT,
            capability = ModelCapability.TEXT_CHAT,
            adapterType = RuntimeAdapterType.LLAMA_CPP,
            visibility = ModelVisibility.CANDIDATE,
            assets = listOf(
                ModelAsset(
                    filename = "RWKV-5.2-3B-NSFW-Role-16k.IQ1_S.gguf",
                    url = "https://huggingface.co/mzwing/RWKV-5.2-3B-NSFW-Role-16k-GGUF/resolve/main/RWKV-5.2-3B-NSFW-Role-16k.IQ1_S.gguf",
                    mirrorUrls = listOf(
                        "https://hf-mirror.com/mzwing/RWKV-5.2-3B-NSFW-Role-16k-GGUF/resolve/main/RWKV-5.2-3B-NSFW-Role-16k.IQ1_S.gguf"
                    ),
                    kind = ModelAssetKind.MODEL
                )
            ),
            verifiedOnDevice = false,
            adapterAvailable = false,
            unavailableReason = "候选模型：HuggingFace 未声明明确 license，且本轮未完成真机加载和 hello 验证"
        ),
        candidateTextModel("mobilellm-350m", "MobileLLM 350M", "Meta 端侧小模型候选，需要 tokenizer 和输出契约复测", "350M", 460, "https://hf-mirror.com/onnx-community/MobileLLM-350M/resolve/main/onnx/model.onnx"),
        candidateTextModel("gemma3-1b-onnx", "Gemma 3 1B ONNX", "Google Gemma 1B 候选，需要内存预算和真机 dry-run", "1B", 1150, "https://hf-mirror.com/onnx-community/gemma-3-1b-it-ONNX/resolve/main/onnx/model.onnx", RuntimeAdapterType.ONNX_TEXT_GENERATION),
        candidateTaskModel("gemma3-1b-task", "Gemma3-1B-IT LiteRT", "Google AI Edge LiteRT-LM 候选，当前公开直链返回 401，需要免登录资产源", "1B", 530, "https://huggingface.co/google/gemma-3-1b-it-litert-preview/resolve/main/gemma3-1b-it-int4.task", RuntimeAdapterType.LITERT_LM, ModelCapability.TEXT_CHAT),
        candidateTaskModel("qwen25-1.5b-task", "Qwen2.5-1.5B LiteRT", "Google AI Edge LiteRT-LM 候选，免登录源已确认，但本轮不作为非 Qwen 扩展目标", "1.5B", 1495, "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_seq128_q8_ekv1280.task", RuntimeAdapterType.LITERT_LM, ModelCapability.TEXT_CHAT),
        candidateTaskModel("gemma3n-e2b-task", "Gemma-3n E2B", "VLM / 文本候选，峰值内存高，需要 LiteRT 多模态 adapter", "E2B", 2991, "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task", RuntimeAdapterType.LITERT_LM, ModelCapability.IMAGE_TEXT),
        candidateTaskModel("gemma3n-e4b-task", "Gemma-3n E4B", "VLM / 文本候选，峰值内存高，需要 LiteRT 多模态 adapter", "E4B", 4202, "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.task", RuntimeAdapterType.LITERT_LM, ModelCapability.IMAGE_TEXT),
        candidateTextModel("llama32-1b-onnx", "Llama 3.2 1B ONNX", "Meta Llama 小模型候选，需要许可、tokenizer 和真机输出验证", "1B", 1200, "https://hf-mirror.com/onnx-community/Llama-3.2-1B-Instruct-ONNX/resolve/main/onnx/model_q4f16.onnx"),
        candidateTextModel("llama32-1b-gguf", "Llama 3.2 1B GGUF", "Meta Llama GGUF 候选，推荐 llama.cpp adapter，需许可和真机 JNI 验证", "1B", 737, "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_0.gguf", RuntimeAdapterType.LLAMA_CPP),
        candidateTextModel("uyara-companion-1.5b-gguf", "Uyara Companion 1.5B GGUF", "情感陪伴方向 GGUF 候选，公开非 gated，需 llama.cpp adapter 真机验证", "1.5B", 900, "https://huggingface.co/mradermacher/uyara-companion-1.5b-GGUF/resolve/main/uyara-companion-1.5b.Q4_K_M.gguf", RuntimeAdapterType.LLAMA_CPP),
        candidateTextModel("tinyllama-1.1b-mnn", "TinyLlama 1.1B Chat MNN", "MNN 官方格式候选，需接入 MNN-LLM runtime，不能用 ONNX/MediaPipe 加载", "1.1B", 624, "https://huggingface.co/taobao-mnn/TinyLlama-1.1B-Chat-MNN/resolve/main/tinyllama-1.1b-int4.mnn", RuntimeAdapterType.MNN_LLM),
        candidateTextModel("deepseek-r1-qwen-1.5b-onnx", "DeepSeek-R1 Distill Qwen 1.5B", "推理模型候选，体积和生成格式需要单独验收", "1.5B", 1600, "https://hf-mirror.com/onnx-community/DeepSeek-R1-Distill-Qwen-1.5B-ONNX/resolve/main/onnx/model_q4f16.onnx"),
        candidateTextModel("phi3-mini-onnx", "Phi-3 Mini 4K ONNX", "微软 Phi 候选，体积较大且输入输出名需复核", "3.8B", 2300, "https://hf-mirror.com/onnx-community/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32/phi3-mini-4k-instruct-cpu-int4-rtn-block-32.onnx"),
        candidateMultiAssetModel("smolvlm-256m", "SmolVLM 256M", "图文理解候选，需要图像预处理和 VLM adapter", ModelCapability.IMAGE_TEXT, RuntimeAdapterType.ONNX_VISION, 470),
        candidateMultiAssetModel("moonshine-tiny-onnx", "Moonshine Tiny ONNX", "轻量 ASR 候选，需要音频特征和 encoder/decoder adapter", ModelCapability.ASR, RuntimeAdapterType.ONNX_ASR, 160),
        candidateMultiAssetModel("whisper-tiny-en", "Whisper Tiny EN", "英文 ASR 候选，需要 whisper.cpp 或 ONNX ASR adapter", ModelCapability.ASR, RuntimeAdapterType.WHISPER_CPP, 75),
        candidateMultiAssetModel("whisper-base", "Whisper Base", "ASR 候选，体积更大，需要流式音频 adapter", ModelCapability.ASR, RuntimeAdapterType.WHISPER_CPP, 145),
        candidateMultiAssetModel("vosk-small-cn", "Vosk Small CN", "中文 ASR 候选，需要 Vosk Android adapter", ModelCapability.ASR, RuntimeAdapterType.VOSK, 50),
        candidateMultiAssetModel("sherpa-onnx-paraformer", "sherpa-onnx Paraformer", "中文 ASR 候选，需要 sherpa-onnx Android adapter", ModelCapability.ASR, RuntimeAdapterType.SHERPA_ONNX, 220),
        candidateMultiAssetModel("silero-vad", "Silero VAD ONNX", "语音活动检测候选，需要音频流和 VAD adapter", ModelCapability.VAD, RuntimeAdapterType.ONNX_ASR, 3),
        candidateMultiAssetModel("kokoro-82m-onnx", "Kokoro 82M ONNX", "TTS 候选，需要声音包和声码器链路", ModelCapability.TTS, RuntimeAdapterType.ONNX_TTS, 330),
        candidateMultiAssetModel("piper-lessac-low", "Piper Lessac Low", "Piper 英文 TTS 候选，需要 Piper runtime", ModelCapability.TTS, RuntimeAdapterType.PIPER, 55),
        candidateMultiAssetModel("sherpa-onnx-vits", "sherpa-onnx VITS", "TTS 候选，需要 sherpa-onnx TTS adapter", ModelCapability.TTS, RuntimeAdapterType.SHERPA_ONNX, 120),
        candidateMultiAssetModel("mobilenetv3-small", "MobileNetV3 Small", "图像分类候选，需要 TFLite/ONNX vision adapter", ModelCapability.IMAGE_CLASSIFICATION, RuntimeAdapterType.ONNX_VISION, 16),
        candidateMultiAssetModel("efficientnet-lite0", "EfficientNet-Lite0", "图像分类候选，需要 TFLite vision adapter", ModelCapability.IMAGE_CLASSIFICATION, RuntimeAdapterType.MEDIAPIPE_TASK, 20),
        candidateMultiAssetModel("clip-vit-b32", "CLIP ViT-B/32", "图文检索候选，需要图像和文本双 encoder adapter", ModelCapability.IMAGE_TEXT, RuntimeAdapterType.ONNX_VISION, 600),
        candidateMultiAssetModel("yolov8n", "YOLOv8n", "目标检测候选，需要检测后处理和阈值配置", ModelCapability.OBJECT_DETECTION, RuntimeAdapterType.NCNN, 12),
        candidateMultiAssetModel("yolo11n", "YOLO11n", "目标检测候选，需要 TFLite/NCNN adapter", ModelCapability.OBJECT_DETECTION, RuntimeAdapterType.NCNN, 12),
        candidateMultiAssetModel("nanodet-m", "NanoDet-M", "目标检测候选，需要 NCNN adapter", ModelCapability.OBJECT_DETECTION, RuntimeAdapterType.NCNN, 8),
        candidateTaskModel("mediapipe-image-segmenter", "MediaPipe Image Segmenter", "图像分割候选，需要 MediaPipe Tasks adapter", "task", 25, "https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/latest/deeplab_v3.tflite", RuntimeAdapterType.MEDIAPIPE_TASK, ModelCapability.IMAGE_SEGMENTATION),
        candidateTaskModel("movenet-lightning", "MoveNet Lightning", "人体姿态候选，需要 Pose adapter", "lightning", 5, "https://storage.googleapis.com/movenet/MoveNet.SinglePose.Lightning.tflite", RuntimeAdapterType.MEDIAPIPE_TASK, ModelCapability.POSE_ESTIMATION),
        candidateTaskModel("blazeface", "BlazeFace", "人脸检测候选，需要 MediaPipe Face adapter", "short", 2, "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite", RuntimeAdapterType.MEDIAPIPE_TASK, ModelCapability.FACE_ANALYSIS),
        candidateMultiAssetModel("mobilesam", "MobileSAM", "图像分割候选，需要 prompt、mask 解码和内存验证", ModelCapability.IMAGE_SEGMENTATION, RuntimeAdapterType.ONNX_VISION, 380),
        // LocalDream 图片模型族：完整登记进工程目录；SD1.5 CPU 已开放生成，
        // QNN 族（sd15npu/sdxl/anima/upscaler）按设备 SoC 门禁判定（DeviceSocCapability）：
        // 骁龙 QNN 设备 adapterAvailable=true（可下载可运行，QNN SDK 构建集成后 native 接管），
        // 非骁龙设备 adapterAvailable=false + 设备不支持原因（物理事实，不是"即将支持"）
        localDreamSdxlModel(
            id = "localdream-illustrious-v16",
            name = "Illustrious v16 SDXL NPU",
            description = "SDXL 动漫标准版：1024 画布，Illustrious v16 风格，20 步精绘画质优先（细节丰富，约 5min/张；与下方 DMD2 同风格但多步高质，追求质量选它）",
            fileUri = "xororz/sdxl-qnn/resolve/main/illustrious_v16_qnn2.28_8gen3.zip",
            defaultPrompt = LocalDreamDefaults.ILLUSTRIOUS_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ANIME_NEGATIVE_PROMPT
        ),
        localDreamSdxlModel(
            id = "localdream-illustrious-v16-dmd2",
            name = "Illustrious v16 DMD2 NPU",
            description = "SDXL DMD2 动漫快图：蒸馏模型，4-8 步快速出图，Illustrious v16 风格（速度 3-5 倍于标准 SDXL，适合快速预览/批量，画质略降；与上方标准版同风格选快选精）",
            fileUri = "xororz/sdxl-qnn/resolve/main/illustrious_v16_dmd2_qnn2.28_8gen3.zip",
            // steps/cfg/scheduler 不在代码里写死：参照仓库中 DMD2 模型随 zip 内置
            // config.json 携带蒸馏参数，当前仅移植 prompt/negativePrompt 两类代码默认
            defaultPrompt = LocalDreamDefaults.ILLUSTRIOUS_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ANIME_NEGATIVE_PROMPT
        ),
        localDreamSdxlModel(
            id = "localdream-cyber-realistic-v10",
            name = "CyberRealistic v10 SDXL NPU",
            description = "SDXL 写实标准版：1024 画布，CyberRealistic v10 风格，20 步精绘真实质感/人像/光影（画质优先，约 5min/张；与下方 DMD2 同风格但多步高质）",
            fileUri = "xororz/sdxl-qnn/resolve/main/cyber_realistic_v10_qnn2.28_8gen3.zip",
            defaultPrompt = LocalDreamDefaults.CYBER_REALISTIC_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.CYBER_REALISTIC_NEGATIVE_PROMPT
        ),
        localDreamSdxlModel(
            id = "localdream-cyber-realistic-v10-dmd2",
            name = "CyberRealistic v10 DMD2 NPU",
            description = "SDXL DMD2 写实快图：蒸馏模型，4-8 步快速真实照片，CyberRealistic v10 风格（速度 3-5 倍于标准 SDXL，适合快速预览，画质略降；与上方标准版同风格选快选精）",
            fileUri = "xororz/sdxl-qnn/resolve/main/cyber_realistic_v10_dmd2_qnn2.28_8gen3.zip",
            defaultPrompt = LocalDreamDefaults.CYBER_REALISTIC_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.CYBER_REALISTIC_NEGATIVE_PROMPT
        ),
        localDreamAnimaModel(
            id = "localdream-anima-base-v1-turbo",
            name = "Anima Base v1 Turbo NPU",
            description = "Anima 基础模型：DiT/Qwen 路线，适合高质量 1024 创作。",
            fileName = "anima_base_v1_turbo_qnn2.28_8gen3.zip"
        ),
        localDreamAnimaModel(
            id = "localdream-animayume-v1-turbo",
            name = "AnimaYume v1 Turbo NPU",
            description = "Anima 梦幻动漫：适合柔和角色、幻想插画和高细节画面。",
            fileName = "animayume_v1_turbo_qnn2.28_8gen3.zip"
        ),
        localDreamAnimaModel(
            id = "localdream-anima-cyberrealistic-v3",
            name = "CyberRealistic v3 Anima NPU",
            description = "Anima 写实模型：面向真实质感、人像和电影感光影。",
            fileName = "cyberrealistic_v3_turbo_qnn2.28_8gen3.zip"
        ),
        localDreamAnimaModel(
            id = "localdream-miaomiao-v14-turbo",
            name = "MiaoMiao v1.4 Turbo NPU",
            description = "Anima 萌系模型：适合可爱角色、头像和二次元贴纸。",
            fileName = "miaomiao_v1.4_turbo_qnn2.28_8gen3.zip"
        ),
        localDreamAnimaModel(
            id = "localdream-novaanime-v25-turbo",
            name = "NovaAnime v2.5 Turbo NPU",
            description = "Anima 动漫插画：适合高饱和角色和清晰线稿。",
            fileName = "novaanime_v2.5_turbo_qnn2.28_8gen3.zip"
        ),
        localDreamAnimaModel(
            id = "localdream-novaanime-v3-turbo",
            name = "NovaAnime v3 Turbo NPU",
            description = "Anima 新版动漫：适合更稳定的角色构图和色彩表现。",
            fileName = "novaanime_v3_turbo_qnn2.28_8gen3.zip"
        ),
        localDreamAnimaModel(
            id = "localdream-rin-flanime-v1-turbo",
            name = "Rin FLAnime v1 Turbo NPU",
            description = "Anima 流行画风：适合轻小说封面和角色海报。",
            fileName = "rin_flanime_v1_turbo_qnn2.28_8gen3.zip"
        ),
        localDreamAnimaModel(
            id = "localdream-sam-anima-realistic-v23",
            name = "Sam Anima Realistic v2.3 NPU",
            description = "Anima 真实人像：适合照片感肖像和自然场景。",
            fileName = "sam_anima_realistic_v2.3_turbo_qnn2.28_8gen3.zip"
        ),
        localDreamAnimaModel(
            id = "localdream-wai-anima-v1-turbo",
            name = "WAI Anima v1 Turbo NPU",
            description = "Anima 综合创作：兼顾动漫人物、场景和快速成片。",
            fileName = "wai_anima_v1_turbo_qnn2.28_8gen3.zip"
        ),
        localDreamSd15NpuModel(
            id = "localdream-anythingv5-npu",
            name = "Anything V5.0 NPU",
            description = "SD1.5 动漫通用：适合头像、角色和二次元插画。",
            filePrefix = "AnythingV5",
            defaultPrompt = LocalDreamDefaults.ANYTHING_V5_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ANIME_NEGATIVE_PROMPT
        ),
        localDreamSd15CpuModel(
            id = "localdream-anythingv5-cpu",
            name = "Anything V5.0 CPU",
            description = "SD1.5 CPU 动漫：非骁龙设备也可下载的通用版本。",
            fileName = "AnythingV5.zip",
            defaultPrompt = LocalDreamDefaults.ANYTHING_V5_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ANIME_NEGATIVE_PROMPT
        ),
        localDreamSd15NpuModel(
            id = "localdream-qteamix-npu",
            name = "QteaMix NPU",
            description = "SD1.5 萌系风格：适合 chibi、可爱角色和贴纸。",
            filePrefix = "QteaMix",
            defaultPrompt = LocalDreamDefaults.QTEAMIX_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ANIME_NEGATIVE_PROMPT
        ),
        localDreamSd15CpuModel(
            id = "localdream-qteamix-cpu",
            name = "QteaMix CPU",
            description = "SD1.5 CPU 萌系：通用设备上的可爱风格生图。",
            fileName = "QteaMix.zip",
            defaultPrompt = LocalDreamDefaults.QTEAMIX_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ANIME_NEGATIVE_PROMPT
        ),
        localDreamSd15NpuModel(
            id = "localdream-cuteyukimix-npu",
            name = "CuteYukiMix NPU",
            description = "SD1.5 清新动漫：适合柔和角色和轻插画。",
            filePrefix = "CuteYukiMix",
            defaultPrompt = LocalDreamDefaults.CUTEYUKIMIX_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ANIME_NEGATIVE_PROMPT
        ),
        localDreamSd15CpuModel(
            id = "localdream-cuteyukimix-cpu",
            name = "CuteYukiMix CPU",
            description = "SD1.5 CPU 清新动漫：通用设备可下载的轻插画模型。",
            fileName = "CuteYukiMix.zip",
            defaultPrompt = LocalDreamDefaults.CUTEYUKIMIX_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ANIME_NEGATIVE_PROMPT
        ),
        localDreamSd15NpuModel(
            id = "localdream-absolutereality-npu",
            name = "Absolute Reality NPU",
            description = "SD1.5 写实通用：适合真实照片感和自然场景。",
            filePrefix = "AbsoluteReality",
            defaultPrompt = LocalDreamDefaults.ABSOLUTE_REALITY_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ABSOLUTE_REALITY_NEGATIVE_PROMPT
        ),
        localDreamSd15CpuModel(
            id = "localdream-absolutereality-cpu",
            name = "Absolute Reality CPU",
            description = "SD1.5 CPU 写实：非骁龙设备上的真实风格模型。",
            fileName = "AbsoluteReality.zip",
            defaultPrompt = LocalDreamDefaults.ABSOLUTE_REALITY_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.ABSOLUTE_REALITY_NEGATIVE_PROMPT
        ),
        localDreamSd15NpuModel(
            id = "localdream-chilloutmix-npu",
            name = "ChilloutMix NPU",
            description = "SD1.5 人像写实：适合照片级人像和半身肖像。",
            filePrefix = "ChilloutMix",
            defaultPrompt = LocalDreamDefaults.CHILLOUTMIX_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.CHILLOUTMIX_NEGATIVE_PROMPT
        ),
        localDreamSd15CpuModel(
            id = "localdream-chilloutmix-cpu",
            name = "ChilloutMix CPU",
            description = "SD1.5 CPU 人像：通用设备上的写实肖像模型。",
            fileName = "ChilloutMix.zip",
            defaultPrompt = LocalDreamDefaults.CHILLOUTMIX_PROMPT,
            defaultNegativePrompt = LocalDreamDefaults.CHILLOUTMIX_NEGATIVE_PROMPT
        ),
        localDreamUpscalerModel(
            id = "localdream-upscaler-anime",
            name = "RealESRGAN Anime 4x",
            description = "动漫超分：适合二次元图片四倍放大和细节增强。",
            fileDir = "xororz/upscaler/resolve/main/realesrgan_x4plus_anime_6b"
        ),
        localDreamUpscalerModel(
            id = "localdream-upscaler-realistic",
            name = "UltraSharpV2 Lite 4x",
            description = "写实超分：适合照片和通用图片四倍清晰化。",
            fileDir = "xororz/upscaler/resolve/main/4x_UltraSharpV2_Lite"
        ),
        // MediaPipe 慢路径候选：保留下载资产用于回归，不再作为首页生图主入口
        ModelInfo(
            id = "minisd-mediapipe",
            name = "MiniSD MediaPipe 生图",
            description = "MediaPipe 候选：真机生成链路偏慢，仅保留下载与后续回归验证。",
            arch = ModelArch.TRANSFORMER,
            paramSize = "MiniSD",
            quantization = "MediaPipe",
            downloadUrl = "https://sdai-models.moroz.cc/SDAI/MediaPipe/minisd.zip",
            mirrorUrls = listOf(
                "https://github.com/ShiftHackZ/Local-Diffusion-Models-SDAI-MediaPipe/releases/download/patch-28082024/minisd.zip",
                "https://gh-proxy.ygxz.in/https://github.com/ShiftHackZ/Local-Diffusion-Models-SDAI-MediaPipe/releases/download/patch-28082024/minisd.zip"
            ),
            fileSizeMB = 1821,
            isFullySupported = false,
            tokenizerUrl = null,
            capability = ModelCapability.IMAGE_GENERATION,
            adapterType = RuntimeAdapterType.MEDIAPIPE_IMAGE_GENERATION,
            visibility = ModelVisibility.CANDIDATE,
            assets = listOf(
                ModelAsset(
                    filename = "minisd.zip",
                    url = "https://sdai-models.moroz.cc/SDAI/MediaPipe/minisd.zip",
                    mirrorUrls = listOf(
                        "https://github.com/ShiftHackZ/Local-Diffusion-Models-SDAI-MediaPipe/releases/download/patch-28082024/minisd.zip",
                        "https://gh-proxy.ygxz.in/https://github.com/ShiftHackZ/Local-Diffusion-Models-SDAI-MediaPipe/releases/download/patch-28082024/minisd.zip"
                    ),
                    kind = ModelAssetKind.ARCHIVE
                )
            ),
            verifiedOnDevice = false,
            adapterAvailable = false,
            unavailableReason = "性能收敛：三星真机点击后生成进度长期无增量，已从首页主入口移除，仅保留下载源和候选回归入口"
        ),
        // 生图第一优先模型：同系列中 OpenCL 版面向 Android GPU 优化，优先满足手机端速度目标
        ModelInfo(
            id = "sd15-mnn-opencl-8bit",
            name = "Stable Diffusion 1.5 MNN OpenCL",
            description = "端侧 OpenCL 文生图：适合 512 图片、头像和表情包草图。",
            arch = ModelArch.TRANSFORMER,
            paramSize = "SD1.5",
            quantization = "INT8",
            downloadUrl = "https://huggingface.co/taobao-mnn/stable-diffusion-v1-5-mnn-opencl/resolve/main/unet.mnn",
            mirrorUrls = listOf(
                "https://modelscope.cn/models/MNN/stable-diffusion-v1-5-mnn-opencl/resolve/master/unet.mnn",
                "https://hf-mirror.com/taobao-mnn/stable-diffusion-v1-5-mnn-opencl/resolve/main/unet.mnn"
            ),
            fileSizeMB = 1100,
            isFullySupported = false,
            tokenizerUrl = null,
            capability = ModelCapability.IMAGE_GENERATION,
            adapterType = RuntimeAdapterType.MNN,
            visibility = ModelVisibility.CANDIDATE,
            assets = listOf(
                mnnDiffusionAsset("alphas.txt"),
                mnnDiffusionAsset("merges.txt"),
                mnnDiffusionAsset("vocab.json"),
                mnnDiffusionAsset("text_encoder.mnn"),
                mnnDiffusionAsset("text_encoder.mnn.weight"),
                mnnDiffusionAsset("unet.mnn"),
                mnnDiffusionAsset("unet.mnn.weight"),
                mnnDiffusionAsset("vae_decoder.mnn"),
                mnnDiffusionAsset("vae_decoder.mnn.weight")
            ),
            verifiedOnDevice = false,
            adapterAvailable = false,
            unavailableReason = "候选模型：三星 s5e8855/a56x 真机已确认 MNN SD1.5 OpenCL 崩溃、CPU 低内存被系统杀进程，未真实出图前不得上架"
        ),
        hiddenModel("mobilellm-125m", "MobileLLM 125M", "已真机复测：下载和加载成功，但基础 hello 输出网页语料碎片，不是正常对话"),
        hiddenModel("gemma3-270m-onnx", "Gemma 3 270M ONNX", "已真机复测：下载、外部数据、tokenizer、加载均成功，但基础 hello 输出重复碎片"),
        hiddenModel("tinyllama-1.1b-onnx", "TinyLlama 1.1B Chat ONNX", "已真机复测：下载、tokenizer、加载均成功，但基础 hello 生成完成后没有可读文本"),
        hiddenModel("llama32-1b-uncensored-gguf", "Llama 3.2 1B Uncensored GGUF", "已本地和真机复测：下载、GGUF 加载均成功，但基础 hello 输出英文碎片和重复语料，不是正常对话"),
        hiddenModel("minicpm5-1b-uncensored-gguf", "MiniCPM5 1B Uncensored GGUF", "已本地复测：公开文件可下载，但当前 java-llama.cpp 4.1.0 无法加载该 GGUF"),
        hiddenModel("companioncat-chatbot-gguf", "CompanionCat Chatbot GGUF", "已本地复测：公开文件可下载，但当前 java-llama.cpp 4.1.0 无法加载该 GGUF"),
        hiddenModel("qwen2-0.5b", "Qwen2 0.5B ONNX", "已真机复测：基础 hello 输出乱码 token"),
        hiddenModel("smollm2-135m", "SmolLM2 135M ONNX", "已真机复测：输出异常且不稳定"),
        hiddenModel("smollm2-135m-mha", "SmolLM2 135M MHA ONNX", "已真机复测：ONNX Runtime shape mismatch")
    )

    /** 对用户展示的可用模型列表，只包含已验证且 adapter 已实现的模型 */
    val models: List<ModelInfo> = allModels.filter {
        it.visibility == ModelVisibility.VERIFIED && it.adapterAvailable
    }

    /** 根据 ID 查找模型 */
    fun findById(id: String): ModelInfo? = allModels.find { it.id == id }

    /** 获取默认模型（当前为内置体验模型） */
    fun getDefault(): ModelInfo = models.first()

    /** 获取完全支持的模型列表 */
    fun getFullySupported(): List<ModelInfo> = models.filter { it.isFullySupported }

    /** 获取需要下载权重的已验证外部模型列表 */
    fun getExperimental(): List<ModelInfo> = models.filter { !it.isFullySupported }

    /** 获取候选模型列表 */
    fun getCandidates(): List<ModelInfo> = allModels.filter { it.visibility == ModelVisibility.CANDIDATE }

    /** 获取隐藏模型列表 */
    fun getHidden(): List<ModelInfo> = allModels.filter { it.visibility == ModelVisibility.HIDDEN }

    /** 构造 ONNX 文本候选模型 */
    private fun candidateTextModel(
        id: String,
        name: String,
        description: String,
        paramSize: String,
        fileSizeMB: Int,
        downloadUrl: String,
        adapterType: RuntimeAdapterType = RuntimeAdapterType.ONNX_TEXT_GENERATION
    ): ModelInfo {
        return ModelInfo(
            id = id,
            name = name,
            description = description,
            arch = ModelArch.TRANSFORMER,
            paramSize = paramSize,
            quantization = "待确认",
            downloadUrl = downloadUrl,
            fileSizeMB = fileSizeMB,
            isFullySupported = false,
            tokenizerUrl = defaultTokenizerUrl(downloadUrl),
            adapterType = adapterType,
            visibility = ModelVisibility.CANDIDATE,
            verifiedOnDevice = false,
            adapterAvailable = false,
            unavailableReason = "候选模型：需要冻结资产、适配 adapter 并完成真机 golden-output 验证"
        )
    }

    /** 构造单文件 task / tflite 候选模型 */
    private fun candidateTaskModel(
        id: String,
        name: String,
        description: String,
        paramSize: String,
        fileSizeMB: Int,
        url: String,
        adapterType: RuntimeAdapterType,
        capability: ModelCapability
    ): ModelInfo {
        return ModelInfo(
            id = id,
            name = name,
            description = description,
            arch = ModelArch.TRANSFORMER,
            paramSize = paramSize,
            quantization = "待确认",
            downloadUrl = url,
            fileSizeMB = fileSizeMB,
            isFullySupported = false,
            capability = capability,
            adapterType = adapterType,
            visibility = ModelVisibility.CANDIDATE,
            assets = listOf(
                ModelAsset(
                    filename = filenameFromUrl(url, "$id.task"),
                    url = url,
                    kind = if (url.endsWith(".task")) ModelAssetKind.TASK else ModelAssetKind.MODEL
                )
            ),
            verifiedOnDevice = false,
            adapterAvailable = false,
            unavailableReason = "候选模型：底层 adapter 尚未接入，不能展示为可用模型"
        )
    }

    /** 构造多资产候选模型占位清单，避免无 manifest 的模型进入首页 */
    private fun candidateMultiAssetModel(
        id: String,
        name: String,
        description: String,
        capability: ModelCapability,
        adapterType: RuntimeAdapterType,
        fileSizeMB: Int
    ): ModelInfo {
        return ModelInfo(
            id = id,
            name = name,
            description = description,
            arch = ModelArch.TRANSFORMER,
            paramSize = "待确认",
            quantization = "待确认",
            downloadUrl = "",
            fileSizeMB = fileSizeMB,
            isFullySupported = false,
            capability = capability,
            adapterType = adapterType,
            visibility = ModelVisibility.CANDIDATE,
            verifiedOnDevice = false,
            adapterAvailable = false,
            unavailableReason = "候选模型：缺少完整资产 manifest、adapter 和真机验证"
        )
    }

    /** 构造 LocalDream SD1.5 CPU zip 模型，运行时文件门禁按上游 sd15cpu 固定布局校验 */
    private fun localDreamSd15CpuModel(
        id: String,
        name: String,
        description: String,
        fileName: String,
        defaultPrompt: String? = null,
        defaultNegativePrompt: String? = null
    ): ModelInfo {
        val url = "https://huggingface.co/xororz/sd-mnn/resolve/main/$fileName"
        return localDreamArchiveModel(
            id = id,
            name = name,
            description = description,
            fileSizeMB = 1229,
            url = url,
            adapterType = RuntimeAdapterType.MNN,
            imageBackendType = "sd15cpu",
            requiredRuntimeFiles = localDreamSd15CpuRuntimeFiles(),
            // Phase 1：SD1.5 CPU native pipeline 已迁移完成（libmagicwx_image_backend.so 已构建），
            // adapter 真实可用；可生成性由 ImageInferenceBackendPlanner 的设备门禁决定。
            // verifiedOnDevice 保持 false + visibility=CANDIDATE，等 Samsung SM-A566E 真机出图后再升 VERIFIED。
            adapterAvailable = true,
            unavailableReason = "",
            defaultPrompt = defaultPrompt,
            defaultNegativePrompt = defaultNegativePrompt,
            // ---- 对齐 local-dream createAnythingV5ModelCPU L671-691 ----
            generationSize = 512,
            approximateSize = "1.2GB",
            runOnCpu = true,
        )
    }

    /** 构造 LocalDream SD1.5 NPU zip 模型，按当前 SoC 自动选择 8gen1 / 8gen2 / min 包 */
    private fun localDreamSd15NpuModel(
        id: String,
        name: String,
        description: String,
        filePrefix: String,
        defaultPrompt: String? = null,
        defaultNegativePrompt: String? = null
    ): ModelInfo {
        val suffix = DeviceSocCapability.qnnSuffix() ?: "min"  // 对齐参照 getChipsetSuffix ?: "min"
        val url = "https://huggingface.co/xororz/sd-qnn/resolve/main/${filePrefix}_qnn2.28_$suffix.zip"
        // 设备门禁：骁龙 QNN 设备上 adapter 即可用（可下载可运行，QNN SDK 构建集成后
        // native 直接接管）；非骁龙设备无 NPU 是物理事实，不再是"即将支持"文案
        val qnnSupported = DeviceSocCapability.qnnSupported()
        return localDreamArchiveModel(
            id = id,
            name = name,
            description = description,
            fileSizeMB = 1127,
            url = url,
            adapterType = RuntimeAdapterType.QNN_IMAGE_GENERATION,
            imageBackendType = "sd15npu",
            requiredRuntimeFiles = localDreamSd15NpuRuntimeFiles(),
            adapterAvailable = qnnSupported,
            unavailableReason = if (qnnSupported) "" else DeviceSocCapability.REASON_NO_QNN,
            defaultPrompt = defaultPrompt,
            defaultNegativePrompt = defaultNegativePrompt,
            // ---- 对齐 local-dream createAnythingV5Model L645-668 ----
            generationSize = 512,
            approximateSize = "1.1GB",
            runOnCpu = false,
        )
    }

    /** 构造 LocalDream SDXL NPU zip 模型，SDXL 仅在骁龙 8 Gen 3 及以上平台开放（对齐参照） */
    private fun localDreamSdxlModel(
        id: String,
        name: String,
        description: String,
        fileUri: String,
        defaultPrompt: String? = null,
        defaultNegativePrompt: String? = null
    ): ModelInfo {
        val url = "https://huggingface.co/$fileUri"
        // 双层设备门禁（对齐参照 isSdxlCapableSoc）：先判 QNN/NPU 能力，再判 8 Gen 3 SoC 白名单
        val supported = DeviceSocCapability.qnnSupported() && DeviceSocCapability.sdxlCapable()
        val reason = when {
            supported -> ""
            !DeviceSocCapability.qnnSupported() -> DeviceSocCapability.REASON_NO_QNN
            else -> DeviceSocCapability.REASON_SOC_BELOW_8GEN3
        }
        return localDreamArchiveModel(
            id = id,
            name = name,
            description = description,
            fileSizeMB = 4301,
            url = url,
            adapterType = RuntimeAdapterType.QNN_IMAGE_GENERATION,
            imageBackendType = "sdxl",
            requiredRuntimeFiles = localDreamSdxlRuntimeFiles(),
            adapterAvailable = supported,
            unavailableReason = reason,
            defaultPrompt = defaultPrompt,
            defaultNegativePrompt = defaultNegativePrompt,
            // ---- 对齐 local-dream createCyberRealisticV10Model L547-569 ----
            generationSize = 1024,
            approximateSize = "4.2GB",
            runOnCpu = false,
            isSdxl = true,
        )
    }

    /** 构造 LocalDream Anima QNN zip 模型，Anima 依赖 Qwen/T5 双 tokenizer 与拆分 DiT */
    private fun localDreamAnimaModel(
        id: String,
        name: String,
        description: String,
        fileName: String,
        defaultPrompt: String? = null,
        defaultNegativePrompt: String? = null
    ): ModelInfo {
        val fileUri = "xororz/anima-qnn/resolve/main/$fileName"
        val url = "https://huggingface.co/$fileUri"
        // Anima 与 SDXL 同为 8gen3 QNN 模型包（文件命名 *_8gen3.zip），共用 8 Gen 3 SoC 门禁
        val supported = DeviceSocCapability.qnnSupported() && DeviceSocCapability.animaCapable()
        val reason = when {
            supported -> ""
            !DeviceSocCapability.qnnSupported() -> DeviceSocCapability.REASON_NO_QNN
            else -> DeviceSocCapability.REASON_SOC_BELOW_8GEN3
        }
        return localDreamArchiveModel(
            id = id,
            name = name,
            description = description,
            fileSizeMB = 5400,
            url = url,
            adapterType = RuntimeAdapterType.QNN_IMAGE_GENERATION,
            imageBackendType = "anima",
            requiredRuntimeFiles = localDreamAnimaRuntimeFiles(),
            adapterAvailable = supported,
            unavailableReason = reason,
            defaultPrompt = defaultPrompt,
            defaultNegativePrompt = defaultNegativePrompt,
            // ---- 对齐 local-dream Anima 模型（参照 createCustomModel L501: isAnima -> 1024） ----
            generationSize = 1024,
            approximateSize = "5.3GB",
            runOnCpu = false,
            isAnima = true,
        )
    }

    /** 构造 LocalDream 超分模型，文件名固定为 upscaler.bin 便于后端统一读取 */
    private fun localDreamUpscalerModel(
        id: String,
        name: String,
        description: String,
        fileDir: String
    ): ModelInfo {
        // 对齐参照 UpscalerRepository：按 SoC 选择 upscaler_<suffix>.bin 权重包
        val suffix = DeviceSocCapability.qnnSuffix() ?: "min"
        val fileUri = "$fileDir/upscaler_$suffix.bin"
        val url = "https://huggingface.co/$fileUri"
        // QNN 超分只在骁龙设备恢复可下载；非骁龙设备显示设备门禁，不提供下载入口
        val qnnSupported = DeviceSocCapability.qnnSupported()
        return ModelInfo(
            id = id,
            name = name,
            description = description,
            arch = ModelArch.STABLE_DIFFUSION,
            paramSize = "4x",
            quantization = "QNN",
            downloadUrl = url,
            fileSizeMB = 128,
            isFullySupported = false,
            capability = ModelCapability.IMAGE_UPSCALING,
            adapterType = RuntimeAdapterType.QNN_IMAGE_GENERATION,
            visibility = ModelVisibility.CANDIDATE,
            assets = listOf(
                ModelAsset(
                    filename = "upscaler.bin",
                    url = url,
                    mirrorUrls = listOf("https://hf-mirror.com/$fileUri"),
                    kind = ModelAssetKind.MODEL
                )
            ),
            imageBackendType = "upscaler",
            requiredRuntimeFiles = listOf("upscaler.bin"),
            verifiedOnDevice = false,
            adapterAvailable = qnnSupported,
            unavailableReason = if (qnnSupported) "" else DeviceSocCapability.REASON_NO_QNN,
            // ---- 对齐 local-dream UpscalerModel（超分模型无生图分辨率） ----
            generationSize = 0,                                    // 超分模型不适用生图分辨率
            approximateSize = "128MB",
            runOnCpu = false,
        )
    }

    /** 构造 LocalDream zip 模型通用元信息，统一放入候选池避免误标真机通过 */
    private fun localDreamArchiveModel(
        id: String,
        name: String,
        description: String,
        fileSizeMB: Int,
        url: String,
        adapterType: RuntimeAdapterType,
        imageBackendType: String,
        requiredRuntimeFiles: List<String>,
        adapterAvailable: Boolean,
        unavailableReason: String,
        defaultPrompt: String? = null,
        defaultNegativePrompt: String? = null,
        // ---- 对齐 local-dream Model 工厂函数字段 ----
        generationSize: Int = 512,
        approximateSize: String = "",
        runOnCpu: Boolean = false,
        isSdxl: Boolean = false,
        isAnima: Boolean = false,
    ): ModelInfo {
        return ModelInfo(
            id = id,
            name = name,
            description = description,
            arch = ModelArch.STABLE_DIFFUSION,
            paramSize = when (imageBackendType) {
                "sdxl" -> "SDXL"
                "anima" -> "Anima"
                else -> "SD1.5"
            },
            quantization = if (adapterType == RuntimeAdapterType.QNN_IMAGE_GENERATION) "QNN" else "MNN",
            downloadUrl = url,
            mirrorUrls = listOf(url.replace("https://huggingface.co/", "https://hf-mirror.com/")),
            fileSizeMB = fileSizeMB,
            isFullySupported = false,
            capability = ModelCapability.IMAGE_GENERATION,
            adapterType = adapterType,
            visibility = ModelVisibility.CANDIDATE,
            assets = listOf(
                ModelAsset(
                    filename = filenameFromUrl(url, "$id.zip"),
                    url = url,
                    mirrorUrls = listOf(url.replace("https://huggingface.co/", "https://hf-mirror.com/")),
                    kind = ModelAssetKind.ARCHIVE
                )
            ),
            imageBackendType = imageBackendType,
            requiredRuntimeFiles = requiredRuntimeFiles,
            verifiedOnDevice = false,
            adapterAvailable = adapterAvailable,
            unavailableReason = unavailableReason,
            defaultPrompt = defaultPrompt,
            defaultNegativePrompt = defaultNegativePrompt,
            // ---- 对齐 local-dream Model 工厂函数字段 ----
            generationSize = generationSize,
            approximateSize = approximateSize,
            runOnCpu = runOnCpu,
            isSdxl = isSdxl,
            isAnima = isAnima,
        )
    }

    /** LocalDream sd15cpu 固定模型文件布局 */
    private fun localDreamSd15CpuRuntimeFiles(): List<String> = listOf(
        "tokenizer.json",
        "clip_v2.mnn",
        "pos_emb.bin",
        "token_emb.bin",
        "unet.mnn",
        "vae_encoder.mnn",
        "vae_decoder.mnn"
    )

    /** LocalDream sd15npu 固定模型文件布局 */
    private fun localDreamSd15NpuRuntimeFiles(): List<String> = listOf(
        "tokenizer.json",
        "clip_v2.mnn",
        "pos_emb.bin",
        "token_emb.bin",
        "unet.bin",
        "vae_encoder.bin",
        "vae_decoder.bin"
    )

    /** LocalDream sdxl 固定模型文件布局 */
    private fun localDreamSdxlRuntimeFiles(): List<String> = listOf(
        "tokenizer.json",
        "clip.mnn",
        "pos_emb.bin",
        "token_emb.bin",
        "clip_2.mnn",
        "pos_emb_2.bin",
        "token_emb_2.bin",
        "unet.bin",
        "vae_encoder.bin",
        "vae_decoder.bin"
    )

    /** LocalDream anima 固定模型文件布局，vae_encoder.bin 为 img2img/inpaint 能力所需 */
    private fun localDreamAnimaRuntimeFiles(): List<String> = listOf(
        "tokenizer.json",
        "tokenizer_t5.json",
        "token_emb.bin",
        "clip.bin",
        "unet_part1.bin",
        "unet_part2.bin",
        "vae_decoder.bin",
        "vae_encoder.bin"
    )

    /** 构造 MNN Stable Diffusion 1.5 OpenCL 优化版资产，主源和镜像保持同文件名一一对应 */
    private fun mnnDiffusionAsset(filename: String): ModelAsset {
        val basePath = "taobao-mnn/stable-diffusion-v1-5-mnn-opencl/resolve/main/$filename"
        val modelScopePath = "MNN/stable-diffusion-v1-5-mnn-opencl/resolve/master/$filename"
        return ModelAsset(
            filename = filename,
            url = "https://huggingface.co/$basePath",
            mirrorUrls = listOf(
                "https://modelscope.cn/models/$modelScopePath",
                "https://hf-mirror.com/$basePath"
            ),
            kind = if (filename.endsWith(".txt") || filename.endsWith(".json")) {
                ModelAssetKind.CONFIG
            } else {
                ModelAssetKind.MODEL
            }
        )
    }

    /** 构造已隐藏模型，保留失败原因防止回归上架 */
    private fun hiddenModel(id: String, name: String, reason: String): ModelInfo {
        return ModelInfo(
            id = id,
            name = name,
            description = reason,
            arch = ModelArch.TRANSFORMER,
            paramSize = "已下线",
            quantization = "N/A",
            downloadUrl = "",
            fileSizeMB = 0,
            isFullySupported = false,
            visibility = ModelVisibility.HIDDEN,
            adapterAvailable = false,
            unavailableReason = reason
        )
    }

    /** 默认把同仓库 tokenizer.json 作为文本候选的必需资产 */
    private fun defaultTokenizerUrl(modelUrl: String): String {
        val withoutOnnxPath = modelUrl.substringBefore("/onnx/", missingDelimiterValue = modelUrl)
        val repositoryRoot = withoutOnnxPath.substringBefore("/cpu_and_mobile/", missingDelimiterValue = withoutOnnxPath)
        return "$repositoryRoot/tokenizer.json"
    }
}

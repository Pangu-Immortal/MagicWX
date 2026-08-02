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

/** 模型架构类型 */
enum class ModelArch {
    BUILTIN,     // 内置体验模型（无需下载，用于首次打开快速体验）
    RWKV,        // RWKV 架构（RNN 状态传递，完全支持）
    TRANSFORMER  // Transformer 架构（KV-cache，实验性支持）
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
    IMAGE_GENERATION      // 文生图 / 图像生成
}

/** 模型运行时 adapter 类型 */
enum class RuntimeAdapterType {
    BUILTIN_TEXT,          // 内置文本体验 adapter
    ONNX_TEXT_GENERATION,  // ONNX Runtime 文本生成 adapter
    ONNX_ASR,              // ONNX 语音识别 adapter（待接入）
    ONNX_TTS,              // ONNX TTS adapter（待接入）
    ONNX_VISION,           // ONNX 视觉 adapter（待接入）
    LITERT_LM,             // Google LiteRT-LM adapter（待接入）
    MEDIAPIPE_TASK,        // MediaPipe Tasks adapter（待接入）
    WHISPER_CPP,           // whisper.cpp adapter（待接入）
    PIPER,                 // Piper TTS adapter（待接入）
    SHERPA_ONNX,           // sherpa-onnx adapter（待接入）
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
    ENCODER,       // 编码器
    DECODER,       // 解码器
    OTHER          // 其它资产
}

/** 单个模型包资产声明 */
data class ModelAsset(
    val filename: String,                                  // 本地保存文件名
    val url: String,                                       // 下载地址
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
 * @param fileSizeMB 预估文件大小（MB）
 * @param isFullySupported 是否完全支持（当前仅内置体验模型为 true）
 * @param tokenizerUrl 分词器 tokenizer.json 下载地址（Transformer 模型必需，RWKV 为 null）
 * @param chatTemplate 聊天模板类型（Transformer 模型使用）
 * @param capability 模型能力类型
 * @param adapterType 运行时 adapter 类型
 * @param visibility 模型可见性
 * @param assets 显式模型包资产列表；为空时从旧 downloadUrl/tokenizerUrl 自动兼容生成
 * @param verifiedOnDevice 是否已有真机验证证据
 * @param adapterAvailable 当前 App 是否已经实现该 adapter
 * @param unavailableReason 候选或下线原因
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
    val chatTemplate: ChatTemplate = ChatTemplate.CHATML, // 聊天模板类型
    val capability: ModelCapability = ModelCapability.TEXT_CHAT, // 模型能力
    val adapterType: RuntimeAdapterType = RuntimeAdapterType.ONNX_TEXT_GENERATION, // runtime adapter
    val visibility: ModelVisibility = ModelVisibility.VERIFIED, // 首页可见性
    val assets: List<ModelAsset> = emptyList(),                 // 显式资产清单
    val verifiedOnDevice: Boolean = false,                      // 真机验证状态
    val adapterAvailable: Boolean = true,                       // adapter 是否已接入
    val unavailableReason: String = ""                          // 不可用原因
) {
    /** 返回完整资产清单；兼容旧字段，避免一次性重写所有调用点 */
    fun resolvedAssets(): List<ModelAsset> {
        if (assets.isNotEmpty()) return assets                  // 显式清单优先，适配多资产模型包
        val resolved = mutableListOf<ModelAsset>()              // 旧模型字段转换结果
        if (downloadUrl.isNotBlank()) {
            resolved += ModelAsset(
                filename = filenameFromUrl(downloadUrl, "model.onnx"),
                url = downloadUrl,
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
 * - 实验性（isFullySupported=false）：已验证可下载、可加载、可完成基础对话的 Transformer 模型
 * - 候选模型（visibility=CANDIDATE）：已进入工程清单，但 adapter 或验证未完成，不在首页展示
 * - 隐藏模型（visibility=HIDDEN）：已有失败证据或暂时不可控，不在首页展示
 */
object ModelRegistry {

    /**
     * 对用户展示的可用模型列表
     *
     * 当前保留条件：
     * 1. 清数据后可从应用内完成下载或无需下载
     * 2. 可正常加载进入对话页
     * 3. 使用基础测试词 hello 可输出自然回复
     *
     * 已下线待重新验证：RWKV-7、DeepSeek-R1、Gemma 3、Phi-3、Llama 3.2、
     * TinyLlama、StableLM 2、MiniCPM、Qwen2 0.5B、SmolLM2 135M、SmolLM2 135M MHA。
     * 原因是下载不可控、输出异常、无输出、链接失效或尚未取得完整真机通过证据。
     */
    val allModels: List<ModelInfo> = listOf(
        // 0. MagicWX 内置体验模型（无需下载权重，确保首次打开可立即对话）
        ModelInfo(
            id = "magicwx-builtin-demo",
            name = "MagicWX 内置体验模型",
            description = "无需下载，首次打开即可体验本地对话流程",
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
        // 1. 阿里巴巴 Qwen3（已真机验证：下载、加载、hello 回复通过）
        ModelInfo(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B",
            description = "通义千问 3 最新轻量版，中英文能力全面升级",
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
        // 2. 阿里巴巴 Qwen2.5 0.5B（已真机验证：下载、加载、hello 回复通过）
        ModelInfo(
            id = "qwen25-0.5b",
            name = "Qwen2.5 0.5B",
            description = "通义千问 2.5 超轻量版，体积小于 Qwen3 0.6B",
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
        // 3. HuggingFace SmolLM2 360M（已真机验证：下载、加载、hello 回复通过）
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
            chatTemplate = ChatTemplate.CHATML,
            verifiedOnDevice = true
        ),
        candidateTextModel("mobilellm-125m", "MobileLLM 125M", "Meta 端侧小模型候选，需要 tokenizer 和输出契约复测", "125M", 180, "https://hf-mirror.com/onnx-community/MobileLLM-125M/resolve/main/onnx/model.onnx"),
        candidateTextModel("mobilellm-350m", "MobileLLM 350M", "Meta 端侧小模型候选，需要 tokenizer 和输出契约复测", "350M", 460, "https://hf-mirror.com/onnx-community/MobileLLM-350M/resolve/main/onnx/model.onnx"),
        candidateTextModel("gemma3-270m-onnx", "Gemma 3 270M ONNX", "Google Gemma 小模型候选，需要 Gemma tokenizer 与外部数据清单", "270M", 340, "https://hf-mirror.com/onnx-community/gemma-3-270m-it-ONNX/resolve/main/onnx/model.onnx", RuntimeAdapterType.ONNX_TEXT_GENERATION),
        candidateTextModel("gemma3-1b-onnx", "Gemma 3 1B ONNX", "Google Gemma 1B 候选，需要内存预算和真机 dry-run", "1B", 1150, "https://hf-mirror.com/onnx-community/gemma-3-1b-it-ONNX/resolve/main/onnx/model.onnx", RuntimeAdapterType.ONNX_TEXT_GENERATION),
        candidateTaskModel("gemma3-1b-task", "Gemma3-1B-IT LiteRT", "Google AI Edge LiteRT-LM 候选，需要 LiteRT adapter", "1B", 530, "https://huggingface.co/google/gemma-3-1b-it-litert-preview/resolve/main/gemma3-1b-it-int4.task", RuntimeAdapterType.LITERT_LM, ModelCapability.TEXT_CHAT),
        candidateTaskModel("qwen25-1.5b-task", "Qwen2.5-1.5B LiteRT", "Google AI Edge LiteRT-LM 候选，需要 LiteRT adapter", "1.5B", 1550, "https://huggingface.co/google/qwen2.5-1.5b-instruct-litert-preview/resolve/main/qwen2.5-1.5b-instruct-int4.task", RuntimeAdapterType.LITERT_LM, ModelCapability.TEXT_CHAT),
        candidateTaskModel("gemma3n-e2b-task", "Gemma-3n E2B", "VLM / 文本候选，峰值内存高，需要 LiteRT 多模态 adapter", "E2B", 2991, "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task", RuntimeAdapterType.LITERT_LM, ModelCapability.IMAGE_TEXT),
        candidateTaskModel("gemma3n-e4b-task", "Gemma-3n E4B", "VLM / 文本候选，峰值内存高，需要 LiteRT 多模态 adapter", "E4B", 4202, "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.task", RuntimeAdapterType.LITERT_LM, ModelCapability.IMAGE_TEXT),
        candidateTextModel("llama32-1b-onnx", "Llama 3.2 1B ONNX", "Meta Llama 小模型候选，需要许可、tokenizer 和真机输出验证", "1B", 1200, "https://hf-mirror.com/onnx-community/Llama-3.2-1B-Instruct-ONNX/resolve/main/onnx/model_q4f16.onnx"),
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
        candidateMultiAssetModel("stable-diffusion-mobile", "Stable Diffusion Mobile", "文生图候选，需要 MNN/NCNN/TFLite diffusion pipeline", ModelCapability.IMAGE_GENERATION, RuntimeAdapterType.MNN, 1800),
        candidateMultiAssetModel("tiny-sd-lcm", "Tiny-SD / LCM", "轻量扩散候选，需要 diffusion scheduler 和图像解码 adapter", ModelCapability.IMAGE_GENERATION, RuntimeAdapterType.MNN, 950),
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

    /** 获取实验性模型列表 */
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

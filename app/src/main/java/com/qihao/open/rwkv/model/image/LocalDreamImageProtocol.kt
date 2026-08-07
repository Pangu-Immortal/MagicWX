/**
 * LocalDreamImageProtocol - LocalDream 图片后端 clean-room 协议层
 *
 * 功能：
 * - LocalDreamGenerationMode: 描述文生图、图生图、局部重绘和 UltraFix 四类生成模式
 * - LocalDreamImageRequest: 承载 /generate 的完整参数，不把提示词硬编码成唯一输入
 * - LocalDreamBackendProtocol: 负责生成 native 后端 JSON，供 Service 和单测复用
 */
package com.qihao.open.rwkv.model.image

/** LocalDream 同类图片生成模式 */
enum class LocalDreamGenerationMode(val wireValue: String) {
    TEXT_TO_IMAGE("txt2img"),   // 文生图：只依赖 prompt 和生成参数
    IMAGE_TO_IMAGE("img2img"),  // 图生图：需要输入图片和 denoise_strength
    INPAINT("inpaint"),         // 局部重绘：需要输入图片和 mask
    ULTRAFIX("ultrafix")        // UltraFix：对大图执行 tiled img2img 修复
}

/** LocalDream 后端图片编码格式 */
enum class LocalDreamImageWireFormat(val wireValue: String) {
    RAW("raw"),   // 原始 RGB 数据，主要用于 native 直传
    JPEG("jpeg"), // JPEG 压缩结果，适合最终预览和落盘
    PNG("png")    // PNG 结果，适合无损预览
}

/** LocalDream /generate 请求，字段与 native 后端协议一一对应 */
data class LocalDreamImageRequest(
    val prompt: String,                                               // 正向提示词
    val negativePrompt: String = "",                                  // 反向提示词
    val steps: Int = 20,                                               // 采样步数
    val cfg: Float = 7f,                                               // classifier-free guidance 强度
    val seed: Long = 42L,                                              // 随机种子，固定后可复现
    val width: Int = 512,                                              // 目标宽度，必须由后端按模型校验
    val height: Int = 512,                                             // 目标高度，必须由后端按模型校验
    val scheduler: String = "dpm",                                     // 调度器名称，如 dpm/euler/lcm/flow
    val mode: LocalDreamGenerationMode = LocalDreamGenerationMode.TEXT_TO_IMAGE, // 生成模式
    val denoiseStrength: Float = 0.6f,                                 // 图生图/重绘降噪强度
    val aspectRatio: String = "1:1",                                   // SDXL/Anima 固定画布裁切比例
    val showDiffusionProcess: Boolean = false,                         // 是否请求中间预览
    val showDiffusionStride: Int = 1,                                  // 中间预览步长
    val previewFormat: LocalDreamImageWireFormat = LocalDreamImageWireFormat.RAW, // 预览格式
    val outputFormat: LocalDreamImageWireFormat = LocalDreamImageWireFormat.JPEG, // 最终格式
    val imageBase64: String = "",                                      // img2img/inpaint/ultrafix 输入图
    val maskBase64: String = "",                                       // inpaint mask
    val batchCount: Int = 1,                                            // 批量生成张数
    val seedFixed: Boolean = false,                                     // 用户是否固定了 seed（不进协议，批量闭环用：固定 seed 时只跑 1 张）
    val ultrafixTileSize: Int = 512,                                    // UltraFix tile 大小
    val ultrafixSteps: Int = 10,                                        // UltraFix 修复步数
    val ultrafixDenoiseSteps: Int = 4,                                  // UltraFix 降噪步数
    val ultrafixQualityDenoise: Boolean = true                          // UltraFix 是否使用质量提示词
) {
    /** 检查请求是否满足当前模式的最小输入要求 */
    fun validate(): String {
        if (prompt.isBlank()) return "提示词不能为空"                    // prompt 是所有生成模式的最小输入
        if (steps <= 0) return "采样步数必须大于 0"                      // 防止 native 接到无效循环次数
        if (width <= 0 || height <= 0) return "图片尺寸必须大于 0"        // 防止非法尺寸进入 native
        if (width % 8 != 0 || height % 8 != 0) return "图片尺寸必须是 8 的倍数" // SD latent 网格要求 8 对齐
        if (batchCount !in 1..10) return "批量张数必须在 1 到 10 之间"   // 与 LocalDream UI 范围一致
        if (mode != LocalDreamGenerationMode.TEXT_TO_IMAGE && imageBase64.isBlank()) {
            return "${mode.wireValue} 需要输入图片"                     // 图生图类模式必须携带 base image
        }
        if (mode == LocalDreamGenerationMode.INPAINT && maskBase64.isBlank()) {
            return "inpaint 需要输入 mask"                              // 局部重绘必须携带 mask
        }
        return ""                                                       // 空字符串表示通过
    }
}

/** LocalDream 后端协议工具，只做确定性序列化，不持有 Android 状态 */
object LocalDreamBackendProtocol {

    /**
     * 构造 /generate JSON 请求体，字段与 local-dream native RequestParser 100% 对齐。
     * native 只读它认识的字段（忽略多余字段）；这里只发送 native 真正消费的字段，
     * 移除了旧 MagicWX 的 output_path/backend_type/memory_mode/mode/batch_count（native 不读）。
     *
     * UltraFix 模式参数映射（对齐参照 ModelRunScreen.startUltrafix + ModelRunSupport.kt）：
     * - steps 发 ultrafixSteps（页面主 steps 不参与 ultrafix 修复）
     * - denoise_strength 由 ultrafixDenoiseSteps/ultrafixSteps 推导，保证 native 恰好跑指定降噪步数
     * - ultrafixQualityDenoise=true 时 prompt 替换为中性质量提示词、negative_prompt 置空
     *
     * @param useOpencl 是否走 OpenCL（GPU），来自 ImageInferenceBackendChoice
     */
    fun buildGenerateJson(
        request: LocalDreamImageRequest,
        useOpencl: Boolean
    ): String {
        // UltraFix 模式下对 prompt/negative/steps/denoise 做整体替换，避免误发主页面参数
        val isUltrafix = request.mode == LocalDreamGenerationMode.ULTRAFIX
        val effectivePrompt = if (isUltrafix && request.ultrafixQualityDenoise) {
            LocalDreamDefaults.ULTRAFIX_QUALITY_PROMPT                     // 质量提示词修复，不用页面 prompt
        } else {
            request.prompt
        }
        val effectiveNegative = if (isUltrafix && request.ultrafixQualityDenoise) "" else request.negativePrompt
        val effectiveSteps = if (isUltrafix) request.ultrafixSteps else request.steps
        val effectiveDenoise = if (isUltrafix) {
            ultrafixDenoiseStrength(request.ultrafixDenoiseSteps, request.ultrafixSteps)
        } else {
            request.denoiseStrength
        }

        val fields = mutableListOf<String>()                            // 保持字段顺序稳定，便于测试和日志 diff
        addString(fields, "prompt", effectivePrompt)                     // 正向提示词
        addString(fields, "negative_prompt", effectiveNegative)          // 反向提示词
        addNumber(fields, "steps", effectiveSteps.toString())            // 采样步数
        addNumber(fields, "cfg", trimFloat(request.cfg))                 // CFG 强度
        addNumber(fields, "seed", request.seed.toString())               // 随机种子
        addNumber(fields, "width", request.width.toString())             // 宽度
        addNumber(fields, "height", request.height.toString())           // 高度
        addString(fields, "scheduler", request.scheduler)                // 调度器：dpm/euler/lcm/...
        addNumber(fields, "denoise_strength", trimFloat(effectiveDenoise)) // 图生图降噪强度（ultrafix 为推导值）
        addString(fields, "aspect_ratio", request.aspectRatio)           // SDXL/Anima 固定画布裁切比例
        addBoolean(fields, "show_diffusion_process", request.showDiffusionProcess) // 是否请求中间预览
        addNumber(fields, "show_diffusion_stride", request.showDiffusionStride.toString()) // 预览步长
        addString(fields, "preview_format", request.previewFormat.wireValue) // 预览格式：raw/jpeg/png
        addString(fields, "output_format", request.outputFormat.wireValue) // 输出格式：raw/jpeg/png
        addBoolean(fields, "use_opencl", useOpencl)                      // MNN 走 CPU 还是 OpenCL
        if (request.imageBase64.isNotBlank()) addString(fields, "image", request.imageBase64) // img2img/inpaint/ultrafix 输入图
        if (request.maskBase64.isNotBlank()) addString(fields, "mask", request.maskBase64)    // inpaint mask
        if (isUltrafix) {
            addBoolean(fields, "ultrafix", true)                         // native 端按布尔字段启用 UltraFix
            addNumber(fields, "tile_size", request.ultrafixTileSize.toString()) // UltraFix tile 大小
        }
        return fields.joinToString(prefix = "{", postfix = "}")          // 输出标准 JSON object
    }

    /**
     * 把目标 UltraFix 降噪步数映射为后端恰好执行该步数的 denoise_strength。
     * 与参照 ModelRunSupport.ultrafixDenoiseStrength 完全一致：
     * 后端按 floor(steps * (1 - strength)) 计算起始步，再跑 (steps - start) 步降噪；
     * 解出 strength = (denoiseSteps - 0.5) / totalSteps，半格偏移保证浮点误差不会跨整数边界。
     */
    fun ultrafixDenoiseStrength(denoiseSteps: Int, totalSteps: Int): Float {
        if (totalSteps <= 0) return 0f                                   // 非法总步数兜底，避免除零
        val clamped = denoiseSteps.coerceIn(0, totalSteps)               // 降噪步数不允许超过总步数
        return ((clamped - 0.5f) / totalSteps).coerceIn(0f, 1f)          // 收敛到合法强度区间
    }

    /** 构造 /tokenize JSON 请求体 */
    fun buildTokenizeJson(prompt: String): String {
        return "{\"prompt\":\"${jsonEscape(prompt)}\"}"                 // tokenize 只需要 prompt
    }

    /** 添加字符串字段 */
    private fun addString(fields: MutableList<String>, key: String, value: String) {
        fields += "\"$key\":\"${jsonEscape(value)}\""                   // 统一转义，避免破坏 JSON 结构
    }

    /** 添加数字字段 */
    private fun addNumber(fields: MutableList<String>, key: String, value: String) {
        fields += "\"$key\":$value"                                      // 数字不加引号，匹配 native 解析器
    }

    /** 添加布尔字段 */
    private fun addBoolean(fields: MutableList<String>, key: String, value: Boolean) {
        fields += "\"$key\":$value"                                      // Kotlin Boolean 输出 true/false
    }

    /** 格式化 Float，去掉多余尾随 0 */
    private fun trimFloat(value: Float): String {
        val text = value.toString()                                      // Kotlin 标准格式已足够稳定
        return if (text.endsWith(".0")) text.dropLast(2) else text       // 7.0 -> 7，减少日志噪声
    }

    /** JSON 字符串转义，避免 prompt / 路径破坏请求体 */
    private fun jsonEscape(value: String): String {
        return buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")                              // 反斜杠必须先转义
                    '"' -> append("\\\"")                                // 双引号必须转义
                    '\n' -> append("\\n")                                // 换行转 JSON 控制符
                    '\r' -> append("\\r")                                // 回车转 JSON 控制符
                    '\t' -> append("\\t")                                // Tab 转 JSON 控制符
                    else -> append(char)                                  // 普通字符原样保留
                }
            }
        }
    }
}

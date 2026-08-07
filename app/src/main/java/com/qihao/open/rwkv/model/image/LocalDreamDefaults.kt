/**
 * LocalDreamDefaults - LocalDream 生图默认参数常量
 *
 * 功能：
 * - 集中承载与参照仓库 local-dream（modules/local-dream）对齐的默认生成参数
 * - 全局默认值来源：io.github.xororz.localdream.data.GenerationDefaults 构造默认
 * - 各模型专属默认提示词来源：io.github.xororz.localdream.data.Model 的
 *   create*Model 工厂函数 codeDefaults（prompt/negativePrompt），逐模型对齐
 *
 * 设计要点：UI 层初始值按「持久化存档 > ModelInfo 模型专属默认 > 本对象全局兜底」
 * 三级回退（对齐参照 ModelConfig.resolve 语义）；ANYTHING_V5 常量保留作全局兜底，
 * 模型专属提示词统一集中在本对象，避免散落在 Compose 页面或注册表里。
 */
package com.qihao.open.rwkv.model.image

/** LocalDream 默认生成参数，数值与 local-dream 上游逐项对齐 */
object LocalDreamDefaults {

    // ---- 全局默认（对齐 local-dream GenerationDefaults 构造默认值） ----
    /** 采样步数，对齐 GenerationDefaults.steps */
    const val STEPS = 20

    /** CFG 强度，对齐 GenerationDefaults.cfg */
    const val CFG = 7f

    /** 调度器 wire id，对齐 GenerationDefaults.scheduler */
    const val SCHEDULER = "dpm"

    /** 图生图/重绘降噪强度，对齐 GenerationDefaults.denoiseStrength */
    const val DENOISE_STRENGTH = 0.6f

    /** 批量张数，对齐 GenerationDefaults.batchCounts */
    const val BATCH_COUNT = 1

    /** 画布宽高比预设，对齐 GenerationDefaults.aspectRatio */
    const val ASPECT_RATIO = "1:1"

    /** 空 seed 表示本次随机，对齐 GenerationDefaults.seed */
    const val SEED = ""

    // ---- AnythingV5 模型专属默认（对齐 Model.createAnythingV5ModelCPU 的 codeDefaults） ----
    /** AnythingV5 默认正向提示词，来源 local-dream Model.kt codeDefaults.prompt */
    const val ANYTHING_V5_PROMPT =
        "masterpiece, best quality, 1girl, solo, cute, white hair,"

    /** AnythingV5 默认反向提示词，来源 local-dream Model.kt codeDefaults.negativePrompt */
    const val ANYTHING_V5_NEGATIVE_PROMPT =
        "lowres, bad anatomy, bad hands, missing fingers, extra fingers, bad arms, " +
            "missing legs, missing arms, poorly drawn face, bad face, fused face, cloned face, " +
            "three crus, fused feet, fused thigh, extra crus, ugly fingers, horn, realistic photo, " +
            "huge eyes, worst face, 2girl, long fingers, disconnected limbs,"

    // ---- 动漫系通用负向提示词 ----
    /**
     * 动漫系模型统一负向提示词：参照仓库中 AnythingV5 / QteaMix / CuteYukiMix /
     * Illustrious v16 的 codeDefaults.negativePrompt 完全一致，集中为单一常量避免重复。
     */
    const val ANIME_NEGATIVE_PROMPT = ANYTHING_V5_NEGATIVE_PROMPT

    // ---- QteaMix 模型专属默认（对齐 createQteaMixModel codeDefaults） ----
    /** QteaMix 默认正向提示词 */
    const val QTEAMIX_PROMPT = "chibi, best quality, 1girl, solo, cute, pink hair,"

    // ---- CuteYukiMix 模型专属默认（对齐 createCuteYukiMixModel codeDefaults） ----
    /** CuteYukiMix 默认正向提示词：参照仓库与 AnythingV5 完全一致，复用同一常量 */
    const val CUTEYUKIMIX_PROMPT = ANYTHING_V5_PROMPT

    // ---- Absolute Reality 模型专属默认（对齐 createAbsoluteRealityModel codeDefaults） ----
    /** Absolute Reality 默认正向提示词 */
    const val ABSOLUTE_REALITY_PROMPT =
        "masterpiece, best quality, ultra-detailed, realistic, 8k, a cat on grass,"

    /** Absolute Reality 默认负向提示词（写实系，与动漫系不同源） */
    const val ABSOLUTE_REALITY_NEGATIVE_PROMPT =
        "worst quality, low quality, normal quality, poorly drawn, lowres, low resolution, " +
            "signature, watermarks, ugly, out of focus, error, blurry, unclear photo, bad photo, " +
            "unrealistic, semi realistic, pixelated, cartoon, anime, cgi, drawing, 2d, 3d, " +
            "censored, duplicate,"

    // ---- ChilloutMix 模型专属默认（对齐 createChilloutMixModel codeDefaults） ----
    /** ChilloutMix 默认正向提示词 */
    const val CHILLOUTMIX_PROMPT =
        "RAW photo, best quality, realistic, photo-realistic, masterpiece, 1girl, upper body, " +
            "facing front, portrait, white shirt"

    /** ChilloutMix 默认负向提示词 */
    const val CHILLOUTMIX_NEGATIVE_PROMPT =
        "paintings, cartoon, anime, lowres, bad anatomy, bad hands, text, error, missing fingers, " +
            "extra digit, cropped, worst quality, low quality, normal quality, jpeg artifacts, " +
            "signature, watermark, username, skin spots, acnes, skin blemishes"

    // ---- CyberRealistic v10 模型专属默认（对齐 createCyberRealisticV10Model codeDefaults） ----
    /** CyberRealistic v10 默认正向提示词（DMD2 版同源） */
    const val CYBER_REALISTIC_PROMPT =
        "masterpiece, best quality, a majestic cat sitting on a windowsill at sunset,"

    /** CyberRealistic v10 默认负向提示词（DMD2 版同源） */
    const val CYBER_REALISTIC_NEGATIVE_PROMPT =
        "lowres, bad anatomy, bad hands, text, error, missing fingers, extra digit, fewer digits, " +
            "cropped, worst quality, low quality, normal quality, jpeg artifacts, signature, " +
            "watermark, username, blurry,"

    // ---- Illustrious v16 模型专属默认（对齐 createIllustriousV16Model codeDefaults） ----
    /** Illustrious v16 默认正向提示词（DMD2 版同源） */
    const val ILLUSTRIOUS_PROMPT =
        "1girl, solo, blue twintails, very long hair, bangs, blue eyes, jewelry, necklace, " +
            "hair bow, off-shoulder white frilled dress, bare shoulders, collarbone, underwater, " +
            "floating hair, reaching towards viewer, air bubbles, blue theme, blurry foreground, " +
            "masterpiece"

    /**
     * UltraFix 质量提示词，对齐 local-dream GenerationDefaults.ULTRAFIX_QUALITY_PROMPT。
     * ultrafixQualityDenoise=true 时 UltraFix 用该中性质量提示词替代页面 prompt 做 tile 修复。
     */
    const val ULTRAFIX_QUALITY_PROMPT = "masterpiece, best quality, 4k resolution"

    // ---- 自定义模型导入兜底提示词（对齐 local-dream Model.kt createCustomModel placeholders L490-492） ----
    /**
     * 自定义模型导入兜底正向提示词，对齐 createCustomModel 的 placeholder prompt。
     * 当导入的模型 zip 不含 config.json 或 config.json 未声明 default_prompt 时使用。
     */
    const val CAT_SAT_ON_MAT = "masterpiece, best quality, a cat sat on a mat,"

    /**
     * 自定义模型导入兜底负向提示词，对齐 createCustomModel 的 placeholder negativePrompt。
     * 注意：与 ANIME_NEGATIVE_PROMPT 相比缺少 "realistic photo," 词条，是独立常量。
     */
    const val CAT_SAT_ON_MAT_NEGATIVE_PROMPT =
        "lowres, bad anatomy, bad hands, missing fingers, extra fingers, bad arms, " +
            "missing legs, missing arms, poorly drawn face, bad face, fused face, cloned face, " +
            "three crus, fused feet, fused thigh, extra crus, ugly fingers, horn, " +
            "huge eyes, worst face, 2girl, long fingers, disconnected limbs,"

    /**
     * 获取采样器显示名。
     * 原本位于 LocalDreamGenerationDraft.kt 的 LocalDreamGenerationOptions；该文件其余成员
     * （draft/toRequest/missingInputHint 等）均已确认无调用方被删除，仅 schedulerLabel 在
     * MainActivity 有真实引用，故迁移到本对象集中承载，避免残留孤儿文件。
     */
    fun schedulerLabel(id: String): String {
        return when (id) {
            "dpm" -> "DPM++ 2M"
            "dpm_karras" -> "DPM++ 2M Karras"
            "dpm_sde" -> "DPM++ 2M SDE"
            "dpm_sde_karras" -> "DPM++ 2M SDE Karras"
            "euler_a" -> "Euler A"
            "euler_a_karras" -> "Euler A Karras"
            "euler" -> "Euler"
            "euler_karras" -> "Euler Karras"
            "lcm" -> "LCM"
            else -> id                                            // 未知 id 原样展示，不吞信息
        }
    }
}

/**
 * ImageGenerationEngine - 端侧图片生成统一接口
 *
 * 功能：
 * - GeneratedImageResult: 返回图片生成文件路径、耗时和失败原因
 * - ImageGenerationEngine: 隔离具体 MNN / MediaPipe / LocalDream 其它生图运行时
 */
package com.qihao.open.rwkv.model.image

/** 端侧生图结果 */
data class GeneratedImageResult(
    val outputPath: String,       // 成功生成的图片绝对路径
    val durationMillis: Long,     // 生成总耗时
    val errorMessage: String = "" // 失败原因，空字符串表示成功
) {
    val isSuccess: Boolean get() = errorMessage.isBlank() && outputPath.isNotBlank()
}

/** 图像生成运行时统一接口 */
interface ImageGenerationEngine : AutoCloseable {
    /** 根据完整请求生成图片 */
    suspend fun generate(request: LocalDreamImageRequest): GeneratedImageResult

    /** 兼容旧 UI：只有 prompt 时按文生图默认参数生成 */
    suspend fun generate(prompt: String): GeneratedImageResult {
        return generate(LocalDreamImageRequest(prompt = prompt)) // 旧入口统一收敛到完整协议
    }
}

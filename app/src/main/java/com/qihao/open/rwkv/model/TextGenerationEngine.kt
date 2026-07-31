/**
 * TextGenerationEngine - 文本生成引擎统一接口
 *
 * 功能：
 * - generate(): 流式生成文本
 * - resetState(): 重置上下文状态
 * - close(): 释放运行资源
 */
package com.qihao.open.rwkv.model

/** 文本生成引擎，统一内置体验模型和 ONNX 模型调用方式 */
interface TextGenerationEngine {
    val isLoaded: Boolean // 当前引擎是否已准备好生成

    /** 流式生成回复文本 */
    suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        onToken: (String) -> Unit
    )

    /** 重置模型上下文状态 */
    fun resetState()

    /** 释放模型或引擎资源 */
    fun close()
}

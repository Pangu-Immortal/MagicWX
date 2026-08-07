/**
 * ImageGenerationEvents - 后台生图事件总线
 *
 * 功能：
 * - ImageGenerationEventType: 描述生图开始、进度、完成、失败和取消状态
 * - ImageGenerationEvents: 在生图前台服务和 ViewModel 之间传递 SSE 生成状态
 */
package com.qihao.open.rwkv.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** 后台生图事件类型 */
enum class ImageGenerationEventType {
    STARTED,   // 生图请求已开始
    PROGRESS,  // 收到后端 SSE 进度
    COMPLETED, // 图片生成完成
    FAILED,    // 图片生成失败
    CANCELLED  // 用户取消生成
}

/** 后台生图事件 */
data class ImageGenerationEvent(
    val modelId: String,                    // 生图模型 ID
    val type: ImageGenerationEventType,     // 生图状态类型
    val prompt: String = "",                // 本次提示词
    val progress: Int = 0,                  // 生成进度，0 到 100
    val outputPath: String = "",            // 成功生成的图片路径
    val previewPath: String = "",           // 中间预览图路径（PROGRESS 事件携带，覆盖式落盘）
    val durationMillis: Long = 0L,          // 后端报告或本地测量的耗时
    val message: String = "",               // 用户可读状态或错误原因
    val seed: Long = 42L                    // 本次生成使用的随机种子，COMPLETED 事件携带，供 Room 落库去重
)

/** 进程内生图事件总线，供前台服务与 ViewModel 同步状态 */
object ImageGenerationEvents {
    private val mutableEvents = MutableSharedFlow<ImageGenerationEvent>(
        extraBufferCapacity = 128
    )

    val events = mutableEvents.asSharedFlow()

    /** 发布生图状态事件 */
    fun publish(event: ImageGenerationEvent) {
        mutableEvents.tryEmit(event)
    }

    /**
     * 订阅本次生图的终态事件（COMPLETED/FAILED/CANCELLED），并在订阅就绪后触发启动动作。
     * 先订阅再启动，避免极快失败事件在订阅前发出而丢失（对齐参照 MNN 引擎 waitForServiceResult）。
     *
     * @param modelId 本次生图模型 ID，用于匹配事件
     * @param prompt 本次提示词，用于匹配事件
     * @param timeoutMillis 等待终态的超时上限
     * @param onStart 订阅建立后执行的启动动作（通常是 startForegroundService）
     */
    suspend fun awaitTerminalEvent(
        modelId: String,
        prompt: String,
        timeoutMillis: Long,
        onStart: () -> Unit
    ): ImageGenerationEvent = coroutineScope {
        val waiter = async {
            withTimeout(timeoutMillis) {
                events.first { event ->
                    event.modelId == modelId &&
                        event.prompt == prompt &&
                        (
                            event.type == ImageGenerationEventType.COMPLETED ||
                                event.type == ImageGenerationEventType.FAILED ||
                                event.type == ImageGenerationEventType.CANCELLED
                            )
                }
            }
        }
        onStart()
        waiter.await()
    }
}

/**
 * ImageBackendEvents - 图片后端进程事件总线
 *
 * 功能：
 * - ImageBackendEventType: 描述后端启动、就绪、停止和失败状态
 * - ImageBackendEvents: 在图片后端前台服务和 ViewModel 之间传递进程状态
 */
package com.qihao.open.rwkv.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 图片后端进程事件类型 */
enum class ImageBackendEventType {
    STARTING, // 后端进程正在启动
    READY,    // 后端健康检查通过
    STOPPED,  // 后端进程已停止
    FAILED    // 后端启动或运行失败
}

/** 图片后端进程事件 */
data class ImageBackendEvent(
    val modelId: String,              // 当前后端服务的模型 ID
    val type: ImageBackendEventType,  // 后端状态类型
    val port: Int = 18081,            // localhost 服务端口
    val message: String = ""          // 用户和日志可读状态
)

/** 进程内后端事件总线，避免导出广播或绑定服务接口 */
object ImageBackendEvents {
    private val mutableEvents = MutableSharedFlow<ImageBackendEvent>(
        extraBufferCapacity = 64
    )

    val events = mutableEvents.asSharedFlow()

    /** 发布后端状态事件 */
    fun publish(event: ImageBackendEvent) {
        mutableEvents.tryEmit(event)
    }
}

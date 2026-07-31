/**
 * ModelDownloadEvents - 模型下载事件总线
 *
 * 功能：
 * - ModelDownloadEvent: 描述下载开始、进度、完成和失败事件
 * - ModelDownloadEvents: 在前台服务和 ViewModel 之间传递进程内下载状态
 */
package com.qihao.open.rwkv.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 下载事件类型 */
enum class ModelDownloadEventType {
    STARTED,    // 下载已启动
    PROGRESS,   // 下载进度更新
    COMPLETED,  // 下载完成
    FAILED      // 下载失败
}

/** 模型下载事件 */
data class ModelDownloadEvent(
    val modelId: String,                  // 模型唯一标识
    val type: ModelDownloadEventType,     // 事件类型
    val downloadedBytes: Long = 0L,        // 已下载字节数
    val totalBytes: Long = 0L,             // 总字节数
    val percent: Int = 0,                 // 进度百分比
    val message: String = ""              // 状态或错误信息
)

/** 进程内下载事件总线，避免导出广播面 */
object ModelDownloadEvents {
    private val mutableEvents = MutableSharedFlow<ModelDownloadEvent>(
        extraBufferCapacity = 64
    )

    val events = mutableEvents.asSharedFlow()

    /** 发布下载事件 */
    fun publish(event: ModelDownloadEvent) {
        mutableEvents.tryEmit(event)
    }
}

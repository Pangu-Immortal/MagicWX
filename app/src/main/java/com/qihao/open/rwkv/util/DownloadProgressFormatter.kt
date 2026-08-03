/**
 * DownloadProgressFormatter - 下载进度文本格式化工具
 *
 * 功能：
 * - formatTransferredSize(): 格式化“已下载 / 总大小”
 * - formatBytes(): 按字节数自动选择 MB 或 GB，避免小进度显示成 0.0 GB
 */
package com.qihao.open.rwkv.util

import java.util.Locale

/** 下载进度文本格式化工具，统一服务通知、下载页和 ViewModel 的显示口径 */
object DownloadProgressFormatter {

    /** 格式化“已下载 / 总大小”，总大小未知时明确显示未知 */
    fun formatTransferredSize(downloaded: Long, total: Long): String {
        val downloadedText = formatBytes(downloaded)            // 已下载大小独立选择单位
        val totalText = if (total > 0L) formatBytes(total) else "未知大小" // 总大小未知时不伪造 0
        return "$downloadedText / $totalText"
    }

    /** 按 1024 进位格式化字节数，低于 1GB 时使用 MB，避免 9MB 显示为 0.0GB */
    fun formatBytes(bytes: Long): String {
        val megabytes = bytes / 1024.0 / 1024.0                 // 下载进度最小展示 MB 粒度
        return if (megabytes >= 1024.0) {
            String.format(Locale.US, "%.1f GB", megabytes / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", megabytes)
        }
    }
}

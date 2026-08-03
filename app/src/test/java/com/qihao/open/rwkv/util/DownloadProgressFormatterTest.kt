/**
 * DownloadProgressFormatterTest - 下载进度格式化单元测试
 *
 * 功能：
 * - 验证小下载量不会在大模型总量下显示为 0.0 GB
 * - 验证 GB 级文件总量仍按 GB 展示
 */
package com.qihao.open.rwkv.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressFormatterTest {

    @Test
    fun transferredSizeUsesMbForSmallDownloadedBytes_whenTotalIsGbScale() {
        val downloaded = 9L * 1024L * 1024L
        val total = 1_572L * 1024L * 1024L

        val text = DownloadProgressFormatter.formatTransferredSize(downloaded, total)

        assertEquals("9.0 MB / 1.5 GB", text)
    }

    @Test
    fun transferredSizeShowsUnknown_whenTotalMissing() {
        val downloaded = 1L * 1024L * 1024L

        val text = DownloadProgressFormatter.formatTransferredSize(downloaded, 0L)

        assertEquals("1.0 MB / 未知大小", text)
    }
}

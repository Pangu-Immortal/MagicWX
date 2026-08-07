/**
 * ImageTokenizeClient - MagicWX 图片后端 /tokenize 端点客户端。
 *
 * 功能：
 * - tokenize(): 把 prompt 提交给 native 后端 CLIP tokenizer，返回真实 token 数与溢出位置，
 *   替代此前按空格/逗号切词的本地估算（中文与子词场景误差大）。
 *
 * 协议（与 magicwx_image_backend registerTokenizeEndpoint 对齐）：
 * - 请求体：{"prompt": "..."}（JSON 转义由 LocalDreamBackendProtocol.buildTokenizeJson 承担）
 * - 响应体：{"count": N, "max_length": 77, "overflow_offset": -1|偏移}
 */
package com.qihao.open.rwkv.service

import com.qihao.open.rwkv.model.image.LocalDreamBackendProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** /tokenize 响应结果 */
data class ImageTokenizeResult(
    val count: Int,              // CLIP token 数（含特殊标记）
    val maxLength: Int,          // 上下文上限（SD1.5 固定 77）
    val overflowOffset: Int      // 溢出起始偏移；-1 表示未溢出
)

/** 调用 MagicWX 图片后端 /tokenize 的轻量客户端 */
object ImageTokenizeClient {

    /** 后端未就绪 / 请求失败时抛出，调用方自行降级到本地估算 */
    suspend fun tokenize(prompt: String): ImageTokenizeResult =
        withContext(Dispatchers.IO) {
            val connection = (URL("http://127.0.0.1:${ImageBackendService.PORT}/tokenize")
                .openConnection() as HttpURLConnection)
            try {
                connection.connectTimeout = 2_000                 // tokenize 极快，超时从短
                connection.readTimeout = 3_000
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val body = LocalDreamBackendProtocol.buildTokenizeJson(prompt)
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    error("图片后端 /tokenize 请求失败: HTTP $responseCode")
                }
                val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                ImageTokenizeResult(
                    count = extractInt(text, "count"),
                    maxLength = extractInt(text, "max_length"),
                    overflowOffset = extractInt(text, "overflow_offset")
                )
            } finally {
                connection.disconnect()
            }
        }

    /** 从扁平 JSON 文本提取整数字段（响应结构固定，避免引入额外 JSON 解析依赖） */
    private fun extractInt(json: String, key: String): Int {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
}

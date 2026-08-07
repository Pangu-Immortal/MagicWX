/**
 * ImageUpscaleClient - MagicWX 图片后端 /upscale 端点的二进制协议客户端。
 *
 * 功能：
 * - upscale(): 将 RGB 字节 POST 到 localhost:18081/upscale，读取 JPEG 响应并落盘，返回文件路径。
 *
 * 协议（与 magicwx_image_backend / local-dream registerUpscaleEndpoint 对齐）：
 * - 请求体：原始 RGB 字节（width*height*3），不经过 base64。
 * - 请求头：X-Image-Width / X-Image-Height / X-Upscaler-Path / X-Use-OpenCL(可选)。
 * - 响应体：JPEG 字节；响应头：X-Output-Width / X-Output-Height。
 */
package com.qihao.open.rwkv.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** /upscale 请求参数（持有 RGB 字节，故不使用 data class 以避免 ByteArray 等比较告警） */
class ImageUpscaleRequest(
    val rgbBytes: ByteArray,           // 原始 RGB 像素字节（width*height*3）
    val width: Int,                    // 输入图宽度
    val height: Int,                   // 输入图高度
    val upscalerPath: String,          // upscaler MNN 模型绝对路径
    val useOpencl: Boolean = false     // 是否启用 OpenCL（MNN GPU 路径）
)

/** /upscale 响应结果 */
data class ImageUpscaleResult(
    val outputPath: String,           // 落盘后的 JPEG 绝对路径
    val outputWidth: Int,             // 输出图宽度（4x 输入）
    val outputHeight: Int             // 输出图高度（4x 输入）
)

/** 调用 MagicWX 图片后端 /upscale 的二进制客户端 */
object ImageUpscaleClient {
    private const val TAG = "ImageUpscaleClient"

    /**
     * 调用 /upscale：POST RGB 字节，读回 JPEG 并写入 outputFile，返回结果。
     * 内部已切到 Dispatchers.IO，可在任意协程上下文挂起调用。
     */
    suspend fun upscale(request: ImageUpscaleRequest, outputFile: File): ImageUpscaleResult =
        withContext(Dispatchers.IO) {
            require(request.rgbBytes.size == request.width * request.height * 3) {
                "RGB 字节数 ${request.rgbBytes.size} 与 ${request.width}x${request.height}x3 不匹配"
            }
            val connection = (URL("http://127.0.0.1:${ImageBackendService.PORT}/upscale")
                .openConnection() as HttpURLConnection)
            try {
                connection.connectTimeout = 30_000               // 放大较慢，连接超时放宽
                connection.readTimeout = 60 * 60 * 1000          // 与 /generate 一致，长时操作
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setRequestProperty("X-Image-Width", request.width.toString())
                connection.setRequestProperty("X-Image-Height", request.height.toString())
                connection.setRequestProperty("X-Upscaler-Path", request.upscalerPath)
                if (request.useOpencl) {
                    connection.setRequestProperty("X-Use-OpenCL", "true")  // 仅在启用时下发
                }
                // 写入原始 RGB 字节作为请求体（不经 base64，最大化吞吐）
                connection.outputStream.use { it.write(request.rgbBytes) }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error("图片后端 /upscale 请求失败: HTTP $responseCode $errorText")
                }
                // 读取 JPEG 响应字节并落盘
                val jpegBytes = connection.inputStream.use { it.readBytes() }
                outputFile.outputStream().use { it.write(jpegBytes) }
                require(outputFile.isFile && outputFile.length() > 0L) {
                    "未能写入放大图片到 ${outputFile.absolutePath}"
                }
                // 响应头缺失时回退到输入宽高的 4x
                val outWidth = connection.getHeaderFieldInt("X-Output-Width", request.width * 4)
                val outHeight = connection.getHeaderFieldInt("X-Output-Height", request.height * 4)
                Log.i(TAG, "放大完成: ${request.width}x${request.height} -> ${outWidth}x${outHeight}, " +
                        "JPEG=${jpegBytes.size / 1024}KB, 保存到 ${outputFile.absolutePath}")
                ImageUpscaleResult(
                    outputPath = outputFile.absolutePath,
                    outputWidth = outWidth,
                    outputHeight = outHeight
                )
            } finally {
                connection.disconnect()
            }
        }
}

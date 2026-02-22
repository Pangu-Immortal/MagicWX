/**
 * ModelDownloader - 多模型文件下载器
 *
 * 功能：
 * - downloadModel(): 根据 ModelInfo 下载 ONNX 模型文件（含伴随数据文件）
 * - isModelReady(): 检查指定模型文件是否已下载
 * - getModelPath(): 获取指定模型的文件路径
 * - deleteModel(): 删除指定模型文件
 * - getDownloadedModels(): 获取所有已下载模型的 ID 列表
 * - migrateOldModel(): 迁移旧版单模型目录到新多模型目录结构
 *
 * 目录结构：
 *   context.filesDir/models/{modelId}/{originalFilename}
 *   context.filesDir/models/{modelId}/{originalFilename}_data （部分模型需要）
 *
 * 关键改进：
 * - 保留模型文件原始名称（如 model_q4.onnx），避免 ONNX 外部数据引用断裂
 * - 自动检测并下载伴随的 _data 数据文件
 * - 网络异常自动重试（最多3次，指数退避）
 * - 增大读写缓冲区到 64KB 提升下载速度
 */
package com.qihao.open.rwkv.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val MODELS_DIR = "models"              // 多模型根目录
        private const val TEMP_SUFFIX = ".downloading"        // 下载临时后缀
        private const val BUFFER_SIZE = 64 * 1024             // 读写缓冲区 64KB（提升下载速度）
        private const val OLD_MODEL_DIR = "model"             // 旧版单模型目录（用于迁移）
        private const val OLD_MODEL_FILE = "model.onnx"       // 旧版模型文件名
        private const val MAX_RETRIES = 5                     // 最大重试次数（大文件需要更多）
        private const val RETRY_DELAY_MS = 2000L              // 重试间隔基数（毫秒）
    }

    // 多模型根目录
    private val modelsRoot: File get() = File(context.filesDir, MODELS_DIR)

    init {
        migrateOldModel() // 启动时检查并迁移旧版模型
    }

    /**
     * 获取指定模型的存储目录
     * @param modelId 模型唯一标识
     */
    private fun getModelDir(modelId: String): File = File(modelsRoot, modelId)

    /**
     * 从下载 URL 中提取原始文件名
     * 例: "https://hf-mirror.com/.../model_q4.onnx" → "model_q4.onnx"
     */
    private fun extractFilename(url: String): String {
        return try {
            val path = URL(url).path                          // 提取 URL 路径部分
            val name = path.substringAfterLast("/")           // 取最后一段作为文件名
            if (name.isNotEmpty() && name.contains(".")) name else OLD_MODEL_FILE
        } catch (e: Exception) {
            OLD_MODEL_FILE                                     // 解析失败回退为默认名
        }
    }

    /**
     * 在模型目录中查找 ONNX 模型文件
     * 支持任意名称的 .onnx 文件（如 model.onnx、model_q4.onnx）
     * @param modelId 模型唯一标识
     * @return 找到的 ONNX 文件，未找到返回 null
     */
    private fun findOnnxFile(modelId: String): File? {
        val dir = getModelDir(modelId)
        if (!dir.exists()) return null
        return dir.listFiles()?.firstOrNull {                 // 查找第一个 .onnx 文件
            it.extension == "onnx" && !it.name.contains(TEMP_SUFFIX)
        }
    }

    /**
     * 获取指定模型的文件路径
     * @param modelId 模型唯一标识
     * @return 模型文件绝对路径
     */
    fun getModelPath(modelId: String): String {
        return findOnnxFile(modelId)?.absolutePath             // 动态查找实际文件
            ?: File(getModelDir(modelId), OLD_MODEL_FILE).absolutePath // 回退路径
    }

    /**
     * 获取指定模型的 tokenizer.json 文件路径
     * @param modelId 模型唯一标识
     * @return tokenizer.json 绝对路径，不存在返回 null
     */
    fun getTokenizerPath(modelId: String): String? {
        val file = File(getModelDir(modelId), "tokenizer.json")
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * 检查指定模型是否已下载完成
     * @param modelId 模型唯一标识
     */
    fun isModelReady(modelId: String): Boolean = findOnnxFile(modelId) != null

    /**
     * 获取所有已下载模型的 ID 列表
     */
    fun getDownloadedModels(): Set<String> {
        val root = modelsRoot
        if (!root.exists()) return emptySet()
        return root.listFiles()
            ?.filter { it.isDirectory && findOnnxFile(it.name) != null } // 有 .onnx 文件的目录
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * 删除指定模型的所有文件（模型 + 数据 + 临时）
     * @param modelId 模型唯一标识
     */
    fun deleteModel(modelId: String) {
        val dir = getModelDir(modelId)
        dir.listFiles()?.forEach { it.delete() }              // 删除目录下所有文件
        dir.delete()                                           // 删除空目录
        Log.d(TAG, "模型文件已删除: $modelId")
    }

    /**
     * 迁移旧版单模型目录到新多模型目录结构
     * 旧路径: filesDir/model/model.onnx → 新路径: filesDir/models/rwkv7-world-0.4b/model.onnx
     */
    private fun migrateOldModel() {
        val oldDir = File(context.filesDir, OLD_MODEL_DIR)     // 旧目录
        val oldFile = File(oldDir, OLD_MODEL_FILE)             // 旧模型文件
        if (!oldFile.exists()) return                          // 无旧文件则跳过

        val defaultId = ModelRegistry.getDefault().id          // 默认模型 ID
        val newDir = getModelDir(defaultId)                    // 新目录

        if (findOnnxFile(defaultId) != null) {
            // 新目录已有模型，删除旧文件
            oldFile.delete()
            oldDir.delete()
            Log.d(TAG, "旧模型已迁移过，删除旧文件")
            return
        }

        // 移动文件到新目录
        newDir.mkdirs()
        if (oldFile.renameTo(File(newDir, OLD_MODEL_FILE))) {
            oldDir.delete()                                    // 删除旧空目录
            Log.d(TAG, "旧模型迁移完成: $OLD_MODEL_DIR → $MODELS_DIR/$defaultId")
        } else {
            Log.e(TAG, "旧模型迁移失败")
        }
    }

    /**
     * 下载指定模型，支持断点续传、重试和伴随数据文件
     *
     * 流程：
     * 1. 下载主 .onnx 文件（保留原始文件名）
     * 2. 尝试下载伴随 _data 文件（部分 ONNX 模型需要外部数据）
     * 3. 合并两阶段进度上报
     *
     * @param modelInfo 模型元信息
     * @param onProgress 下载进度回调 (已下载字节, 总字节, 进度百分比 0~100)
     * @return 下载是否成功
     */
    suspend fun downloadModel(
        modelInfo: ModelInfo,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = getModelDir(modelInfo.id)
            modelDir.mkdirs()                                  // 确保目录存在

            // 提取原始文件名（保留原名避免 ONNX 外部数据引用断裂）
            val filename = extractFilename(modelInfo.downloadUrl)
            val targetFile = File(modelDir, filename)

            // 已经下载完成则跳过
            if (findOnnxFile(modelInfo.id) != null) {
                Log.d(TAG, "模型已存在: ${modelInfo.id}")
                val existingFile = findOnnxFile(modelInfo.id)!!
                onProgress(existingFile.length(), existingFile.length(), 100)
                return@withContext true
            }

            Log.d(TAG, "开始下载模型: ${modelInfo.id} → ${modelInfo.downloadUrl}")

            // ---- 第一阶段：下载主模型文件 ----
            val mainSuccess = downloadSingleFile(
                url = modelInfo.downloadUrl,
                targetFile = targetFile,
                onProgress = onProgress                        // 直接透传进度
            )

            if (!mainSuccess) {
                Log.e(TAG, "主模型文件下载失败: ${modelInfo.id}")
                return@withContext false
            }

            // ---- 第二阶段：尝试下载伴随数据文件 ----
            val dataUrl = "${modelInfo.downloadUrl}_data"       // 数据文件 URL = 模型URL + "_data"
            val dataFilename = "${filename}_data"               // 数据文件名 = 模型文件名 + "_data"
            val dataTargetFile = File(modelDir, dataFilename)

            Log.d(TAG, "尝试下载伴随数据文件: $dataFilename")
            val mainFileSize = targetFile.length()             // 主文件大小

            val dataSuccess = downloadSingleFile(
                url = dataUrl,
                targetFile = dataTargetFile,
                optional = true,                               // 可选文件，404不算失败
                onProgress = { downloaded, total, _ ->
                    // 合并进度：主文件大小 + 数据文件进度
                    val combinedTotal = mainFileSize + total
                    val combinedDownloaded = mainFileSize + downloaded
                    val percent = if (combinedTotal > 0) {
                        (combinedDownloaded * 100 / combinedTotal).toInt()
                    } else 100
                    onProgress(combinedDownloaded, combinedTotal, percent)
                }
            )

            if (dataSuccess) {
                Log.d(TAG, "伴随数据文件下载完成: $dataFilename (${dataTargetFile.length() / 1024 / 1024}MB)")
            } else {
                Log.d(TAG, "无伴随数据文件（单文件模型）")
            }

            // ---- 第三阶段：下载 tokenizer.json（Transformer 模型需要） ----
            if (modelInfo.tokenizerUrl != null) {
                val tokenizerFile = File(modelDir, "tokenizer.json")
                if (!tokenizerFile.exists()) {
                    Log.d(TAG, "下载分词器: tokenizer.json")
                    val tokenizerSuccess = downloadSingleFile(
                        url = modelInfo.tokenizerUrl,
                        targetFile = tokenizerFile,
                        optional = true                  // 可选，失败不阻塞模型使用
                    )
                    if (tokenizerSuccess) {
                        Log.d(TAG, "分词器下载完成: tokenizer.json (${tokenizerFile.length() / 1024}KB)")
                    } else {
                        Log.w(TAG, "分词器下载失败，模型仍可使用但分词可能不准确")
                    }
                }
            }

            Log.d(TAG, "模型下载完成: ${modelInfo.id} → ${targetFile.absolutePath}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "下载异常 [${modelInfo.id}]: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 下载单个文件，支持断点续传和重试
     * @param url 文件下载地址
     * @param targetFile 目标文件
     * @param optional 是否为可选文件（true 时 404 返回 false 而非抛异常）
     * @param onProgress 进度回调
     * @return 是否下载成功
     */
    private fun downloadSingleFile(
        url: String,
        targetFile: File,
        optional: Boolean = false,
        onProgress: ((downloaded: Long, total: Long, percent: Int) -> Unit)? = null
    ): Boolean {
        if (targetFile.exists()) return true                   // 已存在直接返回

        val tempFile = File(targetFile.parent, "${targetFile.name}$TEMP_SUFFIX")

        for (retry in 0 until MAX_RETRIES) {
            try {
                val downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
                val connection = openConnection(url, downloadedBytes)
                val responseCode = connection.responseCode

                // 可选文件返回 404 不算错误
                if (optional && (responseCode == HttpURLConnection.HTTP_NOT_FOUND ||
                            responseCode == HttpURLConnection.HTTP_FORBIDDEN)
                ) {
                    connection.disconnect()
                    return false
                }

                // 校验响应码
                if (responseCode != HttpURLConnection.HTTP_OK &&
                    responseCode != HttpURLConnection.HTTP_PARTIAL
                ) {
                    Log.e(TAG, "HTTP $responseCode (尝试 ${retry + 1}/$MAX_RETRIES) → $url")
                    connection.disconnect()
                    if (retry < MAX_RETRIES - 1) {
                        Thread.sleep(RETRY_DELAY_MS * (retry + 1)) // 指数退避
                        continue
                    }
                    return false
                }

                // 计算总大小
                val contentLength = connection.contentLengthLong
                val totalSize = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    downloadedBytes + contentLength             // 断点续传：已下载 + 剩余
                } else {
                    contentLength                               // 全新下载
                }

                Log.d(TAG, "文件总大小: ${totalSize / 1024 / 1024}MB, 已下载: ${downloadedBytes / 1024 / 1024}MB")

                // 写入文件（追加模式用于断点续传）
                val append = responseCode == HttpURLConnection.HTTP_PARTIAL
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile, append).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var currentDownloaded = downloadedBytes
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            currentDownloaded += bytesRead

                            // 回调进度
                            if (onProgress != null) {
                                val percent = if (totalSize > 0) {
                                    (currentDownloaded * 100 / totalSize).toInt()
                                } else 0
                                onProgress(currentDownloaded, totalSize, percent)
                            }
                        }
                    }
                }

                connection.disconnect()

                // 下载完成，重命名为正式文件
                if (tempFile.renameTo(targetFile)) {
                    Log.d(TAG, "文件下载完成: ${targetFile.name} (${targetFile.length() / 1024 / 1024}MB)")
                    return true
                } else {
                    Log.e(TAG, "文件重命名失败: ${targetFile.name}")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载异常 (尝试 ${retry + 1}/$MAX_RETRIES): ${e.message}")
                if (retry < MAX_RETRIES - 1) {
                    Thread.sleep(RETRY_DELAY_MS * (retry + 1)) // 指数退避等待后重试
                } else {
                    if (optional) return false                 // 可选文件失败不抛异常
                    throw e
                }
            }
        }
        return false
    }

    /**
     * 打开 HTTP 连接，自动处理重定向和断点续传
     * @param urlStr 下载地址
     * @param downloadedBytes 已下载字节数（用于 Range 请求头）
     */
    private fun openConnection(urlStr: String, downloadedBytes: Long): HttpURLConnection {
        var url = URL(urlStr)
        var redirectCount = 0                                  // 防止无限重定向

        while (redirectCount < 10) {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000                       // 30 秒连接超时
            conn.readTimeout = 300_000                         // 5 分钟读取超时（大文件慢速网络）
            conn.instanceFollowRedirects = false               // 手动处理重定向（跨域需要）

            // 断点续传请求头
            if (downloadedBytes > 0) {
                conn.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }

            conn.connect()
            val code = conn.responseCode

            // 处理 3xx 重定向
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location != null) {
                    url = URL(url, location)                   // 支持相对和绝对 URL
                    redirectCount++
                    Log.d(TAG, "重定向到: $url")
                    continue
                }
            }

            return conn
        }

        throw RuntimeException("重定向次数过多 (>10)")
    }
}

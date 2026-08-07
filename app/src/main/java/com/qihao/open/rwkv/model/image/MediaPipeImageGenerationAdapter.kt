/**
 * MediaPipeImageGenerationAdapter - MediaPipe Image Generator 图片生成适配器
 *
 * 功能：
 * - MediaPipeImageGenerationEngine: 加载已解压的 MediaPipe Stable Diffusion 模型目录并生成 PNG
 * - MediaPipeImageGenerationAdapter: 将 RuntimeAdapterType.MEDIAPIPE_IMAGE_GENERATION 接入统一运行时分发
 */
package com.qihao.open.rwkv.model.image

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ImageGeneratorOptions
import com.qihao.open.rwkv.model.ModelCapability
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.adapter.ModelRuntimeAdapter
import com.qihao.open.rwkv.model.adapter.RuntimeLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** MediaPipe Image Generator 生图引擎，按模型选择后持有一个可复用 runtime 实例 */
class MediaPipeImageGenerationEngine(
    private val context: Context,
    private val modelDir: File
) : ImageGenerationEngine {

    companion object {
        private const val TAG = "MediaPipeImageEngine"
        private const val DEFAULT_ITERATIONS = 4
        private const val DEFAULT_SEED = 42
        private const val OUTPUT_DIR = "generated_images"
    }

    private var imageGenerator: ImageGenerator? = null          // MediaPipe native 资源，close 时统一释放

    /** 根据完整请求生成 PNG 图片，并保存到 App 私有目录供 Compose 预览 */
    override suspend fun generate(request: LocalDreamImageRequest): GeneratedImageResult = withContext(Dispatchers.IO) {
        val safeRequest = request.copy(prompt = request.prompt.trim())   // 旧 MediaPipe 路径也接受统一请求
        val validationError = safeRequest.validate()                     // 先做通用参数校验
        if (validationError.isNotBlank()) {
            return@withContext GeneratedImageResult("", 0L, validationError)
        }

        val startedAt = SystemClock.elapsedRealtime()
        try {
            val generator = ensureGenerator()                   // 首次生成时初始化 MediaPipe runtime
            val outputDir = File(context.filesDir, OUTPUT_DIR)
            outputDir.mkdirs()                                  // 输出目录不存在时创建
            val outputExtension = if (safeRequest.outputFormat == LocalDreamImageWireFormat.JPEG) "jpg" else "png"
            val outputFile = File(outputDir, "mediapipe_${System.currentTimeMillis()}.$outputExtension")

            val iterations = safeRequest.steps.coerceAtLeast(1)          // MediaPipe iterations 至少为 1
            val seed = safeRequest.seed.toInt()                          // MediaPipe 当前 API 使用 Int seed
            Log.d(TAG, "开始 MediaPipe 生图: modelDir=${modelDir.absolutePath}, iterations=$iterations")
            val result = generator.generate(safeRequest.prompt, iterations, seed)
            val bitmap = BitmapExtractor.extract(result.generatedImage())
            val compressFormat = if (safeRequest.outputFormat == LocalDreamImageWireFormat.JPEG) {
                Bitmap.CompressFormat.JPEG
            } else {
                Bitmap.CompressFormat.PNG
            }
            outputFile.outputStream().use { output ->
                bitmap.compress(compressFormat, 100, output)
            }

            val duration = SystemClock.elapsedRealtime() - startedAt
            if (!outputFile.isFile || outputFile.length() <= 0L) {
                Log.e(TAG, "MediaPipe 生图未生成有效文件: ${outputFile.absolutePath}")
                return@withContext GeneratedImageResult("", duration, "没有生成有效图片文件")
            }

            Log.d(TAG, "MediaPipe 生图完成: ${outputFile.absolutePath}, size=${outputFile.length()}, duration=${duration}ms")
            return@withContext GeneratedImageResult(outputFile.absolutePath, duration)
        } catch (error: Throwable) {
            val duration = SystemClock.elapsedRealtime() - startedAt
            Log.e(TAG, "MediaPipe 生图失败: ${error.message}", error)
            return@withContext GeneratedImageResult("", duration, error.message ?: "MediaPipe 生图失败")
        }
    }

    /** 懒加载 ImageGenerator，避免模型选择后立即占用大量内存 */
    private fun ensureGenerator(): ImageGenerator {
        val existing = imageGenerator
        if (existing != null) return existing
        val options = ImageGeneratorOptions.builder()
            .setImageGeneratorModelDirectory(modelDir.absolutePath)
            .build()
        val created = ImageGenerator.createFromOptions(context, options)
        imageGenerator = created
        Log.d(TAG, "MediaPipe ImageGenerator 初始化完成: ${modelDir.absolutePath}")
        return created
    }

    /** 释放 MediaPipe native runtime */
    override fun close() {
        runCatching {
            imageGenerator?.close()
            imageGenerator = null
            Log.d(TAG, "MediaPipe ImageGenerator 已关闭")
        }.onFailure { error ->
            Log.e(TAG, "MediaPipe ImageGenerator 关闭失败: ${error.message}", error)
        }
    }
}

/** MediaPipe 图片生成 adapter */
class MediaPipeImageGenerationAdapter : ModelRuntimeAdapter {
    companion object {
        private const val TAG = "MediaPipeImageAdapter"
    }

    override val adapterType: RuntimeAdapterType = RuntimeAdapterType.MEDIAPIPE_IMAGE_GENERATION

    override fun canLoad(modelInfo: ModelInfo): Boolean {
        return modelInfo.adapterAvailable &&
            modelInfo.adapterType == adapterType &&
            modelInfo.capability == ModelCapability.IMAGE_GENERATION
    }

    override suspend fun load(
        context: Context,
        modelInfo: ModelInfo,
        downloader: ModelDownloader
    ): RuntimeLoadResult {
        val readiness = downloader.getModelReadiness(modelInfo)
        if (!readiness.isReady) {
            return RuntimeLoadResult.Unsupported(readiness.reason)
        }
        val modelDir = downloader.getModelDirectory(modelInfo.id)
        Log.d(TAG, "MediaPipe 生图模型目录就绪: ${modelInfo.id}, dir=${modelDir.absolutePath}")
        return RuntimeLoadResult.Image(MediaPipeImageGenerationEngine(context.applicationContext, modelDir))
    }
}

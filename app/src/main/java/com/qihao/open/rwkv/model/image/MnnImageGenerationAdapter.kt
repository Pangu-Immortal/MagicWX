/**
 * MnnImageGenerationAdapter - MNN Stable Diffusion 1.5 图片生成适配器
 *
 * 功能：
 * - MnnImageGenerationEngine: 准备 tokenizer.mtok，并通过图片生成前台服务调用隔离 native 后端
 * - waitForServiceResult(): 订阅本次生图事件，返回完成、失败或取消结果
 * - MnnImageGenerationAdapter: 将 RuntimeAdapterType.MNN 接入统一模型运行时分发
 */
package com.qihao.open.rwkv.model.image

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.qihao.open.rwkv.model.ModelCapability
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.adapter.ModelRuntimeAdapter
import com.qihao.open.rwkv.model.adapter.RuntimeLoadResult
import com.qihao.open.rwkv.service.ImageGenerationEventType
import com.qihao.open.rwkv.service.ImageGenerationEvents
import com.qihao.open.rwkv.service.ImageGenerationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import java.io.File

/** MNN SD1.5 生图引擎，单次生成通过服务和 native 后端进程隔离执行 */
class MnnImageGenerationEngine(
    private val context: Context,
    private val modelId: String,
    private val modelDir: File
) : ImageGenerationEngine {

    companion object {
        private const val TAG = "MnnImageGenerationEngine"
        private const val TOKENIZER_ASSET = "mnn_sd15_tokenizer/tokenizer.mtok"
        private const val TOKENIZER_FILE = "tokenizer.mtok"
        private const val GENERATION_TIMEOUT_MS = 2 * 60 * 60 * 1000L
    }

    /** 根据完整请求启动后台生图服务，最终返回生成图片路径或错误原因 */
    override suspend fun generate(request: LocalDreamImageRequest): GeneratedImageResult = withContext(Dispatchers.IO) {
        val safeRequest = request.copy(prompt = request.prompt.trim())   // 只裁剪 prompt 外层空白，不改用户参数
        val validationError = safeRequest.validate()                     // Kotlin 层先拦截无效请求
        if (validationError.isNotBlank()) {
            return@withContext GeneratedImageResult("", 0L, validationError)
        }

        try {
            val runtimeStatus = ImageInferenceBackendPlanner.currentStableDiffusionRuntimeStatus(context)
            if (!runtimeStatus.canRun) {
                Log.w(TAG, "拦截 MNN SD1.5 生图请求: ${runtimeStatus.reason}")
                return@withContext GeneratedImageResult("", 0L, runtimeStatus.reason)
            }

            ensureTokenizerFile()                              // 后端进程仍需要 tokenizer 文件在模型目录
            val startedAt = SystemClock.elapsedRealtime()      // 本地兜底计时，避免后端未返回耗时
            val backendChoice = ImageInferenceBackendPlanner.currentStableDiffusionChoice(context)
            Log.d(TAG, "启动隔离后端生图: modelId=$modelId, modelDir=${modelDir.absolutePath}, backend=${backendChoice.backend.displayName}, reason=${backendChoice.reason}")

            // 先订阅终态事件再启动服务，避免极快失败事件丢失（共享事件总线助手）
            val finalEvent = ImageGenerationEvents.awaitTerminalEvent(
                modelId = modelId,
                prompt = safeRequest.prompt,
                timeoutMillis = GENERATION_TIMEOUT_MS
            ) {
                ContextCompat.startForegroundService(
                    context,
                    ImageGenerationService.createStartIntent(context, modelId, safeRequest)
                )
            }
            val duration = finalEvent.durationMillis.takeIf { it > 0L } ?: (SystemClock.elapsedRealtime() - startedAt)

            if (finalEvent.type != ImageGenerationEventType.COMPLETED) {
                val message = finalEvent.message.ifBlank { "隔离后端生图失败" }
                Log.e(TAG, "隔离后端生图失败: $message")
                return@withContext GeneratedImageResult("", duration, message)
            }

            val outputFile = File(finalEvent.outputPath)
            if (!outputFile.isFile || outputFile.length() <= 0L) {
                Log.e(TAG, "隔离后端没有生成有效文件: ${outputFile.absolutePath}")
                return@withContext GeneratedImageResult("", duration, "没有生成有效图片文件")
            }

            Log.d(TAG, "隔离后端生图完成: ${outputFile.absolutePath}, size=${outputFile.length()}, duration=${duration}ms")
            return@withContext GeneratedImageResult(outputFile.absolutePath, duration)
        } catch (error: TimeoutCancellationException) {
            Log.e(TAG, "隔离后端生图超时: ${error.message}", error)
            context.startService(ImageGenerationService.createStopIntent(context))
            return@withContext GeneratedImageResult("", 0L, "生图超过 2 小时仍未完成，已停止后台任务")
        } catch (error: Throwable) {
            Log.e(TAG, "隔离后端生图异常: ${error.message}", error)
            return@withContext GeneratedImageResult("", 0L, error.message ?: "隔离后端生图异常")
        }
    }

    /** 将随 APK 内置的 tokenizer.mtok 复制到模型目录，MNN native 要求文件位于 resource root */
    private fun ensureTokenizerFile() {
        val target = File(modelDir, TOKENIZER_FILE)
        if (target.isFile && target.length() > 0L) return       // 已存在时不重复复制
        modelDir.mkdirs()
        context.assets.open(TOKENIZER_ASSET).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "已复制 MNN tokenizer: ${target.absolutePath}, size=${target.length()}")
    }

    override fun close() {
        Log.d(TAG, "MNN 生图引擎关闭；隔离后端由 ImageBackendService 管理")
    }
}

/** MNN 图片生成 adapter */
class MnnImageGenerationAdapter : ModelRuntimeAdapter {
    companion object {
        private const val TAG = "MnnImageGenerationAdapter"
    }

    override val adapterType: RuntimeAdapterType = RuntimeAdapterType.MNN

    override fun canLoad(modelInfo: ModelInfo): Boolean {
        return modelInfo.adapterAvailable &&
            modelInfo.adapterType == RuntimeAdapterType.MNN &&
            modelInfo.capability == ModelCapability.IMAGE_GENERATION
    }

    override suspend fun load(
        context: Context,
        modelInfo: ModelInfo,
        downloader: ModelDownloader
    ): RuntimeLoadResult {
        val runtimeStatus = ImageInferenceBackendPlanner.currentStableDiffusionRuntimeStatus(context)
        if (!runtimeStatus.canRun) {
            Log.w(TAG, "MNN 生图模型被运行时门禁拦截: ${modelInfo.id}, reason=${runtimeStatus.reason}")
            return RuntimeLoadResult.Unsupported(runtimeStatus.reason)
        }
        val readiness = downloader.getModelReadiness(modelInfo)
        if (!readiness.isReady) {
            return RuntimeLoadResult.Unsupported(readiness.reason)
        }
        val modelDir = downloader.getModelDirectory(modelInfo.id)
        Log.d(TAG, "MNN 生图模型加载完成: ${modelInfo.id}, dir=${modelDir.absolutePath}")
        return RuntimeLoadResult.Image(MnnImageGenerationEngine(context.applicationContext, modelInfo.id, modelDir))
    }
}

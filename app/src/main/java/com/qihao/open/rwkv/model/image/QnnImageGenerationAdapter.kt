/**
 * QnnImageGenerationAdapter - QNN/NPU 图片生成适配器
 *
 * 功能：
 * - QnnImageGenerationEngine: 通过图片生成前台服务调用隔离 native 后端，
 *   管线类型（sd15npu/sdxl/anima）由 Service 层按 ModelInfo.imageBackendType 下发
 * - QnnImageGenerationAdapter: 将 RuntimeAdapterType.QNN_IMAGE_GENERATION 接入统一模型运行时分发
 *
 * 设计要点：
 * - 与 MnnImageGenerationAdapter 同为"隔离后端进程"路线，复用 ImageGenerationService +
 *   ImageBackendService 链路；QNN 包自带 tokenizer.json，无需复制 MNN tokenizer 资产
 * - 当前构建未集成 QNN SDK（CPU-only），生成能力由 ImageGenerationService 的生成前门禁
 *   统一拦截并明确报错"该模型需要 QNN 运行时支持"；QNN SDK 集成后该门禁自动失效
 * - 超分模型（IMAGE_UPSCALING）不是生成模型：下载校验可过，但不进入生图页
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

/** QNN SD1.5/SDXL/Anima 生图引擎，单次生成通过服务和 native 后端进程隔离执行 */
class QnnImageGenerationEngine(
    private val context: Context,
    private val modelId: String,
    private val modelDir: File
) : ImageGenerationEngine {

    companion object {
        private const val TAG = "QnnImageGenerationEngine"

        /** 与 MNN 引擎一致的 2 小时兜底超时：QNN 单图更快，但批量/大图仍保留长窗口 */
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
            val startedAt = SystemClock.elapsedRealtime()                // 本地兜底计时，避免后端未返回耗时
            Log.d(TAG, "启动 QNN 隔离后端生图: modelId=$modelId, modelDir=${modelDir.absolutePath}, soc=${DeviceSocCapability.describeGate()}")

            // 先订阅终态事件再启动服务；当前 CPU-only 构建下 QNN 模型会在 Service 层
            // 生成前门禁处明确报错"该模型需要 QNN 运行时支持"，事件经 FAILED 返回
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
                val message = finalEvent.message.ifBlank { "QNN 隔离后端生图失败" }
                Log.e(TAG, "QNN 隔离后端生图失败: $message")
                return@withContext GeneratedImageResult("", duration, message)
            }

            val outputFile = File(finalEvent.outputPath)
            if (!outputFile.isFile || outputFile.length() <= 0L) {
                Log.e(TAG, "QNN 隔离后端没有生成有效文件: ${outputFile.absolutePath}")
                return@withContext GeneratedImageResult("", duration, "没有生成有效图片文件")
            }

            Log.d(TAG, "QNN 隔离后端生图完成: ${outputFile.absolutePath}, size=${outputFile.length()}, duration=${duration}ms")
            return@withContext GeneratedImageResult(outputFile.absolutePath, duration)
        } catch (error: TimeoutCancellationException) {
            Log.e(TAG, "QNN 隔离后端生图超时: ${error.message}", error)
            context.startService(ImageGenerationService.createStopIntent(context))
            return@withContext GeneratedImageResult("", 0L, "生图超过 2 小时仍未完成，已停止后台任务")
        } catch (error: Throwable) {
            Log.e(TAG, "QNN 隔离后端生图异常: ${error.message}", error)
            return@withContext GeneratedImageResult("", 0L, error.message ?: "QNN 隔离后端生图异常")
        }
    }

    override fun close() {
        Log.d(TAG, "QNN 生图引擎关闭；隔离后端由 ImageBackendService 管理")
    }
}

/** QNN/NPU 图片生成 adapter */
class QnnImageGenerationAdapter : ModelRuntimeAdapter {
    companion object {
        private const val TAG = "QnnImageGenerationAdapter"
    }

    override val adapterType: RuntimeAdapterType = RuntimeAdapterType.QNN_IMAGE_GENERATION

    override fun canLoad(modelInfo: ModelInfo): Boolean {
        return modelInfo.adapterAvailable &&
            modelInfo.adapterType == RuntimeAdapterType.QNN_IMAGE_GENERATION &&
            modelInfo.capability == ModelCapability.IMAGE_GENERATION
    }

    override suspend fun load(
        context: Context,
        modelInfo: ModelInfo,
        downloader: ModelDownloader
    ): RuntimeLoadResult {
        // 设备门禁双保险：注册表已按 SoC 判定 adapterAvailable，这里复核防止外部构造绕过
        if (!DeviceSocCapability.qnnSupported()) {
            Log.w(TAG, "QNN 生图模型被设备门禁拦截: ${modelInfo.id}, ${DeviceSocCapability.describeGate()}")
            return RuntimeLoadResult.Unsupported(DeviceSocCapability.REASON_NO_QNN)
        }
        // 超分模型走生图页 /upscale 入口，不作为生成模型加载进生图页
        if (modelInfo.capability == ModelCapability.IMAGE_UPSCALING) {
            Log.d(TAG, "超分模型不进入生图页: ${modelInfo.id}")
            return RuntimeLoadResult.Unsupported(
                "超分模型已下载：生成图片后请在生图页使用超分入口；QNN 超分执行将在 NPU 运行时集成后开放"
            )
        }
        val readiness = downloader.getModelReadiness(modelInfo)
        if (!readiness.isReady) {
            return RuntimeLoadResult.Unsupported(readiness.reason)
        }
        val modelDir = downloader.getRuntimeDirectory(modelInfo)         // zip 可能带顶层文件夹，解析真实运行时目录
        Log.d(TAG, "QNN 生图模型加载完成: ${modelInfo.id}, dir=${modelDir.absolutePath}")
        return RuntimeLoadResult.Image(QnnImageGenerationEngine(context.applicationContext, modelInfo.id, modelDir))
    }
}

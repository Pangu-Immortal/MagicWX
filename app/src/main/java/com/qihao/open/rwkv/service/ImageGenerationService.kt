/**
 * ImageGenerationService - 后台图片生成前台服务
 *
 * 功能：
 * - createStartIntent(): 构造启动后台生图的 Intent
 * - onStartCommand(): 启动图片后端、调用 /generate SSE 并同步通知进度
 * - cancelActiveGeneration(): 断开 HTTP 连接并发布取消状态
 */
package com.qihao.open.rwkv.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.qihao.open.rwkv.MainActivity
import com.qihao.open.rwkv.R
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelRegistry
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.image.ImageInferenceBackend
import com.qihao.open.rwkv.model.image.ImageInferenceBackendPlanner
import com.qihao.open.rwkv.model.image.LocalDreamBackendProtocol
import com.qihao.open.rwkv.model.image.LocalDreamGenerationMode
import com.qihao.open.rwkv.model.image.LocalDreamImageRequest
import com.qihao.open.rwkv.model.image.LocalDreamImageWireFormat
import com.qihao.open.rwkv.model.image.QnnRuntimeAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 前台服务负责在后台执行本地图片生成并显示进度通知 */
class ImageGenerationService : Service() {

    companion object {
        private const val TAG = "ImageGenerationService"
        private const val ACTION_START = "com.qihao.open.rwkv.action.START_IMAGE_GENERATION"
        private const val ACTION_STOP = "com.qihao.open.rwkv.action.STOP_IMAGE_GENERATION"
        private const val EXTRA_MODEL_ID = "model_id"
        private const val EXTRA_PROMPT = "prompt"
        private const val EXTRA_NEGATIVE_PROMPT = "negative_prompt"
        private const val EXTRA_STEPS = "steps"
        private const val EXTRA_CFG = "cfg"
        private const val EXTRA_SEED = "seed"
        private const val EXTRA_WIDTH = "width"
        private const val EXTRA_HEIGHT = "height"
        private const val EXTRA_SCHEDULER = "scheduler"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_DENOISE_STRENGTH = "denoise_strength"
        private const val EXTRA_ASPECT_RATIO = "aspect_ratio"
        private const val EXTRA_SHOW_PROCESS = "show_diffusion_process"
        private const val EXTRA_SHOW_PROCESS_STRIDE = "show_diffusion_stride"
        private const val EXTRA_PREVIEW_FORMAT = "preview_format"
        private const val EXTRA_OUTPUT_FORMAT = "output_format"
        private const val EXTRA_IMAGE_BASE64 = "image_base64"
        private const val EXTRA_MASK_BASE64 = "mask_base64"
        private const val EXTRA_BATCH_COUNT = "batch_count"
        private const val EXTRA_ULTRAFIX_TILE_SIZE = "ultrafix_tile_size"
        private const val EXTRA_ULTRAFIX_STEPS = "ultrafix_steps"
        private const val EXTRA_ULTRAFIX_DENOISE_STEPS = "ultrafix_denoise_steps"
        private const val EXTRA_ULTRAFIX_QUALITY_DENOISE = "ultrafix_quality_denoise"
        private const val EXTRA_LOWRAM = "lowram"                          // SDXL/Anima 低内存模式
        private const val EXTRA_SEQ_DIT = "seq_dit"                        // Anima 序列化 DiT
        private const val EXTRA_PATCH = "patch"                            // 分辨率 patch 文件路径
        private const val CHANNEL_ID = "image_generation"
        private const val NOTIFICATION_ID = 2002
        private const val MEMORY_MODE_SAVING = 0

        /**
         * QNN 运行时库目录名（filesDir/qnn）：QNN SDK 集成后运行时库（libQnnHtp*.so /
         * HTP skel 等）解压到该目录，native 端经 --lib_dir 读取；当前 CPU-only 构建目录为空。
         */
        const val QNN_RUNTIME_DIR = "qnn"

        /** 构造启动生图任务的 Intent */
        fun createStartIntent(context: Context, modelId: String, prompt: String): Intent {
            return createStartIntent(context, modelId, LocalDreamImageRequest(prompt = prompt)) // 兼容旧调用
        }

        /** 构造启动生图任务的 Intent，完整携带 LocalDream 同类生成参数 */
        fun createStartIntent(context: Context, modelId: String, request: LocalDreamImageRequest): Intent {
            return Intent(context, ImageGenerationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_ID, modelId)
                putExtra(EXTRA_PROMPT, request.prompt)
                putExtra(EXTRA_NEGATIVE_PROMPT, request.negativePrompt)
                putExtra(EXTRA_STEPS, request.steps)
                putExtra(EXTRA_CFG, request.cfg)
                putExtra(EXTRA_SEED, request.seed)
                putExtra(EXTRA_WIDTH, request.width)
                putExtra(EXTRA_HEIGHT, request.height)
                putExtra(EXTRA_SCHEDULER, request.scheduler)
                putExtra(EXTRA_MODE, request.mode.wireValue)
                putExtra(EXTRA_DENOISE_STRENGTH, request.denoiseStrength)
                putExtra(EXTRA_ASPECT_RATIO, request.aspectRatio)
                putExtra(EXTRA_SHOW_PROCESS, request.showDiffusionProcess)
                putExtra(EXTRA_SHOW_PROCESS_STRIDE, request.showDiffusionStride)
                putExtra(EXTRA_PREVIEW_FORMAT, request.previewFormat.wireValue)
                putExtra(EXTRA_OUTPUT_FORMAT, request.outputFormat.wireValue)
                putExtra(EXTRA_IMAGE_BASE64, request.imageBase64)
                putExtra(EXTRA_MASK_BASE64, request.maskBase64)
                putExtra(EXTRA_BATCH_COUNT, request.batchCount)
                putExtra(EXTRA_ULTRAFIX_TILE_SIZE, request.ultrafixTileSize)
                putExtra(EXTRA_ULTRAFIX_STEPS, request.ultrafixSteps)
                putExtra(EXTRA_ULTRAFIX_DENOISE_STEPS, request.ultrafixDenoiseSteps)
                putExtra(EXTRA_ULTRAFIX_QUALITY_DENOISE, request.ultrafixQualityDenoise)
                putExtra(EXTRA_LOWRAM, request.lowram)
                putExtra(EXTRA_SEQ_DIT, request.seqDit)
                putExtra(EXTRA_PATCH, request.patch)
            }
        }

        /** 构造停止生图任务的 Intent */
        fun createStopIntent(context: Context): Intent {
            return Intent(context, ImageGenerationService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null                         // 当前生图协程
    private var activeConnection: HttpURLConnection? = null     // 当前 SSE HTTP 连接
    private var activeModelId: String = ""                     // 当前生图模型 ID
    private var activePrompt: String = ""                      // 当前提示词
    @Volatile
    private var completed = false                              // complete 已成功置位：后续流异常不得再发 FAILED 覆盖成功态

    override fun onBind(intent: Intent?): IBinder? = null

    /** 根据 Intent 启动或取消生图任务 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startGeneration(intent, startId)
            ACTION_STOP -> {
                cancelActiveGeneration("已停止")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * H3 健壮性兜底：specialUse FGS 仍受系统限时机制约束（厂商对 FGS 会话有累计时长配额），
     * 超时后系统先回调 onTimeout 再强杀服务。此处必须主动发 FAILED 并清理连接/协程，
     * 否则 UI 会永远停留在“生成中”。已 completed 时保持成功态，只清理退出。
     */
    override fun onTimeout(startId: Int) {
        Log.e(TAG, "specialUse FGS 限时到达，系统即将强杀生图服务: startId=$startId")
        if (!completed && activeModelId.isNotBlank()) {
            ImageGenerationEvents.publish(
                ImageGenerationEvent(
                    modelId = activeModelId,
                    type = ImageGenerationEventType.FAILED,
                    prompt = activePrompt,
                    message = "生图被系统限时终止，请重试"
                )
            )
        }
        activeConnection?.disconnect()                               // 断开 SSE，避免 native 侧挂起
        activeConnection = null
        activeJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    /** 启动一次后台生图 */
    private fun startGeneration(intent: Intent, startId: Int) {
        val modelId = intent.getStringExtra(EXTRA_MODEL_ID).orEmpty()
        val request = imageRequestFromIntent(intent)              // 从 Intent 还原完整生成参数
        val prompt = request.prompt                               // 事件总线仍用 prompt 显示当前任务
        val modelInfo = ModelRegistry.findById(modelId)
        val validationError = request.validate()                  // Service 层再次校验，防止外部错误参数
        if (modelInfo == null || validationError.isNotBlank()) {
            Log.e(TAG, "后台生图启动失败，参数非法: modelId=$modelId validation=$validationError")
            stopSelf(startId)
            return
        }

        activeJob?.cancel()
        activeModelId = modelId
        activePrompt = prompt
        completed = false                                          // 新一轮生成重置竞态保护标记
        createNotificationChannel()
        startForegroundCompat(buildNotification("准备生成图片", 0))
        ImageGenerationEvents.publish(
            ImageGenerationEvent(modelId, ImageGenerationEventType.STARTED, prompt = prompt, message = "准备生成图片")
        )

        activeJob = serviceScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val result = runCatching {
                val downloader = ModelDownloader(applicationContext)
                // zip 解压可能带顶层文件夹（如 AnythingV5/），native 要求文件直接位于 --model_dir 下
                val modelDir = downloader.getRuntimeDirectory(modelInfo)
                // 管线分发：按模型族下发 sd15cpu/sd15npu/sdxl/anima，对齐 native --type 全管线分发
                val pipelineType = modelInfo.imageBackendType.ifBlank { "sd15cpu" }
                require(
                    modelInfo.adapterType == RuntimeAdapterType.MNN ||
                        modelInfo.adapterType == RuntimeAdapterType.QNN_IMAGE_GENERATION
                ) { "当前后台生图服务仅承载 MNN / QNN 隔离后端" }
                require(downloader.getModelReadiness(modelInfo).isReady) {
                    downloader.getModelReadiness(modelInfo).reason
                }

                // ---- Service 层生成前门禁：QNN 管线在 CPU-only 构建下明确报错 ----
                // QNN SDK 尚未集成，QNN 族模型（sd15npu/sdxl/anima/upscaler）发起生成必须得到
                // 明确的"需要 QNN 运行时支持"错误，而不是 native 晦涩崩溃。
                // SDK 集成后 canRunQnnPipeline() 置 true，本门禁自动失效（届时可整体移除）。
                if (QnnRuntimeAvailability.isQnnPipeline(modelInfo.imageBackendType)) {
                    require(QnnRuntimeAvailability.canRunQnnPipeline()) {
                        QnnRuntimeAvailability.QNN_RUNTIME_REQUIRED_MESSAGE
                    }
                }

                val useOpencl: Boolean
                if (modelInfo.adapterType == RuntimeAdapterType.MNN) {
                    val backendChoice = ImageInferenceBackendPlanner.currentStableDiffusionChoice(applicationContext)
                    val runtimeStatus = ImageInferenceBackendPlanner.stableDiffusionRuntimeStatus(backendChoice)
                    require(runtimeStatus.canRun) { runtimeStatus.reason }
                    // Phase 1 CPU 管线尺寸门禁：UNet attention 内存随序列长度平方增长，
                    // 768×768（序列 9216，约 5 倍于 512）在 8GB 中端机实测杀死后端进程；
                    // local-dream CPU 默认同为 512。更大尺寸随 NPU/SDXL 阶段开放。
                    // UltraFix 例外：它的语义就是对用户导入的大图做 tiled 修复（tile 内仍是
                    // 512 级显存占用），width/height 必须等于输入图实际尺寸，因此放行 >512；
                    // 2048 上限是防呆门禁，超过该边长的输入图应在导入裁剪阶段就降采样。
                    if (request.mode == LocalDreamGenerationMode.ULTRAFIX) {
                        require(request.width <= 2048 && request.height <= 2048) {
                            "UltraFix 输入图最大支持 2048×2048（当前 ${request.width}×${request.height}）"
                        }
                    } else {
                        require(request.width <= 512 && request.height <= 512) {
                            "CPU 生图当前仅支持 512×512 及以下尺寸（当前 ${request.width}×${request.height}），更大尺寸将在 NPU 阶段开放"
                        }
                    }
                    useOpencl = backendChoice.backend == ImageInferenceBackend.OPENCL // /generate 的 use_opencl
                } else {
                    // QNN 管线不走 MNN OpenCL 开关；尺寸能力（1024 级）待 SDK 集成后按管线开放
                    useOpencl = false
                }

                // QNN 运行时目录：QNN 族管线经 --lib_dir 下发给 native；CPU 管线传空不下发
                val libDir = if (QnnRuntimeAvailability.isQnnPipeline(modelInfo.imageBackendType)) {
                    File(filesDir, QNN_RUNTIME_DIR).apply { mkdirs() }.absolutePath
                } else {
                    ""
                }
                val backendIntent = ImageBackendService.createStartIntent(
                    applicationContext,
                    modelInfo.id,
                    modelDir.absolutePath,
                    pipelineType = pipelineType,
                    libDir = libDir,
                    lowram = request.lowram,
                    seqDit = request.seqDit,
                    patch = request.patch
                )
                ContextCompat.startForegroundService(applicationContext, backendIntent)
                waitForBackendHealth()

                val outputDir = File(filesDir, "generated_images")
                outputDir.mkdirs()
                val outputExtension = if (request.outputFormat == LocalDreamImageWireFormat.PNG) "png" else "jpg"
                val outputFile = File(outputDir, "sd15_${System.currentTimeMillis()}.$outputExtension")
                requestImageGeneration(
                    request = request,
                    outputFile = outputFile,
                    useOpencl = useOpencl,
                    startedAt = startedAt
                )
            }

            if (result.isFailure && !completed) {
                // M2 竞态保护：complete 已成功（COMPLETED 已发布、结果图已落盘）后，
                // Samsung 等厂商会在 FGS 会话中后段查杀本服务导致 SSE 流抛出 IOException；
                // 此时不得再发 FAILED 覆盖成功态，否则 UI/历史会把一次成功生成显示为失败。
                val message = result.exceptionOrNull()?.message ?: "生图失败"
                updateNotification(message, 0)
                ImageGenerationEvents.publish(
                    ImageGenerationEvent(
                        modelId = modelId,
                        type = ImageGenerationEventType.FAILED,
                        prompt = prompt,
                        durationMillis = SystemClock.elapsedRealtime() - startedAt,
                        message = message
                    )
                )
                Log.e(TAG, "后台生图失败: $message", result.exceptionOrNull())
            } else if (result.isFailure) {
                // 已 completed 后的流异常：只记录日志，成功态保持不动
                Log.w(TAG, "complete 后流异常被忽略（成功态保护）: ${result.exceptionOrNull()?.message}")
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    /**
     * 等待 ImageBackendService 启动的 localhost 后端可用。
     * 放宽到 60s：大模型（~1.2GB UNet+VAE+CLIP）首次加载需要数十秒，
     * 旧 15s 窗口在中端机上会误判后端启动失败（参照 ModelRunSupport.checkBackendHealth 的 60s 口径）。
     * 轮询从 100ms 起步指数退避到 500ms 封顶，加载后期无需高频打 /health。
     */
    private suspend fun waitForBackendHealth() {
        val deadline = SystemClock.elapsedRealtime() + 60_000L      // 60s 总窗口
        var pollDelay = 100L                                        // 首次 100ms 起步
        var attempt = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            attempt++
            if (isBackendHealthy()) return
            Log.d(TAG, "等待图片后端进入 ready: attempt=$attempt")
            delay(pollDelay)
            pollDelay = (pollDelay * 2).coerceAtMost(500L)          // 指数退避，500ms 封顶
        }
        error("图片后端 60 秒内未就绪，无法开始生图")
    }

    /** 检查后端 /health */
    private suspend fun isBackendHealthy(): Boolean = withContext(Dispatchers.IO) {
        val connection = (URL("http://127.0.0.1:${ImageBackendService.PORT}/health").openConnection() as HttpURLConnection)
        return@withContext try {
            connection.connectTimeout = 500
            connection.readTimeout = 500
            connection.requestMethod = "GET"
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (error: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    /** 从 Intent 还原 LocalDream 完整生图请求 */
    private fun imageRequestFromIntent(intent: Intent): LocalDreamImageRequest {
        return LocalDreamImageRequest(
            prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty().trim(),                  // prompt 必须裁剪外层空白
            negativePrompt = intent.getStringExtra(EXTRA_NEGATIVE_PROMPT).orEmpty(),        // 反向提示词允许为空
            steps = intent.getIntExtra(EXTRA_STEPS, 20),                                    // 默认与 LocalDream 全局参数一致
            cfg = intent.getFloatExtra(EXTRA_CFG, 7f),                                      // 默认 CFG=7
            seed = intent.getLongExtra(EXTRA_SEED, 42L),                                    // 默认固定 seed，便于复测
            width = intent.getIntExtra(EXTRA_WIDTH, 512),                                   // SD1.5 默认 512
            height = intent.getIntExtra(EXTRA_HEIGHT, 512),                                 // SD1.5 默认 512
            scheduler = intent.getStringExtra(EXTRA_SCHEDULER).orEmpty().ifBlank { "dpm" }, // 默认 DPM
            mode = generationModeFromWire(intent.getStringExtra(EXTRA_MODE)),               // 恢复生成模式
            denoiseStrength = intent.getFloatExtra(EXTRA_DENOISE_STRENGTH, 0.6f),           // 图生图默认强度
            aspectRatio = intent.getStringExtra(EXTRA_ASPECT_RATIO).orEmpty().ifBlank { "1:1" }, // 默认方图
            showDiffusionProcess = intent.getBooleanExtra(EXTRA_SHOW_PROCESS, false),       // 默认不传中间图
            showDiffusionStride = intent.getIntExtra(EXTRA_SHOW_PROCESS_STRIDE, 1),         // 默认每步可预览
            previewFormat = wireFormatFromWire(intent.getStringExtra(EXTRA_PREVIEW_FORMAT), LocalDreamImageWireFormat.RAW),
            outputFormat = wireFormatFromWire(intent.getStringExtra(EXTRA_OUTPUT_FORMAT), LocalDreamImageWireFormat.JPEG),
            imageBase64 = intent.getStringExtra(EXTRA_IMAGE_BASE64).orEmpty(),              // 图生图输入图
            maskBase64 = intent.getStringExtra(EXTRA_MASK_BASE64).orEmpty(),                // 局部重绘 mask
            batchCount = intent.getIntExtra(EXTRA_BATCH_COUNT, 1),                           // 默认单张生成
            ultrafixTileSize = intent.getIntExtra(EXTRA_ULTRAFIX_TILE_SIZE, 512),            // UltraFix 默认 512 tile
            ultrafixSteps = intent.getIntExtra(EXTRA_ULTRAFIX_STEPS, 10),                    // UltraFix 默认 10 步
            ultrafixDenoiseSteps = intent.getIntExtra(EXTRA_ULTRAFIX_DENOISE_STEPS, 4),      // UltraFix 默认 4 步降噪
            ultrafixQualityDenoise = intent.getBooleanExtra(EXTRA_ULTRAFIX_QUALITY_DENOISE, true), // 默认质量提示词修复
            lowram = intent.getBooleanExtra(EXTRA_LOWRAM, false),                                // SDXL/Anima 低内存模式
            seqDit = intent.getBooleanExtra(EXTRA_SEQ_DIT, false),                               // Anima 序列化 DiT
            patch = intent.getStringExtra(EXTRA_PATCH).orEmpty()                                 // 分辨率 patch 文件路径
        )
    }

    /** 将 wire 字符串映射为生成模式 */
    private fun generationModeFromWire(value: String?): LocalDreamGenerationMode {
        return LocalDreamGenerationMode.entries.firstOrNull { it.wireValue == value }       // 按 wireValue 匹配
            ?: LocalDreamGenerationMode.TEXT_TO_IMAGE                                       // 非法值回退文生图
    }

    /** 将 wire 字符串映射为图片格式 */
    private fun wireFormatFromWire(value: String?, fallback: LocalDreamImageWireFormat): LocalDreamImageWireFormat {
        return LocalDreamImageWireFormat.entries.firstOrNull { it.wireValue == value } ?: fallback // 非法值使用调用方默认
    }

    /** 调用 /generate 并逐行解析 SSE 事件 */
    private suspend fun requestImageGeneration(
        request: LocalDreamImageRequest,
        outputFile: File,
        useOpencl: Boolean,
        startedAt: Long
    ) = withContext(Dispatchers.IO) {
        val requestBody = LocalDreamBackendProtocol.buildGenerateJson(
            request = request,
            useOpencl = useOpencl
        )
        val connection = (URL("http://127.0.0.1:${ImageBackendService.PORT}/generate").openConnection() as HttpURLConnection)
        activeConnection = connection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 60 * 60 * 1000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.outputStream.use { stream ->
                stream.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("图片后端请求失败: HTTP $responseCode $errorText")
            }

            var currentEvent = ""
            connection.inputStream.bufferedReader().useLines { lines ->
                // 用 for 循环替代 forEach：complete 成功后需要立即终止读循环，
                // 避免后续流被厂商查杀时抛异常进入 FAILED 分支（M2 竞态保护）
                for (line in lines) {
                    when {
                        line.startsWith("event:") -> currentEvent = line.substringAfter("event:").trim()
                        line.startsWith("data:") -> {
                            val done = handleSseData(
                                event = currentEvent,
                                data = line.substringAfter("data:").trim(),
                                outputFile = outputFile,
                                startedAt = startedAt,
                                seed = request.seed                  // 传递本次生成 seed，供 COMPLETED 事件携带
                            )
                            if (done) break                          // complete 已闭环，主动结束 SSE 读取
                        }
                    }
                }
            }
        } finally {
            activeConnection = null
            connection.disconnect()
        }
    }

    /**
     * 根据 SSE data 更新 UI 事件和通知（与 local-dream SSE 协议对齐）。
     * @return true 表示生成已闭环，调用方应立即终止 SSE 读循环
     */
    private fun handleSseData(event: String, data: String, outputFile: File, startedAt: Long, seed: Long): Boolean {
        when (event) {
            "progress" -> {
                // native 发 step/total_steps（不是 0-100 的 progress），换算成百分比
                val step = extractIntField(data, "step", 0)
                val total = extractIntField(data, "total_steps", 0)
                val progress = if (total > 0) (step * 100 / total).coerceIn(0, 100) else 0
                // C2 中间预览：progress 事件可携带 base64 image（我方已请求 preview_format=jpeg）。
                // 解码后覆盖式写入固定预览文件，避免每步新建文件撑爆私有目录；
                // 解码/写盘失败只记录日志，进度事件本身不受影响。
                // 注意 CPU 管线 previewSupported=false 时 native 不发预览图，此分支自然不触发。
                val previewPath = writePreviewImage(data)
                updateNotification("正在本机生成图片：$progress%", progress)
                ImageGenerationEvents.publish(
                    ImageGenerationEvent(
                        modelId = activeModelId,
                        type = ImageGenerationEventType.PROGRESS,
                        prompt = activePrompt,
                        progress = progress,
                        previewPath = previewPath,
                        durationMillis = SystemClock.elapsedRealtime() - startedAt,
                        message = "正在生成 $progress%"
                    )
                )
            }
            "complete" -> {
                // native 返回 base64 image + format + generation_time_ms（不是 output_path）
                val duration = extractLongField(data, "generation_time_ms", SystemClock.elapsedRealtime() - startedAt)
                val imageBase64 = extractStringField(data, "image")
                val format = extractStringField(data, "format").ifBlank { "raw" }
                require(imageBase64.isNotBlank()) { "后端 complete 事件未携带 image 数据" }
                val imageBytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
                val written = writeResultImage(imageBytes, format, outputFile, data)
                require(written) { "未能写入生成图片到 ${outputFile.absolutePath}" }
                // 落盘后立即发布 COMPLETED：Samsung Android 16 会在 FGS 会话中后段强杀本服务，
                // 先发事件保证 Room 历史/UI 状态在通知更新前就已闭环
                ImageGenerationEvents.publish(
                    ImageGenerationEvent(
                        modelId = activeModelId,
                        type = ImageGenerationEventType.COMPLETED,
                        prompt = activePrompt,
                        progress = 100,
                        outputPath = outputFile.absolutePath,
                        durationMillis = duration,
                        message = "图片生成完成",
                        seed = seed                      // 携带本次生成真实 seed，供 Room 落库去重
                    )
                )
                updateNotification("图片生成完成", 100)
                completed = true                                     // M2：成功态置位，后续流异常不再发 FAILED
                return true                                          // 通知读循环终止
            }
            "error" -> {
                error(extractStringField(data, "message").ifBlank { "图片后端返回错误" })
            }
        }
        return false                                                 // 其余事件继续读流
    }

    /**
     * 解码 progress 事件中的中间预览图并覆盖式落盘，返回预览文件路径。
     * 固定写 filesDir/generated_images/.preview.jpg：每次覆盖同一文件，
     * UI 端按路径 + 文件 mtime 重新解码即可看到最新预览；无 image 字段时返回空串。
     */
    private fun writePreviewImage(data: String): String {
        val imageBase64 = extractStringField(data, "image")
        if (imageBase64.isBlank()) return ""                         // 本次进度未携带预览图
        return runCatching {
            val previewDir = File(filesDir, "generated_images")
            previewDir.mkdirs()
            val previewFile = File(previewDir, ".preview.jpg")       // 覆盖式，避免每步新文件
            val previewBytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
            previewFile.outputStream().use { it.write(previewBytes) }
            Log.d(TAG, "中间预览已落盘: ${previewFile.absolutePath} (${previewBytes.size} bytes)")
            previewFile.absolutePath
        }.getOrElse { error ->
            Log.w(TAG, "中间预览落盘失败（不影响生成）: ${error.message}")
            ""
        }
    }

    /**
     * 把后端返回的图片字节写入 outputFile。
     * - jpeg/png：native 已编码好，直接落盘
     * - raw：RGB 字节，按 width/height 还原成 Bitmap 再压成 JPEG（与 local-dream rgbBytesToPixels 一致）
     */
    private fun writeResultImage(imageBytes: ByteArray, format: String, outputFile: File, data: String): Boolean {
        return try {
            if (format == "raw") {
                val width = extractIntField(data, "width", 512)
                val height = extractIntField(data, "height", 512)
                val pixels = IntArray(width * height)
                val count = minOf(pixels.size, imageBytes.size / 3)
                for (i in 0 until count) {
                    val idx = i * 3
                    val r = imageBytes[idx].toInt() and 0xFF
                    val g = imageBytes[idx + 1].toInt() and 0xFF
                    val b = imageBytes[idx + 2].toInt() and 0xFF
                    pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                androidx.core.graphics.createBitmap(width, height).apply {
                    setPixels(pixels, 0, width, 0, 0, width, height)
                }.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outputFile.outputStream())
            } else {
                // jpeg/png：字节已是成品图，直接写入
                outputFile.outputStream().use { it.write(imageBytes) }
            }
            outputFile.isFile && outputFile.length() > 0L
        } catch (error: Exception) {
            Log.e(TAG, "写入生成图片失败: ${error.message}", error)
            false
        }
    }

    /** 从简单 JSON 中提取字符串字段 */
    private fun extractStringField(json: String, key: String): String {
        val marker = "\"$key\""
        val keyIndex = json.indexOf(marker)
        if (keyIndex < 0) return ""
        val colonIndex = json.indexOf(':', keyIndex + marker.length)
        if (colonIndex < 0) return ""
        val quoteIndex = json.indexOf('"', colonIndex + 1)
        if (quoteIndex < 0) return ""
        val builder = StringBuilder()
        var escaping = false
        for (index in quoteIndex + 1 until json.length) {
            val char = json[index]
            if (escaping) {
                builder.append(if (char == 'n') '\n' else char)
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else if (char == '"') {
                break
            } else {
                builder.append(char)
            }
        }
        return builder.toString()
    }

    /** 从简单 JSON 中提取 Int 字段 */
    private fun extractIntField(json: String, key: String, fallback: Int): Int {
        return extractNumberText(json, key)?.toIntOrNull() ?: fallback
    }

    /** 从简单 JSON 中提取 Long 字段 */
    private fun extractLongField(json: String, key: String, fallback: Long): Long {
        return extractNumberText(json, key)?.toLongOrNull() ?: fallback
    }

    /** 提取数字字段文本 */
    private fun extractNumberText(json: String, key: String): String? {
        val marker = "\"$key\""
        val keyIndex = json.indexOf(marker)
        if (keyIndex < 0) return null
        val colonIndex = json.indexOf(':', keyIndex + marker.length)
        if (colonIndex < 0) return null
        val start = json.indexOfAny(charArrayOf('-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'), colonIndex + 1)
        if (start < 0) return null
        val end = json.indexOfFirstAfter(start) { !it.isDigit() && it != '-' }
        return json.substring(start, end.takeIf { it > start } ?: json.length)
    }

    /** 查找满足条件的第一个字符位置 */
    private fun String.indexOfFirstAfter(start: Int, predicate: (Char) -> Boolean): Int {
        for (index in start until length) {
            if (predicate(this[index])) return index
        }
        return -1
    }

    /** 取消当前生图任务 */
    private fun cancelActiveGeneration(message: String) {
        Log.d(TAG, "取消后台生图: $message")
        activeConnection?.disconnect()
        activeConnection = null
        activeJob?.cancel()
        if (activeModelId.isNotBlank()) {
            ImageGenerationEvents.publish(
                ImageGenerationEvent(activeModelId, ImageGenerationEventType.CANCELLED, prompt = activePrompt, message = message)
            )
        }
    }

    override fun onDestroy() {
        cancelActiveGeneration("生图服务销毁")
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 使用 dataSync 前台服务类型承载用户可见本地生成任务 */
    /** 使用 specialUse 前台服务类型：Android 16 对 dataSync FGS 有累计时长强杀
     *  （实测 4-11 分钟，SD1.5 CPU 20 步生成会被中途杀死），specialUse 无该限时 */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** 更新常驻生图通知 */
    private fun updateNotification(text: String, progress: Int) {
        if (!canPostNotifications()) {
            Log.d(TAG, "通知权限未授予，生图状态仅写入日志: $text")
            return
        }
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(text, progress)
        )
    }

    /** 构建生图通知 */
    private fun buildNotification(text: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在生成图片")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(progress in 0 until 100)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()
    }

    /** 创建生图通知频道 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "图片生成", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示本地图片生成进度"
        }
        manager.createNotificationChannel(channel)
    }

    /** 判断是否允许显式更新通知栏内容 */
    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}

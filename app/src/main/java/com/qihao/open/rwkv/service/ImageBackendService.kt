/**
 * ImageBackendService - 隔离式图片后端前台服务
 *
 * 功能：
 * - createStartIntent(): 构造启动 native 图片后端进程的 Intent
 * - onStartCommand(): 启动前台服务、ProcessBuilder native executable 并执行 /health 检查
 * - stopBackendProcess(): 停止后端进程，避免 native 崩溃影响 App 主进程
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 前台服务负责持有隔离 native 图片后端进程 */
class ImageBackendService : Service() {

    companion object {
        private const val TAG = "ImageBackendService"
        private const val ACTION_START = "com.qihao.open.rwkv.action.START_IMAGE_BACKEND"
        private const val ACTION_STOP = "com.qihao.open.rwkv.action.STOP_IMAGE_BACKEND"
        private const val EXTRA_MODEL_ID = "model_id"
        private const val EXTRA_MODEL_DIR = "model_dir"
        private const val EXTRA_PIPELINE_TYPE = "pipeline_type"   // sd15cpu/sd15npu/sdxl/anima，与 local-dream --type 对齐
        private const val EXTRA_LIB_DIR = "lib_dir"               // QNN 运行时库目录，对齐 local-dream --lib_dir
        private const val EXTRA_NO_IMG2IMG = "no_img2img"        // 跳过 VAE encoder，阻断 img2img/inpaint
        private const val EXTRA_USE_V_PRED = "use_v_pred"        // v-prediction 模型
        private const val EXTRA_LOWRAM = "lowram"                 // SDXL/Anima 低内存模式
        private const val EXTRA_SEQ_DIT = "seq_dit"               // Anima 序列化 DiT
        private const val EXTRA_PATCH = "patch"                   // 分辨率 patch 文件路径
        private const val CHANNEL_ID = "image_backend"
        private const val NOTIFICATION_ID = 2001
        const val PORT = 18081

        /**
         * 构造启动图片后端的 Intent（与 local-dream BackendService --type 对齐）
         *
         * @param libDir QNN 运行时库目录（filesDir/qnn）；CPU 管线传空串，不下发 --lib_dir
         */
        fun createStartIntent(
            context: Context,
            modelId: String,
            modelDir: String,
            pipelineType: String,
            libDir: String = "",
            noImg2img: Boolean = false,
            useVPred: Boolean = false,
            lowram: Boolean = false,
            seqDit: Boolean = false,
            patch: String = ""
        ): Intent {
            return Intent(context, ImageBackendService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_ID, modelId)
                putExtra(EXTRA_MODEL_DIR, modelDir)
                putExtra(EXTRA_PIPELINE_TYPE, pipelineType)
                putExtra(EXTRA_LIB_DIR, libDir)
                putExtra(EXTRA_NO_IMG2IMG, noImg2img)
                putExtra(EXTRA_USE_V_PRED, useVPred)
                putExtra(EXTRA_LOWRAM, lowram)
                putExtra(EXTRA_SEQ_DIT, seqDit)
                putExtra(EXTRA_PATCH, patch)
            }
        }

        /** 构造停止图片后端的 Intent */
        fun createStopIntent(context: Context): Intent {
            return Intent(context, ImageBackendService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backendProcess: Process? = null                 // 当前 native 后端进程
    private var monitorJob: Job? = null                         // stdout/stderr 日志监控任务
    private var activeModelId: String = ""                      // 当前后端模型 ID
    private var activePipelineType: String = ""                 // 当前后端管线类型（sd15cpu/sd15npu/sdxl/anima）

    override fun onBind(intent: Intent?): IBinder? = null

    /** 根据 Intent 启动或停止图片后端 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBackend(intent, startId)
            ACTION_STOP -> {
                stopBackendProcess("用户请求停止图片后端")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * H3 健壮性兜底：specialUse FGS 仍受系统限时机制约束，超时后系统先回调 onTimeout 再强杀服务。
     * 此处主动停止 native 后端进程并发布 STOPPED，保证 UI 状态与通知及时闭环。
     */
    override fun onTimeout(startId: Int) {
        Log.e(TAG, "specialUse FGS 限时到达，系统即将强杀图片后端服务: startId=$startId")
        stopBackendProcess("图片后端被系统限时终止")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    /** 启动 native 后端进程并等待健康检查 */
    private fun startBackend(intent: Intent, startId: Int) {
        val modelId = intent.getStringExtra(EXTRA_MODEL_ID).orEmpty()
        val modelDir = intent.getStringExtra(EXTRA_MODEL_DIR).orEmpty()
        val pipelineType = intent.getStringExtra(EXTRA_PIPELINE_TYPE).orEmpty().ifBlank { "sd15cpu" }
        val libDir = intent.getStringExtra(EXTRA_LIB_DIR).orEmpty()      // QNN 运行时库目录，CPU 管线为空
        val noImg2img = intent.getBooleanExtra(EXTRA_NO_IMG2IMG, false)
        val useVPred = intent.getBooleanExtra(EXTRA_USE_V_PRED, false)
        val lowram = intent.getBooleanExtra(EXTRA_LOWRAM, false)
        val seqDit = intent.getBooleanExtra(EXTRA_SEQ_DIT, false)
        val patch = intent.getStringExtra(EXTRA_PATCH).orEmpty()
        if (modelId.isBlank() || modelDir.isBlank()) {
            Log.e(TAG, "图片后端启动失败，参数为空: modelId=$modelId modelDir=$modelDir")
            stopSelf(startId)
            return
        }

        val previousModelId = activeModelId                     // 复用判断必须在覆盖前取旧值
        val previousPipeline = activePipelineType               // 管线变化（如 CPU→QNN）同样必须重启进程
        activeModelId = modelId
        activePipelineType = pipelineType
        createNotificationChannel()
        startForegroundCompat(buildNotification("正在启动图片后端", 0))
        ImageBackendEvents.publish(ImageBackendEvent(modelId, ImageBackendEventType.STARTING, message = "正在启动图片后端"))

        serviceScope.launch {
            val startResult = runCatching {
                // 同模型同管线且进程存活健康：直接复用，避免每次生成都杀掉进程重读 ~1.2GB 模型，
                // 同时消除"旧进程被杀时 in-flight 请求一并失败"的竞态
                val current = backendProcess
                if (current != null && previousModelId == modelId && previousPipeline == pipelineType &&
                    isProcessStillRunning(current) && isBackendHealthy()
                ) {
                    Log.d(TAG, "复用已就绪的图片后端进程: modelId=$modelId")
                } else {
                    stopBackendProcess("启动新图片后端前清理旧进程")
                    val process = launchBackendProcess(modelDir, pipelineType, libDir, noImg2img, useVPred, lowram, seqDit, patch)
                    backendProcess = process
                    monitorNativeLogs(process, modelId)
                    waitForHealth()
                }
            }

            if (startResult.isSuccess) {
                updateNotification("图片后端已就绪", 100)
                ImageBackendEvents.publish(ImageBackendEvent(modelId, ImageBackendEventType.READY, message = "图片后端已就绪"))
                Log.d(TAG, "图片后端健康检查通过: modelId=$modelId")
            } else {
                val message = startResult.exceptionOrNull()?.message ?: "图片后端启动失败"
                updateNotification(message, 0)
                ImageBackendEvents.publish(ImageBackendEvent(modelId, ImageBackendEventType.FAILED, message = message))
                Log.e(TAG, "图片后端启动失败: $message", startResult.exceptionOrNull())
                stopBackendProcess(message)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
    }

    /** 使用 ProcessBuilder 启动随 APK 打包的 native executable */
    private fun launchBackendProcess(
        modelDir: String,
        pipelineType: String,
        libDir: String,
        noImg2img: Boolean,
        useVPred: Boolean,
        lowram: Boolean,
        seqDit: Boolean,
        patch: String
    ): Process {
        val nativeDir = applicationInfo.nativeLibraryDir
        val executable = File(nativeDir, "libmagicwx_image_backend.so")
        require(executable.isFile) { "缺少图片后端可执行文件: ${executable.absolutePath}" }
        executable.setExecutable(true, false)                   // 某些系统需要显式设置执行位

        // 与 local-dream BackendService 命令行对齐：--type <pipeline> --model_dir <dir> --port <n>
        val command = mutableListOf(
            executable.absolutePath,
            "--type", pipelineType,
            "--model_dir", modelDir,
            "--port", PORT.toString()
        )
        // QNN 管线（sd15npu/sdxl/anima）需要 --lib_dir 指向 QNN 运行时库目录；
        // CPU 管线 libDir 为空不下发，对齐参照"sd15cpu 不传 --lib_dir"的语义
        if (libDir.isNotBlank()) {
            command += listOf("--lib_dir", libDir)
        }
        if (noImg2img) command += "--no_img2img"
        if (useVPred) command += "--use_v_pred"
        if (lowram) command += "--lowram"                               // SDXL/Anima 低内存模式：逐阶段加载释放
        if (seqDit) command += "--anima_seq_dit"                        // Anima 序列化 DiT：两张分片不共存
        if (patch.isNotBlank()) command += listOf("--patch", patch)     // 分辨率 patch 文件路径
        Log.d(TAG, "启动图片后端进程: ${command.joinToString(" ")}")
        return ProcessBuilder(command)
            .directory(filesDir)
            .redirectErrorStream(true)
            .apply {
                // QNN 运行时库目录同时注入 LD_LIBRARY_PATH/DSP_LIBRARY_PATH（HTP skel 发现依赖），
                // 对齐参照 BackendService 对 runtimeDir 的环境变量处理；CPU 管线保持仅 nativeDir
                environment()["LD_LIBRARY_PATH"] = if (libDir.isNotBlank()) "$nativeDir:$libDir" else nativeDir
                if (libDir.isNotBlank()) {
                    environment()["DSP_LIBRARY_PATH"] = libDir
                }
            }
            .start()
    }

    /** 将 native stdout/stderr 转发到 logcat，便于定位崩溃前最后阶段 */
    private fun monitorNativeLogs(process: Process, modelId: String) {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        Log.d(TAG, "native[$modelId] $line")
                    }
                }
                val exitCode = process.waitFor()
                ImageBackendEvents.publish(
                    ImageBackendEvent(modelId, ImageBackendEventType.STOPPED, message = "图片后端进程退出: $exitCode")
                )
                Log.w(TAG, "图片后端进程退出: modelId=$modelId exitCode=$exitCode")
            } catch (error: IOException) {
                // destroy()/cancel() 会中断 stdout 读取，这是正常停止路径，不能抛出导致 App 崩溃。
                Log.d(TAG, "图片后端日志读取结束: modelId=$modelId, message=${error.message}")
            } catch (error: InterruptedException) {
                // waitFor() 被打断时恢复中断标记，保持协程取消语义可追踪。
                Thread.currentThread().interrupt()
                Log.d(TAG, "图片后端日志监控被中断: modelId=$modelId")
            } catch (error: Exception) {
                // 日志监控不是业务主路径，异常只记录，不允许杀死主进程。
                Log.e(TAG, "图片后端日志监控异常: modelId=$modelId, message=${error.message}", error)
            } finally {
                runCatching { process.inputStream.close() }
                if (isProcessStillRunning(process)) {
                    Log.d(TAG, "图片后端进程仍在运行，日志监控结束: modelId=$modelId")
                }
            }
        }
    }

    /** 使用 API 24 可用的 exitValue 探测进程存活，避免直接调用 API 26 的 Process.isAlive */
    private fun isProcessStillRunning(process: Process): Boolean {
        return try {
            process.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    /**
     * 轮询 /health，避免 UI 在后端未 ready 时发起 /generate。
     * 放宽到 60s 总窗口：~1.2GB SD1.5 模型（UNet+VAE+CLIP）首次加载需要数十秒，
     * 旧 10s 窗口在中端机上会误判启动失败（参照 ModelRunSupport.checkBackendHealth 的 60s 口径）。
     * 轮询从 100ms 起步指数退避到 500ms 封顶，加载后期无需高频打 /health。
     */
    private suspend fun waitForHealth() {
        val deadline = SystemClock.elapsedRealtime() + 60_000L       // 60s 总窗口
        var pollDelay = 100L                                         // 首次 100ms 起步
        var attempt = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            attempt++
            if (isBackendHealthy()) return
            Log.d(TAG, "等待图片后端健康检查: attempt=$attempt")
            delay(pollDelay)
            pollDelay = (pollDelay * 2).coerceAtMost(500L)           // 指数退避，500ms 封顶
        }
        error("图片后端 60 秒内未通过健康检查")
    }

    /** 调用 localhost /health */
    private suspend fun isBackendHealthy(): Boolean = withContext(Dispatchers.IO) {
        val connection = (URL("http://127.0.0.1:$PORT/health").openConnection() as HttpURLConnection)
        return@withContext try {
            connection.connectTimeout = 500
            connection.readTimeout = 500
            connection.requestMethod = "GET"
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (error: Exception) {
            Log.d(TAG, "图片后端健康检查失败: ${error.javaClass.simpleName}: ${error.message}")
            false
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 停止 native 后端进程。
     * H3 健壮性：destroy() 发 SIGTERM 后最多等 3s 让 native 释放模型内存并退出；
     * 超时未退出则 destroyForcibly() 发 SIGKILL，防止僵尸进程继续占用 ~1.2GB 内存
     * 导致下一次启动因内存不足失败。
     */
    private fun stopBackendProcess(reason: String) {
        Log.d(TAG, "停止图片后端进程: $reason")
        monitorJob?.cancel()
        monitorJob = null
        val process = backendProcess
        backendProcess = null
        if (process != null) {
            process.destroy()                                        // SIGTERM：给 native 优雅退出机会
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // API 26+：Process 有带超时的 waitFor 与 destroyForcibly
                    val exited = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                    if (!exited) {
                        Log.w(TAG, "图片后端 3s 内未退出，强制杀死进程")
                        process.destroyForcibly()                    // SIGKILL：兜底回收内存
                    }
                } else {
                    // API 24/25：Process 无带超时 waitFor/destroyForcibly，退化用守护线程 3s 后再发终止信号
                    val watchdog = Thread {
                        Thread.sleep(3000)
                        Log.w(TAG, "图片后端 3s 内未退出，destroy 兜底")
                        process.destroy()                            // destroy 幂等，进程已死则空操作
                    }.apply { isDaemon = true }
                    watchdog.start()
                    process.waitFor()                                // 阻塞等退出，watchdog 到时强杀后自然返回
                }
            }.onFailure { error ->
                Log.w(TAG, "等待图片后端退出异常: ${error.message}")
            }
        }
        if (activeModelId.isNotBlank()) {
            ImageBackendEvents.publish(ImageBackendEvent(activeModelId, ImageBackendEventType.STOPPED, message = reason))
        }
    }

    override fun onDestroy() {
        stopBackendProcess("Service 销毁")
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 使用 specialUse 前台服务类型：Android 16 对 dataSync FGS 有累计时长强杀，
     *  生图后端需要长时驻留（与 Manifest specialUse 声明一致） */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** 更新后端常驻通知 */
    private fun updateNotification(text: String, progress: Int) {
        if (!canPostNotifications()) {
            Log.d(TAG, "通知权限未授予，后端状态仅写入日志: $text")
            return
        }
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(text, progress)
        )
    }

    /** 构建图片后端通知 */
    private fun buildNotification(text: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MagicWX 图片后端")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()
    }

    /** 创建图片后端通知频道 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "图片后端", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示端侧图片后端进程状态"
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

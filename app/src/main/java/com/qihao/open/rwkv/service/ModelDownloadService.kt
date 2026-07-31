/**
 * ModelDownloadService - 模型后台下载前台服务
 *
 * 功能：
 * - createStartIntent(): 构造启动指定模型下载的 Intent
 * - onStartCommand(): 启动 dataSync 前台服务并执行下载
 * - updateNotification(): 常驻通知显示下载进度
 * - onTimeout(): 处理 Android 15+ dataSync 前台服务超时
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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.qihao.open.rwkv.MainActivity
import com.qihao.open.rwkv.R
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 前台服务负责承载大模型下载，避免用户离开下载页后任务被取消 */
class ModelDownloadService : Service() {

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val ACTION_START = "com.qihao.open.rwkv.action.START_MODEL_DOWNLOAD"
        private const val EXTRA_MODEL_ID = "model_id"
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 1001

        /** 构造启动模型下载的 Intent */
        fun createStartIntent(context: Context, modelId: String): Intent {
            return Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_ID, modelId)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null                // 当前下载任务
    private var activeModelId: String? = null         // 当前下载模型 ID

    override fun onBind(intent: Intent?): IBinder? = null

    /** 启动或替换当前模型下载任务 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
        val modelInfo = modelId?.let { ModelRegistry.findById(it) }
        if (modelId == null || modelInfo == null) {
            Log.e(TAG, "下载启动失败，未知模型: $modelId")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForegroundCompat(buildNotification(modelInfo.name, 0, "准备下载..."))
        activeJob?.cancel()
        activeModelId = modelId

        activeJob = serviceScope.launch {
            val downloader = ModelDownloader(applicationContext)
            ModelDownloadEvents.publish(
                ModelDownloadEvent(modelId, ModelDownloadEventType.STARTED, message = "准备下载...")
            )

            var lastPercent = -1
            val success = downloader.downloadModel(modelInfo) { downloaded, total, percent ->
                if (percent != lastPercent) {
                    lastPercent = percent
                    updateNotification(modelInfo.name, percent, formatSize(downloaded, total))
                    ModelDownloadEvents.publish(
                        ModelDownloadEvent(
                            modelId = modelId,
                            type = ModelDownloadEventType.PROGRESS,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            percent = percent,
                            message = formatSize(downloaded, total)
                        )
                    )
                }
            }

            if (success) {
                updateNotification(modelInfo.name, 100, "下载完成")
                ModelDownloadEvents.publish(
                    ModelDownloadEvent(modelId, ModelDownloadEventType.COMPLETED, percent = 100, message = "下载完成")
                )
                Log.d(TAG, "后台模型下载完成: $modelId")
            } else {
                updateNotification(modelInfo.name, lastPercent.coerceAtLeast(0), "下载失败")
                ModelDownloadEvents.publish(
                    ModelDownloadEvent(modelId, ModelDownloadEventType.FAILED, message = "模型下载失败")
                )
                Log.e(TAG, "后台模型下载失败: $modelId")
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    /** Android 15+ dataSync 前台服务超时时主动停止，避免系统 ANR */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.e(TAG, "模型下载前台服务超时: modelId=$activeModelId, type=$fgsType")
        activeJob?.cancel()
        activeModelId?.let {
            ModelDownloadEvents.publish(
                ModelDownloadEvent(it, ModelDownloadEventType.FAILED, message = "系统限制导致下载超时")
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    /** 销毁服务时取消下载协程 */
    override fun onDestroy() {
        activeJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "模型下载服务已销毁")
        super.onDestroy()
    }

    /** 使用带类型的前台服务启动方式，满足 Android 14+ 类型要求 */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** 更新常驻下载通知 */
    private fun updateNotification(modelName: String, percent: Int, text: String) {
        if (!canPostNotifications()) {
            Log.d(TAG, "通知权限未授予，仅保留前台服务任务状态: $percent%")
            return
        }
        val notification = buildNotification(modelName, percent, text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** 构建下载进度通知 */
    private fun buildNotification(modelName: String, percent: Int, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在下载模型：$modelName")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(percent in 0 until 100)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .build()
    }

    /** 创建模型下载通知频道 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "模型下载",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示大模型后台下载进度"
        }
        manager.createNotificationChannel(channel)
    }

    /** 判断当前是否允许在通知栏更新进度 */
    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** 格式化通知中的下载大小 */
    private fun formatSize(downloaded: Long, total: Long): String {
        val dlMB = downloaded / 1024f / 1024f
        val totalMB = total / 1024f / 1024f
        return if (totalMB > 1024) {
            "%.1f GB / %.1f GB".format(dlMB / 1024f, totalMB / 1024f)
        } else {
            "%.1f MB / %.1f MB".format(dlMB, totalMB)
        }
    }
}

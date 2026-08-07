/**
 * ReportImageUtils - 生图结果问题报告工具
 *
 * 功能：
 * - reportImage(context, historyItem): 收集历史记录中的生图参数与产物图片，生成本地问题报告
 * - shareReport(context, reportFile): 通过 FileProvider 分享报告 JSON 文件（可选）
 *
 * 移植自 modules/local-dream/utils/ImageUtils.kt:182 reportImage()
 * 原仓库将 Bitmap 以 Base64 编码后 POST 到 https://report.chino.icu/report 后端；
 * MagicWX 无后端，改为本地落盘方案：
 *   - JSON 报告落盘 files/reports/ 目录，包含所有生图参数（对齐原仓库 GenerationParameters 语义）
 *   - 产物图片拷贝到报告目录下，JSON 中以相对路径引用
 *   - 可选通过 FileProvider 分享报告 JSON 文件
 *
 * 参数对齐（原仓库 GenerationParameters → HistoryEntity 字段）：
 *   prompt          → historyItem.prompt
 *   negative_prompt → historyItem.negativePrompt
 *   steps           → historyItem.steps
 *   cfg             → historyItem.cfg
 *   seed            → historyItem.seed
 *   size            → historyItem.width x historyItem.height
 *   run_on_cpu      → historyItem.runOnCpu
 *   generation_time → historyItem.durationMillis（毫秒）
 *   model_name      → historyItem.modelId
 *   额外字段        → denoiseStrength / useOpenCL / scheduler / mode
 *
 * 设计要点：
 * - 全异步 IO，不阻塞主线程
 * - 报告目录 files/reports/ 不在 FileProvider 暴露范围内（JSON 分享时临时授权）
 * - 图片拷贝采用字节直拷，不重编码，避免二次压缩损失
 * - 详细日志输出，便于追踪报告生成过程
 */
package com.qihao.open.rwkv.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.qihao.open.rwkv.data.db.HistoryEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 生图问题报告工具，全部方法不持有状态 */
object ReportImageUtils {

    private const val TAG = "ReportImageUtils"

    /** 报告存放子目录：context.filesDir/reports/ */
    private const val REPORT_SUBDIR = "reports"

    /** 报告文件名时间戳格式，保证全局唯一 */
    private val FILENAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    /**
     * 收集 historyItem 的生图参数与产物图片，生成本地问题报告。
     *
     * @param context    上下文，用于获取 filesDir 路径
     * @param historyItem 历史记录实体，包含完整生图参数与产物路径
     * @return 报告 JSON 文件；失败时返回 null（日志已记录原因）
     */
    suspend fun reportImage(context: Context, historyItem: HistoryEntity): File? = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始生成问题报告: id=${historyItem.id}, modelId=${historyItem.modelId}, outputPath=${historyItem.outputPath}")

        // 1. 创建报告目录 files/reports/
        val reportsDir = File(context.filesDir, REPORT_SUBDIR)
        if (!reportsDir.exists() && !reportsDir.mkdirs()) {
            Log.e(TAG, "创建报告目录失败: ${reportsDir.absolutePath}")
            return@withContext null
        }

        // 2. 生成唯一报告标识，所有产物以此为前缀
        val reportId = FILENAME_FORMAT.format(Date())
        Log.d(TAG, "报告标识: $reportId")

        // 3. 拷贝产物图片到报告目录，避免原图被移动/删除后报告引用失效
        val imageFileName = "${reportId}_output.png"
        val imageFile = File(reportsDir, imageFileName)
        val sourceImageFile = File(historyItem.outputPath)
        val imageCopied = runCatching {
            if (sourceImageFile.isFile && sourceImageFile.length() > 0L) {
                sourceImageFile.inputStream().use { input ->
                    FileOutputStream(imageFile).use { output ->
                        input.copyTo(output) // 字节直拷，不重编码
                    }
                }
                Log.d(TAG, "产物图片已拷贝: ${imageFile.absolutePath} (${imageFile.length()} bytes)")
                true
            } else {
                Log.w(TAG, "产物图片不存在或为空: ${historyItem.outputPath}，报告不含图片引用")
                false
            }
        }.getOrElse { error ->
            Log.e(TAG, "拷贝产物图片失败: ${error.message}", error)
            false
        }

        // 4. 构建 JSON 报告，字段语义对齐原仓库 reportImage (ImageUtils.kt:196-211)
        val generationTimeMillis = historyItem.durationMillis
        val generationTimeStr = if (generationTimeMillis > 0L) "${generationTimeMillis}ms" else null

        val json = JSONObject().apply {
            // 报告元信息
            put("report_id", reportId)
            put("report_time", System.currentTimeMillis())
            put("model_name", historyItem.modelId)

            // 生图参数，对齐原仓库 GenerationParameters 字段
            put("generation_params", JSONObject().apply {
                put("prompt", historyItem.prompt)
                put("negative_prompt", historyItem.negativePrompt)
                put("steps", historyItem.steps)
                put("cfg", historyItem.cfg.toDouble()) // Float → Double 防 JSON 精度问题
                put("seed", if (historyItem.seed >= 0L) historyItem.seed else JSONObject.NULL)
                put("size", "${historyItem.width}x${historyItem.height}")
                put("run_on_cpu", historyItem.runOnCpu)
                put("generation_time", generationTimeStr ?: JSONObject.NULL)
                // 原仓库 GenerationParameters 的额外字段，一并收录
                put("denoise_strength", historyItem.denoiseStrength.toDouble())
                put("use_opencl", historyItem.useOpenCL)
                put("scheduler", historyItem.scheduler)
                put("mode", historyItem.mode)
            })

            // 产物图片引用（相对路径，报告目录内）
            if (imageCopied) {
                put("output_image", imageFileName)
            } else {
                put("output_image", JSONObject.NULL)
            }

            // 缩略图路径（如有）
            put("thumbnail_path", historyItem.thumbnailPath ?: JSONObject.NULL)
            // 记录创建时间（历史记录时间戳）
            put("record_created_at", historyItem.createdAt)
        }

        // 5. 写入报告 JSON 文件
        val reportFileName = "${reportId}_report.json"
        val reportFile = File(reportsDir, reportFileName)
        runCatching {
            reportFile.writeText(json.toString(2)) // 缩进 2 空格，便于人工阅读
            Log.d(TAG, "报告已生成: ${reportFile.absolutePath} (${reportFile.length()} bytes)")
            Log.d(TAG, "报告内容摘要: prompt=${historyItem.prompt.take(80)}, size=${historyItem.width}x${historyItem.height}, steps=${historyItem.steps}")
        }.getOrElse { error ->
            Log.e(TAG, "写入报告文件失败: ${error.message}", error)
            return@withContext null
        }

        reportFile
    }

    /**
     * 通过 FileProvider 分享报告 JSON 文件。
     * 复用 ImageExportUtils.shareImage 的 FileProvider 机制（authorities = ${applicationId}.fileprovider）。
     * 注意：files/reports/ 目录不在 file_paths.xml 的暴露清单中，
     * 此方法动态生成 content:// Uri 并授予临时读权限，无需修改 file_paths.xml。
     *
     * @param context   上下文
     * @param reportFile 报告 JSON 文件
     * @return true 表示分享 Intent 已成功发起
     */
    fun shareReport(context: Context, reportFile: File): Boolean {
        if (!reportFile.isFile || reportFile.length() <= 0L) {
            Log.w(TAG, "分享失败：报告文件不存在或为空: ${reportFile.absolutePath}")
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                reportFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "MagicWX 生图问题报告")
                putExtra(Intent.EXTRA_TEXT, "MagicWX 生图问题报告，详见附件。")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享问题报告"))
            Log.d(TAG, "已发起报告分享: $uri")
            true
        }.getOrElse { error ->
            Log.e(TAG, "分享报告失败: ${error.message}", error)
            false
        }
    }
}
/**
 * ImageExportUtils - 生图结果导出工具
 *
 * 功能：
 * - saveToGallery(): 把结果文件写入系统相册（MediaStore，Pictures/MagicWX 子目录）
 * - saveBitmapToGallery(): 把内存 Bitmap 编码后写入系统相册（inpaint 贴回合成图用）
 * - shareImage(): 通过 FileProvider 以 ACTION_SEND 分享结果图
 * - mimeTypeOf(): 按扩展名映射 MIME 类型
 *
 * 设计要点：
 * - 参照 modules/local-dream/utils/ImageUtils.kt saveImageFromFile：字节直拷，
 *   不做解码+重编码，避免二次压缩损失
 * - saveBitmapToGallery 的编码口径对齐参照 ImageUtils.saveImage：
 *   宽高>1024 用 JPEG 95，其余 PNG 无损，控制相册体积同时保住细节
 * - API 29+ 走 MediaStore RELATIVE_PATH 免存储权限；API 24-28 走公共目录直写 +
 *   媒体扫描（Manifest 已声明 maxSdkVersion=28 的 WRITE_EXTERNAL_STORAGE）
 * - 分享必须经 FileProvider 授权 content:// Uri，禁止外泄 file:// 私有路径
 */
package com.qihao.open.rwkv.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 生图结果导出工具，全部方法不持有状态 */
object ImageExportUtils {

    private const val TAG = "ImageExportUtils"
    private const val GALLERY_SUBDIR = "MagicWX"          // 相册子目录名，产品对外可见名称

    /** 按扩展名映射 MIME 类型，未知扩展回退 image/jpeg（结果图默认 JPEG） */
    fun mimeTypeOf(file: File): String {
        return when (file.extension.lowercase(Locale.US)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }

    /** 生成导出文件名：MagicWX_时间戳.扩展名，保证相册内可辨识来源 */
    private fun exportFilename(extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "MagicWX_${timestamp}.$extension"
    }

    /**
     * 把结果图保存到相册 Pictures/MagicWX 目录。
     * @return true 表示系统相册已可检索到该图片
     */
    suspend fun saveToGallery(context: Context, sourceFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.isFile || sourceFile.length() <= 0L) {
            Log.w(TAG, "保存失败：结果文件不存在或为空: ${sourceFile.absolutePath}")
            return@withContext false
        }
        val extension = sourceFile.extension.lowercase(Locale.US).ifEmpty { "jpg" }
        val mimeType = mimeTypeOf(sourceFile)
        val filename = exportFilename(extension)

        runCatching {
            writeGalleryEntry(context, filename, mimeType) { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) } // 字节直拷，不重编码
            }
            true
        }.getOrElse { error ->
            Log.e(TAG, "保存到相册失败: ${error.message}", error)
            false
        }
    }

    /**
     * 把内存 Bitmap 编码后保存到相册 Pictures/MagicWX 目录（inpaint 贴回合成图专用）。
     * 编码口径对齐参照 local-dream ImageUtils.saveImage：宽高>1024 用 JPEG 95，其余 PNG 无损。
     * @return true 表示系统相册已可检索到该图片
     */
    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
            Log.w(TAG, "保存失败：位图无效或已回收")
            return@withContext false
        }
        val isLargeImage = bitmap.width > 1024 || bitmap.height > 1024     // 与参照 saveImage 同口径
        val format = if (isLargeImage) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val extension = if (isLargeImage) "jpg" else "png"
        val mimeType = if (isLargeImage) "image/jpeg" else "image/png"
        val quality = if (isLargeImage) 95 else 100
        val filename = exportFilename(extension)

        runCatching {
            writeGalleryEntry(context, filename, mimeType) { output ->
                bitmap.compress(format, quality, output)
            }
            Log.d(TAG, "已保存合成位图到相册: ${bitmap.width}x${bitmap.height}, format=$extension")
            true
        }.getOrElse { error ->
            Log.e(TAG, "保存合成位图到相册失败: ${error.message}", error)
            false
        }
    }

    /**
     * MediaStore 写入公共实现：API 29+ 走 RELATIVE_PATH 免权限，
     * API 24-28 走公共目录直写 + 媒体扫描。[write] 拿到输出流后执行具体写入。
     */
    private fun writeGalleryEntry(
        context: Context,
        filename: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+：MediaStore RELATIVE_PATH，无需任何存储权限
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$GALLERY_SUBDIR"
                )
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("创建相册条目失败")
            resolver.openOutputStream(uri)?.use(write) ?: throw IOException("无法写入相册输出流")
            Log.d(TAG, "已保存到相册: $uri")
        } else {
            // Android 7-9：公共目录直写 + 媒体扫描入库（需 WRITE_EXTERNAL_STORAGE，Manifest 已声明）
            val imagesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                GALLERY_SUBDIR
            )
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                throw IOException("创建相册目录失败: ${imagesDir.absolutePath}")
            }
            val outFile = File(imagesDir, filename)
            FileOutputStream(outFile).use(write)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            Log.d(TAG, "已保存到相册（旧存储路径）: ${outFile.absolutePath}")
        }
    }

    /**
     * 通过 FileProvider 分享结果图：ACTION_SEND + content:// Uri + 临时读权限。
     * FileProvider 在 AndroidManifest 注册，authorities = ${applicationId}.fileprovider。
     */
    fun shareImage(context: Context, sourceFile: File): Boolean {
        if (!sourceFile.isFile || sourceFile.length() <= 0L) {
            Log.w(TAG, "分享失败：结果文件不存在或为空: ${sourceFile.absolutePath}")
            return false
        }
        return runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sourceFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeTypeOf(sourceFile)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)      // 授予目标应用临时读权限
            }
            context.startActivity(Intent.createChooser(intent, "分享图片"))
            Log.d(TAG, "已发起分享: $uri")
            true
        }.getOrElse { error ->
            // 常见失败：FileProvider 未注册或 paths 未覆盖该目录，日志给出定位线索
            Log.e(TAG, "分享失败: ${error.message}", error)
            false
        }
    }
}

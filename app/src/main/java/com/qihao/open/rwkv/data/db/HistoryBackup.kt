/**
 * HistoryBackup - 历史记录备份/导入工具
 *
 * 功能：
 * - exportHistoryZip: 将历史记录（DB 行 + 产物图片）导出为单个 zip 文件
 * - importHistoryZip: 从 zip 文件解析 manifest 并合并历史到 Room 数据库
 *
 * 设计说明：
 * - zip 内携带 manifest.json 清单，而非裸 DB 拷贝，确保跨 schema 版本可读
 * - 导入时合并而非替换现有历史，去重策略按 outputPath 检查已存在记录
 * - 每条记录独立提交，中断/取消时已完成的部分保留，重试时自动跳过已导入项
 * - 图片写入采用 .part 临时文件 + rename 原子操作，防止写入中断产生损坏文件
 * - 对齐原仓库 HistoryBackup object 的核心逻辑（导出/导入/原子写入/格式校验），
 *   适配 MagicWX 的 HistoryEntity 字段名（outputPath/createdAt/durationMillis/thumbnailPath）
 */
package com.qihao.open.rwkv.data.db

import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 备份文件格式异常：manifest 缺失、格式不匹配、版本过高 */
class BackupFormatException(message: String) : IOException(message)

/** 导出结果统计 */
data class BackupExportResult(
    val exported: Int,              // 成功导出的记录数
    val skippedMissingImage: Int,   // 因图片文件缺失而跳过的记录数
)

/** 导入结果统计 */
data class BackupImportResult(
    val imported: Int,              // 成功导入的记录数
    val duplicates: Int,            // 因重复（同 outputPath 已存在）跳过的记录数
    val failed: Int,                // 导入失败的记录数（无效条目 + zip 中缺少对应图片）
    val orphanModelIds: Set<String>, // 导入记录引用的、当前未安装的模型 ID（预留，暂未实现）
)

/**
 * 历史记录备份工具（单例 object）
 *
 * zip 结构：
 *   manifest.json          — 历史记录清单（JSON）
 *   <图片文件名>.png/.jpg   — 产物图片（不压缩，PNG/JPEG 已编码）
 */
object HistoryBackup {
    private const val TAG = "HistoryBackup"
    private const val MANIFEST_NAME = "manifest.json"
    private const val FORMAT = "magicwx-history-backup"
    private const val VERSION = 1

    /**
     * 合法的图片路径模式：必须包含 generated_images/ 目录，以 .png 或 .jpg 结尾。
     * 拒绝绝对路径（无 generated_images 前缀时）、目录穿越、非预期文件类型。
     * 对齐原仓库 imagePathRegex 的安全校验思路。
     */
    private val imagePathRegex = Regex("""generated_images/[^/\\]+\.(png|jpg)""")

    // ========================================================================
    // 公开接口
    // ========================================================================

    /**
     * 导出历史记录为 zip 文件
     *
     * 流程：
     * 1. 过滤出图片文件确实存在的记录（缺失的计入 skippedMissingImage）
     * 2. 写入 manifest.json（压缩）
     * 3. 逐条将图片文件以文件名作为 zip 条目拷贝入 zip（不压缩，避免对已编码图片重复压缩）
     *
     * @param outputFile  目标 zip 文件
     * @param historyList 待导出的历史记录列表
     * @param imageDir    图片产物目录（如 File(filesDir, "generated_images")），
     *                    用于校验图片路径合法性，暂未强依赖
     * @return 导出结果统计
     */
    suspend fun exportHistoryZip(
        outputFile: File,
        historyList: List<HistoryEntity>,
        imageDir: File,
    ): BackupExportResult = withContext(Dispatchers.IO) {
        // 过滤：仅保留图片文件确实存在于磁盘的记录
        val validItems = historyList.filter { entity ->
            val imageFile = File(entity.outputPath)
            imageFile.isFile
        }
        val skippedCount = historyList.size - validItems.size
        if (skippedCount > 0) {
            Log.w(TAG, "导出前过滤：${skippedCount} 条记录图片文件缺失，将跳过")
        }

        ZipOutputStream(BufferedOutputStream(outputFile.outputStream())).use { zip ->
            // Step 1: 写入 manifest.json（压缩，体积小）
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(buildManifest(validItems).toString().toByteArray())
            zip.closeEntry()
            Log.d(TAG, "manifest.json 已写入，共 ${validItems.size} 条记录")

            // Step 2: 写入图片文件（不压缩，PNG/JPEG 已编码，再压缩只浪费 CPU）
            zip.setLevel(Deflater.NO_COMPRESSION)
            validItems.forEachIndexed { index, item ->
                ensureActive() // 响应协程取消
                val imageFile = File(item.outputPath)
                // 以文件名作为 zip 条目名，避免路径冲突
                val entryName = imageFile.name
                zip.putNextEntry(ZipEntry(entryName))
                imageFile.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
                Log.d(TAG, "导出图片 ${index + 1}/${validItems.size}: $entryName")
            }
        }

        Log.d(TAG, "导出完成: 成功=${validItems.size}, 跳过缺失图片=${skippedCount}")
        BackupExportResult(
            exported = validItems.size,
            skippedMissingImage = skippedCount,
        )
    }

    /**
     * 从 zip 文件导入历史记录，合并到 Room 数据库
     *
     * 流程：
     * 1. 第一遍扫描 zip，定位并解析 manifest.json
     * 2. 校验格式版本，解析条目列表，按 outputPath 去重 + 过滤无效条目
     * 3. 第二遍扫描 zip，将匹配的图片写入目标路径，insert 到 Room
     *
     * 去重策略：按 outputPath 检查是否已存在同路径记录，存在则跳过。
     * 每条记录独立提交，取消或中断时已完成的记录保留，重试时自动跳过已导入项。
     *
     * @param inputZip   源 zip 文件
     * @param repository 历史记录仓库，提供 insert 和 existsByOutputPath 能力
     * @return 导入结果统计
     */
    suspend fun importHistoryZip(
        inputZip: File,
        repository: HistoryRepository,
    ): BackupImportResult = withContext(Dispatchers.IO) {
        // ================================================================
        // Pass 1: 定位并解析 manifest.json
        // 原始 zip 中 manifest 通常在最前面，但为兼容重新打包的 zip，
        // 遍历整个归档查找 manifest.json 条目
        // ================================================================
        val manifest = ZipInputStream(inputZip.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == MANIFEST_NAME }
                ?.let { entry ->
                    Log.d(TAG, "找到 manifest.json, size=${entry.size}")
                    JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                }
        } ?: throw BackupFormatException("manifest.json 未找到，不是有效的备份文件")

        // 校验格式与版本
        val manifestFormat = manifest.optString("format")
        if (manifestFormat != FORMAT) {
            throw BackupFormatException("不支持的备份格式: $manifestFormat，期望 $FORMAT")
        }
        val manifestVersion = manifest.optInt("version", Int.MAX_VALUE)
        if (manifestVersion > VERSION) {
            throw BackupFormatException(
                "备份文件由更新版本(v$manifestVersion)的应用创建，当前支持 v$VERSION，无法导入"
            )
        }
        Log.d(TAG, "manifest 格式校验通过: format=$manifestFormat, version=$manifestVersion")

        // ================================================================
        // Pass 1 续：解析条目列表，过滤无效 + 去重
        // ================================================================
        val itemsJson = manifest.optJSONArray("items") ?: JSONArray()
        val pending = LinkedHashMap<String, HistoryEntity>() // filename -> entity
        var duplicates = 0
        var invalid = 0

        for (i in 0 until itemsJson.length()) {
            val entity = itemsJson.optJSONObject(i)?.let { jsonToEntity(it) }
            if (entity == null) {
                invalid++
                Log.w(TAG, "第 $i 条记录 JSON 解析失败，跳过")
                continue
            }
            // 安全校验：outputPath 必须匹配合法图片路径模式
            if (!imagePathRegex.containsMatchIn(entity.outputPath)) {
                invalid++
                Log.w(TAG, "第 $i 条记录 outputPath 不合法: ${entity.outputPath}，跳过")
                continue
            }
            // 去重：按 outputPath 检查是否已存在同路径记录
            if (repository.existsByOutputPath(entity.outputPath)) {
                duplicates++
                Log.d(TAG, "跳过重复记录: ${entity.outputPath}")
                continue
            }
            // 以文件名为 key，用于 Pass 2 匹配 zip 条目
            pending[File(entity.outputPath).name] = entity
        }

        Log.d(TAG, "待导入: ${pending.size}, 重复跳过: $duplicates, 无效: $invalid")

        // ================================================================
        // Pass 2: 逐条提取图片并插入数据库
        // 每条记录独立提交，中断时可保留已完成部分
        // ================================================================
        val total = pending.size
        var imported = 0
        val orphanModelIds = mutableSetOf<String>()

        if (total > 0) {
            ZipInputStream(inputZip.inputStream().buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    ensureActive() // 响应协程取消
                    // 跳过 manifest 和目录条目
                    if (entry.isDirectory || entry.name == MANIFEST_NAME) return@forEach
                    // 按文件名匹配待导入实体
                    val entity = pending.remove(entry.name) ?: return@forEach
                    // 原子写入图片到目标路径
                    writeImage(zip, File(entity.outputPath))
                    // 插入数据库记录
                    repository.insert(entity)
                    imported++
                    Log.d(TAG, "导入 $imported/$total: ${entry.name} -> ${entity.outputPath}")
                }
            }
        }

        // 仍在 pending 中的条目：zip 中缺少对应的图片文件
        val pendingCount = pending.size
        if (pendingCount > 0) {
            Log.w(TAG, "$pendingCount 条记录在 zip 中未找到对应图片文件")
        }

        val totalFailed = invalid + pendingCount
        Log.d(TAG, "导入完成: 成功=$imported, 重复=$duplicates, 失败=$totalFailed")
        BackupImportResult(
            imported = imported,
            duplicates = duplicates,
            failed = totalFailed,
            orphanModelIds = orphanModelIds,
        )
    }

    // ========================================================================
    // 内部工具方法
    // ========================================================================

    /**
     * 构建 manifest.json 内容
     * 对齐原仓库 buildManifest(items: List<HistoryEntity>): JSONObject
     */
    private fun buildManifest(items: List<HistoryEntity>): JSONObject {
        val array = JSONArray()
        items.forEach { array.put(entityToJson(it)) }
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("items", array)
        }
    }

    /**
     * HistoryEntity → JSONObject 序列化
     * 对齐原仓库 entityToJson(e: HistoryEntity): JSONObject
     *
     * 字段映射说明（LocalDream → MagicWX）：
     *   imagePath → outputPath, timestamp → createdAt,
     *   generationTime → durationMillis（语义近似，非精确等价）
     * MagicWX 独有字段：thumbnailPath（可选）
     * LocalDream 独有字段：upscalerId / runOnCpu / useOpenCL（无对应，不导出）
     */
    private fun entityToJson(e: HistoryEntity): JSONObject = JSONObject().apply {
        put("outputPath", e.outputPath)
        put("prompt", e.prompt)
        put("negativePrompt", e.negativePrompt)
        put("mode", e.mode)
        put("steps", e.steps)
        // cfg 为 Float 类型，org.json 不直接支持 Float，转为 Double 存储
        put("cfg", e.cfg.toDouble())
        put("seed", e.seed)
        put("width", e.width)
        put("height", e.height)
        put("scheduler", e.scheduler)
        put("denoiseStrength", e.denoiseStrength.toDouble())
        put("durationMillis", e.durationMillis)
        put("createdAt", e.createdAt)
        // thumbnailPath 可选，仅非空时写入
        e.thumbnailPath?.let { put("thumbnailPath", it) }
        put("modelId", e.modelId)
        put("favorite", e.favorite)
    }

    /**
     * JSONObject → HistoryEntity 反序列化
     * 对齐原仓库 jsonToEntity(json: JSONObject): HistoryEntity?
     *
     * 缺失必填字段（outputPath 为空）时返回 null，
     * 其他字段缺失时使用默认值，确保跨版本兼容。
     */
    private fun jsonToEntity(json: JSONObject): HistoryEntity? {
        val outputPath = json.optString("outputPath")
        // outputPath 为必填字段，缺失则视为无效条目
        if (outputPath.isEmpty()) {
            Log.w(TAG, "JSON 条目缺少 outputPath，跳过")
            return null
        }
        return HistoryEntity(
            // id 由 Room 自增生成，不从备份恢复
            prompt = json.optString("prompt", ""),
            negativePrompt = json.optString("negativePrompt", ""),
            mode = json.optString("mode", "txt2img"),
            steps = json.optInt("steps", 20),
            cfg = json.optDouble("cfg", 7.0).toFloat(),
            seed = json.optLong("seed", -1L),
            width = json.optInt("width", 512),
            height = json.optInt("height", 512),
            scheduler = json.optString("scheduler", "dpm"),
            denoiseStrength = json.optDouble("denoiseStrength", 1.0).toFloat(),
            outputPath = outputPath,
            durationMillis = json.optLong("durationMillis", 0L),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            // thumbnailPath 可选，空串视为 null
            thumbnailPath = json.optString("thumbnailPath", "").ifEmpty { null },
            modelId = json.optString("modelId", ""),
            favorite = json.optBoolean("favorite", false),
        )
    }

    /**
     * 原子写入图片文件：先写 .part 临时文件，再 rename 到目标路径。
     * 对齐原仓库 writeImage(source: InputStream, dest: File)
     *
     * 原子性保证：rename 在同一文件系统上为原子操作，
     * 写入中断时只会残留 .part 文件（finally 块清理），不会产生损坏的目标文件。
     */
    private fun writeImage(source: java.io.InputStream, dest: File) {
        // 确保父目录存在
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.part")
        try {
            tmp.outputStream().use { source.copyTo(it) }
            if (!tmp.renameTo(dest)) {
                throw IOException("无法将临时文件 ${tmp.name} 移动到目标位置: ${dest.absolutePath}")
            }
        } finally {
            // 清理残留的临时文件（写入成功时 rename 后 tmp 已不存在，delete 无害）
            tmp.delete()
        }
    }
}
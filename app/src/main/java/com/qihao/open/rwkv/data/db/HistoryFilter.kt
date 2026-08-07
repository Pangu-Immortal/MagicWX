/**
 * HistoryFilter - 历史记录高级过滤数据类
 *
 * 功能：
 * - 按模型 ID / 生图模式 / 采样器 / 输出尺寸 / 推理设备 / 时间范围 / Prompt 子串 / 收藏状态
 *   组合过滤历史记录
 * - 构建 Room RawQuery 的 SupportSQLiteQuery，供 HistoryDao.query* 系列方法使用
 * - 支持 toSqlQuery（全行）、toIdQuery（仅 id）、toCountQuery（行数）、toRecentQuery（最新 N 条）
 *
 * 移植自：modules/local-dream/app/src/main/java/io/github/xororz/localdream/data/HistoryFilter.kt
 * 适配要点：MagicWX 使用 createdAt 替代 original 的 timestamp 字段。
 */
package com.qihao.open.rwkv.data.db

import androidx.compose.runtime.Immutable
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/** 生图模式枚举，与 generation_history.mode 列值对齐 */
enum class GenerationMode {
    TXT2IMG,
    IMG2IMG,
    INPAINT,
    ULTRAFIX,
    UNKNOWN,
    ;

    companion object {
        /** 从字符串解析模式，未知值返回 UNKNOWN */
        fun fromString(s: String?): GenerationMode = when (s) {
            "TXT2IMG" -> TXT2IMG
            "IMG2IMG" -> IMG2IMG
            "INPAINT" -> INPAINT
            "ULTRAFIX" -> ULTRAFIX
            else -> UNKNOWN
        }
    }
}

/** 推理设备过滤选项 */
enum class DeviceFilter { NPU, CPU, GPU }

/** 收藏状态过滤选项 */
enum class FavoriteFilter { FAVORITE, NOT_FAVORITE }

/**
 * 历史记录组合过滤器。
 *
 * 所有字段默认为 null（不过滤），设置非 null 集合/值后参与 WHERE 条件拼接。
 * 多个条件之间以 AND 连接，同一集合内以 OR 连接。
 */
@Immutable
data class HistoryFilter(
    /** 模型 ID 集合，null 或空集表示不过滤 */
    val modelIds: Set<String>? = null,
    /** 生图模式集合，null 或空集表示不过滤 */
    val modes: Set<GenerationMode>? = null,
    /** 时间范围下限（毫秒时间戳），null 表示无下限 */
    val from: Long? = null,
    /** 时间范围上限（毫秒时间戳），null 表示无上限 */
    val to: Long? = null,
    /** 输出尺寸集合（格式 "宽x高"），null 或空集表示不过滤 */
    val sizes: Set<String>? = null,
    /** 采样器集合，null 或空集表示不过滤 */
    val schedulers: Set<String>? = null,
    /** 推理设备集合，null 或空集表示不过滤 */
    val devices: Set<DeviceFilter>? = null,
    /** Prompt 子串（正向 + 负向均搜索），null 或空串表示不过滤 */
    val promptSubstring: String? = null,
    /** 收藏状态集合，null 或空集表示不过滤 */
    val favorites: Set<FavoriteFilter>? = null,
    /** 排序方向：true=降序（最新优先），false=升序（最旧优先） */
    val descending: Boolean = true,
) {
    /** 构建完整行查询，按时间排序，供列表展示和一次性加载 */
    fun toSqlQuery(): SupportSQLiteQuery = buildQuery(projection = "*", ordered = true)

    /** 构建仅 id 查询，按时间排序，供全选/批量操作获取匹配 id 集合 */
    fun toIdQuery(): SupportSQLiteQuery = buildQuery(projection = "id", ordered = true)

    /** 构建 COUNT 查询，无序，供选择计数器驱动 */
    fun toCountQuery(): SupportSQLiteQuery = buildQuery(projection = "COUNT(*)", ordered = false)

    /** 构建最新 N 条查询，降序，供结果页缩略图条/种子恢复 */
    fun toRecentQuery(limit: Int): SupportSQLiteQuery =
        buildQuery(projection = "*", ordered = true, limit = limit)

    /**
     * 核心 SQL 构建器。
     *
     * @param projection SELECT 子句的投影列（"*" / "id" / "COUNT(*)"）
     * @param ordered 是否附加 ORDER BY 子句
     * @param limit 可选 LIMIT 子句
     * @return 参数化 SQL 查询对象，可直接传入 Dao.rawQuery
     */
    private fun buildQuery(
        projection: String,
        ordered: Boolean,
        limit: Int? = null,
    ): SupportSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // 模型 ID 过滤
        if (!modelIds.isNullOrEmpty()) {
            where += "modelId IN (${modelIds.joinToString(",") { "?" }})"
            args.addAll(modelIds)
        }

        // 模式过滤：TXT2IMG 同时匹配 UNKNOWN（旧数据迁移后无模式记录，用户视角即为 txt2img）
        if (!modes.isNullOrEmpty()) {
            val expanded =
                if (GenerationMode.TXT2IMG in modes) modes + GenerationMode.UNKNOWN else modes
            where += "mode IN (${expanded.joinToString(",") { "?" }})"
            args.addAll(expanded.map { it.name })
        }

        // 时间范围过滤（MagicWX 使用 createdAt 字段）
        if (from != null) {
            where += "createdAt >= ?"
            args += from
        }
        if (to != null) {
            where += "createdAt <= ?"
            args += to
        }

        // 尺寸过滤
        if (!sizes.isNullOrEmpty()) {
            where += "(width || 'x' || height) IN (${sizes.joinToString(",") { "?" }})"
            args.addAll(sizes)
        }

        // 采样器过滤
        if (!schedulers.isNullOrEmpty()) {
            where += "scheduler IN (${schedulers.joinToString(",") { "?" }})"
            args.addAll(schedulers)
        }

        // 推理设备过滤：runOnCpu=false → NPU; runOnCpu=true && useOpenCL=false → CPU; runOnCpu=true && useOpenCL=true → GPU
        if (!devices.isNullOrEmpty()) {
            val parts = mutableListOf<String>()
            if (DeviceFilter.NPU in devices) parts += "runOnCpu = 0"
            if (DeviceFilter.CPU in devices) parts += "(runOnCpu = 1 AND useOpenCL = 0)"
            if (DeviceFilter.GPU in devices) parts += "(runOnCpu = 1 AND useOpenCL = 1)"
            if (parts.isNotEmpty()) {
                where += "(${parts.joinToString(" OR ")})"
            }
        }

        // Prompt 子串搜索（正向 + 负向均查）
        if (!promptSubstring.isNullOrBlank()) {
            where += "(INSTR(prompt, ?) > 0 OR INSTR(negativePrompt, ?) > 0)"
            args += promptSubstring
            args += promptSubstring
        }

        // 收藏状态过滤
        if (!favorites.isNullOrEmpty()) {
            val parts = mutableListOf<String>()
            if (FavoriteFilter.FAVORITE in favorites) parts += "favorite = 1"
            if (FavoriteFilter.NOT_FAVORITE in favorites) parts += "favorite = 0"
            where += "(${parts.joinToString(" OR ")})"
        }

        // 拼接 SQL
        val whereClause = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        val orderClause = if (ordered) {
            val direction = if (descending) "DESC" else "ASC"
            // MagicWX 使用 createdAt 替代 original 的 timestamp
            "ORDER BY createdAt $direction, id $direction"
        } else {
            ""
        }
        val limitClause = if (limit != null) "LIMIT $limit" else ""

        val sql = "SELECT $projection FROM generation_history $whereClause $orderClause $limitClause"

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
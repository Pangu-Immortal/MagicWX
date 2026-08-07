/**
 * HistoryRepository - 历史记录仓库
 *
 * 功能：
 * - 封装 HistoryDao，为上层（ViewModel / Service）提供统一的历史读写入口
 * - insert: 落盘一条生图记录，返回新行 id
 * - observe: 暴露按时间倒序的 Flow，供 UI 订阅自动刷新
 * - delete / deleteByIds / deleteAllForModel: 单条/批量/按模型删除
 * - renameModelId: 重命名模型 ID 并同步更新 outputPath
 * - setFavorite: 切换收藏状态
 * - filterByModel / filterByFavorite: 预置过滤 Flow
 * - observeModelIds / observeSchedulers / observeSizes: DISTINCT 值 Flow，供过滤 chips
 * - query / queryCount / queryOnce / queryIds: RawQuery 动态过滤（供 HistoryFilter 驱动）
 * - getPaged: 简单 limit/offset 分页查询
 *
 * 设计说明：单数据源 Dao 的薄封装，保持单一职责；后续如需文件清理、
 * 缩略图生成等副作用，应在更上层协调，不在此处耦合，避免上帝类。
 */
package com.qihao.open.rwkv.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {

    /** 插入一条历史记录，返回新行 id */
    suspend fun insert(entity: HistoryEntity): Long = dao.insert(entity)

    /** 观察全部历史记录（按 createdAt 倒序），UI 订阅后自动刷新 */
    fun observe(): Flow<List<HistoryEntity>> = dao.getAllOrderedByCreatedAtDesc()

    /** 按主键读取单条记录，删除前取 outputPath 联动清理磁盘文件用 */
    suspend fun getById(id: Long): HistoryEntity? = dao.getById(id)

    /** 一次性读取全部记录，清空前批量清理磁盘产物文件用 */
    suspend fun getAllOnce(): List<HistoryEntity> = dao.getAllOnce()

    /** 按主键删除单条历史记录 */
    suspend fun delete(id: Long) = dao.delete(id)

    /** 批量删除指定 id 列表的历史记录，返回删除行数 */
    suspend fun deleteByIds(ids: List<Long>): Int = dao.deleteByIds(ids)

    /** 删除指定模型 ID 的全部历史记录，返回删除行数 */
    suspend fun deleteAllForModel(modelId: String): Int = dao.deleteAllForModel(modelId)

    /** 重命名模型 ID 并同步更新 outputPath 前缀，返回受影响行数 */
    suspend fun renameModelId(oldId: String, newId: String): Int = dao.renameModelId(oldId, newId)

    /** 清空全部历史记录 */
    suspend fun deleteAll() = dao.deleteAll()

    /** 切换单条记录的收藏状态 */
    suspend fun setFavorite(id: Long, favorite: Boolean): Int = dao.setFavorite(id, favorite)

    /** 按模型 ID 筛选历史记录 Flow，供 UI 订阅自动刷新 */
    fun filterByModel(modelId: String): Flow<List<HistoryEntity>> = dao.filterByModel(modelId)

    /** 仅返回已收藏的历史记录 Flow，供 UI 订阅自动刷新 */
    fun filterByFavorite(): Flow<List<HistoryEntity>> = dao.filterByFavorite()

    /** 按 outputPath 检查是否已存在同路径记录，供 insert 前去重 */
    suspend fun existsByOutputPath(path: String): Boolean = dao.countByOutputPath(path) > 0

    // ---------- 对齐 local-dream 的批量/过滤/分页能力 ----------

    /** 观察历史记录中出现的所有不重复模型 ID，供过滤 chips 使用 */
    fun observeModelIds(): Flow<List<String>> = dao.observeModelIds()

    /** 观察历史记录中出现的所有不重复采样器，供过滤 chips 使用 */
    fun observeSchedulers(): Flow<List<String>> = dao.observeSchedulers()

    /** 观察历史记录中出现的所有不重复尺寸，供过滤 chips 使用 */
    fun observeSizes(): Flow<List<String>> = dao.observeSizes()

    /** 通过 RawQuery 动态查询历史记录 Flow，供 HistoryFilter 驱动列表订阅 */
    fun query(filter: SupportSQLiteQuery): Flow<List<HistoryEntity>> = dao.query(filter)

    /** 通过 RawQuery 查询匹配行数 Flow，供选择计数器驱动 */
    fun queryCount(filter: SupportSQLiteQuery): Flow<Int> = dao.queryCount(filter)

    /** 通过 RawQuery 一次性查询历史记录列表 */
    suspend fun queryOnce(filter: SupportSQLiteQuery): List<HistoryEntity> = dao.queryOnce(filter)

    /** 通过 RawQuery 一次性查询匹配的 id 列表，供全选/批量操作 */
    suspend fun queryIds(filter: SupportSQLiteQuery): List<Long> = dao.queryIds(filter)

    /** 按 id 列表批量读取历史记录 */
    suspend fun getByIds(ids: List<Long>): List<HistoryEntity> = dao.getByIds(ids)

    /** 观察单条记录的收藏状态变化 */
    fun observeFavorite(id: Long): Flow<Boolean?> = dao.observeFavorite(id)

    /** 简单分页查询：按 createdAt 倒序，limit/offset */
    suspend fun getPaged(limit: Int, offset: Int): List<HistoryEntity> = dao.getPaged(limit, offset)

    companion object {
        /** 从 Context 构建仓库，便于 ViewModel / Application 直接获取 */
        fun create(context: Context): HistoryRepository =
            HistoryRepository(AppDatabase.get(context).historyDao())
    }
}

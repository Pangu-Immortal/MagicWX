/**
 * HistoryDao - 历史记录数据访问对象
 *
 * 功能：
 * - insert: 插入一条历史记录，返回自增主键
 * - getAllOrderedByCreatedAtDesc: 按 createdAt 倒序观察全部历史，支持 UI 实时刷新
 * - delete / deleteByIds / deleteAllForModel: 单条/批量/按模型删除
 * - renameModelId: 重命名模型 ID 并同步更新 outputPath 前缀
 * - setFavorite: 切换单条记录的收藏状态
 * - getFavorites: 按收藏状态筛选全部历史 Flow
 * - filterByModel: 按模型 ID 筛选历史 Flow
 * - filterByFavorite: 仅返回已收藏记录 Flow
 * - observeModelIds / observeSchedulers / observeSizes: DISTINCT 值 Flow，供过滤 chips
 * - query / queryCount / queryOnce / queryIds: RawQuery 动态过滤（供 HistoryFilter 驱动）
 * - getPaged: 简单 limit/offset 分页查询
 *
 * 全部写操作为 suspend，查询返回 Flow 以支持响应式订阅。
 */
package com.qihao.open.rwkv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    /** 插入一条历史记录，REPLACE 策略避免主键冲突，返回新行 id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    /** 按 createdAt 倒序观察全部历史记录，UI 订阅后自动刷新 */
    @Query("SELECT * FROM generation_history ORDER BY createdAt DESC")
    fun getAllOrderedByCreatedAtDesc(): Flow<List<HistoryEntity>>

    /** 按主键读取单条记录；删除前取出 outputPath 联动清理磁盘文件用 */
    @Query("SELECT * FROM generation_history WHERE id = :id")
    suspend fun getById(id: Long): HistoryEntity?

    /** 一次性读取全部记录；清空历史前批量清理磁盘产物文件用 */
    @Query("SELECT * FROM generation_history")
    suspend fun getAllOnce(): List<HistoryEntity>

    /** 按主键删除单条历史记录 */
    @Query("DELETE FROM generation_history WHERE id = :id")
    suspend fun delete(id: Long)

    /** 批量删除指定 id 列表的历史记录，返回删除行数。
     *  调用方应在 @Transaction 内调用，确保一次提交触发一次 Flow 刷新。 */
    @Query("DELETE FROM generation_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    /** 删除指定模型 ID 的全部历史记录，返回删除行数 */
    @Query("DELETE FROM generation_history WHERE modelId = :modelId")
    suspend fun deleteAllForModel(modelId: String): Int

    /** 重命名模型 ID，并同步更新 outputPath 中 history/<oldId>/ → history/<newId>/ 前缀，
     *  使已落盘缩略图路径与目录重命名保持一致。返回受影响行数。 */
    @Query(
        "UPDATE generation_history SET modelId = :newId, " +
            "outputPath = REPLACE(outputPath, 'history/' || :oldId || '/', 'history/' || :newId || '/') " +
            "WHERE modelId = :oldId"
    )
    suspend fun renameModelId(oldId: String, newId: String): Int

    /** 清空全部历史记录 */
    @Query("DELETE FROM generation_history")
    suspend fun deleteAll()

    /** 切换单条记录的收藏状态，返回受影响行数 */
    @Query("UPDATE generation_history SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean): Int

    /** 按收藏状态筛选历史记录，按 createdAt 倒序；true=仅收藏，false=仅未收藏 */
    @Query("SELECT * FROM generation_history WHERE favorite = :fav ORDER BY createdAt DESC")
    fun getFavorites(fav: Boolean): Flow<List<HistoryEntity>>

    /** 按模型 ID 筛选历史记录，按 createdAt 倒序 */
    @Query("SELECT * FROM generation_history WHERE modelId = :modelId ORDER BY createdAt DESC")
    fun filterByModel(modelId: String): Flow<List<HistoryEntity>>

    /** 仅返回已收藏的历史记录，按 createdAt 倒序（filterByFavorite 便捷方法） */
    @Query("SELECT * FROM generation_history WHERE favorite = 1 ORDER BY createdAt DESC")
    fun filterByFavorite(): Flow<List<HistoryEntity>>

    /** 按 outputPath 计数同路径记录，供 insert 前去重检查 */
    @Query("SELECT COUNT(*) FROM generation_history WHERE outputPath = :path")
    suspend fun countByOutputPath(path: String): Int

    // ---------- 对齐 local-dream 的批量/过滤/分页能力 ----------

    /** 观察历史记录中出现的所有不重复模型 ID，供过滤 chips 使用 */
    @Query("SELECT DISTINCT modelId FROM generation_history ORDER BY modelId")
    fun observeModelIds(): Flow<List<String>>

    /** 观察历史记录中出现的所有不重复采样器，供过滤 chips 使用 */
    @Query("SELECT DISTINCT scheduler FROM generation_history ORDER BY scheduler")
    fun observeSchedulers(): Flow<List<String>>

    /** 观察历史记录中出现的所有不重复尺寸（宽x高），供过滤 chips 使用，
     *  按面积降序排列，大尺寸优先。 */
    @Query("SELECT DISTINCT (width || 'x' || height) FROM generation_history ORDER BY width * height DESC")
    fun observeSizes(): Flow<List<String>>

    /** 通过 RawQuery 动态查询历史记录 Flow，供 HistoryFilter 驱动列表订阅 */
    @RawQuery(observedEntities = [HistoryEntity::class])
    fun query(filter: SupportSQLiteQuery): Flow<List<HistoryEntity>>

    /** 通过 RawQuery 查询匹配行数 Flow，供选择计数器驱动 */
    @RawQuery(observedEntities = [HistoryEntity::class])
    fun queryCount(filter: SupportSQLiteQuery): Flow<Int>

    /** 通过 RawQuery 一次性查询历史记录列表，供一次性加载场景 */
    @RawQuery
    suspend fun queryOnce(filter: SupportSQLiteQuery): List<HistoryEntity>

    /** 通过 RawQuery 一次性查询匹配的 id 列表，供全选/批量操作 */
    @RawQuery
    suspend fun queryIds(filter: SupportSQLiteQuery): List<Long>

    /** 按 id 列表批量读取历史记录，供批量操作的详情回填 */
    @Query("SELECT * FROM generation_history WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<HistoryEntity>

    /** 观察单条记录的收藏状态变化，供详情页独立订阅 */
    @Query("SELECT favorite FROM generation_history WHERE id = :id")
    fun observeFavorite(id: Long): Flow<Boolean?>

    /** 简单分页查询：按 createdAt 倒序，limit/offset，供列表分页加载。
     *  避免引入 Paging 重依赖，由 ViewModel 管理 offset 状态。 */
    @Query("SELECT * FROM generation_history ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<HistoryEntity>
}

/**
 * PinnedModels - 模型固定 Pin 单例
 *
 * 功能：
 * - 基于 SharedPreferences 持久化用户置顶的模型 ID 列表
 * - 提供 isPinned(id)/pin(id)/unpin(id)/getPinnedIds() 查询与操作接口
 * - observePinned() 返回 Flow<List<String>>，支持 Compose 响应式订阅
 * - sort() 将置顶模型排在列表前，保持 pin 顺序
 * - rename() 支持模型 ID 变更时迁移 pin 记录
 *
 * 对齐参照：modules/local-dream .../data/PinnedModels.kt（object 单例，
 * SharedPreferences 持久化，新行分隔 id 列表）
 *
 * 使用方式：Application.onCreate() 中调用 PinnedModels.initialize(this) 完成初始化，
 * 之后各层通过 PinnedModels.isPinned(id) / pin(id) / observePinned() 等操作。
 */
package com.qihao.open.rwkv.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PinnedModels {

    private const val TAG = "PinnedModels"
    private const val PREFS_NAME = "magicwx_pinned_models"
    private const val KEY = "pinned_model_ids"

    /** 弱引用持有 Application Context，避免静态泄漏；通过 initialize() 注入 */
    private var appContext: Context? = null

    /** SharedPreferences 实例，initialize() 后可用 */
    private var prefs: SharedPreferences? = null

    /** 置顶 ID 列表的响应式流，pin/unpin/rename 时自动更新 */
    private val _pinnedIds = MutableStateFlow<List<String>>(emptyList())

    /** 外部只读订阅：置顶 ID 列表变化时自动发射新值 */
    val pinnedFlow: Flow<List<String>> = _pinnedIds.asStateFlow()

    // ---- 初始化 ----

    /**
     * 初始化单例，注入 Application Context。
     * 应在 Application.onCreate() 中调用一次；重复调用安全（幂等）。
     */
    fun initialize(context: Context) {
        if (appContext != null) return                           // 已初始化，幂等跳过
        appContext = context.applicationContext
        prefs = appContext!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _pinnedIds.value = loadPinnedIds()
        Log.d(TAG, "PinnedModels 初始化完成，已置顶 ${_pinnedIds.value.size} 个模型")
    }

    /** 获取 Context，未初始化时抛出明确异常 */
    private fun requireContext(): Context {
        return appContext ?: error("PinnedModels 未初始化，请先调用 PinnedModels.initialize(context)")
    }

    /** 获取 SharedPreferences，未初始化时抛出明确异常 */
    private fun requirePrefs(): SharedPreferences {
        return prefs ?: error("PinnedModels 未初始化，请先调用 PinnedModels.initialize(context)")
    }

    // ---- 持久化读写 ----

    /** 从 SharedPreferences 读取置顶 ID 列表 */
    private fun loadPinnedIds(): List<String> {
        return requirePrefs().getString(KEY, "").orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }                          // 过滤空行（兼容旧数据）
    }

    /** 将置顶 ID 列表写入 SharedPreferences 并同步 StateFlow */
    private fun savePinnedIds(ids: List<String>) {
        requirePrefs().edit { putString(KEY, ids.joinToString("\n")) }
        _pinnedIds.value = ids
    }

    // ---- 公共查询接口 ----

    /**
     * 判断指定模型 ID 是否已被置顶。
     * @param id 模型唯一标识
     * @return true 表示该模型已在置顶列表中
     */
    fun isPinned(id: String): Boolean = id in _pinnedIds.value

    /**
     * 获取当前置顶的模型 ID 列表（按 pin 顺序，最近置顶的在前）。
     * @return 置顶 ID 列表，无置顶时返回空列表
     */
    fun getPinnedIds(): List<String> = _pinnedIds.value

    /**
     * 观察置顶 ID 列表变化，返回 Flow。
     * 每次 pin/unpin/rename 操作后自动发射新值，适合 Compose collectAsState() 订阅。
     * @return 置顶 ID 列表的响应式流
     */
    fun observePinned(): Flow<List<String>> = pinnedFlow

    // ---- 置顶操作 ----

    /**
     * 将指定模型 ID 置顶。
     * 新置顶的 ID 插入列表最前（最近置顶排第一）；已置顶的 ID 保持原位。
     * @param id 模型唯一标识
     */
    fun pin(id: String) {
        val current = _pinnedIds.value
        if (id in current) {
            Log.d(TAG, "模型已置顶，跳过: $id")
            return
        }
        val updated = listOf(id) + current                       // 新置顶排在最前
        savePinnedIds(updated)
        Log.d(TAG, "模型已置顶: $id, 当前置顶数=${updated.size}")
    }

    /**
     * 批量置顶多个模型 ID。
     * 对齐参照 PinnedModels.pin(context, ids: Collection<String>)：新 ID 插入列表最前。
     * @param ids 待置顶的模型 ID 集合
     */
    fun pin(ids: Collection<String>) {
        val current = _pinnedIds.value
        val toAdd = ids.filter { it !in current }
        if (toAdd.isEmpty()) {
            Log.d(TAG, "所有模型已置顶，跳过批量 pin")
            return
        }
        val updated = toAdd + current                            // 新置顶 IDs 排在最前
        savePinnedIds(updated)
        Log.d(TAG, "批量置顶完成: +${toAdd.size}, 当前置顶数=${updated.size}")
    }

    /**
     * 取消指定模型 ID 的置顶。
     * 如果 ID 不在置顶列表中，无副作用。
     * @param id 模型唯一标识
     */
    fun unpin(id: String) {
        val current = _pinnedIds.value
        if (id !in current) {
            Log.d(TAG, "模型未置顶，跳过取消: $id")
            return
        }
        val updated = current.filter { it != id }
        savePinnedIds(updated)
        Log.d(TAG, "已取消置顶: $id, 当前置顶数=${updated.size}")
    }

    /**
     * 批量取消多个模型 ID 的置顶。
     * 对齐参照 PinnedModels.unpin(context, ids: Collection<String>)。
     * @param ids 待取消置顶的模型 ID 集合
     */
    fun unpin(ids: Collection<String>) {
        val remove = ids.toSet()
        val current = _pinnedIds.value
        val updated = current.filter { it !in remove }
        if (updated.size == current.size) {
            Log.d(TAG, "所有模型未置顶，跳过批量取消")
            return
        }
        savePinnedIds(updated)
        Log.d(TAG, "批量取消置顶完成: -${current.size - updated.size}, 当前置顶数=${updated.size}")
    }

    // ---- 模型 ID 变更 ----

    /**
     * 模型 ID 变更时保持置顶记录，保留其在列表中的位置。
     * 对齐参照 PinnedModels.rename(context, oldId, newId)。
     * @param oldId 旧模型唯一标识
     * @param newId 新模型唯一标识
     */
    fun rename(oldId: String, newId: String) {
        val current = _pinnedIds.value
        if (oldId !in current) {
            Log.d(TAG, "旧 ID 不在置顶列表中，跳过 rename: $oldId → $newId")
            return
        }
        val updated = current.map { if (it == oldId) newId else it }
        savePinnedIds(updated)
        Log.d(TAG, "置顶记录已迁移: $oldId → $newId")
    }

    // ---- 排序工具 ----

    /**
     * 将模型列表按置顶状态排序：置顶模型排在前面，保持 pin 顺序；其余模型保持原顺序。
     * 对齐参照 PinnedModels.sort(models, pinnedIds)。
     *
     * @param models 待排序的模型元信息列表
     * @return 排序后的模型列表（置顶模型在前）
     */
    fun sort(models: List<ModelInfo>): List<ModelInfo> {
        val pinnedIds = _pinnedIds.value
        if (pinnedIds.isEmpty()) return models                    // 无置顶时保持原顺序
        val rank = pinnedIds.withIndex().associate { (i, id) -> id to i }
        val (pinned, others) = models.partition { it.id in rank }
        return pinned.sortedBy { rank.getValue(it.id) } + others  // 置顶按 pin 顺序，其余不变
    }
}
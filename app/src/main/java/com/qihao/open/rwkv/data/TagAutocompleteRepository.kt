/**
 * TagAutocompleteRepository.kt - Tag 自动补全仓库（单例）
 *
 * 功能：
 * - 从 assets/tags/ 内置 CSV 加载 Danbooru 标签集（约 200 条质量/外貌/画风标签）
 * - 支持外部 CSV 导入覆盖内置数据（importMainCsv / importTranslationCsv）
 * - 基于 fzf 模糊匹配 + 位图预过滤 + Damerau-Levenshtein 纠错的高效补全建议
 * - 二进制缓存加速后续启动
 * - 提供 extractActiveTag / applySuggestion / adjustActiveTagWeight / clearActiveTag 等文本操作工具
 *
 * 加载策略：优先外部 CSV（用户导入），否则从 assets 内置加载
 *
 * 移植自：modules/local-dream app/.../data/TagAutocompleteRepository.kt
 * 适配：assets 内置 CSV 为主数据源，保留外部 CSV 覆盖路径
 */
package com.qihao.open.rwkv.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.qihao.open.rwkv.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TagAutocompleteRepository private constructor(private val context: Context) {
    private val loadMutex = Mutex()

    @Volatile
    private var loadedData: TagData? = null

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dictDir = File(context.filesDir, DICT_DIR).also { it.mkdirs() }
    private val mainFile = File(dictDir, MAIN_FILE_NAME)
    private val translationFile = File(dictDir, TRANSLATION_FILE_NAME)
    private val cacheFile = File(dictDir, CACHE_FILE_NAME)

    private val _state = MutableStateFlow(readStateFromDisk())
    val state: StateFlow<DictionaryState> = _state.asStateFlow()

    /** 预热：确保词典数据已加载到内存 */
    suspend fun warmUp() {
        if (!state.value.mainImported) return
        ensureLoaded()
    }

    /**
     * 查询补全建议。
     * 自动检测查询语言：含非 ASCII 字母（中文等）走翻译匹配，否则走英文模糊匹配。
     *
     * @param query 用户输入的查询文本
     * @param limit 最大返回条数，默认 12
     * @return 按得分降序 + 人气降序排列的补全建议列表
     */
    suspend fun suggest(query: String, limit: Int = 12): List<TagSuggestion> {
        // mainImported 仅反映外部 CSV 导入；assets 内置 CSV 由 ensureLoaded() 加载，
        // 此处不再用 mainImported 拦截，否则 assets 内置模式下候选永远空（bug 修复）
        val isTranslationQuery = containsNonAsciiLetter(query)
        if (isTranslationQuery && !state.value.translationImported) return emptyList()

        val normalizedQuery = normalizeQuery(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val data = ensureLoaded() ?: return emptyList()

        return withContext(Dispatchers.Default) {
            if (isTranslationQuery) {
                suggestByTranslation(data, normalizeTranslation(query), limit)
            } else {
                suggestByEnglish(data, normalizedQuery, limit)
            }
        }
    }

    // ============================================================
    // 外部 CSV 导入（覆盖内置数据）
    // ============================================================

    /**
     * 从外部 URI 导入主标签 CSV，覆盖内置 assets 数据。
     * @param uri 外部 CSV 文件的 content URI
     * @param displayName 显示名称（可为 null）
     * @return 导入结果
     */
    suspend fun importMainCsv(uri: Uri, displayName: String?): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val lineCount = copyUriToFile(uri, mainFile) { cells ->
                cells.getOrNull(0)?.trim()?.removePrefix("﻿").isNullOrEmpty().not()
            }
            if (lineCount == 0) {
                mainFile.delete()
                return@withContext ImportResult.Error("empty")
            }
            invalidate()
            prefs.edit {
                putString(KEY_MAIN_NAME, displayName ?: MAIN_FILE_NAME)
                putInt(KEY_MAIN_LINES, lineCount)
            }
            _state.value = readStateFromDisk()
            ImportResult.Success(lineCount)
        }.getOrElse {
            mainFile.delete()
            ImportResult.Error(it.message ?: "unknown")
        }
    }

    /**
     * 从外部 URI 导入翻译 CSV，覆盖内置 assets 翻译数据。
     * @param uri 外部 CSV 文件的 content URI
     * @param displayName 显示名称（可为 null）
     * @return 导入结果
     */
    suspend fun importTranslationCsv(uri: Uri, displayName: String?): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val lineCount = copyUriToFile(uri, translationFile) { cells ->
                if (cells.size < 2) return@copyUriToFile false
                val key = cells[0].trim().removePrefix("﻿")
                if (key.isEmpty()) return@copyUriToFile false
                extractTranslation(cells) != null
            }
            if (lineCount == 0) {
                translationFile.delete()
                return@withContext ImportResult.Error("empty")
            }
            invalidate()
            prefs.edit {
                putString(KEY_TRANSLATION_NAME, displayName ?: TRANSLATION_FILE_NAME)
                putInt(KEY_TRANSLATION_LINES, lineCount)
            }
            _state.value = readStateFromDisk()
            ImportResult.Success(lineCount)
        }.getOrElse {
            translationFile.delete()
            ImportResult.Error(it.message ?: "unknown")
        }
    }

    /** 清除外部导入的主标签 CSV（回退到 assets 内置数据） */
    fun clearMainCsv() {
        mainFile.delete()
        prefs.edit {
            remove(KEY_MAIN_NAME)
            remove(KEY_MAIN_LINES)
        }
        invalidate()
        _state.value = readStateFromDisk()
    }

    /** 清除外部导入的翻译 CSV（回退到 assets 内置数据） */
    fun clearTranslationCsv() {
        translationFile.delete()
        prefs.edit {
            remove(KEY_TRANSLATION_NAME)
            remove(KEY_TRANSLATION_LINES)
        }
        invalidate()
        _state.value = readStateFromDisk()
    }

    // ============================================================
    // 内部：缓存与数据加载
    // ============================================================

    /** 使内存缓存和磁盘缓存失效 */
    private fun invalidate() {
        loadedData = null
        if (cacheFile.exists()) cacheFile.delete()
    }

    /**
     * 从磁盘状态读取词典导入状态。
     * 外部文件存在则优先，否则检查 assets 内置数据是否可用。
     */
    private fun readStateFromDisk(): DictionaryState {
        val mainImported = mainFile.exists() && mainFile.length() > 0
        val translationImported = translationFile.exists() && translationFile.length() > 0
        // 若外部文件存在，使用外部文件信息；否则标记为 assets 内置
        if (mainImported) {
            return DictionaryState(
                mainImported = true,
                mainFileName = prefs.getString(KEY_MAIN_NAME, null),
                mainEntryCount = prefs.getInt(KEY_MAIN_LINES, 0),
                translationImported = translationImported,
                translationFileName = if (translationImported) prefs.getString(KEY_TRANSLATION_NAME, null) else null,
                translationEntryCount = if (translationImported) prefs.getInt(KEY_TRANSLATION_LINES, 0) else 0,
            )
        }
        // 无外部文件，尝试检查 assets 内置数据
        val assetsAvailable = try {
            context.assets.open("tags/main.csv").use { it.read() }
            true
        } catch (_: Exception) {
            false
        }
        return DictionaryState(
            mainImported = assetsAvailable,
            mainFileName = if (assetsAvailable) "内置标签" else null,
            mainEntryCount = if (assetsAvailable) prefs.getInt(KEY_ASSETS_MAIN_LINES, 0) else 0,
            translationImported = assetsAvailable,
            translationFileName = if (assetsAvailable) "内置翻译" else null,
            translationEntryCount = if (assetsAvailable) prefs.getInt(KEY_ASSETS_TRANSLATION_LINES, 0) else 0,
        )
    }

    /** 确保数据已加载（双重检查锁 + Mutex），返回 TagData 或 null */
    private suspend fun ensureLoaded(): TagData? {
        loadedData?.let { return it }
        return loadMutex.withLock {
            loadedData?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) { loadData() }
            loadedData = loaded
            loaded
        }
    }

    /**
     * 加载数据主流程：
     * 1. 优先检查外部文件（用户导入的 CSV）
     * 2. 无外部文件时从 assets 内置加载
     * 3. 尝试二进制缓存命中
     */
    private fun loadData(): TagData? {
        val source = resolveDataSource()
        if (source == DataSource.NONE) return null

        val cached = loadEntriesFromCache(source)
        val entries: List<TagEntry>
        val englishBitmaps: LongArray
        if (cached != null) {
            entries = cached.first
            englishBitmaps = cached.second
            Log.d(TAG, "loadData: 缓存命中, source=$source, entries=${entries.size}")
        } else {
            val parsed = when (source) {
                DataSource.EXTERNAL -> parseCsvEntries()
                DataSource.ASSETS -> parseAssetsEntries()
                DataSource.NONE -> return null
            } ?: return null
            englishBitmaps = computeEnglishBitmaps(parsed)
            saveEntriesToCache(parsed, englishBitmaps, source)
            entries = parsed
            Log.d(TAG, "loadData: 解析完成, source=$source, entries=${entries.size}")
        }
        return buildIndexes(entries, englishBitmaps)
    }

    /** 数据源枚举：外部文件 / assets 内置 / 无 */
    private enum class DataSource { EXTERNAL, ASSETS, NONE }

    /** 判断当前数据源 */
    private fun resolveDataSource(): DataSource {
        if (mainFile.exists() && mainFile.length() > 0) return DataSource.EXTERNAL
        return try {
            context.assets.open("tags/main.csv").use { DataSource.ASSETS }
        } catch (_: Exception) {
            DataSource.NONE
        }
    }

    // ============================================================
    // Assets 内置 CSV 解析
    // ============================================================

    /**
     * 从 assets 加载内置主标签 CSV 和翻译 CSV。
     * 与外部文件解析共享同一套条目构建逻辑，但数据源不同。
     */
    private fun parseAssetsEntries(): List<TagEntry>? {
        return try {
            // 加载翻译映射
            val translationMap = try {
                context.assets.open("tags/translation.csv").use { loadTranslationMapFromStream(it) }
            } catch (_: Exception) {
                Log.w(TAG, "parseAssetsEntries: 翻译文件不可用，跳过翻译")
                emptyMap()
            }
            // 加载主标签
            context.assets.open("tags/main.csv").use { input ->
                parseEntriesFromStream(input, translationMap) { estimated ->
                    prefs.edit {
                        putInt(KEY_ASSETS_MAIN_LINES, estimated)
                    }
                }
            }.also { entries ->
                // 更新翻译条目计数
                if (translationMap.isNotEmpty()) {
                    prefs.edit { putInt(KEY_ASSETS_TRANSLATION_LINES, translationMap.size) }
                }
                _state.value = readStateFromDisk()
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseAssetsEntries: 加载失败", e)
            null
        }
    }

    /**
     * 从 InputStream 解析翻译映射（english → translation）。
     * 与外部文件 loadTranslationMap 共享同一逻辑，仅数据源不同。
     */
    private fun loadTranslationMapFromStream(input: InputStream): Map<String, String> {
        val map = HashMap<String, String>(256)
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                val cells = parseCsvLine(line)
                if (cells.size < 2) return@forEach
                val english = cells[0].trim().removePrefix("﻿")
                if (english.isEmpty()) return@forEach
                val translation = extractTranslation(cells) ?: return@forEach
                map[english] = translation
            }
        }
        return map
    }

    /**
     * 从 InputStream 解析主标签条目列表。
     * 与外部文件 parseCsvEntries 共享同一逻辑，仅数据源不同。
     * @param onParsed 解析完成后回调，传入条目数（用于更新 SharedPreferences）
     */
    private fun parseEntriesFromStream(
        input: InputStream,
        translationMap: Map<String, String>,
        onParsed: (Int) -> Unit,
    ): List<TagEntry>? {
        val entries = ArrayList<TagEntry>(256)
        val englishSet = HashSet<String>(512)

        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                val cells = parseCsvLine(line)
                if (cells.isEmpty()) return@forEach

                val english = cells.getOrNull(0)?.trim()?.removePrefix("﻿").orEmpty()
                if (english.isEmpty()) return@forEach
                // 去重：同一英文名只保留首次出现
                if (!englishSet.add(english)) return@forEach

                val category = cells.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                val postCount = cells.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                val aliases = cells.getOrNull(3)
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()

                val translation = translationMap[english]
                entries += TagEntry(
                    english = english,
                    translation = translation,
                    category = category,
                    postCount = postCount,
                    aliases = aliases,
                    normalizedEnglish = normalizeQuery(english),
                    normalizedAliases = aliases.map(::normalizeQuery).filter { it.isNotEmpty() },
                    normalizedTranslation = translation?.let(::normalizeTranslation),
                )
            }
        }
        onParsed(entries.size)
        return entries
    }

    // ============================================================
    // 外部文件 CSV 解析（与原版一致）
    // ============================================================

    /** 为每个条目计算英文名+别名组合的字符存在位图 */
    private fun computeEnglishBitmaps(entries: List<TagEntry>): LongArray {
        val bitmaps = LongArray(entries.size * CharBitmap.WORDS)
        for (i in entries.indices) {
            val offset = i * CharBitmap.WORDS
            val entry = entries[i]
            CharBitmap.addInto(bitmaps, offset, entry.normalizedEnglish)
            for (alias in entry.normalizedAliases) CharBitmap.addInto(bitmaps, offset, alias)
        }
        return bitmaps
    }

    /**
     * 从外部文件解析主标签 CSV。
     * 优先读取翻译文件构建翻译映射，再逐行解析主标签。
     */
    private fun parseCsvEntries(): List<TagEntry>? {
        if (!mainFile.exists()) return null
        val translationMap = if (translationFile.exists()) loadTranslationMap() else emptyMap()
        val estimated = prefs.getInt(KEY_MAIN_LINES, 0).coerceAtLeast(16)
        val entries = ArrayList<TagEntry>(estimated)
        val englishSet = HashSet<String>(estimated * 2)

        mainFile.inputStream().use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isEmpty()) return@forEach
                    val cells = parseCsvLine(line)
                    if (cells.isEmpty()) return@forEach

                    val english = cells.getOrNull(0)?.trim()?.removePrefix("﻿").orEmpty()
                    if (english.isEmpty()) return@forEach
                    if (!englishSet.add(english)) return@forEach

                    val category = cells.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    val postCount = cells.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                    val aliases = cells.getOrNull(3)
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        .orEmpty()

                    val translation = translationMap[english]
                    entries += TagEntry(
                        english = english,
                        translation = translation,
                        category = category,
                        postCount = postCount,
                        aliases = aliases,
                        normalizedEnglish = normalizeQuery(english),
                        normalizedAliases = aliases.map(::normalizeQuery).filter { it.isNotEmpty() },
                        normalizedTranslation = translation?.let(::normalizeTranslation),
                    )
                }
            }
        }
        return entries
    }

    // ============================================================
    // 索引构建
    // ============================================================

    /**
     * 构建搜索索引：按首字符分桶、翻译条目列表、英文位图、翻译位图。
     * 首字符分桶仅用于拼写纠错回退；模糊匹配本身扫描全量条目列表。
     */
    private fun buildIndexes(entries: List<TagEntry>, englishBitmaps: LongArray): TagData {
        val englishByHead = HashMap<Char, MutableList<TagEntry>>(64)
        val aliasByHead = HashMap<Char, MutableList<AliasRef>>(64)
        val translationEntries = ArrayList<TagEntry>(entries.size)

        for (entry in entries) {
            entry.normalizedEnglish.firstOrNull()?.let { head ->
                englishByHead.getOrPut(head) { mutableListOf() } += entry
            }
            for (alias in entry.normalizedAliases) {
                alias.firstOrNull()?.let { head ->
                    aliasByHead.getOrPut(head) { mutableListOf() } += AliasRef(entry, alias)
                }
            }
            if (!entry.normalizedTranslation.isNullOrEmpty()) {
                translationEntries += entry
            }
        }

        // 翻译位图从已缓存条目中推导，仅需几毫秒
        val translationBitmaps = LongArray(translationEntries.size * CharBitmap.WORDS)
        for (i in translationEntries.indices) {
            translationEntries[i].normalizedTranslation?.let { normalized ->
                CharBitmap.addInto(translationBitmaps, i * CharBitmap.WORDS, normalized)
            }
        }

        return TagData(
            entries = entries,
            englishByHead = englishByHead,
            aliasByHead = aliasByHead,
            translationEntries = translationEntries,
            englishBitmaps = englishBitmaps,
            translationBitmaps = translationBitmaps,
        )
    }

    /** 从外部翻译文件加载 english→translation 映射 */
    private fun loadTranslationMap(): Map<String, String> {
        val estimated = prefs.getInt(KEY_TRANSLATION_LINES, 0).coerceAtLeast(16)
        val map = HashMap<String, String>(estimated * 2)
        translationFile.inputStream().use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isEmpty()) return@forEach
                    val cells = parseCsvLine(line)
                    if (cells.size < 2) return@forEach
                    val english = cells[0].trim().removePrefix("﻿")
                    if (english.isEmpty()) return@forEach
                    val translation = extractTranslation(cells) ?: return@forEach
                    map[english] = translation
                }
            }
        }
        return map
    }

    // ============================================================
    // 二进制缓存读写
    // ============================================================

    /**
     * 从二进制缓存加载条目和位图。
     * 缓存校验：根据数据源类型检查版本号或文件尺寸/mtime。
     */
    private fun loadEntriesFromCache(source: DataSource): Pair<List<TagEntry>, LongArray>? {
        if (!cacheFile.exists()) return null
        return runCatching {
            DataInputStream(BufferedInputStream(cacheFile.inputStream())).use { input ->
                // 读取缓存头
                if (input.readInt() != CACHE_MAGIC) return@use null
                if (input.readInt() != CACHE_VERSION) return@use null
                val cachedSourceType = input.readInt()

                when (cachedSourceType) {
                    CACHE_SOURCE_EXTERNAL -> {
                        // 外部文件：校验文件尺寸和修改时间
                        val cachedMainSize = input.readLong()
                        val cachedMainMtime = input.readLong()
                        val cachedTransSize = input.readLong()
                        val cachedTransMtime = input.readLong()
                        val currentMainSize = mainFile.length()
                        val currentMainMtime = mainFile.lastModified()
                        val currentTransSize = if (translationFile.exists()) translationFile.length() else 0L
                        val currentTransMtime = if (translationFile.exists()) translationFile.lastModified() else 0L
                        if (cachedMainSize != currentMainSize ||
                            cachedMainMtime != currentMainMtime ||
                            cachedTransSize != currentTransSize ||
                            cachedTransMtime != currentTransMtime
                        ) {
                            Log.d(TAG, "loadEntriesFromCache: 外部文件已变更，缓存失效")
                            return@use null
                        }
                    }
                    CACHE_SOURCE_ASSETS -> {
                        // assets 内置：校验 app versionCode
                        val cachedVersionCode = input.readInt()
                        if (cachedVersionCode != BuildConfig.VERSION_CODE) {
                            Log.d(TAG, "loadEntriesFromCache: 版本号变更(${cachedVersionCode}→${BuildConfig.VERSION_CODE})，缓存失效")
                            return@use null
                        }
                    }
                    else -> {
                        Log.w(TAG, "loadEntriesFromCache: 未知数据源类型 $cachedSourceType")
                        return@use null
                    }
                }

                // 读取条目数据
                val count = input.readInt()
                if (count < 0) return@use null
                val list = ArrayList<TagEntry>(count)
                repeat(count) {
                    val english = input.readUTF()
                    val translation = if (input.readBoolean()) input.readUTF() else null
                    val category = input.readInt()
                    val postCount = input.readInt()
                    val aliasCount = input.readInt()
                    val aliases = if (aliasCount == 0) {
                        emptyList()
                    } else {
                        ArrayList<String>(aliasCount).also { list2 ->
                            repeat(aliasCount) { list2 += input.readUTF() }
                        }
                    }
                    val normalizedEnglish = input.readUTF()
                    val normalizedAliasCount = input.readInt()
                    val normalizedAliases = if (normalizedAliasCount == 0) {
                        emptyList()
                    } else {
                        ArrayList<String>(normalizedAliasCount).also { list2 ->
                            repeat(normalizedAliasCount) { list2 += input.readUTF() }
                        }
                    }
                    val normalizedTranslation = if (input.readBoolean()) input.readUTF() else null
                    list += TagEntry(
                        english = english,
                        translation = translation,
                        category = category,
                        postCount = postCount,
                        aliases = aliases,
                        normalizedEnglish = normalizedEnglish,
                        normalizedAliases = normalizedAliases,
                        normalizedTranslation = normalizedTranslation,
                    )
                }
                // 读取位图
                val bitmaps = LongArray(count * CharBitmap.WORDS)
                for (k in bitmaps.indices) bitmaps[k] = input.readLong()
                list to bitmaps
            }
        }.getOrElse {
            Log.w(TAG, "loadEntriesFromCache: 缓存读取失败", it)
            cacheFile.delete()
            null
        }
    }

    /**
     * 将条目和位图写入二进制缓存。
     * 缓存头包含数据源类型及对应校验信息。
     */
    private fun saveEntriesToCache(entries: List<TagEntry>, englishBitmaps: LongArray, source: DataSource) {
        runCatching {
            DataOutputStream(BufferedOutputStream(cacheFile.outputStream())).use { output ->
                output.writeInt(CACHE_MAGIC)
                output.writeInt(CACHE_VERSION)
                when (source) {
                    DataSource.EXTERNAL -> {
                        output.writeInt(CACHE_SOURCE_EXTERNAL)
                        output.writeLong(mainFile.length())
                        output.writeLong(mainFile.lastModified())
                        output.writeLong(if (translationFile.exists()) translationFile.length() else 0L)
                        output.writeLong(if (translationFile.exists()) translationFile.lastModified() else 0L)
                    }
                    DataSource.ASSETS -> {
                        output.writeInt(CACHE_SOURCE_ASSETS)
                        output.writeInt(BuildConfig.VERSION_CODE)
                    }
                    DataSource.NONE -> return@runCatching
                }
                output.writeInt(entries.size)
                for (entry in entries) {
                    output.writeUTF(entry.english)
                    val translation = entry.translation
                    if (translation != null) {
                        output.writeBoolean(true)
                        output.writeUTF(translation)
                    } else {
                        output.writeBoolean(false)
                    }
                    output.writeInt(entry.category)
                    output.writeInt(entry.postCount)
                    output.writeInt(entry.aliases.size)
                    for (a in entry.aliases) output.writeUTF(a)
                    output.writeUTF(entry.normalizedEnglish)
                    output.writeInt(entry.normalizedAliases.size)
                    for (a in entry.normalizedAliases) output.writeUTF(a)
                    val normTr = entry.normalizedTranslation
                    if (normTr != null) {
                        output.writeBoolean(true)
                        output.writeUTF(normTr)
                    } else {
                        output.writeBoolean(false)
                    }
                }
                for (value in englishBitmaps) output.writeLong(value)
            }
            Log.d(TAG, "saveEntriesToCache: 缓存已写入, source=$source, entries=${entries.size}")
        }.onFailure {
            Log.w(TAG, "saveEntriesToCache: 缓存写入失败", it)
            cacheFile.delete()
        }
    }

    // ============================================================
    // 翻译字段提取
    // ============================================================

    /**
     * 从 CSV 单元格列表中提取翻译文本。
     * 跳过第 0 列（英文名），取第一个非空、非纯数字的字段作为翻译。
     */
    private fun extractTranslation(cells: List<String>): String? {
        for (i in 1 until cells.size) {
            val raw = cells[i].trim().removePrefix("﻿")
            if (raw.isEmpty()) continue
            if (raw.toIntOrNull() != null) continue
            if (raw.toDoubleOrNull() != null) continue
            return raw
        }
        return null
    }

    // ============================================================
    // 翻译查询（中文搜索）
    // ============================================================

    /** 按翻译字段匹配：遍历翻译条目列表，位图预过滤 + 模糊评分 */
    private fun suggestByTranslation(data: TagData, normalizedQuery: String, limit: Int): List<TagSuggestion> {
        if (normalizedQuery.isEmpty() || limit <= 0) return emptyList()
        val pattern = normalizedQuery.toCharArray()
        val queryBitmap = CharBitmap.of(normalizedQuery)
        val q0 = queryBitmap[0]
        val q1 = queryBitmap[1]

        val entries = data.translationEntries
        val bitmaps = data.translationBitmaps
        val topK = TopKLongs(limit)
        for (i in entries.indices) {
            if (!CharBitmap.contains(bitmaps, i * CharBitmap.WORDS, q0, q1)) continue
            val normalized = entries[i].normalizedTranslation ?: continue
            val fuzzyScore = FuzzyMatcher.score(pattern, normalized)
            if (fuzzyScore == FuzzyMatcher.NO_MATCH) continue
            topK.offer(fuzzyScore + popularityBonus(entries[i].postCount), i)
        }

        val results = ArrayList<TagSuggestion>(limit)
        topK.forEach { score, index ->
            val entry = entries[index]
            results += TagSuggestion(
                replacementTag = entry.english,
                primaryText = entry.english,
                secondaryText = entry.translation,
                matchType = TagMatchType.Translation,
                category = entry.category,
                postCount = entry.postCount,
                score = score,
            )
        }

        return results
            .sortedWith(compareByDescending<TagSuggestion> { it.score }.thenByDescending { it.postCount })
            .take(limit)
    }

    // ============================================================
    // 英文查询（模糊匹配 + 纠错回退）
    // ============================================================

    /**
     * 按英文名+别名模糊匹配：遍历全量条目，位图预过滤 + 模糊评分，
     * 匹配不足时拼写纠错回退补充。
     */
    private fun suggestByEnglish(data: TagData, normalizedQuery: String, limit: Int): List<TagSuggestion> {
        if (normalizedQuery.isEmpty() || limit <= 0) return emptyList()
        val pattern = normalizedQuery.toCharArray()
        val queryBitmap = CharBitmap.of(normalizedQuery)
        val q0 = queryBitmap[0]
        val q1 = queryBitmap[1]

        val entries = data.entries
        val bitmaps = data.englishBitmaps
        val topK = TopKLongs(limit)
        for (i in entries.indices) {
            // 位图预过滤：快速拒绝不包含所有查询字符的条目
            if (!CharBitmap.contains(bitmaps, i * CharBitmap.WORDS, q0, q1)) continue
            val entry = entries[i]
            // 对英文名和所有别名取最佳模糊匹配分
            var best = FuzzyMatcher.score(pattern, entry.normalizedEnglish)
            for (alias in entry.normalizedAliases) {
                val aliasScore = FuzzyMatcher.score(pattern, alias)
                if (aliasScore > best) best = aliasScore
            }
            if (best == FuzzyMatcher.NO_MATCH) continue
            topK.offer(best + popularityBonus(entry.postCount), i)
        }

        val results = ArrayList<TagSuggestion>(limit + limit)
        val matched = HashSet<String>()
        topK.forEach { score, index ->
            val entry = entries[index]
            results += buildEnglishSuggestion(entry, pattern, score)
            matched += entry.english
        }

        // 模糊匹配不足时，以 Damerau-Levenshtein 编辑距离纠错回退补充
        if (results.size < limit) {
            appendCorrections(data, normalizedQuery, matched, results)
        }

        return results
            .sortedWith(compareByDescending<TagSuggestion> { it.score }.thenByDescending { it.postCount })
            .take(limit)
    }

    /**
     * 对 Top-K 幸存者反推匹配来源（英文名 vs 别名），以决定展示字段和匹配类型。
     */
    private fun buildEnglishSuggestion(entry: TagEntry, pattern: CharArray, score: Int): TagSuggestion {
        val englishScore = FuzzyMatcher.score(pattern, entry.normalizedEnglish)
        var aliasScore = FuzzyMatcher.NO_MATCH
        var aliasValue: String? = null
        for (alias in entry.normalizedAliases) {
            val candidateScore = FuzzyMatcher.score(pattern, alias)
            if (candidateScore > aliasScore) {
                aliasScore = candidateScore
                aliasValue = alias
            }
        }
        return if (englishScore >= aliasScore) {
            buildSuggestion(entry, TagMatchType.Prefix, score)
        } else {
            buildSuggestion(entry, TagMatchType.Alias, score, aliasValue)
        }
    }

    /**
     * 拼写纠错回退：对首字符匹配的候选条目计算 Damerau-Levenshtein 编辑距离，
     * 距离在阈值内的作为 Correction 类型建议。
     */
    private fun appendCorrections(
        data: TagData,
        normalizedQuery: String,
        matched: Set<String>,
        out: MutableList<TagSuggestion>,
    ) {
        val head = normalizedQuery.first()
        val threshold = correctionThreshold(normalizedQuery.length)
        val candidates = HashSet<TagEntry>()
        candidates += data.englishByHead[head].orEmpty()
        for (ref in data.aliasByHead[head].orEmpty()) candidates += ref.entry

        for (entry in candidates) {
            if (entry.english in matched) continue

            val englishDistance = if (abs(entry.normalizedEnglish.length - normalizedQuery.length) <= threshold &&
                entry.normalizedEnglish.firstOrNull() == head
            ) {
                damerauLevenshtein(normalizedQuery, entry.normalizedEnglish, threshold)
            } else {
                threshold + 1
            }
            var bestDistance = englishDistance
            var matchedAlias: String? = null

            if (bestDistance > threshold) {
                for (aliasValue in entry.normalizedAliases) {
                    if (abs(aliasValue.length - normalizedQuery.length) > threshold) continue
                    if (aliasValue.firstOrNull() != head) continue
                    val aliasDistance = damerauLevenshtein(normalizedQuery, aliasValue, threshold)
                    if (aliasDistance < bestDistance) {
                        bestDistance = aliasDistance
                        matchedAlias = aliasValue
                    }
                }
            }

            if (bestDistance <= threshold) {
                out += buildSuggestion(
                    entry,
                    TagMatchType.Correction,
                    CORRECTION_BASE - bestDistance * CORRECTION_DISTANCE_PENALTY + popularityBonus(entry.postCount),
                    matchedAlias,
                )
            }
        }
    }

    /** 构建单条 TagSuggestion */
    private fun buildSuggestion(
        entry: TagEntry,
        matchType: TagMatchType,
        score: Int,
        aliasValue: String? = null,
    ): TagSuggestion {
        val secondary = when (matchType) {
            TagMatchType.Alias -> aliasValue?.let(::tagUnderscoresToSpaces)
            else -> entry.translation
        }
        return TagSuggestion(
            replacementTag = entry.english,
            primaryText = entry.english,
            secondaryText = secondary,
            matchType = matchType,
            category = entry.category,
            postCount = entry.postCount,
            score = score,
        )
    }

    // ============================================================
    // 外部 URI 文件复制
    // ============================================================

    /**
     * 从 content URI 复制 CSV 到内部存储，同时过滤无效行。
     * @param uri 外部 CSV 的 content URI
     * @param target 目标文件
     * @param isValidRow 行有效性判断函数
     * @return 有效行数
     */
    private fun copyUriToFile(uri: Uri, target: File, isValidRow: (List<String>) -> Boolean): Int {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开 URI: $uri")
        var validRows = 0
        input.use { stream ->
            target.outputStream().use { out ->
                val reader = BufferedReader(InputStreamReader(stream))
                val writer = out.bufferedWriter()
                reader.forEachLine { rawLine ->
                    val line = rawLine.trim()
                    if (line.isEmpty()) return@forEachLine
                    val cells = parseCsvLine(line)
                    if (!isValidRow(cells)) return@forEachLine
                    writer.write(rawLine)
                    writer.newLine()
                    validRows++
                }
                writer.flush()
            }
        }
        return validRows
    }

    // ============================================================
    // 评分与阈值辅助
    // ============================================================

    /**
     * 人气加成：高分 tag 在同等模糊匹配下优先展示。
     * 加成幅度与模糊分同量级，避免人气压倒明显更优的匹配。
     */
    private fun popularityBonus(postCount: Int): Int = when {
        postCount >= 1_000_000 -> 30
        postCount >= 100_000 -> 22
        postCount >= 10_000 -> 15
        postCount >= 1_000 -> 9
        postCount >= 100 -> 4
        else -> 0
    }

    /** 拼写纠错的最大编辑距离阈值：短词 1，中词 2，长词 3 */
    private fun correctionThreshold(length: Int): Int = when {
        length <= 4 -> 1
        length <= 8 -> 2
        else -> 3
    }

    // ============================================================
    // 内部数据类
    // ============================================================

    /**
     * 纯持有类（不参与比较），避免 LongArray 字段拖入 data class 的
     * 引用型 equals/hashCode 开销。
     */
    private class TagData(
        val entries: List<TagEntry>,
        val englishByHead: Map<Char, List<TagEntry>>,
        val aliasByHead: Map<Char, List<AliasRef>>,
        val translationEntries: List<TagEntry>,
        val englishBitmaps: LongArray,
        val translationBitmaps: LongArray,
    )

    private data class AliasRef(val entry: TagEntry, val alias: String)

    // ============================================================
    // 伴生对象：常量、单例、文本操作工具
    // ============================================================

    companion object {
        private const val TAG = "TagAutocomplete"

        // SharedPreferences 键
        private const val PREFS_NAME = "tag_autocomplete_prefs"
        private const val DICT_DIR = "tagcomplete"
        private const val MAIN_FILE_NAME = "main.csv"
        private const val TRANSLATION_FILE_NAME = "translation.csv"
        private const val CACHE_FILE_NAME = "dict.bin"
        private const val CACHE_MAGIC = 0x54444333 // "TDC3"
        private const val CACHE_VERSION = 4 // 升级：新增 sourceType 字段
        private const val CACHE_SOURCE_EXTERNAL = 1
        private const val CACHE_SOURCE_ASSETS = 2

        // 纠错评分常量：低于模糊匹配但高于稀疏大间隔匹配
        private const val CORRECTION_BASE = 60
        private const val CORRECTION_DISTANCE_PENALTY = 25

        // 外部文件 SharedPreferences 键
        private const val KEY_MAIN_NAME = "main_csv_name"
        private const val KEY_MAIN_LINES = "main_csv_lines"
        private const val KEY_TRANSLATION_NAME = "translation_csv_name"
        private const val KEY_TRANSLATION_LINES = "translation_csv_lines"
        // assets 内置数据行数缓存键
        private const val KEY_ASSETS_MAIN_LINES = "assets_main_csv_lines"
        private const val KEY_ASSETS_TRANSLATION_LINES = "assets_translation_csv_lines"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TagAutocompleteRepository? = null

        /** 获取单例实例 */
        fun getInstance(context: Context): TagAutocompleteRepository =
            instance ?: synchronized(this) {
                instance ?: TagAutocompleteRepository(context.applicationContext).also {
                    instance = it
                }
            }

        // ============================================================
        // 文本操作工具（静态方法，不依赖实例状态）
        // ============================================================

        /**
         * 从文本中提取光标位置的活跃 tag token。
         * 以逗号作为 tag 分隔符，取光标所在段的文本。
         *
         * @param text 完整 prompt 文本
         * @param selection 光标位置（UTF-16 偏移）
         * @return 活跃 tag 上下文，光标不在有效位置时返回 null
         */
        fun extractActiveTag(text: String, selection: Int): ActiveTagContext? {
            if (selection <= 0 || selection > text.length) return null
            // 找光标前最近的逗号作为段起始
            val segmentStart = text.lastIndexOf(',', startIndex = selection - 1).let {
                if (it == -1) 0 else it + 1
            }
            // 找光标后最近的逗号作为段结束
            val segmentEnd = text.indexOf(',', startIndex = selection).let {
                if (it == -1) text.length else it
            }
            // 跳过段起始的空白字符
            var trimmedStart = segmentStart
            while (trimmedStart < selection && text[trimmedStart].isWhitespace()) trimmedStart++
            val token = text.substring(trimmedStart, selection).trim()
            if (token.isEmpty()) return null
            return ActiveTagContext(
                token = token,
                trimmedStart = trimmedStart,
                trimmedEnd = selection,
                segmentEnd = segmentEnd,
            )
        }

        /**
         * 将补全建议应用到文本中，替换光标所在 tag 段。
         * 自动处理逗号分隔符和括号转义。
         *
         * @param text 原始文本
         * @param selection 光标位置
         * @param suggestion 选中的补全建议
         * @return (新文本, 新光标位置)
         */
        fun applySuggestion(text: String, selection: Int, suggestion: TagSuggestion): Pair<String, Int> {
            val context = extractActiveTag(text, selection) ?: return text to selection
            val prefix = text.substring(0, context.trimmedStart)
            val suffix = text.substring(context.segmentEnd)
            val separator = if (suffix.startsWith(",")) "" else ", "
            // 嵌入类型保持下划线格式（用于文件名匹配），其他类型转义括号+下划线转空格
            val core = if (suggestion.matchType == TagMatchType.Embedding) {
                suggestion.replacementTag
            } else {
                escapePromptParentheses(tagUnderscoresToSpaces(suggestion.replacementTag))
            }
            val replacement = core + separator
            val updated = prefix + replacement + suffix.trimStart()
            return updated to (prefix.length + replacement.length)
        }

        // ============================================================
        // Tag 段解析（内部）
        // ============================================================

        /** 逗号分隔的 tag 段 */
        private data class TagSegment(val start: Int, val end: Int, val content: String)

        /** 从原始起止位置提取 trim 后的 tag 段文本 */
        private fun segmentBetween(text: String, rawStart: Int, rawEnd: Int): TagSegment? {
            var start = rawStart
            while (start < rawEnd && text[start].isWhitespace()) start++
            var end = rawEnd
            while (end > start && text[end - 1].isWhitespace()) end--
            if (start >= end) return null
            return TagSegment(start, end, text.substring(start, end))
        }

        /** 获取光标所在逗号段（trim 后） */
        private fun activeTagSegment(text: String, selection: Int): TagSegment? {
            if (selection < 0 || selection > text.length) return null
            val segmentStart = text.lastIndexOf(',', startIndex = selection - 1).let {
                if (it == -1) 0 else it + 1
            }
            val segmentEnd = text.indexOf(',', startIndex = selection).let {
                if (it == -1) text.length else it
            }
            return segmentBetween(text, segmentStart, segmentEnd)
        }

        /**
         * 解析光标所在（或前一个）tag 段。光标在空槽时回退到前一个已完成 tag，
         * 使权重/清除操作能作用于刚选中的 tag 而非空槽。
         */
        private fun resolveTagSegment(text: String, selection: Int): TagSegment? {
            activeTagSegment(text, selection)?.let { return it }
            if (selection <= 0 || selection > text.length) return null
            val prevComma = text.lastIndexOf(',', startIndex = selection - 1)
            if (prevComma < 0) return null
            val start = text.lastIndexOf(',', startIndex = prevComma - 1).let {
                if (it == -1) 0 else it + 1
            }
            return segmentBetween(text, start, prevComma)
        }

        // ============================================================
        // 权重解析
        // ============================================================

        /** 显式权重格式 "(tag:weight)"，如 "(masterpiece:1.2)" */
        private val explicitWeightRegex = Regex("""^\((.*):(-?\d+(?:\.\d+)?)\)$""")

        /**
         * 检查字符串是否被一对匹配的括号/方括号完整包裹（支持反斜杠转义）。
         */
        private fun isBalancedWrap(s: String, open: Char, close: Char): Boolean {
            if (s.length < 2 || s.first() != open || s.last() != close) return false
            var depth = 0
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' -> i++ // 反斜杠转义下一个字符
                    c == open -> depth++
                    c == close -> {
                        depth--
                        if (depth == 0 && i != s.length - 1) return false
                    }
                }
                i++
            }
            return depth == 0
        }

        /** 四舍五入到小数点后一位 */
        private fun roundToTenth(value: Double): Double = Math.round(value * 10.0) / 10.0

        /**
         * 解析权重 tag：支持 A1111 简写和显式格式。
         * "(tag)" → 1.1, "((tag))" → 1.2（每层括号 +0.1）
         * "[tag]" → 0.9, "[[tag]]" → 0.8（每层方括号 -0.1）
         * "(tag:1.3)" → 1.3（显式权重，外层括号不叠加）
         */
        private fun parseWeightedTag(content: String): Pair<String, Double> {
            var inner = content.trim()
            var delta = 0.0
            while (true) {
                // 优先匹配显式权重格式
                val match = explicitWeightRegex.matchEntire(inner)
                if (match != null) {
                    val body = match.groupValues[1]
                    val weight = match.groupValues[2].toDoubleOrNull()
                    if (body.isNotEmpty() && weight != null) {
                        return body to roundToTenth(weight + delta)
                    }
                }
                // 按顺序剥离外层括号/方括号
                when {
                    isBalancedWrap(inner, '(', ')') -> delta += 0.1
                    isBalancedWrap(inner, '[', ']') -> delta -= 0.1
                    else -> return inner to roundToTenth(1.0 + delta)
                }
                inner = inner.substring(1, inner.length - 1).trim()
            }
        }

        /** 格式化权重为一位小数，使用 '.' 作为小数点（不受 locale 影响） */
        private fun formatTagWeight(weight: Double): String =
            String.format(Locale.US, "%.1f", weight)

        /**
         * 调整活跃 tag 的注意力权重，步进为 delta。
         * A1111 简写括号会被归一化为显式格式；权重回到 1.0 时剥离括号。
         *
         * @param text 原始文本
         * @param selection 光标位置
         * @param delta 权重增量（正数增强，负数减弱）
         * @return (新文本, 新光标位置)，无法定位 tag 时返回 null
         */
        fun adjustActiveTagWeight(text: String, selection: Int, delta: Double): Pair<String, Int>? {
            val segment = resolveTagSegment(text, selection) ?: return null
            val (inner, weight) = parseWeightedTag(segment.content)
            val newWeight = roundToTenth(weight + delta)
            val replacement = if (newWeight == 1.0) inner else "($inner:${formatTagWeight(newWeight)})"
            val updated = text.substring(0, segment.start) + replacement + text.substring(segment.end)
            val lengthDelta = replacement.length - (segment.end - segment.start)
            // 光标在 tag 内时锚定到 tag 末尾，在之后时按长度差偏移
            val newSelection = if (selection in segment.start..segment.end) {
                segment.start + replacement.length
            } else {
                (selection + lengthDelta).coerceIn(0, updated.length)
            }
            return updated to newSelection
        }

        /**
         * 清除光标所在 tag 段及其相邻逗号。
         * 光标在空槽时（如刚插入 tag 后的 ", "）只折叠空槽，
         * 不删除前一个完整 tag。
         *
         * @param text 原始文本
         * @param selection 光标位置
         * @return (新文本, 新光标位置)，无法操作时返回 null
         */
        fun clearActiveTag(text: String, selection: Int): Pair<String, Int>? {
            val segment = activeTagSegment(text, selection)
            if (segment != null) {
                val commaBefore = text.lastIndexOf(',', startIndex = segment.start - 1)
                val commaAfter = text.indexOf(',', startIndex = segment.end)
                return when {
                    commaBefore >= 0 -> {
                        val prefix = text.substring(0, commaBefore)
                        val suffix = if (commaAfter >= 0) text.substring(commaAfter) else ""
                        (prefix + suffix) to prefix.length
                    }
                    commaAfter >= 0 -> text.substring(commaAfter + 1).trimStart() to 0
                    else -> "" to 0
                }
            }
            // 光标在空槽：折叠空槽
            if (selection < 0 || selection > text.length) return null
            val prevComma = text.lastIndexOf(',', startIndex = selection - 1)
            val nextComma = text.indexOf(',', startIndex = selection)
            return when {
                prevComma >= 0 -> {
                    val end = if (nextComma >= 0) nextComma else text.length
                    text.removeRange(prevComma, end) to prevComma
                }
                nextComma >= 0 -> text.substring(nextComma + 1).trimStart() to 0
                else -> null
            }
        }

        /**
         * 在活跃 tag 之后插入 ", " 并将光标定位到空槽，
         * 方便用户立即输入下一个 tag。
         */
        fun appendTagAfterActive(text: String, selection: Int): Pair<String, Int>? {
            val segment = activeTagSegment(text, selection) ?: return null
            val insertion = ", "
            val updated = text.substring(0, segment.end) + insertion + text.substring(segment.end)
            return updated to (segment.end + insertion.length)
        }
    }
}
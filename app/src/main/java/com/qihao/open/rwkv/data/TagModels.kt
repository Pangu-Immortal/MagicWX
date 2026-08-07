/**
 * TagModels.kt - Tag 自动补全数据模型
 *
 * 功能：
 * - 定义 Tag 匹配类型、Tag 条目、补全建议等核心数据结构
 * - 涵盖前缀匹配、别名匹配、翻译匹配、拼写纠错、嵌入匹配五种类型
 * - DictionaryState 追踪词典导入状态，ImportResult 表达导入结果
 *
 * 移植自：modules/local-dream app/.../data/TagModels.kt
 */
package com.qihao.open.rwkv.data

/** Tag 匹配类型：前缀/别名/翻译/拼写纠错/嵌入 */
enum class TagMatchType {
    Prefix,
    Alias,
    Translation,
    Correction,
    Embedding,
}

/**
 * 单条 Tag 条目，含原始数据与归一化搜索字段
 * @param english 英文 tag 名称（原始格式，含下划线）
 * @param translation 中文翻译（可为 null）
 * @param category Danbooru 分类：0=general, 1=artist, 3=copyright, 4=character, 5=meta
 * @param postCount Danbooru 作品数，用于人气加成排序
 * @param aliases 英文别名列表
 * @param normalizedEnglish 归一化英文名（小写、下划线归一化）
 * @param normalizedAliases 归一化别名列表
 * @param normalizedTranslation 归一化翻译（去空格、小写）
 */
data class TagEntry(
    val english: String,
    val translation: String?,
    val category: Int,
    val postCount: Int,
    val aliases: List<String>,
    val normalizedEnglish: String,
    val normalizedAliases: List<String>,
    val normalizedTranslation: String?,
)

/**
 * 单条补全建议，展示在弹出列表中
 * @param replacementTag 选中后替换到文本框中的 tag 文本
 * @param primaryText 主显示文本（英文 tag 名）
 * @param secondaryText 辅助显示文本（翻译或别名）
 * @param matchType 匹配类型，决定 badge 颜色与标签
 * @param category Danbooru 分类，决定圆点颜色
 * @param postCount 作品数，显示为人气标签
 * @param score 匹配得分（fzf 模糊分 + 人气加成）
 */
data class TagSuggestion(
    val replacementTag: String,
    val primaryText: String,
    val secondaryText: String?,
    val matchType: TagMatchType,
    val category: Int,
    val postCount: Int,
    val score: Int,
)

/** 光标所在位置的活跃 tag 上下文：token 文本、trim 起止位置、逗号分隔段结束位置 */
data class ActiveTagContext(val token: String, val trimmedStart: Int, val trimmedEnd: Int, val segmentEnd: Int)

/** 词典导入状态快照 */
data class DictionaryState(
    val mainImported: Boolean = false,
    val mainFileName: String? = null,
    val mainEntryCount: Int = 0,
    val translationImported: Boolean = false,
    val translationFileName: String? = null,
    val translationEntryCount: Int = 0,
)

/** 导入操作结果 */
sealed class ImportResult {
    data class Success(val lineCount: Int) : ImportResult()
    data class Error(val reason: String) : ImportResult()
}
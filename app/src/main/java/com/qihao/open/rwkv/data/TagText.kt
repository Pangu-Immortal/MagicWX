/**
 * TagText.kt - Tag 文本处理工具函数集
 *
 * 功能：
 * - parseCsvLine：解析单行 CSV（支持双引号字段与 "" 转义）
 * - normalizeQuery：查询文本归一化（小写、空格→下划线、连续下划线合并）
 * - normalizeTranslation：翻译文本归一化（去空格、小写）
 * - containsNonAsciiLetter：检测是否含非 ASCII 字母（用于判断中文查询）
 * - tagUnderscoresToSpaces：tag 名下划线转空格（带 \_ 转义保护）
 * - escapePromptParentheses：转义 prompt 括号避免被解析为权重分组
 * - damerauLevenshtein：Damerau-Levenshtein 编辑距离（带 early-out）
 *
 * 移植自：modules/local-dream app/.../data/TagText.kt
 */
package com.qihao.open.rwkv.data

import kotlin.math.abs

/** 连续下划线正则，用于归一化合并 */
private val underscoreRegex = Regex("_+")

/**
 * 解析单行 CSV 文本，支持双引号字段和 "" 转义。
 * 逗号在引号内不会被当作字段分隔符。
 *
 * @param line 原始 CSV 行
 * @return 各字段字符串列表
 */
internal fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < line.length) {
        val char = line[index]
        when {
            // 双引号：在引号内且下一个字符也是引号 → 转义为单个引号，否则切换引号状态
            char == '"' -> {
                if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else {
                    inQuotes = !inQuotes
                }
            }
            // 逗号且不在引号内 → 字段分隔符
            char == ',' && !inQuotes -> {
                result += current.toString()
                current.setLength(0)
            }
            // 普通字符
            else -> current.append(char)
        }
        index++
    }
    result += current.toString()
    return result
}

/**
 * 翻译文本归一化：去首尾空格、去所有空格、小写。
 * 用于中文查询时匹配翻译字段。
 */
internal fun normalizeTranslation(value: String): String = value.trim().replace(" ", "").lowercase()

/**
 * 查询文本归一化：去首尾空格、小写、空格→下划线、连字符→下划线、合并连续下划线。
 * 用于英文 tag 查询时的输入预处理。
 */
internal fun normalizeQuery(value: String): String = value
    .trim()
    .lowercase()
    .replace(' ', '_')
    .replace('-', '_')
    .replace(underscoreRegex, "_")

/**
 * 检测字符串是否包含非 ASCII 字母（如中文、日文、韩文等）。
 * 用于判断用户是否在用中文搜索 tag。
 */
internal fun containsNonAsciiLetter(value: String): Boolean =
    value.any { it.code > 127 && it.isLetter() }

/**
 * 将存储格式的 tag 名转换为显示/插入格式：
 * - 裸下划线 '_' 转为空格（词分隔符）
 * - 转义下划线 '\_' 保留为字面下划线 '_'
 *
 * 例："blonde_hair" → "blonde hair", "a\_b" → "a_b"
 */
internal fun tagUnderscoresToSpaces(tag: String): String {
    if ('_' !in tag) return tag
    val sb = StringBuilder(tag.length)
    var i = 0
    while (i < tag.length) {
        val c = tag[i]
        when {
            // 转义下划线：跳过分隔符，保留字面下划线
            c == '\\' && i + 1 < tag.length && tag[i + 1] == '_' -> {
                sb.append('_')
                i += 2
            }
            // 裸下划线 → 空格
            c == '_' -> {
                sb.append(' ')
                i++
            }
            // 普通字符直接追加
            else -> {
                sb.append(c)
                i++
            }
        }
    }
    return sb.toString()
}

/**
 * 转义 Stable Diffusion prompt 中的注意力元字符，避免 tag 的字面括号被解析为权重分组。
 * 例："aqua (konosuba)" → "aqua \\(konosuba\\)"
 */
internal fun escapePromptParentheses(text: String): String =
    text.replace("(", "\\(").replace(")", "\\)")

/**
 * Damerau-Levenshtein 编辑距离，带 early-out。
 * 当某行所有单元格均超过 maxDistance 时提前返回 maxDistance + 1，
 * 仅用于拼写纠错回退路径。
 *
 * @param source 源字符串
 * @param target 目标字符串
 * @param maxDistance 最大允许距离，超出则提前终止
 * @return 实际编辑距离，或 maxDistance + 1 表示超出阈值
 */
internal fun damerauLevenshtein(source: String, target: String, maxDistance: Int): Int {
    if (source == target) return 0
    // 长度差超过阈值，不可能在允许距离内
    if (abs(source.length - target.length) > maxDistance) return maxDistance + 1

    val rows = source.length + 1
    val cols = target.length + 1
    val dp = Array(rows) { IntArray(cols) }

    for (i in 0 until rows) dp[i][0] = i
    for (j in 0 until cols) dp[0][j] = j

    for (i in 1 until rows) {
        var rowMin = Int.MAX_VALUE
        for (j in 1 until cols) {
            val cost = if (source[i - 1] == target[j - 1]) 0 else 1
            // 三种基本操作：删除、插入、替换
            var value = minOf(
                dp[i - 1][j] + 1,       // 删除
                dp[i][j - 1] + 1,       // 插入
                dp[i - 1][j - 1] + cost, // 替换
            )
            // Damerau 扩展：相邻字符交换
            if (i > 1 && j > 1 && source[i - 1] == target[j - 2] && source[i - 2] == target[j - 1]) {
                value = minOf(value, dp[i - 2][j - 2] + cost)
            }
            dp[i][j] = value
            if (value < rowMin) rowMin = value
        }
        // 整行所有值都超过阈值，提前终止
        if (rowMin > maxDistance) return maxDistance + 1
    }

    return dp[source.length][target.length]
}
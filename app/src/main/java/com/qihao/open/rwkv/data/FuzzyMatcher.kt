/**
 * FuzzyMatcher.kt - fzf 风格模糊匹配算法（Kotlin 移植）
 *
 * 功能：
 * - 实现 fzf FuzzyMatchV1 算法，pattern 必须是 text 的子序列
 * - 词边界匹配、连续匹配得分更高，分散/大间隔匹配得分更低
 * - score() 返回匹配得分或 NO_MATCH，positions() 返回匹配字符位置
 * - 零分配、纯计算，适合高频调用场景
 *
 * 移植自：modules/local-dream app/.../data/FuzzyMatcher.kt
 */
package com.qihao.open.rwkv.data

/**
 * fzf 风格模糊匹配器（移植自 fzf 的 algo.go FuzzyMatchV1 算法与评分模型）。
 *
 * 调用方需预先做大小写归一化，pattern 和 text 按原文逐字符比较。
 * 匹配得分规则：词边界（字符串开头或 '_' 后）和连续匹配段得分最高，
 * 分散匹配且间隔大则得分最低。pattern 不是 text 子序列时返回 [NO_MATCH]。
 */
object FuzzyMatcher {
    const val NO_MATCH: Int = Int.MIN_VALUE

    // 单字符匹配基础分
    private const val SCORE_MATCH = 16
    // 间隔起始惩罚
    private const val SCORE_GAP_START = -3
    // 间隔延续惩罚
    private const val SCORE_GAP_EXTENSION = -1

    // 词边界（首字符）奖励分，保持适度以免长边界缩写总是压过短连续匹配
    private const val BONUS_BOUNDARY = SCORE_MATCH / 2

    // 字母↔数字过渡奖励（已小写化，无 camelCase 场景，仅保留数字边界）
    private const val BONUS_CAMEL_123 = BONUS_BOUNDARY + SCORE_GAP_EXTENSION

    // 连续匹配段内每字符的最低奖励
    private const val BONUS_CONSECUTIVE = -(SCORE_GAP_START + SCORE_GAP_EXTENSION)

    // 首个匹配字符的额外乘数，使 pattern 的起始锚定在更有意义的位置
    private const val BONUS_FIRST_CHAR_MULTIPLIER = 2

    // 字符分类：非单词 / 数字 / 字母
    private const val CLASS_NON_WORD = 0
    private const val CLASS_NUMBER = 1
    private const val CLASS_LETTER = 2

    // 起始/结束索引打包常量
    private const val INDEX_SHIFT = 32
    private const val INDEX_MASK = 0xffffffffL

    /**
     * 仅计算匹配得分，零分配。pattern 不存在时返回 [NO_MATCH]。
     * @param pattern 已归一化的查询字符数组
     * @param text 已归一化的目标文本
     */
    fun score(pattern: CharArray, text: String): Int {
        val packed = locate(pattern, text)
        if (packed < 0L) return NO_MATCH
        val sidx = (packed ushr INDEX_SHIFT).toInt()
        val eidx = (packed and INDEX_MASK).toInt()
        return calculate(pattern, text, sidx, eidx, null)
    }

    /**
     * 返回 pattern 在 text 中匹配字符的索引（升序），不存在时返回 null。
     * @param pattern 已归一化的查询字符数组
     * @param text 已归一化的目标文本
     */
    fun positions(pattern: CharArray, text: String): IntArray? {
        if (pattern.isEmpty()) return IntArray(0)
        val packed = locate(pattern, text)
        if (packed < 0L) return null
        val sidx = (packed ushr INDEX_SHIFT).toInt()
        val eidx = (packed and INDEX_MASK).toInt()
        val out = IntArray(pattern.size)
        calculate(pattern, text, sidx, eidx, out)
        return out
    }

    /**
     * 前向贪心定位匹配区间，再反向收紧起点使匹配跨度尽量靠右。
     * 返回 (sidx shl 32 | eidx) 打包值，或 -1 表示 pattern 不是子序列。
     */
    private fun locate(pattern: CharArray, text: String): Long {
        val m = pattern.size
        val n = text.length
        if (m == 0) return 0L
        if (m > n) return -1L

        var pidx = 0
        var sidx = -1
        var eidx = -1
        var j = 0
        // 前向贪心：找 pattern 每个字符在 text 中的首次出现位置
        while (j < n) {
            if (text[j] == pattern[pidx]) {
                if (sidx < 0) sidx = j
                pidx++
                if (pidx == m) {
                    eidx = j + 1
                    break
                }
            }
            j++
        }
        if (eidx < 0) return -1L

        // 反向收紧：从匹配区间末尾向前找，使起点尽量靠右
        pidx = m - 1
        j = eidx - 1
        while (j >= sidx) {
            if (text[j] == pattern[pidx]) {
                pidx--
                if (pidx < 0) {
                    sidx = j
                    break
                }
            }
            j--
        }
        return (sidx.toLong() shl INDEX_SHIFT) or (eidx.toLong() and INDEX_MASK)
    }

    /**
     * 在 [sidx, eidx) 区间内单次前向累加 fzf 得分。
     * positionsOut 非 null 时填充匹配字符索引（恰好 pattern.size 个）。
     */
    private fun calculate(
        pattern: CharArray,
        text: String,
        sidx: Int,
        eidx: Int,
        positionsOut: IntArray?,
    ): Int {
        val m = pattern.size
        var pidx = 0
        var score = 0
        var inGap = false
        var consecutive = 0
        var firstBonus = 0
        var posCount = 0
        // 前一个字符的分类，用于判断词边界
        var prevClass = if (sidx > 0) charClassOf(text[sidx - 1]) else CLASS_NON_WORD

        var j = sidx
        while (j < eidx) {
            val c = text[j]
            val clazz = charClassOf(c)
            if (c == pattern[pidx]) {
                if (positionsOut != null) positionsOut[posCount++] = j
                var bonus = bonusFor(prevClass, clazz)
                if (consecutive == 0) {
                    firstBonus = bonus
                } else {
                    // 连续匹配段内：若出现边界奖励，重置 firstBonus 为更大的值
                    if (bonus >= BONUS_BOUNDARY && bonus > firstBonus) firstBonus = bonus
                    bonus = maxOf(maxOf(bonus, firstBonus), BONUS_CONSECUTIVE)
                }
                score += if (pidx == 0) {
                    // 首个匹配字符获得双倍奖励
                    SCORE_MATCH + bonus * BONUS_FIRST_CHAR_MULTIPLIER
                } else {
                    SCORE_MATCH + bonus
                }
                inGap = false
                consecutive++
                pidx++
                if (pidx == m) break
            } else {
                // 非匹配字符：间隔惩罚
                score += if (inGap) SCORE_GAP_EXTENSION else SCORE_GAP_START
                inGap = true
                consecutive = 0
                firstBonus = 0
            }
            prevClass = clazz
            j++
        }
        return score
    }

    /** 字符分类：字母 / 数字 / 非单词 */
    private fun charClassOf(c: Char): Int = when {
        c in 'a'..'z' || c in 'A'..'Z' -> CLASS_LETTER
        c in '0'..'9' -> CLASS_NUMBER
        else -> CLASS_NON_WORD
    }

    /**
     * 计算词边界奖励：非单词字符后的字母获得 BONUS_BOUNDARY，
     * 字母↔数字过渡获得 BONUS_CAMEL_123。
     * 注意：CJK 字符归入 CLASS_NON_WORD，不获得边界奖励，依靠连续奖励弥补。
     */
    private fun bonusFor(prevClass: Int, clazz: Int): Int = when {
        clazz == CLASS_NON_WORD -> 0
        prevClass == CLASS_NON_WORD -> BONUS_BOUNDARY
        prevClass == CLASS_NUMBER && clazz != CLASS_NUMBER -> BONUS_CAMEL_123
        prevClass != CLASS_NUMBER && clazz == CLASS_NUMBER -> BONUS_CAMEL_123
        else -> 0
    }
}
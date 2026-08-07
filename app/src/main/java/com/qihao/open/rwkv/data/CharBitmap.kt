/**
 * CharBitmap.kt - 语言无关的字符存在性位图预过滤器
 *
 * 功能：
 * - 每个字符通过 Knuth 乘法哈希映射到 128 位位图中的一个 bit
 * - 条目位图 OR 所有字符位，查询位图同理
 * - 若 (entry & query) == query 不成立，则 pattern 不可能是 text 的子序列
 * - 哈希碰撞只产生假阳性（落入真实检查），绝不产生假阴性
 * - 对 Latin、CJK、Cyrillic 等所有文字同等有效
 *
 * 移植自：modules/local-dream app/.../data/CharBitmap.kt
 */
package com.qihao.open.rwkv.data

/**
 * 语言无关的字符存在性位图，用于模糊匹配预过滤。
 * 每个字符通过 Knuth 乘法哈希映射到 128 位（2 个 Long）中的 1 个 bit，
 * 查询时 (entry & query) == query 快速拒绝绝大多数不匹配候选。
 *
 * 哈希碰撞只会削弱过滤（产生假阳性），绝不丢弃真实匹配。
 * 因直接对 code unit 哈希而不假设字母表，对 Latin/CJK/Cyrillic 等所有文字一致生效。
 */
object CharBitmap {
    /** 每个位图的 Long 数量（128 位 = 2 个 Long） */
    const val WORDS = 2

    // log2(128) = 7
    private const val SHIFT = 7
    // Knuth 乘法哈希常数
    private const val GOLDEN = 0x9E3779B1L

    /**
     * 将 [s] 中所有字符的位 OR 入 [out] 的 [offset, offset + WORDS) 区间。
     * @param out 目标 LongArray
     * @param offset 写入起始偏移（条目索引 × WORDS）
     * @param s 待哈希的字符序列
     */
    fun addInto(out: LongArray, offset: Int, s: CharSequence) {
        for (i in s.indices) {
            val bit = bitOf(s[i])
            out[offset + (bit ushr 6).toInt()] = out[offset + (bit ushr 6).toInt()] or (1L shl (bit.toInt() and 63))
        }
    }

    /**
     * 为 [s] 新建一个位图（用于单次查询）。
     * @param s 查询字符序列
     * @return 长度为 WORDS 的新 LongArray
     */
    fun of(s: CharSequence): LongArray = LongArray(WORDS).also { addInto(it, 0, s) }

    /**
     * 检查查询位图的每个 bit 是否都在条目位图中置位。
     * @param entry 条目位图数组
     * @param offset 条目起始偏移
     * @param q0 查询位图第 0 个 Long
     * @param q1 查询位图第 1 个 Long
     * @return true 当且仅当 entry 包含 query 的所有位
     */
    fun contains(entry: LongArray, offset: Int, q0: Long, q1: Long): Boolean =
        entry[offset] and q0 == q0 && entry[offset + 1] and q1 == q1

    /** Knuth 乘法哈希：将字符 code 映射到 [0, 128) 的 bit 索引 */
    private fun bitOf(c: Char): Int =
        ((c.code.toLong() * GOLDEN and 0xFFFFFFFFL) ushr (32 - SHIFT)).toInt()
}
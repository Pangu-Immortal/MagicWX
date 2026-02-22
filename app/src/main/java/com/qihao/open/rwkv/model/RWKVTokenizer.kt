/**
 * RWKVTokenizer - RWKV World 分词器
 *
 * 功能：
 * - encode(): 将文本编码为 token ID 列表
 * - decode(): 将 token ID 列表解码为文本
 * - formatChat(): RWKV 专用聊天格式
 * - 基于 vocab.json 词汇表，使用贪心最长匹配算法
 */
package com.qihao.open.rwkv.model

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RWKVTokenizer(context: Context) : ITokenizer {

    companion object {
        private const val TAG = "RWKVTokenizer"
        private const val VOCAB_PATH = "model/vocab.json" // assets 中的词汇表路径
    }

    // token ID → 文本映射
    private val decoder = mutableMapOf<Int, String>()

    // 文本 → token ID 映射
    private val encoder = mutableMapOf<String, Int>()

    // 前缀集合，用于快速判断是否存在以某前缀开头的 token
    private val prefixSet = mutableSetOf<String>()

    override val eosTokenId: Int = 0                    // RWKV 使用 0 作为 EOS
    override val stopTokenIds: Set<Int> = setOf(0, 1)   // RWKV 停止 token
    override val vocabSize: Int get() = decoder.size

    init {
        // 从 assets 加载词汇表
        val json = context.assets.open(VOCAB_PATH).bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, String>>() {}.type
        val vocabMap: Map<String, String> = Gson().fromJson(json, type)

        // 构建编码和解码映射
        for ((idStr, text) in vocabMap) {
            val id = idStr.toInt()
            decoder[id] = text
            encoder[text] = id

            // 将该 token 的所有前缀加入前缀集合
            for (i in 1..text.length) {
                prefixSet.add(text.substring(0, i))
            }
        }

        Log.d(TAG, "词汇表加载完成，共 ${decoder.size} 个 token")
    }

    /**
     * 将文本编码为 token ID 列表
     * 使用贪心最长匹配算法
     */
    override fun encode(text: String): List<Int> {
        val result = mutableListOf<Int>()
        var start = 0 // 当前匹配起始位置

        while (start < text.length) {
            var end = text.length // 尝试匹配的结束位置
            var matched = false

            // 从最长子串开始，逐步缩短直到找到匹配
            while (end > start) {
                val sub = text.substring(start, end)
                val tokenId = encoder[sub]
                if (tokenId != null) {
                    result.add(tokenId) // 找到匹配的 token
                    start = end          // 移动到下一段
                    matched = true
                    break
                }

                // 如果当前子串不是任何 token 的前缀，则快速跳过
                if (!prefixSet.contains(sub)) {
                    end = minOf(start + sub.length - 1, end - 1)
                } else {
                    end--
                }
            }

            // 未能匹配任何 token，跳过当前字符
            if (!matched) {
                Log.w(TAG, "无法编码字符: '${text[start]}' (${text[start].code})")
                start++
            }
        }

        return result
    }

    /** 将 token ID 列表解码为文本 */
    override fun decode(tokens: List<Int>): String {
        return tokens.mapNotNull { decoder[it] }.joinToString("")
    }

    /** 将单个 token ID 解码为文本 */
    override fun decode(tokenId: Int): String {
        return decoder[tokenId] ?: ""
    }

    /** RWKV 聊天模板格式 */
    override fun formatChat(userMessage: String): String {
        return "User: $userMessage\n\nAssistant:"       // RWKV World 模型聊天格式
    }
}

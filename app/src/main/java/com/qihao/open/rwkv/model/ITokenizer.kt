/**
 * ITokenizer - 分词器统一接口
 *
 * 功能：
 * - encode(): 将文本编码为 token ID 列表
 * - decode(): 将 token ID 解码为文本
 * - formatChat(): 将用户消息格式化为模型输入（含聊天模板）
 *
 * 实现类：
 * - RWKVTokenizer: RWKV World 模型专用分词器（基于 vocab.json）
 * - HFTokenizer: HuggingFace 格式分词器（基于 tokenizer.json，支持 Transformer 模型）
 */
package com.qihao.open.rwkv.model

interface ITokenizer {
    /** 将文本编码为 token ID 列表 */
    fun encode(text: String): List<Int>

    /** 将单个 token ID 解码为文本 */
    fun decode(tokenId: Int): String

    /** 将 token ID 列表解码为文本 */
    fun decode(tokens: List<Int>): String

    /** 将用户消息格式化为模型聊天模板 */
    fun formatChat(userMessage: String): String

    /** EOS（结束）token ID */
    val eosTokenId: Int

    /** 所有可能的停止 token ID 集合 */
    val stopTokenIds: Set<Int>

    /** 词汇表大小 */
    val vocabSize: Int
}

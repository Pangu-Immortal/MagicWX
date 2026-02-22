/**
 * HFTokenizer - HuggingFace 格式分词器
 *
 * 功能：
 * - 从 tokenizer.json 加载词汇表和特殊 token
 * - 支持 GPT-2 风格的字节级 BPE 编码（ByteLevel pre-tokenization）
 * - 支持 SentencePiece 风格的 ▁ 编码
 * - 自动检测 EOS/BOS 等特殊 token
 * - 内置 ChatML、Llama3、Gemma 聊天模板
 *
 * 适用模型：SmolLM2、DeepSeek-R1、Qwen3、Gemma3、Phi-3、Llama3.2、TinyLlama、StableLM、MiniCPM
 */
package com.qihao.open.rwkv.model

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** 聊天模板类型 */
enum class ChatTemplate {
    CHATML,     // SmolLM2, Qwen3, DeepSeek, Phi-3, TinyLlama, StableLM, MiniCPM
    LLAMA3,     // Llama 3.2
    GEMMA       // Gemma 3
}

class HFTokenizer(
    tokenizerJsonPath: String,                          // tokenizer.json 文件路径
    private val chatTemplate: ChatTemplate = ChatTemplate.CHATML  // 聊天模板类型
) : ITokenizer {

    companion object {
        private const val TAG = "HFTokenizer"
    }

    private val encoder: Map<String, Int>               // 文本 → token ID
    private val decoder: Map<Int, String>               // token ID → 文本
    private val prefixSet: Set<String>                  // 前缀集合（加速贪心匹配）
    private val bytesToUnicode: Map<Int, Char>          // GPT-2 字节到 Unicode 映射
    private val unicodeToBytes: Map<Char, Int>          // Unicode 到字节的反向映射
    private var useByteLevel = false                    // 是否使用字节级 BPE（GPT-2 风格）

    override val eosTokenId: Int                        // 主 EOS token ID
    override val stopTokenIds: Set<Int>                 // 所有停止 token ID
    override val vocabSize: Int                         // 词汇表大小

    init {
        Log.d(TAG, "加载 tokenizer.json: $tokenizerJsonPath")
        val jsonStr = File(tokenizerJsonPath).readText()

        // 使用 Gson 解析为 Map 结构（避免定义过多 data class）
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val root: Map<String, Any> = Gson().fromJson(jsonStr, type)

        // ---- 解析 model.vocab ----
        val enc = mutableMapOf<String, Int>()
        val dec = mutableMapOf<Int, String>()

        @Suppress("UNCHECKED_CAST")
        val model = root["model"] as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val vocab = model?.get("vocab") as? Map<String, Double> ?: emptyMap()

        for ((text, idDouble) in vocab) {
            val id = idDouble.toInt()                   // Gson 将整数解析为 Double
            enc[text] = id
            dec[id] = text
        }

        // ---- 解析 added_tokens（特殊 token）----
        val stops = mutableSetOf<Int>()
        var mainEos = 0

        @Suppress("UNCHECKED_CAST")
        val addedTokens = root["added_tokens"] as? List<Map<String, Any>> ?: emptyList()

        for (token in addedTokens) {
            val content = token["content"] as? String ?: continue
            val id = (token["id"] as? Double)?.toInt() ?: continue
            val special = token["special"] as? Boolean ?: false

            enc[content] = id                           // 特殊 token 也加入编码映射
            dec[id] = content

            // 识别停止 token
            if (special && content in setOf(
                    "</s>", "<|endoftext|>", "<|im_end|>",
                    "<|eot_id|>", "<eos>", "<|end|>",
                    "<end_of_turn>", "<|end_of_text|>"
                )
            ) {
                stops.add(id)
                if (mainEos == 0) mainEos = id          // 第一个匹配作为主 EOS
            }
        }

        // 回退：如果没找到 EOS，使用 vocab 中的 </s>
        if (mainEos == 0) mainEos = enc["</s>"] ?: enc["<|endoftext|>"] ?: 0

        encoder = enc
        decoder = dec
        eosTokenId = mainEos
        stopTokenIds = stops
        vocabSize = dec.size

        // ---- 检测是否使用字节级 BPE ----
        @Suppress("UNCHECKED_CAST")
        val preTokenizer = root["pre_tokenizer"] as? Map<String, Any>
        useByteLevel = detectByteLevel(preTokenizer) || enc.containsKey("Ġ") // Ġ 是 GPT-2 空格字符
        Log.d(TAG, "使用字节级 BPE: $useByteLevel")

        // ---- 构建 GPT-2 字节到 Unicode 映射 ----
        bytesToUnicode = buildBytesToUnicode()
        unicodeToBytes = bytesToUnicode.entries.associate { (k, v) -> v to k }

        // ---- 构建前缀集合（用于贪心最长匹配加速） ----
        val pfx = mutableSetOf<String>()
        for (key in enc.keys) {
            if (key.length <= 30) {                     // 只索引长度合理的 token
                for (i in 1..key.length) {
                    pfx.add(key.substring(0, i))
                }
            }
        }
        prefixSet = pfx

        Log.d(TAG, "词汇表加载完成: ${dec.size} 个 token, EOS=$eosTokenId, stops=$stops")
    }

    // ==================== 编码 ====================

    override fun encode(text: String): List<Int> {
        return if (useByteLevel) {
            encodeByteLevel(text)                       // GPT-2 风格字节级 BPE
        } else {
            encodeSentencePiece(text)                    // SentencePiece 风格
        }
    }

    /** GPT-2 风格字节级 BPE 编码 */
    private fun encodeByteLevel(text: String): List<Int> {
        val bytes = text.toByteArray(Charsets.UTF_8)    // 文本转 UTF-8 字节
        val unicodeStr = bytes.map { b ->
            bytesToUnicode[b.toInt() and 0xFF] ?: '?'   // 字节转 Unicode 字符
        }.joinToString("")
        return greedyEncode(unicodeStr)
    }

    /** SentencePiece 风格编码 */
    private fun encodeSentencePiece(text: String): List<Int> {
        val processed = "▁" + text.replace(" ", "▁")    // 空格替换为 ▁
        return greedyEncode(processed)
    }

    /** 贪心最长匹配编码（通用） */
    private fun greedyEncode(text: String): List<Int> {
        val result = mutableListOf<Int>()
        var start = 0

        while (start < text.length) {
            var end = minOf(text.length, start + 50)    // 单个 token 最大长度限制
            var matched = false

            while (end > start) {
                val sub = text.substring(start, end)
                val tokenId = encoder[sub]
                if (tokenId != null) {
                    result.add(tokenId)                 // 找到匹配
                    start = end
                    matched = true
                    break
                }
                // 不是任何 token 的前缀，快速跳过
                if (sub.length > 1 && !prefixSet.contains(sub)) {
                    end = maxOf(start + 1, end - 3)
                } else {
                    end--
                }
            }

            if (!matched) {
                // 字节回退：单个字符的字节编码
                if (useByteLevel) {
                    // GPT-2 模式下，单个 Unicode 字符应该已经在 vocab 中
                    val ch = text[start].toString()
                    val id = encoder[ch]
                    if (id != null) result.add(id)
                } else {
                    // SentencePiece 字节回退 <0xNN>
                    val bytes = text[start].toString().toByteArray(Charsets.UTF_8)
                    for (b in bytes) {
                        val hex = "<0x%02X>".format(b.toInt() and 0xFF)
                        val id = encoder[hex]
                        if (id != null) result.add(id)
                    }
                }
                start++
            }
        }

        return result
    }

    // ==================== 解码 ====================

    override fun decode(tokenId: Int): String {
        val tokenStr = decoder[tokenId] ?: return ""

        // 跳过特殊 token 的输出
        if (tokenStr.startsWith("<") && tokenStr.endsWith(">") && tokenStr.length > 2) {
            // 字节 token <0xNN> 需要解码
            if (tokenStr.startsWith("<0x") && tokenStr.length == 6) {
                val hex = tokenStr.substring(3, 5)
                return try {
                    String(byteArrayOf(hex.toInt(16).toByte()), Charsets.UTF_8)
                } catch (_: Exception) { "" }
            }
            return ""                                   // 其他特殊 token 不输出
        }

        return if (useByteLevel) {
            decodeByteLevel(tokenStr)                   // GPT-2 风格解码
        } else {
            tokenStr.replace("▁", " ")                  // SentencePiece 风格解码
        }
    }

    override fun decode(tokens: List<Int>): String {
        return if (useByteLevel) {
            // 字节级：先收集所有字节再转 UTF-8
            val bytes = mutableListOf<Byte>()
            for (id in tokens) {
                val tokenStr = decoder[id] ?: continue
                if (tokenStr.startsWith("<") && tokenStr.endsWith(">")) continue // 跳过特殊 token
                for (ch in tokenStr) {
                    val b = unicodeToBytes[ch]
                    if (b != null) bytes.add(b.toByte())
                }
            }
            String(bytes.toByteArray(), Charsets.UTF_8)
        } else {
            tokens.mapNotNull { decoder[it] }.joinToString("").replace("▁", " ")
        }
    }

    /** GPT-2 字节级解码：Unicode 字符转回字节 */
    private fun decodeByteLevel(tokenStr: String): String {
        val bytes = tokenStr.map { ch ->
            (unicodeToBytes[ch] ?: ch.code).toByte()
        }.toByteArray()
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    // ==================== 聊天模板 ====================

    override fun formatChat(userMessage: String): String {
        return when (chatTemplate) {
            ChatTemplate.CHATML ->                      // SmolLM2, Qwen3, DeepSeek, Phi-3 等
                "<|im_start|>user\n$userMessage<|im_end|>\n<|im_start|>assistant\n"

            ChatTemplate.LLAMA3 ->                      // Llama 3.2
                "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\n$userMessage<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"

            ChatTemplate.GEMMA ->                       // Gemma 3
                "<start_of_turn>user\n$userMessage<end_of_turn>\n<start_of_turn>model\n"
        }
    }

    // ==================== 工具方法 ====================

    /** 检测 pre_tokenizer 是否包含 ByteLevel */
    private fun detectByteLevel(preTokenizer: Map<String, Any>?): Boolean {
        if (preTokenizer == null) return false
        val type = preTokenizer["type"] as? String ?: ""
        if (type == "ByteLevel") return true

        // 嵌套结构（Sequence 类型）
        @Suppress("UNCHECKED_CAST")
        val pretokenizers = preTokenizer["pretokenizers"] as? List<Map<String, Any>>
        return pretokenizers?.any { (it["type"] as? String) == "ByteLevel" } == true
    }

    /**
     * 构建 GPT-2 字节到 Unicode 映射表
     *
     * GPT-2 使用 256 个 Unicode 字符来表示所有可能的字节值：
     * - 可打印 ASCII 和扩展 Latin 字符直接映射
     * - 不可打印字节映射到更高的 Unicode 代码点
     */
    private fun buildBytesToUnicode(): Map<Int, Char> {
        val bs = mutableListOf<Int>()
        val cs = mutableListOf<Int>()

        // 可打印 ASCII: !, ", #, ..., ~
        for (b in '!'.code..'~'.code) { bs.add(b); cs.add(b) }
        // Latin-1 补充: ¡, ¢, ..., ¬
        for (b in '¡'.code..'¬'.code) { bs.add(b); cs.add(b) }
        // Latin-1 补充: ®, ¯, ..., ÿ
        for (b in '®'.code..'ÿ'.code) { bs.add(b); cs.add(b) }

        // 未覆盖的字节映射到 256+ 的 Unicode 代码点
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }

        return bs.zip(cs).associate { (b, c) -> b to c.toChar() }
    }
}

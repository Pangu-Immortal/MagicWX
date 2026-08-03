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
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.io.File

/** 聊天模板类型 */
enum class ChatTemplate {
    PLAIN,      // 无官方聊天模板的 base / 移动端小模型，直接输入用户文本
    CHATML,     // SmolLM2, DeepSeek, Phi-3, TinyLlama, StableLM, MiniCPM
    QWEN3,      // Qwen3，默认追加 /no_think，避免普通聊天暴露推理块
    DEEPSEEK_R1, // DeepSeek-R1 Distill，使用官方 <｜User｜>/<｜Assistant｜> 模板
    TINYLLAMA,  // TinyLlama Chat，使用 <|user|>/<|assistant|> 模板
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
    private val prefixSet: Set<String>                  // 前缀集合（用于 SentencePiece 贪心匹配回退）
    private val bpeRanks: Map<Pair<String, String>, Int> // BPE merge 对 → 优先级
    private val bpeCache = mutableMapOf<String, List<Int>>() // BPE 结果缓存，降低重复编码开销
    private val specialTokenIds: Map<String, Int>       // 特殊 token 文本 → token ID
    private val addedTokenIds: Map<String, Int>         // added_tokens 文本 → token ID，包含 special=false 的控制符
    private val addedTokenTexts: List<String>           // added_tokens 文本，按长度排序用于优先匹配
    private val specialTokenIdSet: Set<Int>             // 特殊 token ID 集合，用于解码过滤
    private val controlTokenIdSet: Set<Int>             // 控制 token ID 集合，用于过滤模板符号
    private val bytesToUnicode: Map<Int, Char>          // GPT-2 字节到 Unicode 映射
    private val unicodeToBytes: Map<Char, Int>          // Unicode 到字节的反向映射
    private var useByteLevel = false                    // 是否使用字节级 BPE（GPT-2 风格）
    private val byteLevelPattern = Regex(
        """'s|'t|'re|'ve|'m|'ll|'d| ?[\p{L}]+| ?[\p{N}]+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+"""
    )

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
        val ranks = mutableMapOf<Pair<String, String>, Int>()

        for ((text, idDouble) in vocab) {
            val id = idDouble.toInt()                   // Gson 将整数解析为 Double
            enc[text] = id
            dec[id] = text
        }

        // ---- 解析 model.merges：BPE 的核心规则，不能用词表贪心替代 ----
        @Suppress("UNCHECKED_CAST")
        val merges = model?.get("merges") as? List<Any> ?: emptyList()
        for ((rank, merge) in merges.withIndex()) {
            val parts = when (merge) {
                is String -> merge.split(" ")
                is List<*> -> merge.mapNotNull { it as? String }
                else -> emptyList()
            }
            if (parts.size == 2) {
                ranks[parts[0] to parts[1]] = rank       // rank 越小优先级越高
            }
        }

        // ---- 解析 added_tokens（特殊 token）----
        val stops = mutableSetOf<Int>()
        val specialIds = mutableMapOf<String, Int>()
        val addedIds = mutableMapOf<String, Int>()
        val controlIds = mutableSetOf<Int>()
        var mainEos = 0

        @Suppress("UNCHECKED_CAST")
        val addedTokens = root["added_tokens"] as? List<Map<String, Any>> ?: emptyList()

        for (token in addedTokens) {
            val content = token["content"] as? String ?: continue
            val id = (token["id"] as? Double)?.toInt() ?: continue
            val special = token["special"] as? Boolean ?: false

            enc[content] = id                           // 特殊 token 也加入编码映射
            dec[id] = content
            addedIds[content] = id                       // added token 必须整体编码，DeepSeek 部分控制符 special=false

            if (special) {
                specialIds[content] = id                 // 编码时必须先整体匹配特殊 token
            }
            if (isControlTokenText(content)) {
                controlIds.add(id)                       // 输出时过滤所有模板控制符，避免 UI 暴露协议标记
            }

            // 识别停止 token：结束符和新一轮角色起始符都不应进入 UI 文本
            if (special && content in setOf(
                    "</s>", "<|endoftext|>", "<|im_end|>",
                    "<|eot_id|>", "<eos>", "<|end|>",
                    "<end_of_turn>", "<|end_of_text|>",
                    "<|im_start|>", "<|start_header_id|>",
                    "<start_of_turn>"
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
        bpeRanks = ranks
        specialTokenIds = specialIds
        addedTokenIds = addedIds
        addedTokenTexts = addedIds.keys.sortedByDescending { it.length }
        specialTokenIdSet = specialIds.values.toSet()
        controlTokenIdSet = controlIds + specialTokenIdSet
        eosTokenId = mainEos
        stopTokenIds = stops
        vocabSize = dec.size

        // ---- 检测是否使用字节级 BPE ----
        @Suppress("UNCHECKED_CAST")
        val preTokenizer = root["pre_tokenizer"] as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val decoderConfig = root["decoder"] as? Map<String, Any>
        useByteLevel = detectByteLevel(preTokenizer, decoderConfig) // 只按 tokenizer 结构判断，避免 Gemma 误判 ByteLevel
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

        Log.d(TAG, "词汇表加载完成: ${dec.size} 个 token, merges=${ranks.size}, EOS=$eosTokenId, stops=$stops")
    }

    // ==================== 编码 ====================

    override fun encode(text: String): List<Int> {
        return if (useByteLevel) {
            encodeWithSpecialTokens(text, ::encodeByteLevelSegment) // 特殊 token 必须先整体保留
        } else {
            encodeWithSpecialTokens(text, ::encodeSentencePieceSegment) // SentencePiece 同样保留特殊 token
        }
    }

    /** 按特殊 token 边界切分普通文本，避免 ChatML 控制符被拆碎 */
    private fun encodeWithSpecialTokens(
        text: String,
        encodePlainText: (String) -> List<Int>
    ): List<Int> {
        val result = mutableListOf<Int>()
        var index = 0

        while (index < text.length) {
            val matchedAddedToken = addedTokenTexts.firstOrNull { text.startsWith(it, index) }
            if (matchedAddedToken != null) {
                addedTokenIds[matchedAddedToken]?.let { result.add(it) } // added token 原样编码为单 ID
                index += matchedAddedToken.length
                continue
            }

            var nextSpecialIndex = text.length
            for (token in addedTokenTexts) {
                val found = text.indexOf(token, startIndex = index)
                if (found >= 0 && found < nextSpecialIndex) nextSpecialIndex = found
            }

            result += encodePlainText(text.substring(index, nextSpecialIndex))
            index = nextSpecialIndex
        }

        return result
    }

    /** GPT-2 风格字节级 BPE 编码普通片段 */
    private fun encodeByteLevelSegment(text: String): List<Int> {
        val result = mutableListOf<Int>()
        for (piece in preTokenizeByteLevel(text)) {
            val unicodeStr = piece.toByteArray(Charsets.UTF_8).map { b ->
                bytesToUnicode[b.toInt() and 0xFF] ?: '?'       // 字节转 Unicode 字符
            }.joinToString("")
            result += encodeBpeToken(unicodeStr)
        }
        return result
    }

    /** SentencePiece 风格编码普通片段 */
    private fun encodeSentencePieceSegment(text: String): List<Int> {
        val processed = "▁" + text.replace(" ", "▁")    // 空格替换为 ▁
        return greedyEncode(processed)
    }

    /** ByteLevel + Digits 预分词，贴近 tokenizer.json 中 Sequence(Digits, ByteLevel) */
    private fun preTokenizeByteLevel(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val pieces = mutableListOf<String>()
        byteLevelPattern.findAll(text).forEach { match ->
            val value = match.value
            if (value.any { it.isDigit() } && value.all { it.isDigit() || it == ' ' }) {
                val leadingSpaces = value.takeWhile { it == ' ' }
                if (leadingSpaces.isNotEmpty()) pieces.add(leadingSpaces) // 数字前空格独立编码为 Ġ
                value.filter { it.isDigit() }.forEach { pieces.add(it.toString()) }
            } else {
                pieces.add(value)
            }
        }
        return pieces
    }

    /** 对单个 ByteLevel 片段应用 BPE merges，按 rank 从高优先级到低优先级合并 */
    private fun encodeBpeToken(token: String): List<Int> {
        bpeCache[token]?.let { return it }
        if (token.isEmpty()) return emptyList()
        val directId = encoder[token]
        if (directId != null) return listOf(directId).also { bpeCache[token] = it }

        var parts = token.map { it.toString() }
        while (parts.size > 1) {
            var bestIndex = -1
            var bestRank = Int.MAX_VALUE
            for (i in 0 until parts.lastIndex) {
                val rank = bpeRanks[parts[i] to parts[i + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestIndex = i
                }
            }
            if (bestIndex < 0) break

            val merged = mutableListOf<String>()
            var i = 0
            while (i < parts.size) {
                if (i < parts.lastIndex &&
                    parts[i] == parts[bestIndex] &&
                    parts[i + 1] == parts[bestIndex + 1]
                ) {
                    merged.add(parts[i] + parts[i + 1])         // 同一轮合并所有相同最优 pair
                    i += 2
                } else {
                    merged.add(parts[i])
                    i++
                }
            }
            parts = merged
        }

        val encoded = parts.flatMap { part ->
            encoder[part]?.let { listOf(it) }
                ?: encodeUnknownByteLevelPart(part)             // 极端缺词时按字符回退，保证不崩溃
        }
        bpeCache[token] = encoded
        return encoded
    }

    /** ByteLevel 未知片段回退编码，避免 tokenizer.json 异常时整段丢失 */
    private fun encodeUnknownByteLevelPart(part: String): List<Int> {
        return part.mapNotNull { ch -> encoder[ch.toString()] }
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
        if (tokenId in controlTokenIdSet) return ""      // 所有模板控制 token 都不直接显示
        if (isControlTokenText(tokenStr)) return ""      // 过滤 vocab 内但未登记为 added_token 的角色符

        // SentencePiece 字节 token <0xNN> 需要按字节解码
        if (isByteFallbackToken(tokenStr)) {
            val hex = tokenStr.substring(3, 5)
            return try {
                String(byteArrayOf(hex.toInt(16).toByte()), Charsets.UTF_8)
            } catch (_: Exception) { "" }
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
                if (id in controlTokenIdSet) continue    // 跳过控制 token，避免模板符进入 UI
                for (ch in tokenStr) {
                    val b = unicodeToBytes[ch]
                    if (b != null) bytes.add(b.toByte())
                }
            }
            decodeUtf8Bytes(bytes.toByteArray())
        } else {
            decodeSentencePieceTokens(tokens)            // SentencePiece 需要合并 <0xNN> 字节回退 token
        }
    }

    /** SentencePiece 解码：过滤控制符并合并连续 <0xNN> 字节 token */
    private fun decodeSentencePieceTokens(tokens: List<Int>): String {
        val text = StringBuilder()
        val bytes = mutableListOf<Byte>()
        for (id in tokens) {
            val tokenStr = decoder[id] ?: continue
            if (id in controlTokenIdSet || isControlTokenText(tokenStr)) continue
            if (isByteFallbackToken(tokenStr)) {
                tokenStr.substring(3, 5).toIntOrNull(16)?.let { bytes.add(it.toByte()) }
                continue
            }
            flushDecodedBytes(bytes, text)
            text.append(tokenStr.replace("▁", " "))
        }
        flushDecodedBytes(bytes, text)
        return text.toString()
    }

    /** 将累计字节按 UTF-8 写入输出文本，并清空缓冲 */
    private fun flushDecodedBytes(bytes: MutableList<Byte>, text: StringBuilder) {
        if (bytes.isEmpty()) return
        text.append(decodeUtf8Bytes(bytes.toByteArray()))
        bytes.clear()
    }

    /** GPT-2 字节级解码：Unicode 字符转回字节 */
    private fun decodeByteLevel(tokenStr: String): String {
        val bytes = tokenStr.map { ch ->
            (unicodeToBytes[ch] ?: ch.code).toByte()
        }.toByteArray()
        return try {
            decodeUtf8Bytes(bytes)
        } catch (_: Exception) { "" }
    }

    /** UTF-8 解码时忽略尚未完成的多字节片段，避免流式输出出现 � 并触发重复渲染 */
    private fun decodeUtf8Bytes(bytes: ByteArray): String {
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    // ==================== 聊天模板 ====================

    override fun formatChat(userMessage: String): String {
        return when (chatTemplate) {
            ChatTemplate.PLAIN -> userMessage           // 无模板模型不能强行注入 ChatML 控制符

            ChatTemplate.CHATML ->                      // SmolLM2, Qwen3, DeepSeek, Phi-3 等
                "<|im_start|>user\n$userMessage<|im_end|>\n<|im_start|>assistant\n"

            ChatTemplate.QWEN3 ->                       // Qwen3 默认关闭思考块，移动端 UI 只展示最终答复
                "<|im_start|>user\n$userMessage /no_think<|im_end|>\n<|im_start|>assistant\n"

            ChatTemplate.DEEPSEEK_R1 ->                 // DeepSeek-R1 Distill 官方模板，并预填空 thinking 直接进入正文
                "<｜begin▁of▁sentence｜><｜User｜>$userMessage<｜Assistant｜><think>\n</think>\n\n"

            ChatTemplate.TINYLLAMA ->                   // TinyLlama Chat 官方模板
                "<|user|>\n$userMessage</s><|assistant|>\n"

            ChatTemplate.LLAMA3 ->                      // Llama 3.2
                "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\n$userMessage<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"

            ChatTemplate.GEMMA ->                       // Gemma 3
                "<start_of_turn>user\n$userMessage<end_of_turn>\n<start_of_turn>model\n"
        }
    }

    // ==================== 工具方法 ====================

    /** 检测 pre_tokenizer 是否包含 ByteLevel */
    private fun detectByteLevel(preTokenizer: Map<String, Any>?, decoderConfig: Map<String, Any>?): Boolean {
        if (usesSentencePieceStyleDecoder(decoderConfig)) return false // Gemma BPE 用 ▁ 空格替换，不是 GPT-2 ByteLevel
        if (preTokenizer == null) return false
        val type = preTokenizer["type"] as? String ?: ""
        if (type == "ByteLevel") return true

        // 嵌套结构（Sequence 类型）
        @Suppress("UNCHECKED_CAST")
        val pretokenizers = preTokenizer["pretokenizers"] as? List<Map<String, Any>>
        return pretokenizers?.any { (it["type"] as? String) == "ByteLevel" } == true
    }

    /** 判断 decoder 是否为 SentencePiece/Gemma 风格：用 ▁ 表示空格，再做 ByteFallback 融合 */
    private fun usesSentencePieceStyleDecoder(decoderConfig: Map<String, Any>?): Boolean {
        if (decoderConfig == null) return false
        if (decoderConfig.toString().contains("▁")) return true // tokenizer.json 声明了 ▁ 空格恢复规则
        return false
    }

    /** 判断 added token 是否为模型模板/多模态/推理控制符 */
    private fun isControlTokenText(text: String): Boolean {
        return text.startsWith("<") && text.endsWith(">") && !isByteFallbackToken(text) // 排除 SentencePiece 字节 token
    }

    /** 判断是否为 SentencePiece 字节回退 token，例如 <0x0A> */
    private fun isByteFallbackToken(text: String): Boolean {
        return text.startsWith("<0x") && text.endsWith(">") && text.length == 6
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

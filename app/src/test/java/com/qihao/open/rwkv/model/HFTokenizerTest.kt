/**
 * HFTokenizerTest - HuggingFace BPE 分词器回归测试
 *
 * 功能：
 * - 验证 ChatML 特殊 token 不会被拆成普通字符
 * - 验证 ByteLevel BPE merges 会被真实应用
 * - 验证解码阶段过滤特殊控制符，避免 UI 出现模板标记
 */
package com.qihao.open.rwkv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HFTokenizerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun chatMlPromptUsesSpecialTokensAndBpeMerges() {
        val tokenizer = HFTokenizer(writeMiniBpeTokenizer(), ChatTemplate.CHATML)

        val ids = tokenizer.encode(tokenizer.formatChat("hello"))

        assertEquals(listOf(1, 3, 4, 12, 2, 4, 1, 19, 24, 4), ids)
    }

    @Test
    fun byteLevelDecodeFiltersTemplateControlTokens() {
        val tokenizer = HFTokenizer(writeMiniBpeTokenizer(), ChatTemplate.CHATML)

        val text = tokenizer.decode(listOf(1, 3, 4, 12, 2, 4, 1, 19, 24, 4))

        assertEquals("user\nhello\nassistant\n", text)
        assertEquals("", tokenizer.decode(1))
        assertTrue(tokenizer.stopTokenIds.contains(1))
        assertTrue(tokenizer.stopTokenIds.contains(2))
    }

    @Test
    fun byteLevelDecodeIgnoresIncompleteUtf8Bytes() {
        val tokenizer = HFTokenizer(writeMiniBpeTokenizer(), ChatTemplate.CHATML)

        assertEquals("", tokenizer.decode(listOf(30, 31, 32)))
        assertEquals("😊", tokenizer.decode(listOf(30, 31, 32, 33)))
    }

    @Test
    fun qwen3TemplateRequestsNoThinkingBlock() {
        val tokenizer = HFTokenizer(writeMiniBpeTokenizer(), ChatTemplate.QWEN3)

        val prompt = tokenizer.formatChat("hello")

        assertTrue(prompt.contains("hello /no_think"))
    }

    @Test
    fun deepSeekTemplateUsesOfficialControlTokensAndFiltersThem() {
        val tokenizer = HFTokenizer(writeMiniDeepSeekTokenizer(), ChatTemplate.DEEPSEEK_R1)

        val prompt = tokenizer.formatChat("hello")
        val decoded = tokenizer.decode(listOf(151646, 151644, 100, 151645, 151648, 101, 151649, 151643))

        assertTrue(prompt.startsWith("<｜begin▁of▁sentence｜><｜User｜>hello<｜Assistant｜>"))
        assertTrue(prompt.contains("<think>\n</think>\n\n"))
        assertEquals(" hello world", decoded)
    }

    @Test
    fun tinyLlamaTemplateAndSentencePieceByteFallbackDecode() {
        val tokenizer = HFTokenizer(writeMiniTinyLlamaTokenizer(), ChatTemplate.TINYLLAMA)

        val prompt = tokenizer.formatChat("hello")
        val decoded = tokenizer.decode(listOf(10, 100, 20, 30, 101, 2))

        assertTrue(prompt.startsWith("<|user|>\nhello</s><|assistant|>"))
        assertEquals(" hello\n world", decoded)
    }

    /** 写入最小 ByteLevel BPE tokenizer，复现 SmolLM2 的 ChatML 关键路径 */
    private fun writeMiniBpeTokenizer(): String {
        val file = temporaryFolder.newFile("tokenizer.json")
        file.writeText(
            """
            {
              "model": {
                "type": "BPE",
                "vocab": {
                  "<|endoftext|>": 0,
                  "<|im_start|>": 1,
                  "<|im_end|>": 2,
                  "user": 3,
                  "Ċ": 4,
                  "h": 5,
                  "e": 6,
                  "l": 7,
                  "o": 8,
                  "he": 9,
                  "hel": 10,
                  "hell": 11,
                  "hello": 12,
                  "a": 13,
                  "s": 14,
                  "i": 15,
                  "t": 16,
                  "n": 17,
                  "as": 18,
                  "ass": 19,
                  "is": 20,
                  "ist": 21,
                  "ista": 22,
                  "istan": 23,
                  "istant": 24,
                  "ð": 30,
                  "Ł": 31,
                  "ĺ": 32,
                  "Ĭ": 33
                },
                "merges": [
                  "h e",
                  "he l",
                  "hel l",
                  "hell o",
                  "a s",
                  "as s",
                  "i s",
                  "is t",
                  "ist a",
                  "ista n",
                  "istan t"
                ]
              },
              "added_tokens": [
                {"id": 0, "content": "<|endoftext|>", "special": true},
                {"id": 1, "content": "<|im_start|>", "special": true},
                {"id": 2, "content": "<|im_end|>", "special": true}
              ],
              "pre_tokenizer": {
                "type": "Sequence",
                "pretokenizers": [
                  {"type": "Digits", "individual_digits": true},
                  {"type": "ByteLevel", "add_prefix_space": false, "trim_offsets": true, "use_regex": true}
                ]
              },
              "decoder": {"type": "ByteLevel", "add_prefix_space": true, "trim_offsets": true, "use_regex": true}
            }
            """.trimIndent()
        )
        return file.absolutePath
    }

    /** 写入最小 SentencePiece tokenizer，复现 DeepSeek-R1 added token 控制符路径 */
    private fun writeMiniDeepSeekTokenizer(): String {
        val file = temporaryFolder.newFile("deepseek-tokenizer.json")
        file.writeText(
            """
            {
              "model": {
                "type": "Unigram",
                "vocab": {
                  "▁hello": 100,
                  "▁world": 101,
                  "<｜end▁of▁sentence｜>": 151643,
                  "<｜User｜>": 151644,
                  "<｜Assistant｜>": 151645,
                  "<｜begin▁of▁sentence｜>": 151646,
                  "<think>": 151648,
                  "</think>": 151649
                }
              },
              "added_tokens": [
                {"id": 151643, "content": "<｜end▁of▁sentence｜>", "special": true},
                {"id": 151644, "content": "<｜User｜>", "special": false},
                {"id": 151645, "content": "<｜Assistant｜>", "special": false},
                {"id": 151646, "content": "<｜begin▁of▁sentence｜>", "special": true},
                {"id": 151648, "content": "<think>", "special": false},
                {"id": 151649, "content": "</think>", "special": false}
              ]
            }
            """.trimIndent()
        )
        return file.absolutePath
    }

    /** 写入最小 TinyLlama tokenizer，复现 vocab 控制符和 <0xNN> 字节解码 */
    private fun writeMiniTinyLlamaTokenizer(): String {
        val file = temporaryFolder.newFile("tinyllama-tokenizer.json")
        file.writeText(
            """
            {
              "model": {
                "type": "Unigram",
                "vocab": {
                  "</s>": 2,
                  "<|user|>": 10,
                  "<|assistant|>": 20,
                  "<0x0A>": 30,
                  "<0x20>": 31,
                  "▁hello": 100,
                  "▁world": 101
                }
              },
              "added_tokens": [
                {"id": 2, "content": "</s>", "special": true}
              ]
            }
            """.trimIndent()
        )
        return file.absolutePath
    }
}

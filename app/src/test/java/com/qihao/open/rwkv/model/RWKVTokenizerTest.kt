/**
 * RWKVTokenizerTest - RWKV 分词器基础行为测试
 *
 * 功能：
 * - 验证最长匹配编码
 * - 验证 token 解码
 * - 验证 RWKV 聊天模板格式
 */
package com.qihao.open.rwkv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RWKVTokenizerTest {

    @Test
    fun encodeUsesLongestAvailableToken() {
        val tokenizer = RWKVTokenizer("""{"1":"A","2":"AB","3":"B"}""")

        assertEquals(listOf(2), tokenizer.encode("AB"))
    }

    @Test
    fun decodeReturnsMappedTokenText() {
        val tokenizer = RWKVTokenizer("""{"1":"A","2":"B"}""")

        assertEquals("AB", tokenizer.decode(listOf(1, 2)))
        assertEquals("A", tokenizer.decode(1))
    }

    @Test
    fun formatChatUsesRwkvPromptShape() {
        val tokenizer = RWKVTokenizer("""{"1":"A"}""")

        assertEquals("User: hello\n\nAssistant:", tokenizer.formatChat("hello"))
        assertTrue(tokenizer.stopTokenIds.contains(0))
    }
}

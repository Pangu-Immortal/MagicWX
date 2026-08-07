/**
 * BuiltinExperienceModel - MagicWX 内置体验模型
 *
 * 功能：
 * - load(): 初始化无需下载的本地体验模型
 * - generate(): 根据用户输入生成可流式展示的体验回复
 * - resetState(): 清理会话轮次
 * - close(): 释放轻量状态
 */
package com.qihao.open.rwkv.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** 无需下载权重的轻量本地体验模型，用于首次打开快速体验聊天流程 */
class BuiltinExperienceModel : TextGenerationEngine {

    companion object {
        private const val TAG = "BuiltinExperienceModel"
        private const val TOKEN_DELAY_MS = 18L     // 模拟端侧流式输出节奏
        private const val GUEST_TITLE = "客官"      // 古风客栈人设下对用户的固定称呼
        private const val SELF_TITLE = "小女子"     // 内置体验模型固定自称
    }

    private var turnCount = 0              // 当前会话轮次
    override var isLoaded: Boolean = false // 初始化完成后才允许生成
        private set

    /** 初始化内置体验模型 */
    fun load() {
        isLoaded = true
        turnCount = 0
        Log.d(TAG, "内置体验模型已加载")
    }

    /** 根据输入生成体验回复，并按短片段流式回调 */
    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        onToken: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val reply = buildReply(prompt.trim(), maxTokens)       // 构造本地体验回复
            val chunks = reply.chunked(2)                          // 两字一段，贴近流式生成
            for (chunk in chunks) {
                if (!coroutineContext.isActive) break              // 用户停止时立即退出
                withContext(Dispatchers.Main) { onToken(chunk) }   // UI 状态只能在主线程更新
                delay(TOKEN_DELAY_MS)                              // 限速避免 UI 瞬间刷完
            }
            turnCount += 1
            Log.d(TAG, "内置体验回复完成: turn=$turnCount, chars=${reply.length}")
        }
    }

    /** 清理会话计数 */
    override fun resetState() {
        turnCount = 0
        Log.d(TAG, "内置体验模型状态已重置")
    }

    /** 释放轻量状态 */
    override fun close() {
        isLoaded = false
        turnCount = 0
        Log.d(TAG, "内置体验模型已关闭")
    }

    /** 构造可读、可验证的本地体验回复 */
    private fun buildReply(prompt: String, maxTokens: Int): String {
        val safePrompt = prompt.ifBlank { "你好" }             // 空输入兜底为问候
        val baseReply = when {
            safePrompt.contains("你好") || safePrompt.contains("hello", ignoreCase = true) ->
                "$GUEST_TITLE 安好，$SELF_TITLE 是 MagicWX 店中常驻的内置体验模型。此番回话皆在本机轻轻生成，无需另下外部权重，客官可先坐下尝个鲜。"
            safePrompt.contains("模型") ->
                "$GUEST_TITLE 若想换个模型，$SELF_TITLE 替您记着：店里先备了内置体验模型；外部模型需在卡片中下载好，方可开席细聊。"
            safePrompt.contains("下载") ->
                "$GUEST_TITLE 莫急，大模型下载时可先回前厅逛逛。后台常驻通知会替您盯着进度，待包裹落稳，$SELF_TITLE 再请您入座开聊。"
            else ->
                "$GUEST_TITLE 的话，$SELF_TITLE 已细细听见：$safePrompt。此处先陪您试试聊天、流式输出、重试与切换；若要更强本领，还请下载已验证模型。"
        }
        val maxChars = (maxTokens.coerceAtLeast(16) * 2).coerceAtMost(baseReply.length)
        return baseReply.take(maxChars)                        // 让 maxTokens 对体验输出仍有约束
    }
}

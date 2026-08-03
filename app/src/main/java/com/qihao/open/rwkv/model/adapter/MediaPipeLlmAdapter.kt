/**
 * MediaPipeLlmAdapter - MediaPipe LLM Inference 文本模型适配器
 *
 * 功能：
 * - MediaPipeLlmEngine: 用 MediaPipe Tasks GenAI 加载 .task 端侧大模型包
 * - MediaPipeLlmAdapter: 将 LITERT_LM / MEDIAPIPE_TASK 文本模型接入统一 TextGenerationEngine
 */
package com.qihao.open.rwkv.model.adapter

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.qihao.open.rwkv.model.ModelCapability
import com.qihao.open.rwkv.model.ModelAssetKind
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.TextGenerationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** MediaPipe LLM 文本生成引擎，封装官方 LlmInference 生命周期 */
class MediaPipeLlmEngine(
    private val context: Context,                         // Android 上下文，用于创建 MediaPipe runtime
    private val modelPath: String,                        // 本地 .task / .bin 模型路径
    private val maxTokens: Int = DEFAULT_MAX_TOKENS       // 默认生成长度，避免移动端长时间阻塞
) : TextGenerationEngine {

    companion object {
        private const val TAG = "MediaPipeLlmEngine"
        private const val DEFAULT_MAX_TOKENS = 256
    }

    private var inference: LlmInference? = null            // MediaPipe LLM 推理实例

    override val isLoaded: Boolean get() = inference != null

    /** 初始化 MediaPipe LLM runtime；必须放后台线程，避免阻塞 Compose 主线程 */
    suspend fun load() = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始加载 MediaPipe LLM: $modelPath")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .build()
        inference = LlmInference.createFromOptions(context, options)
        Log.d(TAG, "MediaPipe LLM 加载完成")
    }

    /** 生成回复；当前先使用同步接口，保证首版 adapter 稳定可编译、可测 */
    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val engine = inference ?: return@withContext
        Log.d(TAG, "MediaPipe LLM 开始生成: maxTokens=$maxTokens, temperature=$temperature, topP=$topP")
        val response = engine.generateResponse(prompt)
        Log.d(TAG, "MediaPipe LLM 生成完成: chars=${response.length}")
        if (response.isNotBlank()) {
            withContext(Dispatchers.Main) { onToken(response) }
        }
    }

    /** MediaPipe 同步接口每次生成会重置隐式 session；这里保留统一接口语义 */
    override fun resetState() {
        Log.d(TAG, "MediaPipe LLM resetState: 使用隐式 session，无需手动重置")
    }

    /** 释放 MediaPipe native runtime */
    override fun close() {
        try {
            inference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe LLM 释放失败: ${e.message}", e)
        } finally {
            inference = null
        }
    }
}

/** MediaPipe LLM adapter，负责从模型资产中找到 .task / .litertlm / .bin 并创建文本引擎 */
class MediaPipeLlmAdapter : ModelRuntimeAdapter {

    companion object {
        private const val TAG = "MediaPipeLlmAdapter"
    }

    override val adapterType: RuntimeAdapterType = RuntimeAdapterType.LITERT_LM

    override fun canLoad(modelInfo: ModelInfo): Boolean {
        return modelInfo.adapterAvailable &&
            modelInfo.capability == ModelCapability.TEXT_CHAT &&
            modelInfo.adapterType in setOf(RuntimeAdapterType.LITERT_LM, RuntimeAdapterType.MEDIAPIPE_TASK)
    }

    override suspend fun load(
        context: Context,
        modelInfo: ModelInfo,
        downloader: ModelDownloader
    ): RuntimeLoadResult {
        if (!canLoad(modelInfo)) {
            return RuntimeLoadResult.Unsupported(modelInfo.unavailableReason.ifBlank {
                "当前模型不是可加载的 MediaPipe LLM 文本模型"
            })
        }

        val readiness = downloader.getModelReadiness(modelInfo)
        if (!readiness.isReady) {
            return RuntimeLoadResult.Unsupported(readiness.reason)
        }

        val modelAsset = modelInfo.resolvedAssets().firstOrNull { asset ->
            asset.kind in setOf(ModelAssetKind.TASK, ModelAssetKind.MODEL)
        } ?: return RuntimeLoadResult.Unsupported("MediaPipe LLM 模型缺少 .task/.litertlm 资产声明")

        val modelFile = File(downloader.getAssetPath(modelInfo, modelAsset))
        if (!modelFile.isFile || modelFile.length() <= 0L) {
            return RuntimeLoadResult.Unsupported("MediaPipe LLM 模型文件不存在或为空: ${modelAsset.filename}")
        }

        Log.d(TAG, "开始加载 MediaPipe LLM 模型: ${modelInfo.id}")
        val engine = MediaPipeLlmEngine(context.applicationContext, modelFile.absolutePath)
        engine.load()
        Log.d(TAG, "MediaPipe LLM 模型加载完成: ${modelInfo.id}")
        return RuntimeLoadResult.Text(engine)
    }
}

/**
 * LlamaCppAdapter - GGUF / llama.cpp 文本模型适配器
 *
 * 功能：
 * - LlamaCppEngine: 使用 java-llama.cpp 加载本地 GGUF 文件并生成文本
 * - LlamaCppAdapter: 将 LLAMA_CPP 模型接入统一 TextGenerationEngine
 */
package com.qihao.open.rwkv.model.adapter

import android.content.Context
import android.util.Log
import com.qihao.open.rwkv.model.ChatTemplate
import com.qihao.open.rwkv.model.ModelAssetKind
import com.qihao.open.rwkv.model.ModelCapability
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.TextGenerationEngine
import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** llama.cpp 文本生成引擎，封装 GGUF native runtime 生命周期 */
class LlamaCppEngine(
    private val context: Context,                         // Android 上下文，用于配置 native 临时目录
    private val modelPath: String,                        // 本地 GGUF 模型文件路径
    private val chatTemplate: ChatTemplate                // 模型对应聊天模板
) : TextGenerationEngine {

    companion object {
        private const val TAG = "LlamaCppEngine"
        private const val CTX_SIZE = 1024                 // 移动端默认上下文窗口，控制内存占用
        private const val BATCH_SIZE = 128                // 小批量降低首轮内存峰值
        private const val MAX_PREDICT = 256               // 限制单轮生成长度，避免真机长时间阻塞
        private val STOP_STRINGS = arrayOf(
            "</s>",
            "<s>",
            "<|eot_id|>",
            "<|end_of_text|>",
            "<|im_end|>",
            "<|user|>",
            "User:"
        )
    }

    private var llamaModel: LlamaModel? = null            // java-llama.cpp native 模型实例

    override val isLoaded: Boolean get() = llamaModel != null

    /** 初始化 llama.cpp native runtime；模型加载必须在后台线程执行 */
    suspend fun load() = withContext(Dispatchers.IO) {
        configureNativeTempDir()
        val threads = max(2, Runtime.getRuntime().availableProcessors() / 2)
        val parameters = ModelParameters()
            .setModel(modelPath)
            .setCtxSize(CTX_SIZE)
            .setBatchSize(BATCH_SIZE)
            .setThreads(threads)
            .setThreadsBatch(threads)
            .disableLog()

        Log.d(TAG, "开始加载 GGUF 模型: path=$modelPath, threads=$threads")
        llamaModel = LlamaModel(parameters)
        Log.d(TAG, "GGUF 模型加载完成: $modelPath")
    }

    /** 生成回复；逐 token 回调给 Compose 层显示 */
    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val activeModel = llamaModel ?: return@withContext
        val formattedPrompt = formatPrompt(prompt)
        val effectiveTopP = topP.coerceIn(0.8f, 1.0f)  // llama.cpp 对过低 topP 敏感，提升下限避免重复退化
        val parameters = InferenceParameters(formattedPrompt)
            .setNPredict(maxTokens.coerceIn(1, MAX_PREDICT))
            .setTemperature(temperature.coerceIn(0.0f, 2.0f))
            .setTopK(40)
            .setTopP(effectiveTopP)
            .setRepeatLastN(64)
            .setRepeatPenalty(1.1f)
            .setStopStrings(*STOP_STRINGS)

        Log.d(TAG, "llama.cpp 开始生成: maxTokens=$maxTokens, temperature=$temperature, topP=$effectiveTopP")
        val completion = activeModel.complete(parameters) // complete() 只返回补全文本，避免 generate() 把 prompt 回显到 UI
        if (completion.isNotBlank()) {
            withContext(Dispatchers.Main) { onToken(completion) } // 统一回到主线程更新 Compose 消息状态
        }
        Log.d(TAG, "llama.cpp 生成完成")
    }

    /** llama.cpp 当前按完整 prompt 生成；重试时由上层重新传入 prompt 即可 */
    override fun resetState() {
        Log.d(TAG, "llama.cpp resetState: 当前引擎不保存跨轮上下文")
    }

    /** 释放 native 模型资源，避免切换模型后持续占用内存 */
    override fun close() {
        try {
            llamaModel?.close()
        } catch (e: Exception) {
            Log.e(TAG, "llama.cpp 模型释放失败: ${e.message}", e)
        } finally {
            llamaModel = null
        }
    }

    /** 设置 java-llama.cpp native 资源解压目录，避免 Android 默认 tmp 目录不可写 */
    private fun configureNativeTempDir() {
        val tempDir = File(context.codeCacheDir, "jllama")
        if (!tempDir.exists()) tempDir.mkdirs()
        System.setProperty("de.kherud.llama.tmpdir", tempDir.absolutePath)
    }

    /** 根据注册表模板构造最小聊天 prompt，不插入额外策略或审查文本 */
    private fun formatPrompt(userMessage: String): String {
        return when (chatTemplate) {
            ChatTemplate.LLAMA3 ->
                "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\n$userMessage<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
            ChatTemplate.TINYLLAMA ->
                "<|user|>\n$userMessage</s><|assistant|>\n"
            ChatTemplate.GEMMA ->
                "<start_of_turn>user\n$userMessage<end_of_turn>\n<start_of_turn>model\n"
            ChatTemplate.RWKV_USER_ASSISTANT ->
                "<s>User: $userMessage\n\nAssistant:"
            ChatTemplate.CHATML, ChatTemplate.QWEN3, ChatTemplate.DEEPSEEK_R1 ->
                "<|im_start|>user\n$userMessage<|im_end|>\n<|im_start|>assistant\n"
            ChatTemplate.PLAIN -> userMessage
        }
    }
}

/** llama.cpp adapter，负责从显式资产中定位 .gguf 主文件并创建文本引擎 */
class LlamaCppAdapter : ModelRuntimeAdapter {

    companion object {
        private const val TAG = "LlamaCppAdapter"
    }

    override val adapterType: RuntimeAdapterType = RuntimeAdapterType.LLAMA_CPP

    override fun canLoad(modelInfo: ModelInfo): Boolean {
        return modelInfo.adapterAvailable &&
            modelInfo.capability == ModelCapability.TEXT_CHAT &&
            modelInfo.adapterType == RuntimeAdapterType.LLAMA_CPP
    }

    override suspend fun load(
        context: Context,
        modelInfo: ModelInfo,
        downloader: ModelDownloader
    ): RuntimeLoadResult {
        if (!canLoad(modelInfo)) {
            return RuntimeLoadResult.Unsupported(modelInfo.unavailableReason.ifBlank {
                "当前模型不是可加载的 llama.cpp 文本模型"
            })
        }

        val readiness = downloader.getModelReadiness(modelInfo)
        if (!readiness.isReady) {
            return RuntimeLoadResult.Unsupported(readiness.reason)
        }

        val modelAsset = modelInfo.resolvedAssets().firstOrNull { asset ->
            asset.kind == ModelAssetKind.MODEL && asset.filename.endsWith(".gguf")
        } ?: return RuntimeLoadResult.Unsupported("llama.cpp 模型缺少 .gguf 资产声明")

        val modelFile = File(downloader.getAssetPath(modelInfo, modelAsset))
        if (!modelFile.isFile || modelFile.length() <= 0L) {
            return RuntimeLoadResult.Unsupported("GGUF 模型文件不存在或为空: ${modelAsset.filename}")
        }

        Log.d(TAG, "开始加载 llama.cpp 模型: ${modelInfo.id}")
        val engine = LlamaCppEngine(context.applicationContext, modelFile.absolutePath, modelInfo.chatTemplate)
        engine.load()
        Log.d(TAG, "llama.cpp 模型加载完成: ${modelInfo.id}")
        return RuntimeLoadResult.Text(engine)
    }
}

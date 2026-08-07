/**
 * ModelRuntimeAdapter - 模型运行时 adapter 分发层
 *
 * 功能：
 * - RuntimeLoadResult: 描述模型加载结果，区分文本引擎和未支持原因
 * - ModelRuntimeAdapter: 所有模型运行时 adapter 的统一接口
 * - BuiltinTextAdapter: 内置体验模型 adapter
 * - OnnxTextGenerationAdapter: ONNX Runtime 文本生成 adapter
 * - LlamaCppAdapter: GGUF / llama.cpp 文本生成 adapter
 * - QnnImageGenerationAdapter: QNN / NPU 生图 adapter（设备门禁 + 隔离后端进程）
 * - UnsupportedRuntimeAdapter: 未接入 runtime 的候选模型阻断 adapter
 * - ModelRuntimeAdapterFactory: 按 ModelInfo.adapterType 选择具体 adapter
 */
package com.qihao.open.rwkv.model.adapter

import android.content.Context
import android.util.Log
import com.qihao.open.rwkv.model.BuiltinExperienceModel
import com.qihao.open.rwkv.model.HFTokenizer
import com.qihao.open.rwkv.model.ModelArch
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.RWKVModel
import com.qihao.open.rwkv.model.RWKVTokenizer
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.TextGenerationEngine
import com.qihao.open.rwkv.model.image.ImageGenerationEngine
import com.qihao.open.rwkv.model.image.MediaPipeImageGenerationAdapter
import com.qihao.open.rwkv.model.image.MnnImageGenerationAdapter
import com.qihao.open.rwkv.model.image.QnnImageGenerationAdapter

/** 模型加载结果 */
sealed class RuntimeLoadResult {
    data class Text(val engine: TextGenerationEngine) : RuntimeLoadResult() // 已加载文本生成引擎
    data class Image(val engine: ImageGenerationEngine) : RuntimeLoadResult() // 已加载图像生成引擎
    data class Unsupported(val reason: String) : RuntimeLoadResult()        // 当前 runtime 不支持
}

/** 模型运行时 adapter 统一接口 */
interface ModelRuntimeAdapter {
    val adapterType: RuntimeAdapterType                 // adapter 类型
    fun canLoad(modelInfo: ModelInfo): Boolean          // 是否允许尝试加载
    suspend fun load(
        context: Context,
        modelInfo: ModelInfo,
        downloader: ModelDownloader
    ): RuntimeLoadResult
}

/** 内置文本体验模型 adapter */
class BuiltinTextAdapter : ModelRuntimeAdapter {
    override val adapterType: RuntimeAdapterType = RuntimeAdapterType.BUILTIN_TEXT

    override fun canLoad(modelInfo: ModelInfo): Boolean {
        return modelInfo.adapterType == adapterType && modelInfo.arch == ModelArch.BUILTIN
    }

    override suspend fun load(
        context: Context,
        modelInfo: ModelInfo,
        downloader: ModelDownloader
    ): RuntimeLoadResult {
        val engine = BuiltinExperienceModel()           // 内置模型不依赖外部权重
        engine.load()                                   // 初始化固定体验回复引擎
        return RuntimeLoadResult.Text(engine)
    }
}

/** ONNX Runtime 文本生成 adapter */
class OnnxTextGenerationAdapter : ModelRuntimeAdapter {
    companion object {
        private const val TAG = "OnnxTextGenerationAdapter"
    }

    override val adapterType: RuntimeAdapterType = RuntimeAdapterType.ONNX_TEXT_GENERATION

    override fun canLoad(modelInfo: ModelInfo): Boolean {
        return modelInfo.adapterType == adapterType && modelInfo.adapterAvailable
    }

    override suspend fun load(
        context: Context,
        modelInfo: ModelInfo,
        downloader: ModelDownloader
    ): RuntimeLoadResult {
        val readiness = downloader.getModelReadiness(modelInfo) // 加载前统一检查资产完整性
        if (!readiness.isReady) {
            return RuntimeLoadResult.Unsupported(readiness.reason)
        }

        val tokenizer = if (modelInfo.arch == ModelArch.RWKV) {
            RWKVTokenizer(context)                       // RWKV 使用内置词表 tokenizer
        } else {
            val tokenizerPath = downloader.getTokenizerPath(modelInfo.id)
                ?: return RuntimeLoadResult.Unsupported("Transformer 模型缺少 tokenizer.json: ${modelInfo.id}")
            HFTokenizer(tokenizerPath, modelInfo.chatTemplate) // Transformer 使用下载的 HF tokenizer
        }

        Log.d(TAG, "开始加载 ONNX 文本模型: ${modelInfo.id}")
        val engine = RWKVModel(tokenizer)                // 复用现有 ONNX 文本生成封装
        engine.loadModel(downloader.getModelPath(modelInfo.id))
        Log.d(TAG, "ONNX 文本模型加载完成: ${modelInfo.id}")
        return RuntimeLoadResult.Text(engine)
    }
}

/** 未支持 adapter：用于候选模型和隐藏模型，防止假加载成功 */
class UnsupportedRuntimeAdapter(
    override val adapterType: RuntimeAdapterType,
    private val reason: String
) : ModelRuntimeAdapter {
    override fun canLoad(modelInfo: ModelInfo): Boolean = false

    override suspend fun load(
        context: Context,
        modelInfo: ModelInfo,
        downloader: ModelDownloader
    ): RuntimeLoadResult {
        return RuntimeLoadResult.Unsupported(reason.ifBlank {
            "当前模型需要 ${modelInfo.adapterType} adapter，MagicWX 尚未接入该运行时"
        })
    }
}

/** 按模型元信息创建对应 runtime adapter */
object ModelRuntimeAdapterFactory {
    fun create(modelInfo: ModelInfo): ModelRuntimeAdapter {
        return when (modelInfo.adapterType) {
            RuntimeAdapterType.BUILTIN_TEXT -> BuiltinTextAdapter()
            RuntimeAdapterType.ONNX_TEXT_GENERATION -> {
                if (modelInfo.adapterAvailable) {
                    OnnxTextGenerationAdapter()
                } else {
                    UnsupportedRuntimeAdapter(modelInfo.adapterType, modelInfo.unavailableReason)
                }
            }
            RuntimeAdapterType.LITERT_LM -> {
                if (modelInfo.adapterAvailable) {
                    MediaPipeLlmAdapter()
                } else {
                    UnsupportedRuntimeAdapter(modelInfo.adapterType, modelInfo.unavailableReason)
                }
            }
            RuntimeAdapterType.LLAMA_CPP -> {
                if (modelInfo.adapterAvailable) {
                    LlamaCppAdapter()
                } else {
                    UnsupportedRuntimeAdapter(modelInfo.adapterType, modelInfo.unavailableReason)
                }
            }
            RuntimeAdapterType.MEDIAPIPE_IMAGE_GENERATION -> {
                if (modelInfo.adapterAvailable) {
                    MediaPipeImageGenerationAdapter()
                } else {
                    UnsupportedRuntimeAdapter(modelInfo.adapterType, modelInfo.unavailableReason)
                }
            }
            RuntimeAdapterType.QNN_IMAGE_GENERATION -> {
                // QNN 族 adapterAvailable 由设备 SoC 门禁决定（DeviceSocCapability）：
                // 骁龙设备路由到 QNN 生图 adapter；非骁龙设备走未支持 adapter 并携带设备原因
                if (modelInfo.adapterAvailable) {
                    QnnImageGenerationAdapter()
                } else {
                    UnsupportedRuntimeAdapter(modelInfo.adapterType, modelInfo.unavailableReason)
                }
            }
            RuntimeAdapterType.MNN -> {
                if (modelInfo.adapterAvailable) {
                    MnnImageGenerationAdapter()
                } else {
                    UnsupportedRuntimeAdapter(modelInfo.adapterType, modelInfo.unavailableReason)
                }
            }
            else -> UnsupportedRuntimeAdapter(modelInfo.adapterType, modelInfo.unavailableReason)
        }
    }
}

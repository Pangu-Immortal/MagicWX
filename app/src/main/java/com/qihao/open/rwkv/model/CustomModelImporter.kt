/**
 * CustomModelImporter - 自定义模型导入扫描器
 *
 * 功能：
 * - scanCustomModels(): 扫描 models/ 子目录，解析 marker 文件识别用户导入的自定义生图模型
 * - createCustomModel(): 动态构造 ModelInfo（含兜底提示词 + generationSize + runOnCpu 语义）
 * - isReservedModelId(): 检查目录名是否与内置模型冲突
 * - readConfigJson(): 读取模型目录中的 config.json 提取默认提示词
 *
 * 对齐参照：modules/local-dream/.../Model.kt
 *   - ModelRepository.scanCustomModels  L444-482
 *   - ModelRepository.createCustomModel L485-509
 *   - ModelConfig.read                    L60-82
 */
package com.qihao.open.rwkv.model

import android.content.Context
import android.util.Log
import com.qihao.open.rwkv.model.image.LocalDreamDefaults
import org.json.JSONObject
import java.io.File

object CustomModelImporter {

    private const val TAG = "CustomModelImporter"
    private const val MODELS_DIR = "models"

    // marker 文件名，对齐 local-dream ModelRepository.scanCustomModels L461-464
    private const val MARKER_FINISHED = "finished"    // CPU 完成标记
    private const val MARKER_NPU_CUSTOM = "npucustom" // NPU 自定义标记
    private const val MARKER_SDXL = "SDXL"            // SDXL 架构标记
    private const val MARKER_ANIMA = "ANIMA"          // Anima 架构标记

    // config.json 文件名，对齐 local-dream ModelConfig.FILE_NAME L46
    private const val CONFIG_FILE = "config.json"

    /**
     * 获取模型存储根目录，路径与 ModelDownloader 一致
     * 对齐 local-dream Model.getModelsDir L259
     */
    fun getModelsDir(context: Context): File =
        File(context.filesDir, MODELS_DIR).apply {
            if (!exists()) mkdirs()
        }

    /**
     * 内置模型 ID 集合，自定义模型目录名与之冲突则跳过扫描
     * 对齐 local-dream ModelRepository.RESERVED_MODEL_IDS L915-924
     * MagicWX 侧从 ModelRegistry.allModels 动态提取，避免硬编码不同步
     */
    fun reservedModelIds(): Set<String> = ModelRegistry.allModels.map { it.id }.toSet()

    /** 检查目录名是否为内置模型保留 ID */
    fun isReservedModelId(id: String): Boolean = id in reservedModelIds()

    /**
     * 扫描 models/ 子目录，识别用户通过 zip 导入的自定义生图模型
     * 对齐 local-dream ModelRepository.scanCustomModels L444-482
     *
     * 扫描逻辑：
     * 1. 遍历 models/ 下每个子目录
     * 2. 跳过与内置模型 ID 冲突的目录名
     * 3. 按优先级检查 marker 文件：ANIMA > SDXL > finished > npucustom
     * 4. 无任何 marker 文件的目录跳过
     * 5. 结果按名称字母序排列
     *
     * @param context Android Context，用于获取 filesDir
     * @return 识别到的自定义模型列表，可能为空
     */
    fun scanCustomModels(context: Context): List<ModelInfo> {
        val modelsDir = getModelsDir(context)
        val customModels = mutableListOf<ModelInfo>()
        val reservedIds = reservedModelIds()

        if (!modelsDir.exists() || !modelsDir.isDirectory) {
            Log.d(TAG, "models/ 目录不存在或非目录，跳过自定义模型扫描")
            return customModels
        }

        modelsDir.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach

            val modelId = dir.name
            // 对齐参照 L453-458: 跳过与内置模型 ID 冲突的目录
            if (modelId in reservedIds) {
                Log.w(
                    TAG,
                    "skip custom model '$modelId': id conflicts with a built-in model"
                )
                return@forEach
            }

            // 检测 marker 文件，对齐参照 L461-478
            val finishedFile = File(dir, MARKER_FINISHED)
            val npuCustomFile = File(dir, MARKER_NPU_CUSTOM)
            val sdxlFile = File(dir, MARKER_SDXL)
            val animaFile = File(dir, MARKER_ANIMA)

            val modelInfo = when {
                animaFile.exists() ->
                    createCustomModel(dir, isNpu = true, isAnima = true)

                sdxlFile.exists() ->
                    createCustomModel(dir, isNpu = true, isSdxl = true)

                finishedFile.exists() ->
                    createCustomModel(dir, isNpu = false)

                npuCustomFile.exists() ->
                    createCustomModel(dir, isNpu = true)

                else -> null // 无任何 marker 文件，跳过
            }

            if (modelInfo != null) {
                customModels.add(modelInfo)
                Log.d(TAG, "发现自定义模型: $modelId (backend=${modelInfo.imageBackendType})")
            }
        }

        return customModels.sortedBy { it.name.lowercase() }
    }

    /**
     * 动态构造自定义模型的 ModelInfo
     * 对齐 local-dream ModelRepository.createCustomModel L485-509
     *
     * 字段映射语义：
     * - isSdxl/isAnima → generationSize(1024)，否则 512（对齐参照 L501）
     * - runOnCpu = !isNpu（对齐参照 L505）→ adapterType 选 MNN(QNN_IMAGE_GENERATION)
     * - 提示词：config.json 优先，空缺由 LocalDreamDefaults.CAT_SAT_ON_MAT 兜底
     * - adapterAvailable=true：用户已手动导入，视为本机可用
     * - verifiedOnDevice=true：目录已存在即视为已就绪
     * - 插入 models 列表首位由调用方（ViewModel/Integrator）负责
     *
     * @param modelDir 模型子目录（如 filesDir/models/my-custom-sd15）
     * @param isNpu 是否 NPU 模型（对应 marker npucustom/SDXL/ANIMA）
     * @param isSdxl 是否 SDXL 架构（对应 marker SDXL）
     * @param isAnima 是否 Anima 架构（对应 marker ANIMA）
     */
    fun createCustomModel(
        modelDir: File,
        isNpu: Boolean = false,
        isSdxl: Boolean = false,
        isAnima: Boolean = false
    ): ModelInfo {
        val modelId = modelDir.name
        // 对齐参照 L487-494: 导入模型无代码级默认值，config.json 优先，兜底提示词仅填充空缺
        val config = readConfigJson(modelDir)
        val prompt = config?.prompt ?: LocalDreamDefaults.CAT_SAT_ON_MAT
        val negativePrompt = config?.negativePrompt
            ?: LocalDreamDefaults.CAT_SAT_ON_MAT_NEGATIVE_PROMPT

        // 对齐参照 L501: isSdxl || isAnima → 1024, else → 512
        // 对齐参照 L505: runOnCpu = !isNpu（通过 adapterType 映射：MNN=CPU, QNN=NPU）

        // 确定后端类型字符串，对齐参照 Model.backendType L141-147
        val imageBackendType = when {
            isAnima -> "anima"
            isSdxl -> "sdxl"
            isNpu -> "sd15npu"
            else -> "sd15cpu"
        }

        // adapter 映射：NPU 族用 QNN_IMAGE_GENERATION，CPU 用 MNN
        val adapterType = when {
            isAnima || isSdxl || isNpu -> RuntimeAdapterType.QNN_IMAGE_GENERATION
            else -> RuntimeAdapterType.MNN
        }

        // 参数量描述，对齐参照 generationSize 语义
        val paramSize = when {
            isAnima -> "Anima"
            isSdxl -> "SDXL"
            else -> "SD1.5"
        }

        // 量化方式描述
        val quantization = if (isNpu || isAnima || isSdxl) "QNN" else "MNN"

        return ModelInfo(
            id = modelId,
            name = modelId,                                      // 对齐参照 L498: name = modelId
            description = "自定义导入模型",                        // 对齐参照 L499: R.string.custom_model
            arch = ModelArch.STABLE_DIFFUSION,
            paramSize = paramSize,
            quantization = quantization,
            downloadUrl = "",                                    // 自定义模型无需下载
            fileSizeMB = 0,                                      // 已在本地，不计算大小
            isFullySupported = false,                            // 非内置模型
            tokenizerUrl = null,                                 // 生图模型无需文本分词器
            capability = ModelCapability.IMAGE_GENERATION,
            adapterType = adapterType,
            visibility = ModelVisibility.VERIFIED,               // 用户导入模型应可见
            assets = emptyList(),                                // 无需下载资产
            imageBackendType = imageBackendType,
            requiredRuntimeFiles = emptyList(),                   // 目录已存在即视为完整
            verifiedOnDevice = true,                             // 已导入即视为已就绪
            adapterAvailable = true,                             // 用户手动导入，视为本机可用
            defaultPrompt = prompt,
            defaultNegativePrompt = negativePrompt,
            // ---- 对齐 local-dream createCustomModel L485-509 ----
            generationSize = if (isSdxl || isAnima) 1024 else 512, // 对齐参照 L501
            approximateSize = "自定义",                            // 对齐参照 L502："Custom"
            runOnCpu = !(isNpu || isSdxl || isAnima),             // 对齐参照 L505: runOnCpu = !isNpu
            isSdxl = isSdxl,                                      // 对齐参照 L507
            isAnima = isAnima,                                     // 对齐参照 L508
            isCustom = true,                                       // 对齐参照 L506
        )
    }

    /**
     * 读取模型目录中的 config.json，提取默认提示词
     * 对齐 local-dream ModelConfig.read L60-82
     *
     * 仅提取 prompt/negativePrompt；steps/cfg/scheduler 等字段由 UI 层持久化存档管理，
     * 不在 ModelInfo 中承载。
     */
    private fun readConfigJson(modelDir: File): ModelConfigDefaults? {
        val file = File(modelDir, CONFIG_FILE)
        if (!file.isFile) {
            Log.d(TAG, "模型目录 ${modelDir.name} 无 config.json，使用兜底提示词")
            return null
        }

        return try {
            val json = JSONObject(file.readText())
            ModelConfigDefaults(
                prompt = json.optStringOrNull("default_prompt"),
                negativePrompt = json.optStringOrNull("default_negative_prompt")
            ).also {
                Log.d(TAG, "读取 config.json: ${modelDir.name} prompt=${it.prompt != null} negativePrompt=${it.negativePrompt != null}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "解析 config.json 失败: ${file.path}, ${e.message}")
            null
        }
    }

    /** config.json 中提取的默认提示词对（内部数据结构） */
    private data class ModelConfigDefaults(
        val prompt: String?,
        val negativePrompt: String?
    )

    /**
     * JSONObject 安全读取非空字符串字段
     * 对齐 local-dream ModelConfig.optStringOrNull L84-86
     */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) {
            optString(key).takeIf { it.isNotEmpty() }
        } else {
            null
        }
}
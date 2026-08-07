/**
 * ModelDownloader - 多模型文件下载器
 *
 * 功能：
 * - downloadModel(): 根据 ModelInfo 下载 ONNX 模型文件（含伴随数据文件）
 * - isModelReady(): 检查指定模型文件是否已下载
 * - getModelPath(): 获取指定模型的文件路径
 * - deleteModel(): 删除指定模型文件
 * - getDownloadedModels(): 获取所有已下载模型的 ID 列表
 * - migrateOldModel(): 迁移旧版单模型目录到新多模型目录结构
 *
 * 目录结构：
 *   filesDir/models/{modelId}/{originalFilename}
 *   filesDir/models/{modelId}/{originalFilename}_data （部分模型需要）
 *
 * 关键改进：
 * - 保留模型文件原始名称（如 model_q4.onnx），避免 ONNX 外部数据引用断裂
 * - 自动检测并下载伴随的 _data 数据文件
 * - 网络异常按源快速重试（每个下载源最多 2 次）并自动切换备用源
 * - 增大读写缓冲区到 64KB 提升下载速度
 */
package com.qihao.open.rwkv.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

class ModelDownloader private constructor(
    private val filesDir: File,                           // 应用私有文件根目录
    private val enableMigration: Boolean                  // 是否执行旧版目录迁移
) {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val MODELS_DIR = "models"              // 多模型根目录
        private const val TEMP_SUFFIX = ".downloading"        // 下载临时后缀
        private const val BUFFER_SIZE = 64 * 1024             // 读写缓冲区 64KB（提升下载速度）
        private const val OLD_MODEL_DIR = "model"             // 旧版单模型目录（用于迁移）
        private const val OLD_MODEL_FILE = "model.onnx"       // 旧版模型文件名
        private const val MAX_RETRIES = 2                     // 单个下载源最多试 2 次，兼顾断点续传和快速切源
        private const val RETRY_DELAY_MS = 500L               // 重试间隔基数（毫秒），避免用户长时间无反馈
        private const val EXTERNAL_DATA_HINT_BYTES = 10L * 1024L * 1024L // 小 ONNX 大模型通常需要外部数据
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416    // Android HttpURLConnection 未暴露该常量
        private const val FAST_CONNECT_TIMEOUT_MS = 5_000     // 镜像源连接 5 秒无响应即失败，减少首屏等待
        private const val FAST_READ_TIMEOUT_MS = 8_000        // 镜像源 8 秒无下载进度即重试或切换备用源
        private const val SLOW_CONNECT_TIMEOUT_MS = 15_000    // GitHub 等单源大文件允许更长连接时间
        private const val SLOW_READ_TIMEOUT_MS = 18_000       // 大文件源 18 秒无数据即切源，避免用户长时间看不到进度
        private val GITHUB_PROXY_PREFIXES = listOf(           // GitHub Release 备用代理，按顺序失败切换
            "https://gh-proxy.ygxz.in/",
            "https://gh.llkk.cc/"
        )
    }

    constructor(context: Context) : this(context.filesDir, enableMigration = true)

    internal constructor(filesDir: File) : this(filesDir, enableMigration = false)

    /** 模型包加载前的完整性检查结果 */
    data class ModelReadiness(
        val isReady: Boolean,                             // 是否允许进入模型加载
        val reason: String,                               // 就绪或失败原因
        val modelFile: File? = null,                      // 主 ONNX 文件
        val tokenizerFile: File? = null                   // Transformer 分词器文件
    )

    // 多模型根目录
    private val modelsRoot: File get() = File(filesDir, MODELS_DIR)

    init {
        if (enableMigration) migrateOldModel()            // 启动时检查并迁移旧版模型
    }

    /**
     * 获取指定模型的存储目录
     * @param modelId 模型唯一标识
     */
    private fun getModelDir(modelId: String): File = File(modelsRoot, modelId)

    /** 获取模型目录路径，供非 ONNX 运行时按目录加载多资产模型包 */
    fun getModelDirectory(modelId: String): File {
        return getModelDir(modelId)                            // 只暴露目录对象，不绕过完整性检查
    }

    /**
     * 解析真正承载运行时文件的目录。
     *
     * HuggingFace 上的 LocalDream zip 包解压后通常保留单个顶层文件夹
     * （如 AnythingV5.zip → AnythingV5/），导致运行时文件位于模型目录的
     * 一层子目录之下，而 native 后端要求文件直接位于 --model_dir 下。
     * 解析规则：
     * - 模型顶层目录已含首个必需文件 → 平铺布局，直接返回模型目录；
     * - 否则查找包含该文件的唯一子目录 → 返回该子目录；
     * - 均不满足 → 回退模型目录本身（交由完整性门禁报缺失）。
     *
     * @param modelInfo 模型元信息，按 requiredRuntimeFiles 首个文件作为探针
     */
    fun getRuntimeDirectory(modelInfo: ModelInfo): File {
        val modelDir = getModelDir(modelInfo.id)
        val probe = modelInfo.requiredRuntimeFiles.firstOrNull() ?: return modelDir // 无布局约束的模型直接用顶层目录
        if (File(modelDir, probe).isFile) return modelDir      // 平铺布局优先，兼容 local-dream 式解压
        // 递归查找 probe 文件所在目录（BFS 优先浅层）。CPU 模型为一层嵌套（AnythingV5/），
        // 此前单层探针够用；NPU 模型为两层深 output_512/qnn_models_<suffix>/，必须递归才能命中。
        return findDirContainingFile(modelDir, probe) ?: modelDir
    }

    /**
     * BFS 递归查找包含目标文件的子目录，优先返回浅层命中（平铺 > 一层 > 两层）。
     * 用于 zip 解压后模型文件位于任意深度子目录时的运行时目录定位。
     */
    private fun findDirContainingFile(root: File, targetName: String): File? {
        val queue = ArrayDeque<File>()
        root.listFiles()?.filter { it.isDirectory }?.let { queue.addAll(it) }
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            if (File(dir, targetName).isFile) return dir
            dir.listFiles()?.filter { it.isDirectory }?.let { queue.addAll(it) }
        }
        return null
    }

    /**
     * 从下载 URL 中提取原始文件名
     * 例: "https://hf-mirror.com/.../model_q4.onnx" → "model_q4.onnx"
     */
    private fun extractFilename(url: String): String {
        return try {
            val path = URL(url).path                          // 提取 URL 路径部分
            val name = path.substringAfterLast("/")           // 取最后一段作为文件名
            if (name.isNotEmpty() && name.contains(".")) name else OLD_MODEL_FILE
        } catch (e: Exception) {
            OLD_MODEL_FILE                                     // 解析失败回退为默认名
        }
    }

    /**
     * 在模型目录中查找 ONNX 模型文件
     * 支持任意名称的 .onnx 文件（如 model.onnx、model_q4.onnx）
     * @param modelId 模型唯一标识
     * @return 找到的 ONNX 文件，未找到返回 null
     */
    private fun findOnnxFile(modelId: String): File? {
        val dir = getModelDir(modelId)
        if (!dir.exists()) return null
        return dir.listFiles()
            ?.sortedBy { it.name }                            // 固定顺序，避免多文件时结果漂移
            ?.firstOrNull { isReadyOnnxFile(it) }              // 只接受非空正式 ONNX 文件
    }

    /**
     * 判断文件是否为可加载的主 ONNX 文件
     * @param file 待检查文件
     */
    private fun isReadyOnnxFile(file: File): Boolean {
        return file.isFile &&                                  // 必须是普通文件
            file.extension == "onnx" &&                        // 必须是 ONNX 主文件
            !file.name.endsWith(TEMP_SUFFIX) &&                // 不能是临时下载文件
            file.length() > 0L                                 // 零字节文件视为损坏
    }

    /**
     * 判断 tokenizer.json 是否存在且非空
     * @param modelId 模型唯一标识
     */
    private fun hasReadyTokenizer(modelId: String): Boolean {
        val file = File(getModelDir(modelId), "tokenizer.json")
        return file.isFile && file.length() > 0L               // Transformer 必须有有效分词器文件
    }

    /** 获取模型资产的本地路径 */
    fun getAssetPath(modelInfo: ModelInfo, asset: ModelAsset): String {
        return File(getModelDir(modelInfo.id), asset.filename).absolutePath
    }

    /** 判断显式资产是否已经下载完成 */
    private fun hasReadyAsset(modelInfo: ModelInfo, asset: ModelAsset): Boolean {
        if (asset.kind == ModelAssetKind.ARCHIVE) {
            return isArchiveAssetReady(modelInfo, asset)        // zip 包解压后不强制保留原始压缩包
        }
        val file = File(getAssetPath(modelInfo, asset))
        return file.isFile && file.length() > 0L               // 所有必需资产必须是非空正式文件
    }

    /** 判断 zip 模型包是否已经下载或完成解压 */
    private fun isArchiveAssetReady(modelInfo: ModelInfo, asset: ModelAsset): Boolean {
        val modelDir = getModelDir(modelInfo.id)
        if (!modelDir.isDirectory) return false
        return modelDir.walkTopDown().any { file ->
            file.isFile &&
                !file.name.endsWith(TEMP_SUFFIX) &&
                !file.name.equals(asset.filename, ignoreCase = true) &&
                file.length() > 0L
        }                                                       // 解压后目录中至少应有一个运行时文件
    }

    /**
     * 获取指定模型的文件路径
     * @param modelId 模型唯一标识
     * @return 模型文件绝对路径
     */
    fun getModelPath(modelId: String): String {
        return findOnnxFile(modelId)?.absolutePath             // 动态查找实际文件
            ?: File(getModelDir(modelId), OLD_MODEL_FILE).absolutePath // 回退路径
    }

    /**
     * 获取指定模型的 tokenizer.json 文件路径
     * @param modelId 模型唯一标识
     * @return tokenizer.json 绝对路径，不存在返回 null
     */
    fun getTokenizerPath(modelId: String): String? {
        val file = File(getModelDir(modelId), "tokenizer.json")
        return if (file.isFile && file.length() > 0L) file.absolutePath else null
    }

    /**
     * 检查指定模型是否已下载完成
     * @param modelId 模型唯一标识
     */
    fun isModelReady(modelId: String): Boolean {
        val modelInfo = ModelRegistry.findById(modelId)
        return if (modelInfo != null) {
            isModelReady(modelInfo)                            // 已登记模型按架构校验完整资产
        } else {
            findOnnxFile(modelId) != null                      // 未登记目录仅做兼容性 ONNX 检查
        }
    }

    /**
     * 检查指定模型包是否满足加载前置条件
     * @param modelInfo 模型元信息
     */
    fun isModelReady(modelInfo: ModelInfo): Boolean {
        return getModelReadiness(modelInfo).isReady            // 统一走结构化完整性门禁
    }

    /**
     * 获取指定模型包的完整性检查结果
     * @param modelInfo 模型元信息
     */
    fun getModelReadiness(modelInfo: ModelInfo): ModelReadiness {
        if (modelInfo.adapterType == RuntimeAdapterType.BUILTIN_TEXT) {
            return ModelReadiness(true, "内置体验模型无需下载") // 内置体验模型不依赖外部 ONNX 文件
        }
        if (!modelInfo.adapterAvailable && !isDownloadOnlyImagePackage(modelInfo)) {
            return ModelReadiness(
                false,
                modelInfo.unavailableReason.ifBlank {
                    "当前模型需要 ${modelInfo.adapterType} adapter，暂未接入"
                }
            )
        }

        val requiredAssets = modelInfo.resolvedAssets().filter { it.required }
        val missingAsset = requiredAssets.firstOrNull { !hasReadyAsset(modelInfo, it) }
        if (missingAsset != null && modelInfo.assets.isNotEmpty()) {
            return ModelReadiness(false, "缺少必需资产: ${missingAsset.filename}")
        }
        val missingRuntimeFile = findMissingRequiredRuntimeFile(modelInfo)
        if (missingRuntimeFile != null) {
            return ModelReadiness(false, "缺少运行时文件: $missingRuntimeFile")
        }
        if (modelInfo.assets.isNotEmpty() && modelInfo.adapterType != RuntimeAdapterType.ONNX_TEXT_GENERATION) {
            return ModelReadiness(true, "显式模型资产已就绪") // 非文本 adapter 接入后按资产清单判定，不强制 tokenizer
        }

        val modelFile = findOnnxFile(modelInfo.id)
        if (modelFile == null) {
            return ModelReadiness(false, "缺少完整的 ONNX 模型文件") // 主模型不存在时不能进入 READY
        }
        if (needsExternalData(modelInfo, modelFile) && !hasReadyExternalData(modelInfo.id, modelFile)) {
            return ModelReadiness(false, "缺少 ONNX 外部数据文件", modelFile) // 防止小 ONNX 半包误进加载
        }

        if (modelInfo.arch == ModelArch.TRANSFORMER) {
            val tokenizerPath = getTokenizerPath(modelInfo.id)
            if (tokenizerPath == null) {
                Log.w(TAG, "Transformer 模型缺少 tokenizer.json: ${modelInfo.id}")
                return ModelReadiness(false, "Transformer 模型缺少 tokenizer.json", modelFile)
            }
            return ModelReadiness(true, "模型文件和 tokenizer.json 已就绪", modelFile, File(tokenizerPath))
        }

        return ModelReadiness(true, "模型文件已就绪", modelFile)
    }

    /** 校验 LocalDream / 多文件模型包的固定运行时布局，避免只解出一个文件就误判 ready */
    private fun findMissingRequiredRuntimeFile(modelInfo: ModelInfo): String? {
        if (modelInfo.requiredRuntimeFiles.isEmpty()) return null
        val runtimeDir = getRuntimeDirectory(modelInfo)        // zip 可能带顶层文件夹，按实际运行时目录校验
        if (!runtimeDir.isDirectory) return modelInfo.requiredRuntimeFiles.first()
        return modelInfo.requiredRuntimeFiles.firstOrNull { relativePath ->
            val file = File(runtimeDir, relativePath)
            !file.isFile || file.length() <= 0L
        }
    }

    /** LocalDream 图片模型允许先下载和校验，即使 native adapter 还未完全开放生成 */
    private fun isDownloadOnlyImagePackage(modelInfo: ModelInfo): Boolean {
        return modelInfo.imageBackendType.isNotBlank() &&
            (modelInfo.capability == ModelCapability.IMAGE_GENERATION ||
                modelInfo.capability == ModelCapability.IMAGE_UPSCALING)
    }

    /**
     * 检查 NPU 模型是否需要升级到 v3 格式。
     *
     * 对齐参照 modules/local-dream .../data/Model.kt:286-294 needsModelUpgrade：
     * 仅 NPU 模型需要 v3 marker 文件；模型目录存在但缺少 v3 标记文件时返回 true，
     * 表示当前模型包为旧版本，需要提示用户升级。
     *
     * @param modelId 模型唯一标识
     * @param isNpu 是否为 NPU 模型（CPU 模型不需要 v3 marker）
     * @return true 表示模型已下载但需要升级到 v3 格式
     */
    fun needsModelUpgrade(modelId: String, isNpu: Boolean): Boolean {
        if (!isNpu) return false                                 // CPU 模型不依赖 v3 marker
        val modelDir = getModelDir(modelId)
        if (!modelDir.exists() || !modelDir.isDirectory) return false // 模型未下载，无需升级提示
        val v3Marker = File(modelDir, "v3")
        return !v3Marker.exists()                                // v3 marker 缺失即需升级
    }

    /** 判断大模型小 ONNX 是否需要伴随外部数据文件 */
    private fun needsExternalData(modelInfo: ModelInfo, modelFile: File): Boolean {
        return modelInfo.fileSizeMB > 10 && modelFile.length() < EXTERNAL_DATA_HINT_BYTES
    }

    /** 判断 ONNX 外部数据文件是否存在且非空 */
    private fun hasReadyExternalData(modelId: String, modelFile: File): Boolean {
        val dataFile = File(getModelDir(modelId), "${modelFile.name}_data")
        return dataFile.isFile && dataFile.length() > 0L
    }

    /**
     * 获取所有已下载模型的 ID 列表
     */
    fun getDownloadedModels(): Set<String> {
        val root = modelsRoot
        val builtinModels = ModelRegistry.models
            .filter { it.adapterType == RuntimeAdapterType.BUILTIN_TEXT } // 内置体验模型始终可用
            .map { it.id }
            .toSet()
        if (!root.exists()) return builtinModels
        val fileBackedModels = root.listFiles()
            ?.filter { it.isDirectory && isModelReady(it.name) }         // 只列出完整可加载模型包
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
        return builtinModels + fileBackedModels
    }

    /**
     * 删除指定模型的所有文件（模型 + 数据 + 临时）
     * @param modelId 模型唯一标识
     */
    fun deleteModel(modelId: String) {
        val modelInfo = ModelRegistry.findById(modelId)
        if (modelInfo?.adapterType == RuntimeAdapterType.BUILTIN_TEXT) {
            Log.d(TAG, "跳过内置体验模型删除: $modelId")        // 内置模型不允许删除
            return
        }
        val dir = getModelDir(modelId)
        dir.listFiles()?.forEach { it.delete() }              // 删除目录下所有文件
        dir.delete()                                           // 删除空目录
        Log.d(TAG, "模型文件已删除: $modelId")
    }

    /**
     * 迁移旧版单模型目录到新多模型目录结构
     * 旧路径: filesDir/model/model.onnx → 新路径: filesDir/models/rwkv7-world-0.4b/model.onnx
     */
    private fun migrateOldModel() {
        val oldDir = File(filesDir, OLD_MODEL_DIR)             // 旧目录
        val oldFile = File(oldDir, OLD_MODEL_FILE)             // 旧模型文件
        if (!oldFile.exists()) return                          // 无旧文件则跳过

        val legacyTarget = ModelRegistry.models.firstOrNull { it.arch == ModelArch.RWKV } // 只迁移到仍展示的 RWKV 模型
        if (legacyTarget == null) {
            Log.d(TAG, "注册表未启用 RWKV，跳过旧模型迁移: ${oldFile.absolutePath}")
            return                                             // 避免把旧权重塞入内置体验模型目录
        }

        val defaultId = legacyTarget.id                        // 旧版单模型只对应 RWKV 权重
        val newDir = getModelDir(defaultId)                    // 新目录

        if (findOnnxFile(defaultId) != null) {
            // 新目录已有模型，删除旧文件
            oldFile.delete()
            oldDir.delete()
            Log.d(TAG, "旧模型已迁移过，删除旧文件")
            return
        }

        // 移动文件到新目录
        newDir.mkdirs()
        if (oldFile.renameTo(File(newDir, OLD_MODEL_FILE))) {
            oldDir.delete()                                    // 删除旧空目录
            Log.d(TAG, "旧模型迁移完成: $OLD_MODEL_DIR → $MODELS_DIR/$defaultId")
        } else {
            Log.e(TAG, "旧模型迁移失败")
        }
    }

    /**
     * 下载指定模型，支持断点续传、重试和伴随数据文件
     *
     * 流程：
     * 1. 下载主 .onnx 文件（保留原始文件名）
     * 2. 尝试下载伴随 _data 文件（部分 ONNX 模型需要外部数据）
     * 3. 合并两阶段进度上报
     *
     * @param modelInfo 模型元信息
     * @param onProgress 下载进度回调 (已下载字节, 总字节, 进度百分比 0~100)
     * @return 下载是否成功
     */
    suspend fun downloadModel(
        modelInfo: ModelInfo,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit,
        onStatus: ((message: String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = getModelDir(modelInfo.id)
            modelDir.mkdirs()                                  // 确保目录存在

            if (!modelInfo.adapterAvailable && !isDownloadOnlyImagePackage(modelInfo)) {
                Log.e(TAG, "adapter 未接入，拒绝下载候选模型: ${modelInfo.id}")
                onStatus?.invoke(modelInfo.unavailableReason.ifBlank {
                    "当前模型需要 ${modelInfo.adapterType} adapter，暂未接入"
                })
                return@withContext false
            }

            if (modelInfo.adapterType == RuntimeAdapterType.BUILTIN_TEXT) {
                onProgress(0L, 0L, 100)                         // 内置模型无需下载，直接完成
                return@withContext true
            }

            if (modelInfo.assets.isNotEmpty()) {
                val assetSuccess = downloadDeclaredAssets(modelInfo, onProgress, onStatus)
                if (!assetSuccess) return@withContext false
                return@withContext isModelReady(modelInfo)       // 显式资产下载后仍走统一门禁
            }

            // 提取原始文件名（保留原名避免 ONNX 外部数据引用断裂）
            val filename = extractFilename(modelInfo.downloadUrl)
            val targetFile = File(modelDir, filename)

            // 已经下载完成则跳过
            if (isModelReady(modelInfo)) {
                Log.d(TAG, "模型已存在: ${modelInfo.id}")
                val existingFile = findOnnxFile(modelInfo.id)!!
                onProgress(existingFile.length(), existingFile.length(), 100)
                return@withContext true
            }

            Log.d(TAG, "开始下载模型: ${modelInfo.id} → ${resolveDownloadUrls(modelInfo.downloadUrl, modelInfo.mirrorUrls)}")

            // ---- 第一阶段：下载主模型文件 ----
            val mainSuccess = downloadFirstAvailableFile(
                urls = resolveDownloadUrls(modelInfo.downloadUrl, modelInfo.mirrorUrls),
                targetFile = targetFile,
                onStatus = onStatus,
                onProgress = onProgress                        // 直接透传进度
            )

            if (!mainSuccess) {
                Log.e(TAG, "主模型文件下载失败: ${modelInfo.id}")
                return@withContext false
            }

            // ---- 第二阶段：尝试下载伴随数据文件 ----
            val dataUrl = "${modelInfo.downloadUrl}_data"       // 数据文件 URL = 模型URL + "_data"
            val dataFilename = "${filename}_data"               // 数据文件名 = 模型文件名 + "_data"
            val dataTargetFile = File(modelDir, dataFilename)

            Log.d(TAG, "尝试下载伴随数据文件: $dataFilename")
            val mainFileSize = targetFile.length()             // 主文件大小

            val dataSuccess = downloadFirstAvailableFile(
                urls = resolveDownloadUrls(dataUrl),
                targetFile = dataTargetFile,
                optional = true,                               // 可选文件，404不算失败
                onStatus = onStatus,
                onProgress = { downloaded, total, _ ->
                    // 合并进度：主文件大小 + 数据文件进度
                    val combinedTotal = mainFileSize + total
                    val combinedDownloaded = mainFileSize + downloaded
                    val percent = if (combinedTotal > 0) {
                        (combinedDownloaded * 100 / combinedTotal).toInt()
                    } else 100
                    onProgress(combinedDownloaded, combinedTotal, percent)
                }
            )

            if (dataSuccess) {
                Log.d(TAG, "伴随数据文件下载完成: $dataFilename (${dataTargetFile.length() / 1024 / 1024}MB)")
            } else {
                Log.d(TAG, "无伴随数据文件（单文件模型）")
                if (needsExternalData(modelInfo, targetFile)) {
                    Log.e(TAG, "主 ONNX 文件过小且缺少外部数据文件: ${modelInfo.id}")
                    return@withContext false                   // 需要外部数据的大模型不允许半包就绪
                }
            }

            // ---- 第三阶段：下载 tokenizer.json（Transformer 模型需要） ----
            if (modelInfo.tokenizerUrl != null) {
                val tokenizerFile = File(modelDir, "tokenizer.json")
                if (!hasReadyTokenizer(modelInfo.id)) {
                    Log.d(TAG, "下载分词器: tokenizer.json")
                    val tokenizerSuccess = downloadFirstAvailableFile(
                        urls = resolveDownloadUrls(modelInfo.tokenizerUrl),
                        targetFile = tokenizerFile,
                        optional = false,                // Transformer 分词器是必需资产
                        onStatus = onStatus
                    )
                    if (tokenizerSuccess) {
                        Log.d(TAG, "分词器下载完成: tokenizer.json (${tokenizerFile.length() / 1024}KB)")
                    } else {
                        Log.e(TAG, "分词器下载失败，模型包不完整: ${modelInfo.id}")
                        return@withContext false         // 禁止使用错误 tokenizer 降级
                    }
                }
            }

            if (!isModelReady(modelInfo)) {
                Log.e(TAG, "模型包完整性检查失败: ${modelInfo.id}")
                return@withContext false                 // 最终 ready 门禁防止半包进入加载
            }

            Log.d(TAG, "模型下载完成: ${modelInfo.id} → ${targetFile.absolutePath}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "下载异常 [${modelInfo.id}]: ${e.message}", e)
            return@withContext false
        }
    }

    /** 下载显式声明的多资产模型包 */
    private fun downloadDeclaredAssets(
        modelInfo: ModelInfo,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit,
        onStatus: ((message: String) -> Unit)?
    ): Boolean {
        val assets = modelInfo.resolvedAssets().filter { it.url.isNotBlank() }
        if (assets.isEmpty()) {
            onStatus?.invoke("模型未声明可下载资产")
            return false
        }

        var completedBytes = assets.sumOf { asset ->
            val file = File(getAssetPath(modelInfo, asset))
            if (file.isFile) file.length() else 0L              // 已存在资产计入聚合进度
        }
        val knownTotalBytes = assets.sumOf { asset ->
            val file = File(getAssetPath(modelInfo, asset))
            if (file.isFile) file.length() else 0L              // 远端总大小未知前，先用本地已完成大小兜底
        }

        assets.forEachIndexed { index, asset ->
            val targetFile = File(getAssetPath(modelInfo, asset))
            if (hasReadyAsset(modelInfo, asset)) {
                onStatus?.invoke("资产已存在: ${asset.filename}")
                return@forEachIndexed
            }
            if (asset.kind == ModelAssetKind.ARCHIVE && targetFile.isFile && targetFile.length() > 0L) {
                onStatus?.invoke("检测到已下载模型包，正在解压: ${asset.filename}")
                unzipArchiveToModelDirectory(targetFile, getModelDir(modelInfo.id))
                if (targetFile.exists() && !targetFile.delete()) {
                    Log.w(TAG, "zip 模型包删除失败，将保留到下次清理: ${targetFile.absolutePath}")
                }
                return@forEachIndexed
            }

            onStatus?.invoke("正在下载资产 ${index + 1}/${assets.size}: ${asset.filename}")
            val success = downloadFirstAvailableFile(
                urls = resolveDownloadUrls(asset.url, asset.mirrorUrls),
                targetFile = targetFile,
                optional = !asset.required,
                onStatus = onStatus,
                onProgress = { downloaded, total, _ ->
                    val dynamicTotal = if (total > 0L) {
                        knownTotalBytes + total                 // 当前资产有长度时显示可计算总量
                    } else {
                        knownTotalBytes
                    }
                    val dynamicDownloaded = completedBytes + downloaded
                    val percent = if (dynamicTotal > 0L) {
                        (dynamicDownloaded * 100 / dynamicTotal).toInt().coerceIn(0, 99)
                    } else {
                        ((index * 100) / assets.size).coerceIn(0, 99)
                    }
                    onProgress(dynamicDownloaded, dynamicTotal, percent)
                }
            )
            if (!success && asset.required) {
                Log.e(TAG, "必需资产下载失败: ${modelInfo.id}/${asset.filename}")
                return false
            }
            if (success && asset.kind == ModelAssetKind.ARCHIVE) {
                onStatus?.invoke("正在解压模型包: ${asset.filename}")
                unzipArchiveToModelDirectory(targetFile, getModelDir(modelInfo.id))
                if (targetFile.exists() && !targetFile.delete()) {
                    Log.w(TAG, "zip 模型包删除失败，将保留到下次清理: ${targetFile.absolutePath}")
                }
            }
            if (targetFile.isFile) completedBytes += targetFile.length()
        }

        onProgress(completedBytes, completedBytes, 100)
        return true
    }

    /** 安全解压 zip 模型包，防止恶意 entry 逃逸出模型目录 */
    private fun unzipArchiveToModelDirectory(archiveFile: File, modelDir: File) {
        require(archiveFile.isFile && archiveFile.length() > 0L) {
            "zip 模型包不存在或为空: ${archiveFile.name}"
        }
        val canonicalRoot = modelDir.canonicalFile
        ZipFile(archiveFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val target = File(modelDir, entry.name).canonicalFile
                if (!target.path.startsWith(canonicalRoot.path + File.separator) && target != canonicalRoot) {
                    throw IllegalStateException("zip 模型包包含非法路径: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()                            // 目录 entry 直接创建
                } else {
                    target.parentFile?.mkdirs()                 // 文件 entry 先补齐父目录
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output, BUFFER_SIZE)   // 使用统一大缓冲减少解压时间
                        }
                    }
                }
            }
        }
        Log.d(TAG, "zip 模型包解压完成: ${archiveFile.name} → ${modelDir.absolutePath}")
    }

    /** 从多个候选源下载同一个文件，当前源无进度或失败时快速切到下一个源 */
    private fun downloadFirstAvailableFile(
        urls: List<String>,
        targetFile: File,
        optional: Boolean = false,
        onStatus: ((message: String) -> Unit)? = null,
        onProgress: ((downloaded: Long, total: Long, percent: Int) -> Unit)? = null
    ): Boolean {
        for ((index, url) in urls.withIndex()) {
            onStatus?.invoke("正在连接 ${sourceName(url)}")
            Log.d(TAG, "尝试下载源: $url")
            if (downloadSingleFile(url, targetFile, optional, onProgress)) {
                Log.d(TAG, "下载源成功: $url")
                return true
            }
            if (optional && targetFile.exists().not()) {
                Log.d(TAG, "可选文件当前源不可用，尝试下一个源: $url")
            } else {
                Log.w(TAG, "下载源失败，切换下一个源: $url")
            }
            if (index < urls.lastIndex) {
                onStatus?.invoke("当前源无响应，正在切换备用源")
            }
        }
        return false
    }

    /** 根据原始 URL 和手工备用源生成候选源，原始源优先，自动补充国内可访问镜像 */
    internal fun resolveDownloadUrls(originalUrl: String, mirrorUrls: List<String> = emptyList()): List<String> {
        if (originalUrl.isBlank()) return emptyList()
        val urls = linkedSetOf<String>()
        val declaredUrls = listOf(originalUrl) + mirrorUrls     // 手工声明顺序是最可信的下载顺序
        declaredUrls.forEach { declaredUrl ->
            addDownloadUrlWithFallbacks(urls, declaredUrl)      // 每个声明源都追加可推导备用源
        }
        return urls.toList()
    }

    /** 添加单个源及其可推导备用源，保证 tokenizer 等伴随文件也能自动切镜像 */
    private fun addDownloadUrlWithFallbacks(urls: LinkedHashSet<String>, declaredUrl: String) {
        if (declaredUrl.isBlank()) return
        urls += declaredUrl                                    // 先尝试真实声明源，保留官方 URL 优先级
        if (declaredUrl.startsWith("https://github.com/")) {
            GITHUB_PROXY_PREFIXES.forEach { prefix ->
                urls += "$prefix$declaredUrl"                  // GitHub Release 大文件自动补充代理镜像
            }
        }
        val hfMirrorUrl = toHfMirrorUrl(declaredUrl)
        if (hfMirrorUrl != null) urls += hfMirrorUrl            // 官方 HuggingFace 失败时自动切 hf-mirror
        val modelScopeUrl = toModelScopeUrl(hfMirrorUrl ?: declaredUrl)
        if (modelScopeUrl != null) urls += modelScopeUrl        // ModelScope 仅作为最后兜底，不抢首位
    }

    /** 将官方 HuggingFace resolve 直链转换为 hf-mirror 同路径直链 */
    private fun toHfMirrorUrl(url: String): String? {
        return when {
            url.startsWith("https://huggingface.co/") ->
                url.replace("https://huggingface.co/", "https://hf-mirror.com/")
            else -> null
        }
    }

    /** 将 hf-mirror 路径转换为 ModelScope 同路径直链；官方 HuggingFace 不机械转换，避免 RWKV 误打 404 */
    private fun toModelScopeUrl(url: String): String? {
        return when {
            url.startsWith("https://hf-mirror.com/") ->
                url.replace("https://hf-mirror.com/", "https://modelscope.cn/models/")
            else -> null
        }
    }

    /** 根据 URL 展示用户可理解的源名称 */
    private fun sourceName(url: String): String {
        return when {
            url.startsWith("https://modelscope.cn/") -> "ModelScope"
            url.startsWith("https://hf-mirror.com/") -> "HF Mirror"
            url.startsWith("https://huggingface.co/") -> "HuggingFace"
            url.contains("gh-proxy") || url.contains("gh.llkk.cc") -> "GitHub 镜像"
            url.contains("sdai-models.moroz.cc") -> "SDAI 模型源"
            else -> URL(url).host
        }
    }

    /**
     * 下载单个文件，支持断点续传和重试
     * @param url 文件下载地址
     * @param targetFile 目标文件
     * @param optional 是否为可选文件（true 时 404 返回 false 而非抛异常）
     * @param onProgress 进度回调
     * @return 是否下载成功
     */
    private fun downloadSingleFile(
        url: String,
        targetFile: File,
        optional: Boolean = false,
        onProgress: ((downloaded: Long, total: Long, percent: Int) -> Unit)? = null
    ): Boolean {
        if (targetFile.isFile && targetFile.length() > 0L) return true // 已存在有效文件直接返回
        if (targetFile.exists() && targetFile.length() == 0L) {
            targetFile.delete()                                // 删除零字节坏文件后重新下载
        }

        val tempFile = File(targetFile.parent, "${targetFile.name}$TEMP_SUFFIX")

        for (retry in 0 until MAX_RETRIES) {
            var connection: HttpURLConnection? = null
            try {
                var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
                connection = openConnection(url, downloadedBytes)
                val responseCode = connection.responseCode

                // 可选文件返回 404 不算错误
                if (optional && (responseCode == HttpURLConnection.HTTP_NOT_FOUND ||
                            responseCode == HttpURLConnection.HTTP_FORBIDDEN ||
                            responseCode == HttpURLConnection.HTTP_UNAUTHORIZED)
                ) {
                    return false
                }

                if (responseCode == HTTP_RANGE_NOT_SATISFIABLE &&
                    tempFile.isFile &&
                    tempFile.length() > 0L
                ) {
                    return promoteTempFile(tempFile, targetFile)        // Range 已超出时视为临时文件已完整
                }

                // 校验响应码
                if (responseCode != HttpURLConnection.HTTP_OK &&
                    responseCode != HttpURLConnection.HTTP_PARTIAL
                ) {
                    Log.e(TAG, "HTTP $responseCode (尝试 ${retry + 1}/$MAX_RETRIES) → $url")
                    if (retry < MAX_RETRIES - 1) {
                        Thread.sleep(RETRY_DELAY_MS * (retry + 1)) // 指数退避
                        continue
                    }
                    return false
                }

                if (responseCode == HttpURLConnection.HTTP_OK && downloadedBytes > 0L) {
                    Log.w(TAG, "服务器未接受 Range，重新从 0 下载: ${targetFile.name}")
                    tempFile.delete()                                  // 服务器不支持续传时必须截断旧临时文件
                    downloadedBytes = 0L
                }

                // 计算总大小
                val contentLength = connection.contentLengthLong
                val totalSize = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                        ?: (downloadedBytes + contentLength)    // 优先使用服务端声明的完整总大小
                } else {
                    contentLength                               // 全新下载
                }

                Log.d(TAG, "文件总大小: ${totalSize / 1024 / 1024}MB, 已下载: ${downloadedBytes / 1024 / 1024}MB")

                // 写入文件（追加模式用于断点续传）
                val append = responseCode == HttpURLConnection.HTTP_PARTIAL
                var currentDownloaded = downloadedBytes
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile, append).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            currentDownloaded += bytesRead

                            // 回调进度
                            if (onProgress != null) {
                                val percent = if (totalSize > 0) {
                                    (currentDownloaded * 100 / totalSize).toInt()
                                } else 0
                                onProgress(currentDownloaded, totalSize, percent)
                            }
                        }
                    }
                }

                if (totalSize > 0L && currentDownloaded < totalSize) {
                    Log.w(
                        TAG,
                        "文件短读，继续断点续传: ${targetFile.name} ${currentDownloaded}/${totalSize}"
                    )
                    if (retry < MAX_RETRIES - 1) {
                        Thread.sleep(RETRY_DELAY_MS * (retry + 1)) // 短读不改名，下一轮继续 Range
                        continue
                    }
                    return false
                }

                // 下载完成，重命名为正式文件
                return promoteTempFile(tempFile, targetFile)
            } catch (e: Exception) {
                Log.e(TAG, "下载异常 (尝试 ${retry + 1}/$MAX_RETRIES): ${e.message}")
                if (retry < MAX_RETRIES - 1) {
                    Thread.sleep(RETRY_DELAY_MS * (retry + 1)) // 指数退避等待后重试
                } else {
                    return false                               // 保留临时文件，下一次下载继续断点续传
                }
            } finally {
                connection?.disconnect()
            }
        }
        return false
    }

    /** 将完整临时文件提升为正式文件 */
    private fun promoteTempFile(tempFile: File, targetFile: File): Boolean {
        if (!tempFile.isFile || tempFile.length() <= 0L) return false
        if (targetFile.exists() && !targetFile.delete()) {
            Log.e(TAG, "旧目标文件删除失败: ${targetFile.name}")
            return false
        }
        return if (tempFile.renameTo(targetFile)) {
            Log.d(TAG, "文件下载完成: ${targetFile.name} (${targetFile.length() / 1024 / 1024}MB)")
            true
        } else {
            Log.e(TAG, "文件重命名失败: ${targetFile.name}")
            false
        }
    }

    /** 解析 Content-Range 中的完整文件大小 */
    private fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange == null) return null
        val totalText = contentRange.substringAfter("/", missingDelimiterValue = "")
        return totalText.toLongOrNull()
    }

    /**
     * 打开 HTTP 连接，自动处理重定向和断点续传
     * @param urlStr 下载地址
     * @param downloadedBytes 已下载字节数（用于 Range 请求头）
     */
    private fun openConnection(urlStr: String, downloadedBytes: Long): HttpURLConnection {
        var url = URL(urlStr)
        var redirectCount = 0                                  // 防止无限重定向

        while (redirectCount < 10) {
            val conn = url.openConnection() as HttpURLConnection
            val currentUrl = url.toString()                     // 重定向后的 CDN 域名才是真实下载源
            conn.connectTimeout = connectTimeoutFor(currentUrl) // 按实际源类型设置连接超时，兼顾快切和大文件稳定性
            conn.readTimeout = readTimeoutFor(currentUrl)       // 按实际源类型设置读超时，防止大文件 CDN 首包慢
            conn.instanceFollowRedirects = false               // 手动处理重定向（跨域需要）
            conn.setRequestProperty("User-Agent", "MagicWX-Android/1.1")
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Accept-Encoding", "identity") // 避免压缩导致 Content-Length 失真
            conn.setRequestProperty("Connection", "close")

            // 断点续传请求头
            if (downloadedBytes > 0) {
                conn.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }

            conn.connect()
            val code = conn.responseCode

            // 处理 3xx 重定向
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location != null) {
                    url = URL(url, location)                   // 支持相对和绝对 URL
                    redirectCount++
                    Log.d(TAG, "重定向到: $url")
                    continue
                }
            }

            return conn
        }

        throw RuntimeException("重定向次数过多 (>10)")
    }

    /** 根据原始下载源返回连接超时，镜像源快切，GitHub Release 单源放宽 */
    private fun connectTimeoutFor(url: String): Int {
        return if (isSlowSingleSource(url)) SLOW_CONNECT_TIMEOUT_MS else FAST_CONNECT_TIMEOUT_MS
    }

    /** 根据原始下载源返回读超时，避免大文件 CDN 首包慢导致下载中断 */
    private fun readTimeoutFor(url: String): Int {
        return if (isSlowSingleSource(url)) SLOW_READ_TIMEOUT_MS else FAST_READ_TIMEOUT_MS
    }

    /** 判断是否为无 ModelScope 备用源的大文件下载域 */
    private fun isSlowSingleSource(url: String): Boolean {
        return url.startsWith("https://github.com/") ||
            url.contains("github-releases.githubusercontent.com") ||
            url.contains("release-assets.githubusercontent.com") ||
            url.contains(".hf.co/") ||
            url.contains("xet-bridge") ||
            url.contains("cas-bridge") ||
            url.contains("sdai-models.moroz.cc")
    }
}

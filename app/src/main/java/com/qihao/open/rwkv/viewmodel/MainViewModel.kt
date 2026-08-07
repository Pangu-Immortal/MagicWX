/**
 * MainViewModel - 主界面状态管理
 *
 * 功能：
 * - selectModel(): 选择要使用的模型
 * - downloadModel(): 启动选中模型的下载
 * - loadModel(): 加载模型到内存
 * - sendMessage(): 发送消息并获取模型回复
 * - resetChat(): 重置对话
 * - stopGenerating(): 停止当前生成
 * - switchModel(): 切换到其他模型
 */
package com.qihao.open.rwkv.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qihao.open.rwkv.model.ModelCapability
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.ModelRegistry
import com.qihao.open.rwkv.model.ModelVisibility
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.TextGenerationEngine
import com.qihao.open.rwkv.model.adapter.ModelRuntimeAdapterFactory
import com.qihao.open.rwkv.model.adapter.RuntimeLoadResult
import com.qihao.open.rwkv.model.image.DeviceSocCapability
import com.qihao.open.rwkv.model.image.ImageGenerationEngine
import com.qihao.open.rwkv.model.image.ImageInferenceBackendChoice
import com.qihao.open.rwkv.model.image.ImageInferenceBackendPlanner
import com.qihao.open.rwkv.model.image.LocalDreamImageRequest
import com.qihao.open.rwkv.data.db.HistoryEntity
import com.qihao.open.rwkv.data.db.HistoryRepository
import com.qihao.open.rwkv.data.db.HistoryBackup
import com.qihao.open.rwkv.data.db.BackupImportResult
import com.qihao.open.rwkv.service.ImageBackendEvent
import com.qihao.open.rwkv.service.ImageBackendEventType
import com.qihao.open.rwkv.service.ImageBackendEvents
import com.qihao.open.rwkv.service.ImageGenerationEventType
import com.qihao.open.rwkv.service.ImageGenerationEvents
import com.qihao.open.rwkv.service.ImageGenerationService
import com.qihao.open.rwkv.service.ImageUpscaleClient
import com.qihao.open.rwkv.service.ImageUpscaleRequest
import com.qihao.open.rwkv.service.ModelDownloadEventType
import com.qihao.open.rwkv.service.ModelDownloadEvents
import com.qihao.open.rwkv.service.ModelDownloadService
import com.qihao.open.rwkv.util.DownloadProgressFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 聊天消息数据类 */
data class ChatMessage(
    val content: String,            // 消息内容
    val isUser: Boolean,            // 是否为用户消息
    val isGenerating: Boolean = false, // 是否正在生成中
    val durationMillis: Long = 0L    // AI 回复生成耗时，生成中实时更新，结束后冻结
)

/** 生图界面状态 */
data class ImageGenerationUiState(
    val prompt: String = "",                 // 最近一次生图提示词
    val outputPath: String = "",             // 成功生成的图片路径
    val isGenerating: Boolean = false,        // 是否正在生成图片
    val progress: Int = 0,                    // 后台 SSE 生图进度
    val previewPath: String = "",             // 中间预览图路径（progress SSE 解码落盘，覆盖式）
    val durationMillis: Long = 0L,            // 生图耗时
    val errorMessage: String = "",            // 生图失败原因
    val backendStatusMessage: String = "",    // 图片后端进程状态文案
    val backendReady: Boolean = false,         // 图片后端健康检查是否通过
    val batchIndex: Int = 0,                  // 批量生成当前第几张（0 表示非批量或未开始）
    val batchTotal: Int = 0,                  // 批量生成总张数（0 表示非批量）
    val isUpscaling: Boolean = false,          // /upscale 是否执行中
    val upscaleOutputPath: String = "",        // 超分成功后输出 JPEG 路径
    val upscaleErrorMessage: String = ""       // 超分失败原因
)

/**
 * inpaint 结果贴回原图的临时上下文（仅进程内有效，不做持久化）：
 * 由生图请求发起时写入，结果页保存时消费，用于把小尺寸 inpaint 结果
 * 按裁剪矩形羽化贴回裁剪前的完整原图（InpaintBlendUtils.blendInpaintResult）。
 * 进程被杀即失效，保存链路自动回退为保存原始结果图，不产生功能性错误。
 *
 * 对齐参照 ModelRunScreen 的 snapshotCropRect / snapshotMaskBitmap / snapshotSelectedImageUri
 * 快照语义；区别是 MagicWX 直接持有解码后的原图 Bitmap，避免保存时重新解码 Uri 引入坐标漂移。
 * 位图所有权在 UI 层（MainActivity 裁剪上下文），ViewModel 只借用引用，不负责 recycle。
 */
data class InpaintBlendContext(
    val originalBitmap: Bitmap,        // 裁剪前的完整原图
    val cropRect: Rect,                // 生成输入图对应的裁剪矩形（originalBitmap 像素坐标系）
    val maskBitmap: Bitmap?            // 本次生成使用的 inpaint 蒙版（计算羽化权重）；null 时整块粘贴
)

/** 应用状态 */
enum class AppState {
    MODEL_SELECT,   // 模型选择界面
    NEED_DOWNLOAD,  // 需要下载选中的模型
    DOWNLOADING,    // 正在下载
    LOADING_MODEL,  // 正在加载模型
    READY,          // 就绪，可以对话
    READY_IMAGE,    // 生图模型就绪，可以生成图片
    ERROR           // 出错
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"

        /** 批量循环等待单张终态的超时上限：CPU 512 单张约数分钟，30 分钟兜底防服务被杀后死等 */
        private const val BATCH_TERMINAL_TIMEOUT_MS = 30 * 60 * 1000L

        /** 历史缩略图最长边像素：256px 足够 2 列网格展示，体积与解码成本都小 */
        private const val HISTORY_THUMBNAIL_MAX_EDGE = 256

        /** 历史缩略图 JPEG 压缩质量：80 在网格尺寸下肉眼无损且文件极小 */
        private const val HISTORY_THUMBNAIL_QUALITY = 80

        /** 历史缩略图目录（相对 filesDir）：与结果图 generated_images 同根，便于统一清理 */
        private const val HISTORY_THUMBS_SUBDIR = "generated_images/thumbs"

        /** 历史缩略图文件名前缀：thumb_<时间戳>.jpg */
        private const val HISTORY_THUMB_FILE_PREFIX = "thumb_"
    }

    private val downloader = ModelDownloader(application)
    private val historyRepository = HistoryRepository.create(application)   // Room 生图历史持久化
    private var lastImageRequest: LocalDreamImageRequest? = null             // 记录本次生成参数，complete 时落历史
    private val _lastReturnedSeed = MutableStateFlow(-1L)                    // 最近一次成功生成的 seed，供 UI 种子回填按钮使用
    val lastReturnedSeed: StateFlow<Long> = _lastReturnedSeed.asStateFlow()

    // 当前文本生成模型（懒初始化）
    private var model: TextGenerationEngine? = null
    private var imageGenerator: ImageGenerationEngine? = null
    private var loadedImageModel: ModelInfo? = null
    private var generateJob: Job? = null // 当前生成任务
    private var upscaleJob: Job? = null   // 当前超分任务，防重复并支持取消

    // === UI 状态 ===

    // 应用状态
    private val _appState = MutableStateFlow(AppState.MODEL_SELECT)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    // 当前选中的模型
    private val _selectedModel = MutableStateFlow<ModelInfo?>(null)
    val selectedModel: StateFlow<ModelInfo?> = _selectedModel.asStateFlow()

    // 已下载的模型 ID 集合
    private val _downloadedModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModels: StateFlow<Set<String>> = _downloadedModels.asStateFlow()

    // 当前已加载的模型 ID
    private val _loadedModelId = MutableStateFlow<String?>(null)
    val loadedModelId: StateFlow<String?> = _loadedModelId.asStateFlow()

    // 下载进度 (0~100)
    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    // 下载信息文本（已下载/总大小）
    private val _downloadInfo = MutableStateFlow("")
    val downloadInfo: StateFlow<String> = _downloadInfo.asStateFlow()

    // 首页按模型 ID 展示的后台下载进度
    private val _downloadProgressByModelId = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgressByModelId: StateFlow<Map<String, Int>> = _downloadProgressByModelId.asStateFlow()

    // 本机当前可用存储字节数，读取 App 私有目录所在分区
    private val _availableStorageBytes = MutableStateFlow(0L)
    val availableStorageBytes: StateFlow<Long> = _availableStorageBytes.asStateFlow()

    // App 启动时选择的图片推理架构，UI 直接展示给用户
    private val _imageInferenceBackend = MutableStateFlow(
        ImageInferenceBackendPlanner.currentStableDiffusionChoice(application)
    )
    val imageInferenceBackend: StateFlow<ImageInferenceBackendChoice> = _imageInferenceBackend.asStateFlow()

    // 聊天消息列表
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // 生图状态
    private val _imageGenerationState = MutableStateFlow(ImageGenerationUiState())
    val imageGenerationState: StateFlow<ImageGenerationUiState> = _imageGenerationState.asStateFlow()

    // inpaint 贴回原图临时上下文：请求发起时由 UI 写入，结果页保存时读取（进程内有效）
    private val _inpaintBlendContext = MutableStateFlow<InpaintBlendContext?>(null)
    val inpaintBlendContext: StateFlow<InpaintBlendContext?> = _inpaintBlendContext.asStateFlow()

    // 生图历史（Room 持久化，按 createdAt 倒序），替代旧的内存 sessionHistory
    val history: StateFlow<List<HistoryEntity>> = historyRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 错误信息
    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    // 是否正在生成回复
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        refreshDownloadedModels() // 启动时刷新已下载列表
        refreshAvailableStorage() // 启动时读取本机剩余可用存储
        observeDownloadEvents()   // 监听前台服务下载进度
        observeImageBackendEvents() // 监听图片后端进程状态
        observeImageGenerationEvents() // 监听后台生图服务进度
    }

    /** 刷新已下载模型列表 */
    private fun refreshDownloadedModels() {
        _downloadedModels.value = downloader.getDownloadedModels()
        refreshAvailableStorage()                              // 模型包状态变化后同步剩余容量
        Log.d(TAG, "已下载模型: ${_downloadedModels.value}")
    }

    /** P2: 检查 NPU 模型是否需要升级到 v3 格式（仅 NPU 模型检查 v3 marker） */
    fun needsModelUpgrade(modelId: String, isNpu: Boolean): Boolean {
        return downloader.needsModelUpgrade(modelId, isNpu)
    }

    /** 刷新本机可用存储空间 */
    private fun refreshAvailableStorage() {
        try {
            val statFs = StatFs(getApplication<Application>().filesDir.absolutePath)
            _availableStorageBytes.value = statFs.availableBytes // 读取当前分区真实可写空间
            Log.d(TAG, "本机可用存储: ${_availableStorageBytes.value} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "读取本机可用存储失败: ${e.message}", e)
            _availableStorageBytes.value = 0L                   // 读取失败时 UI 显示未知
        }
    }

    /**
     * 选择模型
     * @param modelInfo 选中的模型信息
     */
    fun selectModel(modelInfo: ModelInfo) {
        _selectedModel.value = modelInfo
        Log.d(TAG, "选中模型: ${modelInfo.id} (${modelInfo.name})")

        // 设备门禁：本机不支持的 QNN 模型（SoC 无 QNN/NPU 或低于 8 Gen 3）不开放选择/下载，
        // 这是物理能力门禁而非排期问题；UI 层已优先用 Toast 拦截，这里是服务层兜底
        if (modelInfo.isDeviceGatedQnnUnavailable) {
            _appState.value = AppState.ERROR
            _errorMessage.value = modelInfo.unavailableReason.ifBlank {
                DeviceSocCapability.REASON_NO_QNN
            }
            Log.w(TAG, "拦截设备门禁 QNN 模型选择: ${modelInfo.id}, ${DeviceSocCapability.describeGate()}")
            return
        }

        val isDownloadOnlyPackage = isDownloadOnlyImagePackage(modelInfo) // LocalDream 候选包允许先下载校验
        if (modelInfo.visibility != ModelVisibility.VERIFIED && !isDownloadOnlyPackage) {
            _appState.value = AppState.ERROR
            _errorMessage.value = modelInfo.unavailableReason.ifBlank {
                "当前模型还没有完成真机验证，暂不在首页开放。"
            }
            Log.w(TAG, "拦截未验证模型选择: ${modelInfo.id}")
            return
        }
        if (!modelInfo.adapterAvailable && !isDownloadOnlyPackage) {
            _appState.value = AppState.ERROR
            _errorMessage.value = modelInfo.unavailableReason.ifBlank {
                "当前模型需要 ${modelInfo.adapterType} adapter，暂未接入"
            }
            return
        }
        if (!isDownloadOnlyPackage) {
            val runtimeUnavailableReason = imageRuntimeUnavailableReason(modelInfo)
            if (runtimeUnavailableReason != null) {
                _appState.value = AppState.ERROR
                _errorMessage.value = runtimeUnavailableReason
                Log.w(TAG, "拦截本机不可用生图模型: ${modelInfo.id}, reason=$runtimeUnavailableReason")
                return
            }
        }

        val activeProgress = _downloadProgressByModelId.value[modelInfo.id]
        if (activeProgress != null) {
            _downloadProgress.value = activeProgress            // 回到下载页时恢复该模型当前百分比
            if (_downloadInfo.value.isBlank()) {
                _downloadInfo.value = "正在后台下载..."          // 没收到字节事件时给出明确状态
            }
            _appState.value = AppState.DOWNLOADING
            return
        }

        if (downloader.isModelReady(modelInfo) && !modelInfo.adapterAvailable) {
            _appState.value = AppState.ERROR
            _errorMessage.value = modelInfo.unavailableReason.ifBlank {
                "模型已下载；该模型的生成能力即将开放，敬请期待。"
            }
        } else if (downloader.isModelReady(modelInfo)) {
            // 已下载，直接加载
            loadModel(modelInfo)
        } else {
            // 需要下载
            _appState.value = AppState.NEED_DOWNLOAD
        }
    }

    /** 启动选中模型的下载 */
    fun downloadModel() {
        val modelInfo = _selectedModel.value ?: return                 // 未选中模型
        if (_appState.value == AppState.DOWNLOADING) return            // 防止重复下载
        // 设备门禁兜底：本机不支持的 QNN 模型不开放下载（与 selectModel 同口径）
        if (modelInfo.isDeviceGatedQnnUnavailable) {
            _appState.value = AppState.ERROR
            _errorMessage.value = modelInfo.unavailableReason.ifBlank {
                DeviceSocCapability.REASON_NO_QNN
            }
            Log.w(TAG, "拦截设备门禁 QNN 模型下载: ${modelInfo.id}")
            return
        }
        val isDownloadOnlyPackage = isDownloadOnlyImagePackage(modelInfo) // LocalDream 候选包允许下载
        if (modelInfo.visibility != ModelVisibility.VERIFIED && !isDownloadOnlyPackage) {
            _appState.value = AppState.ERROR
            _errorMessage.value = modelInfo.unavailableReason.ifBlank {
                "当前模型还没有完成真机验证，暂不开放下载入口。"
            }
            Log.w(TAG, "拦截未验证模型下载: ${modelInfo.id}")
            return
        }
        if (!modelInfo.adapterAvailable && !isDownloadOnlyPackage) {
            _appState.value = AppState.ERROR
            _errorMessage.value = modelInfo.unavailableReason.ifBlank {
                "当前模型需要 ${modelInfo.adapterType} adapter，暂未接入"
            }
            return
        }
        if (!isDownloadOnlyPackage) {
            val runtimeUnavailableReason = imageRuntimeUnavailableReason(modelInfo)
            if (runtimeUnavailableReason != null) {
                _appState.value = AppState.ERROR
                _errorMessage.value = runtimeUnavailableReason
                Log.w(TAG, "拦截本机不可用生图模型下载: ${modelInfo.id}, reason=$runtimeUnavailableReason")
                return
            }
        }
        if (modelInfo.adapterType == RuntimeAdapterType.BUILTIN_TEXT) {
            loadModel(modelInfo)                                       // 内置模型无需下载
            return
        }

        _appState.value = AppState.DOWNLOADING
        _downloadProgress.value = 0
        _downloadInfo.value = "准备下载..."
        updateModelDownloadProgress(modelInfo.id, 0)                  // 首页立即显示该模型正在下载

        val intent = ModelDownloadService.createStartIntent(getApplication(), modelInfo.id)
        ContextCompat.startForegroundService(getApplication(), intent)  // 用户点击触发前台服务下载
        Log.d(TAG, "已启动后台下载服务: ${modelInfo.id}")
    }

    /** 返回生图模型在当前设备的不可用原因；非生图模型直接放行 */
    private fun imageRuntimeUnavailableReason(modelInfo: ModelInfo): String? {
        if (modelInfo.capability != ModelCapability.IMAGE_GENERATION) return null
        if (modelInfo.adapterType != RuntimeAdapterType.MNN) return null // MediaPipe 等独立运行时不套用 MNN 崩溃名单
        val runtimeStatus = ImageInferenceBackendPlanner.currentStableDiffusionRuntimeStatus(getApplication())
        return if (runtimeStatus.canRun) null else runtimeStatus.reason
    }

    /**
     * 加载模型到内存
     * @param modelInfo 要加载的模型信息
     */
    private fun loadModel(modelInfo: ModelInfo) {
        _appState.value = AppState.LOADING_MODEL

        viewModelScope.launch {
            try {
                // 释放旧模型资源
                model?.close()
                model = null
                imageGenerator?.close()
                imageGenerator = null
                loadedImageModel = null
                _loadedModelId.value = null

                val adapter = ModelRuntimeAdapterFactory.create(modelInfo)
                val loadResult = adapter.load(getApplication(), modelInfo, downloader)
                when (loadResult) {
                    is RuntimeLoadResult.Text -> {
                        model = loadResult.engine                  // 仅文本 adapter 可进入聊天页
                        _appState.value = AppState.READY
                    }
                    is RuntimeLoadResult.Image -> {
                        imageGenerator = loadResult.engine         // 图片 adapter 进入生图页
                        loadedImageModel = modelInfo               // 后台服务需要知道当前图片模型
                        _imageGenerationState.value = ImageGenerationUiState()
                        _inpaintBlendContext.value = null          // 重新加载模型后旧贴回上下文失效
                        _appState.value = AppState.READY_IMAGE
                    }
                    is RuntimeLoadResult.Unsupported -> {
                        throw IllegalStateException(loadResult.reason)
                    }
                }
                _loadedModelId.value = modelInfo.id

                Log.d(TAG, "模型加载完成: ${modelInfo.id}，进入就绪状态")
            } catch (e: Exception) {
                Log.e(TAG, "模型加载失败 [${modelInfo.id}]: ${e.message}", e)
                _appState.value = AppState.ERROR
                _errorMessage.value = "模型加载失败: ${e.message}"
            }
        }
    }

    /**
     * 切换到其他模型（返回模型选择界面）
     */
    /** 删除单条生图历史，并联动删除磁盘产物文件 */
    fun deleteHistory(id: Long) = viewModelScope.launch {
        // 先取出记录再删行：outputPath 指向的结果图与 thumbnailPath 缩略图一并删除，防孤儿文件膨胀私有目录
        val entity = runCatching { historyRepository.getById(id) }.getOrNull()
        deleteHistoryOutputFile(entity?.outputPath)
        deleteHistoryOutputFile(entity?.thumbnailPath)          // 缩略图同步清理
        historyRepository.delete(id)
        Log.d(TAG, "已删除历史记录: id=$id, file=${entity?.outputPath}")
    }

    /** 清空全部生图历史，并联动删除全部磁盘产物文件 */
    fun clearHistory() = viewModelScope.launch {
        // 先遍历删文件再清表：任何单文件删除失败都不阻断其余记录清理
        val entities = runCatching { historyRepository.getAllOnce() }.getOrDefault(emptyList())
        entities.forEach { entity ->
            deleteHistoryOutputFile(entity.outputPath)
            deleteHistoryOutputFile(entity.thumbnailPath)       // 缩略图同步清理
        }
        historyRepository.deleteAll()
        Log.d(TAG, "已清空生图历史: ${entities.size} 条记录及产物文件")
    }

    /** 切换单条历史记录的收藏状态，立即更新 Room */
    fun setFavorite(id: Long, favorite: Boolean) = viewModelScope.launch {
        historyRepository.setFavorite(id, favorite)
        Log.d(TAG, "已切换收藏状态: id=$id favorite=$favorite")
    }

    // ---- 高级过滤：暴露 DISTINCT 值 Flow 供 UI chips 取值 ----

    /** 观察历史记录中出现的所有不重复模型 ID，供过滤 chips 使用 */
    fun observeModelIds(): Flow<List<String>> = historyRepository.observeModelIds()

    /** 观察历史记录中出现的所有不重复采样器，供过滤 chips 使用 */
    fun observeSchedulers(): Flow<List<String>> = historyRepository.observeSchedulers()

    /** 观察历史记录中出现的所有不重复尺寸，供过滤 chips 使用 */
    fun observeSizes(): Flow<List<String>> = historyRepository.observeSizes()

    /** 按高级过滤条件查询历史记录 Flow，供 UI 订阅自动刷新 */
    fun queryHistory(filter: com.qihao.open.rwkv.data.db.HistoryFilter): Flow<List<HistoryEntity>> =
        historyRepository.query(filter.toSqlQuery())

    /** 导入历史 zip 备份，合并到 Room 数据库 */
    suspend fun importHistoryZip(inputZip: File): com.qihao.open.rwkv.data.db.BackupImportResult =
        HistoryBackup.importHistoryZip(inputZip, historyRepository)

    /** 删除历史产物磁盘文件；runCatching 保证文件缺失/权限异常不影响数据库删除 */
    private fun deleteHistoryOutputFile(outputPath: String?) {
        if (outputPath.isNullOrBlank()) return
        runCatching {
            val file = File(outputPath)
            if (file.isFile && file.delete()) {
                Log.d(TAG, "已删除历史产物文件: $outputPath")
            }
        }.onFailure { error ->
            // 文件删除失败只记录日志：数据库行仍会被删除，避免历史记录清不掉
            Log.w(TAG, "删除历史产物文件失败: $outputPath, ${error.message}")
        }
    }

    /**
     * 为刚完成生成的结果图创建历史网格缩略图（IO 线程调用）：
     * 解码 outputPath → 最长边缩放到 256px → JPEG(80) 写入 filesDir/generated_images/thumbs/。
     * 任何异常向上抛出，由调用方 runCatching 兜底为 null（历史页回退文字卡）。
     *
     * @return 缩略图绝对路径，写入 HistoryEntity.thumbnailPath
     */
    private fun createHistoryThumbnail(outputPath: String): String {
        val source = BitmapFactory.decodeFile(outputPath)
            ?: error("无法解码结果图: $outputPath")              // 文件损坏/被删时明确失败
        try {
            // 最长边等比缩放到 256px；结果图本身小于该边长时不放大，直接复用解码结果
            val maxEdge = maxOf(source.width, source.height)
            val thumbWidth: Int
            val thumbHeight: Int
            if (maxEdge <= HISTORY_THUMBNAIL_MAX_EDGE) {
                thumbWidth = source.width
                thumbHeight = source.height
            } else {
                val ratio = HISTORY_THUMBNAIL_MAX_EDGE.toFloat() / maxEdge
                thumbWidth = (source.width * ratio).toInt().coerceAtLeast(1)   // 防极端比例缩到 0
                thumbHeight = (source.height * ratio).toInt().coerceAtLeast(1)
            }
            val thumb = if (thumbWidth == source.width && thumbHeight == source.height) {
                source                                           // 无需缩放：复用原图避免重复内存
            } else {
                Bitmap.createScaledBitmap(source, thumbWidth, thumbHeight, true) // 双线性过滤更平滑
            }
            val thumbsDir = File(getApplication<Application>().filesDir, HISTORY_THUMBS_SUBDIR)
            if (!thumbsDir.isDirectory && !thumbsDir.mkdirs()) {
                error("无法创建缩略图目录: ${thumbsDir.absolutePath}")
            }
            val thumbFile = File(thumbsDir, "$HISTORY_THUMB_FILE_PREFIX${System.currentTimeMillis()}.jpg")
            FileOutputStream(thumbFile).use { output ->
                thumb.compress(Bitmap.CompressFormat.JPEG, HISTORY_THUMBNAIL_QUALITY, output)
            }
            if (thumb !== source) thumb.recycle()                // 复用原图时不回收，交由下方统一回收
            Log.d(TAG, "已生成历史缩略图: ${thumbFile.absolutePath} (${thumb.width}x${thumb.height})")
            return thumbFile.absolutePath
        } finally {
            source.recycle()                                     // 解码产物统一回收，防大图驻留内存
        }
    }

    fun switchModel() {
        stopGenerating()         // 停止当前生成
        _messages.value = emptyList() // 清空消息
        model?.resetState()      // 重置模型状态
        _imageGenerationState.value = ImageGenerationUiState() // 清空生图结果
        _inpaintBlendContext.value = null                        // 切换模型后贴回上下文失效
        loadedImageModel = null
        _appState.value = AppState.MODEL_SELECT
        Log.d(TAG, "切换模型，返回选择界面")
    }

    /** 发送消息并获取回复 */
    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_isGenerating.value) return
        val rwkv = model ?: return

        // 添加用户消息
        val userMsg = ChatMessage(content = userInput, isUser = true)
        _messages.value = _messages.value + userMsg

        generateAssistantReply(rwkv, userInput)
    }

    /** 重试最近一轮 AI 回复 */
    fun retryLastResponse() {
        if (_isGenerating.value) return
        val rwkv = model ?: return
        val lastUserIndex = _messages.value.indexOfLast { it.isUser }
        if (lastUserIndex < 0) return

        val prompt = _messages.value[lastUserIndex].content
        _messages.value = _messages.value.take(lastUserIndex + 1) // 移除旧 AI 回复，保留用户问题
        model?.resetState()                                      // 重试必须从干净推理状态重新生成
        generateAssistantReply(rwkv, prompt)
    }

    /** 为指定用户输入生成 AI 回复 */
    private fun generateAssistantReply(rwkv: TextGenerationEngine, userInput: String) {
        // 添加空的 AI 消息占位
        val aiMsg = ChatMessage(content = "", isUser = false, isGenerating = true)
        _messages.value = _messages.value + aiMsg

        _isGenerating.value = true

        generateJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                val buffer = StringBuilder()

                rwkv.generate(
                    prompt = userInput,
                    maxTokens = 256,
                    temperature = 1.0f,
                    topP = 0.1f
                ) { token ->
                    buffer.append(token)
                    val visibleContent = sanitizeModelOutput(buffer.toString())

                    // 更新最后一条消息的内容
                    val current = _messages.value.toMutableList()
                    current[current.lastIndex] = ChatMessage(
                        content = visibleContent,
                        isUser = false,
                        isGenerating = true,
                        durationMillis = SystemClock.elapsedRealtime() - startedAt
                    )
                    _messages.value = current
                }

                // 生成完毕，标记为非生成状态
                val finalContent = sanitizeModelOutput(buffer.toString())
                val current = _messages.value.toMutableList()
                current[current.lastIndex] = ChatMessage(
                    content = finalContent.ifEmpty { "(无输出)" },
                    isUser = false,
                    isGenerating = false,
                    durationMillis = SystemClock.elapsedRealtime() - startedAt
                )
                _messages.value = current
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动停止生成，不视为错误
                Log.d(TAG, "生成已被用户停止")
                val current = _messages.value.toMutableList()
                current[current.lastIndex] = ChatMessage(
                    content = current[current.lastIndex].content.ifEmpty { "(已停止)" },
                    isUser = false,
                    isGenerating = false,
                    durationMillis = SystemClock.elapsedRealtime() - startedAt
                )
                _messages.value = current
            } catch (e: Exception) {
                Log.e(TAG, "生成失败: ${e.message}", e)
                val current = _messages.value.toMutableList()
                current[current.lastIndex] = ChatMessage(
                    content = "生成出错: ${e.message}",
                    isUser = false,
                    isGenerating = false,
                    durationMillis = SystemClock.elapsedRealtime() - startedAt
                )
                _messages.value = current
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** 停止当前生成 */
    fun stopGenerating() {
        generateJob?.cancel()
        _isGenerating.value = false
        if (_imageGenerationState.value.isGenerating) {
            getApplication<Application>().startService(
                ImageGenerationService.createStopIntent(getApplication())
            )
            _imageGenerationState.value = _imageGenerationState.value.copy(isGenerating = false, errorMessage = "已停止")
        }
    }

    /** 重置对话（清空消息和模型状态） */
    fun resetChat() {
        stopGenerating()
        _messages.value = emptyList()
        _imageGenerationState.value = ImageGenerationUiState()
        _inpaintBlendContext.value = null                        // 重置会话后贴回上下文失效
        model?.resetState()
        Log.d(TAG, "对话已重置")
    }

    /** 生成图片，兼容旧 UI 只传 prompt 的入口 */
    fun generateImage(prompt: String) {
        // 旧实现固定 seed=42：同一提示词永远出同一张图；改为随机 seed 恢复生成多样性
        generateImage(LocalDreamImageRequest(prompt = prompt, seed = randomImageSeed()))
    }

    /**
     * 更新 inpaint 贴回原图上下文。
     * 由 UI 在发起生图时调用：inpaint 且具备"裁剪导入原图 + 裁剪矩形 + 蒙版"时写入，
     * 其余情况传 null 清除，保证保存链路只对本次 inpaint 结果贴回、不误贴其它结果。
     * 位图所有权在 UI 层，这里只替换引用，不 recycle（避免 UI 仍持有的引用失效）。
     */
    fun setInpaintBlendContext(context: InpaintBlendContext?) {
        _inpaintBlendContext.value = context
        Log.d(
            TAG,
            if (context == null) {
                "inpaint 贴回上下文已清除"
            } else {
                "inpaint 贴回上下文已设置: 原图=${context.originalBitmap.width}x${context.originalBitmap.height}, " +
                    "cropRect=${context.cropRect}, mask=${context.maskBitmap != null}"
            },
        )
    }

    /** 生成随机生图种子：正数区间，与 UI seed 输入框 coerceIn(0, Int.MAX_VALUE) 口径一致 */
    private fun randomImageSeed(): Long {
        return kotlin.random.Random.nextLong(1L, Int.MAX_VALUE.toLong())
    }

    /** 使用 LocalDream 同类完整参数生成图片 */
    fun generateImage(request: LocalDreamImageRequest) {
        val validationError = request.validate()
        if (validationError.isNotBlank()) {
            _imageGenerationState.value = _imageGenerationState.value.copy(
                isGenerating = false,
                errorMessage = validationError
            )
            Log.w(TAG, "生图参数校验失败: $validationError")
            return
        }
        if (_imageGenerationState.value.isGenerating) return
        val imageModel = loadedImageModel ?: return

        if (imageModel.adapterType == RuntimeAdapterType.MNN) {
            // C4 批量闭环（参照 ModelRunScreen 批量逻辑）：
            // 固定 seed 时只生成 1 张并 Toast 说明（批量依赖每轮随机 seed 出不同结果）；
            // 空 seed 时按 batchCount 顺序循环，每张等上一张终态后再起下一张。
            val actualBatch = if (request.seedFixed) 1 else request.batchCount.coerceIn(1, 10)
            if (request.seedFixed && request.batchCount > 1) {
                android.widget.Toast
                    .makeText(getApplication(), "已固定随机种子，本次只生成 1 张", android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
            generateJob?.cancel()                                     // 同时间只保留一个批量任务
            generateJob = viewModelScope.launch {
                runBatchGeneration(request, imageModel.id, actualBatch)
            }
            Log.d(TAG, "已启动批量生图: modelId=${imageModel.id}, 计划 $actualBatch 张")
            return
        }

        lastImageRequest = request                                              // 记录本次请求，complete 时写入历史

        val generator = imageGenerator ?: return

        generateJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val durationTicker = launch {
                while (true) {
                    delay(500L)
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    val estimatedProgress = ((elapsed / 60_000f) * 90).toInt().coerceIn(1, 90)
                    _imageGenerationState.value = _imageGenerationState.value.copy(
                        isGenerating = true,
                        durationMillis = elapsed,
                        progress = maxOf(_imageGenerationState.value.progress, estimatedProgress),
                        backendStatusMessage = "本机生图运行中 · ${imageModel.adapterType.name}"
                    )
                }
            }
            _imageGenerationState.value = ImageGenerationUiState(
                prompt = request.prompt,
                isGenerating = true,
                progress = 1,
                backendStatusMessage = "使用独立图片 adapter 生成",
                backendReady = true
            )
            try {
                val result = generator.generate(request)
                durationTicker.cancel()
                _imageGenerationState.value = ImageGenerationUiState(
                    prompt = request.prompt,
                    outputPath = result.outputPath,
                    isGenerating = false,
                    progress = if (result.isSuccess) 100 else _imageGenerationState.value.progress,
                    durationMillis = result.durationMillis.takeIf { it > 0L }
                        ?: (SystemClock.elapsedRealtime() - startedAt),
                    errorMessage = result.errorMessage,
                    backendStatusMessage = _imageGenerationState.value.backendStatusMessage,
                    backendReady = true
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                durationTicker.cancel()
                Log.d(TAG, "生图已被用户停止")
                _imageGenerationState.value = _imageGenerationState.value.copy(
                    isGenerating = false,
                    durationMillis = SystemClock.elapsedRealtime() - startedAt,
                    errorMessage = "已停止"
                )
            } catch (e: Exception) {
                durationTicker.cancel()
                Log.e(TAG, "生图失败: ${e.message}", e)
                _imageGenerationState.value = _imageGenerationState.value.copy(
                    isGenerating = false,
                    durationMillis = SystemClock.elapsedRealtime() - startedAt,
                    errorMessage = e.message ?: "生图失败"
                )
            } finally {
                durationTicker.cancel()
            }
        }
    }

    /**
     * C4 批量闭环：按序循环生成 total 张，每张等上一张 COMPLETED/FAILED/CANCELLED 再起下一张
     * （参照 ModelRunScreen 批量逻辑）。后端单次请求只承载单张，循环责任在 ViewModel 层。
     * - 固定 seed：每张沿用同一 seed（调用方已把 total 收敛为 1，此处保持通用）
     * - 空 seed：每轮重新随机 seed，保证批量结果互不相同
     * - CANCELLED（用户停止）：立即终止整个批次；FAILED：与参照一致继续下一张
     */
    private suspend fun runBatchGeneration(request: LocalDreamImageRequest, modelId: String, total: Int) {
        for (index in 1..total) {
            val iteration = request.copy(
                seed = if (request.seedFixed) request.seed else randomImageSeed(), // 每轮随机 seed
                batchCount = 1                                                     // 协议层恒按单张下发
            )
            lastImageRequest = iteration                                           // complete 时按本轮参数落历史
            _imageGenerationState.value = ImageGenerationUiState(
                prompt = iteration.prompt,
                isGenerating = true,
                progress = 0,
                batchIndex = index,
                batchTotal = total,
                backendStatusMessage = if (total > 1) "准备生成第 $index/$total 张" else "等待图片后端启动",
                backendReady = _imageGenerationState.value.backendReady
            )
            val intent = ImageGenerationService.createStartIntent(getApplication(), modelId, iteration)
            ContextCompat.startForegroundService(getApplication(), intent)
            Log.d(TAG, "批量生图第 $index/$total 张已启动: seed=${iteration.seed}")

            // 等待本张终态事件；服务被厂商强杀而无事件时由超时兜底，防止批次死等
            val terminal = kotlinx.coroutines.withTimeoutOrNull(BATCH_TERMINAL_TIMEOUT_MS) {
                ImageGenerationEvents.events.first { event ->
                    event.modelId == modelId &&
                        (
                            event.type == ImageGenerationEventType.COMPLETED ||
                                event.type == ImageGenerationEventType.FAILED ||
                                event.type == ImageGenerationEventType.CANCELLED
                            )
                }
            }
            when {
                terminal == null -> {
                    Log.e(TAG, "批量生图第 $index/$total 张等待终态超时，终止批次")
                    _imageGenerationState.value = _imageGenerationState.value.copy(
                        isGenerating = false,
                        errorMessage = "批量生成超时，已停止"
                    )
                    return
                }
                terminal.type == ImageGenerationEventType.CANCELLED -> {
                    Log.d(TAG, "用户已停止，批量生图中止于第 $index/$total 张")
                    return                                                         // 用户停止：不再继续下一张
                }
                terminal.type == ImageGenerationEventType.FAILED -> {
                    // 与参照一致：单张失败不中断批次（服务下一轮会重新拉起后端进程）
                    Log.w(TAG, "批量生图第 $index/$total 张失败: ${terminal.message}，继续下一张")
                }
                else -> Log.d(TAG, "批量生图第 $index/$total 张完成: ${terminal.outputPath}")
            }
        }
        Log.d(TAG, "批量生图全部结束: 计划 $total 张")
    }

    /** 把最近一张成功生成的图发给 native 后端 /upscale 做 4x 放大 */
    fun upscaleCurrent() {
        val outputPath = _imageGenerationState.value.outputPath
        if (_imageGenerationState.value.isUpscaling) return            // 防重复触发
        if (outputPath.isBlank()) {
            _imageGenerationState.value = _imageGenerationState.value.copy(upscaleErrorMessage = "暂无可超分的图片，请先完成一次生成")
            return
        }
        val upscalerPath = resolveUpscalerModelPath()
        if (upscalerPath == null) {
            _imageGenerationState.value = _imageGenerationState.value.copy(
                upscaleErrorMessage = "超分暂不可用：上游仅提供 QNN 超分模型（骁龙 NPU），MNN 版超分模型开放后自动启用"
            )
            return
        }
        upscaleJob?.cancel()                                           // 同一时刻只保留一次超分
        upscaleJob = viewModelScope.launch {
            _imageGenerationState.value = _imageGenerationState.value.copy(isUpscaling = true, upscaleErrorMessage = "")
            try {
                val result = withContext(Dispatchers.IO) {
                    val bitmap = BitmapFactory.decodeFile(outputPath)
                        ?: error("无法读取待超分图片：$outputPath")      // 文件损坏/被删时明确报错
                    val rgbBytes = bitmapToRgbBytes(bitmap)            // 与 local-dream 一致的 RGB 提取
                    val target = File(File(outputPath).parentFile, "upscaled_${System.currentTimeMillis()}.jpg")
                    ImageUpscaleClient.upscale(
                        ImageUpscaleRequest(rgbBytes, bitmap.width, bitmap.height, upscalerPath),
                        target
                    )
                }
                _imageGenerationState.value = _imageGenerationState.value.copy(
                    isUpscaling = false,
                    upscaleOutputPath = result.outputPath,
                    upscaleErrorMessage = ""
                )
                Log.d(TAG, "超分完成: ${result.outputPath} (${result.outputWidth}x${result.outputHeight})")
            } catch (error: Exception) {
                if (error is CancellationException) throw error        // 协程取消不当作失败
                Log.e(TAG, "超分失败: ${error.message}", error)
                _imageGenerationState.value = _imageGenerationState.value.copy(
                    isUpscaling = false,
                    upscaleErrorMessage = error.message ?: "超分失败"
                )
            }
        }
    }

    /**
     * 解析超分模型路径：只认 MNN 格式（upscaler.mnn），优先 anime，其次写实。
     * 上游 xororz/upscaler 仓库仅提供 QNN 上下文二进制（upscaler_*.bin，骁龙 NPU 专用），
     * local-dream 按扩展名分发：.mnn 走 MNN、.bin 走 QNN；当前 CPU 阶段无 QNN runtime，
     * 因此 .bin 一律不视为可用，避免送进 MNN 加载器得到晦涩报错。
     *
     * 任务 6 口径：QNN .bin 超分模型已在 qnnSupported() 设备恢复可下载（ModelInfo 目录），
     * 但 QNN 超分执行走 NPU 阶段（QNN SDK 集成后由 native --lib_dir 运行时接管），
     * 本函数保持只认 .mnn 的现策略不变；MNN 版超分模型开放后自动启用。
     */
    private fun resolveUpscalerModelPath(): String? {
        listOf("localdream-upscaler-anime", "localdream-upscaler-realistic").forEach { modelId ->
            val file = File(downloader.getModelDirectory(modelId), "upscaler.mnn")  // 仅 MNN 格式可用
            if (file.isFile) return file.absolutePath
        }
        return null
    }

    /** 从 Bitmap 提取 RGB 平面字节（width*height*3），通道顺序与 local-dream performUpscale 完全一致 */
    private fun bitmapToRgbBytes(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgb = ByteArray(bitmap.width * bitmap.height * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            rgb[i * 3] = ((pixel shr 16) and 0xFF).toByte()      // R
            rgb[i * 3 + 1] = ((pixel shr 8) and 0xFF).toByte()   // G
            rgb[i * 3 + 2] = (pixel and 0xFF).toByte()           // B
        }
        return rgb
    }

    /**
     * 删除指定模型文件
     * @param modelId 模型唯一标识
     */
    fun deleteModel(modelId: String) {
        val modelInfo = ModelRegistry.findById(modelId)
        if (modelInfo?.adapterType == RuntimeAdapterType.BUILTIN_TEXT) {
            Log.d(TAG, "内置体验模型不可删除: $modelId")
            refreshDownloadedModels()
            return
        }

        // 如果正在使用该模型，先切换
        if (_loadedModelId.value == modelId) {
            switchModel()
            model?.close()
            model = null
            imageGenerator?.close()
            imageGenerator = null
            loadedImageModel = null
            _loadedModelId.value = null
        }
        downloader.deleteModel(modelId)
        refreshDownloadedModels()
        Log.d(TAG, "已删除模型: $modelId")
    }

    /** 返回模型选择界面 */
    fun goToModelSelect() {
        _appState.value = AppState.MODEL_SELECT
    }

    /** 监听前台服务下载事件，并同步 UI 状态 */
    private fun observeDownloadEvents() {
        viewModelScope.launch {
            ModelDownloadEvents.events.collect { event ->
                val selectedId = _selectedModel.value?.id
                when (event.type) {
                    ModelDownloadEventType.STARTED -> {
                        updateModelDownloadProgress(event.modelId, 0)  // 首页显示下载中标识
                        if (event.modelId == selectedId) {
                            _downloadProgress.value = 0
                            _downloadInfo.value = event.message.ifEmpty { "准备下载..." }
                        }
                    }
                    ModelDownloadEventType.PROGRESS -> {
                        updateModelDownloadProgress(event.modelId, event.percent)
                        refreshAvailableStorage()              // 下载过程中实时反映空间消耗
                        if (event.modelId == selectedId) {
                            _downloadProgress.value = event.percent
                            _downloadInfo.value = event.message.ifEmpty {
                                DownloadProgressFormatter.formatTransferredSize(
                                    event.downloadedBytes,
                                    event.totalBytes
                                )
                            }
                        }
                    }
                    ModelDownloadEventType.COMPLETED -> {
                        removeModelDownloadProgress(event.modelId)
                        refreshDownloadedModels()                       // 服务完成后刷新本地包状态
                        if (event.modelId == selectedId && _appState.value == AppState.DOWNLOADING) {
                            val selectedModelInfo = _selectedModel.value ?: return@collect
                            if (!selectedModelInfo.adapterAvailable) {
                                _appState.value = AppState.ERROR
                                _errorMessage.value = selectedModelInfo.unavailableReason.ifBlank {
                                    "模型已下载；该模型即将支持，敬请期待。"
                                }
                                Log.w(TAG, "下载完成但 adapter 未开放，阻断自动加载: ${event.modelId}")
                            } else {
                                Log.d(TAG, "下载完成且用户仍在下载页，开始加载模型: ${event.modelId}")
                                loadModel(selectedModelInfo)
                            }
                        } else {
                            Log.d(TAG, "下载完成，保持当前界面: ${event.modelId}")
                        }
                    }
                    ModelDownloadEventType.FAILED -> {
                        removeModelDownloadProgress(event.modelId)
                        refreshAvailableStorage()
                        if (event.modelId == selectedId) {
                            _appState.value = AppState.ERROR
                            _errorMessage.value = event.message.ifEmpty { "模型下载失败，请检查网络后重试" }
                        }
                    }
                }
            }
        }
    }

    /** 监听图片后端进程事件，并同步图片生成页状态 */
    private fun observeImageBackendEvents() {
        viewModelScope.launch {
            ImageBackendEvents.events.collect { event ->
                val currentModelId = loadedImageModel?.id ?: _selectedModel.value?.id
                if (event.modelId != currentModelId) return@collect
                _imageGenerationState.value = reduceImageBackendEventForState(
                    currentState = _imageGenerationState.value,
                    event = event
                )
            }
        }
    }

    /** 监听后台生图事件，并同步图片生成页状态 */
    private fun observeImageGenerationEvents() {
        viewModelScope.launch {
            ImageGenerationEvents.events.collect { event ->
                val currentModelId = loadedImageModel?.id ?: _selectedModel.value?.id
                if (event.modelId != currentModelId) return@collect
                when (event.type) {
                    ImageGenerationEventType.STARTED -> {
                        _imageGenerationState.value = ImageGenerationUiState(
                            prompt = event.prompt,
                            isGenerating = true,
                            progress = 0,
                            // STARTED 清空中间预览：新一轮生成不得残留上一张的预览图
                            previewPath = "",
                            // 批量信息跨事件保留：进度区需要持续显示“第 x/N 张”
                            batchIndex = _imageGenerationState.value.batchIndex,
                            batchTotal = _imageGenerationState.value.batchTotal,
                            backendStatusMessage = _imageGenerationState.value.backendStatusMessage,
                            backendReady = _imageGenerationState.value.backendReady
                        )
                    }
                    ImageGenerationEventType.PROGRESS -> {
                        _imageGenerationState.value = _imageGenerationState.value.copy(
                            isGenerating = true,
                            progress = event.progress,
                            // previewPath 为空表示本步未携带预览图：保留上一次预览，避免闪烁
                            previewPath = event.previewPath.ifBlank { _imageGenerationState.value.previewPath },
                            durationMillis = event.durationMillis,
                            errorMessage = "",
                            backendReady = _imageGenerationState.value.backendReady
                        )
                    }
                    ImageGenerationEventType.COMPLETED -> {
                        _imageGenerationState.value = ImageGenerationUiState(
                            prompt = event.prompt,
                            outputPath = event.outputPath,
                            isGenerating = false,
                            progress = 100,
                            // 完成后清空预览位：结果页展示成品图，预览文件等待下一轮覆盖
                            previewPath = "",
                            batchIndex = _imageGenerationState.value.batchIndex,
                            batchTotal = _imageGenerationState.value.batchTotal,
                            durationMillis = event.durationMillis,
                            backendStatusMessage = _imageGenerationState.value.backendStatusMessage,
                            backendReady = _imageGenerationState.value.backendReady
                        )
                        // 持久化本次生成到 Room 历史（参数来自 lastImageRequest，产物路径/耗时来自事件）
                        _lastReturnedSeed.value = event.seed        // 记录最近一次成功种子，供 UI 种子回填按钮
                        val req = lastImageRequest
                        viewModelScope.launch {
                            // 去重兜底：同 outputPath 已存在记录则跳过 insert，防止 COMPLETED 事件重复触发落库
                            if (historyRepository.existsByOutputPath(event.outputPath)) {
                                Log.w(TAG, "历史记录已存在，跳过重复 insert: outputPath=${event.outputPath}")
                                return@launch
                            }
                            // 历史网格缩略图：IO 线程解码结果图缩放落盘；runCatching 失败返回 null，
                            // 历史页回退文字卡样式，绝不影响历史记录主流程
                            val thumbnailPath = withContext(Dispatchers.IO) {
                                runCatching { createHistoryThumbnail(event.outputPath) }
                                    .onFailure { error -> Log.w(TAG, "生成历史缩略图失败，回退文字卡: ${error.message}") }
                                    .getOrNull()
                            }
                            historyRepository.insert(
                                HistoryEntity(
                                    prompt = req?.prompt ?: event.prompt,
                                    negativePrompt = req?.negativePrompt ?: "",
                                    mode = req?.mode?.wireValue ?: "txt2img",
                                    steps = req?.steps ?: 20,
                                    cfg = req?.cfg ?: 7f,
                                    seed = event.seed,                             // 以事件携带的真实 seed 为准，兜底 req 值
                                    width = req?.width ?: 512,
                                    height = req?.height ?: 512,
                                    scheduler = req?.scheduler ?: "dpm",
                                    denoiseStrength = req?.denoiseStrength ?: 0.6f,
                                    outputPath = event.outputPath,
                                    durationMillis = event.durationMillis,
                                    createdAt = System.currentTimeMillis(),
                                    thumbnailPath = thumbnailPath, // 缩略图路径，生成失败为 null
                                    // H4：历史记录绑定生成模型 ID，事件自带 modelId，缺失时取当前加载模型兜底
                                    modelId = event.modelId.ifBlank { loadedImageModel?.id.orEmpty() }
                                )
                            )
                        }
                    }
                    ImageGenerationEventType.FAILED -> {
                        _imageGenerationState.value = _imageGenerationState.value.copy(
                            isGenerating = false,
                            previewPath = "",                         // 失败后不再保留中间预览
                            durationMillis = event.durationMillis,
                            errorMessage = event.message.ifBlank { "生图失败" },
                            backendStatusMessage = _imageGenerationState.value.backendStatusMessage,
                            backendReady = _imageGenerationState.value.backendReady
                        )
                    }
                    ImageGenerationEventType.CANCELLED -> {
                        _imageGenerationState.value = _imageGenerationState.value.copy(
                            isGenerating = false,
                            previewPath = "",                         // 停止后清空预览位
                            batchIndex = 0,                           // 用户主动终止：批量计数归零
                            batchTotal = 0,
                            errorMessage = event.message.ifBlank { "已停止" }
                        )
                    }
                }
            }
        }
    }

    /** 更新首页指定模型的下载进度 */
    private fun updateModelDownloadProgress(modelId: String, percent: Int) {
        val next = _downloadProgressByModelId.value.toMutableMap()
        next[modelId] = percent.coerceIn(0, 100)
        _downloadProgressByModelId.value = next
    }

    /** 移除首页指定模型的下载中状态 */
    private fun removeModelDownloadProgress(modelId: String) {
        val next = _downloadProgressByModelId.value.toMutableMap()
        next.remove(modelId)
        _downloadProgressByModelId.value = next
    }

    /** 判断图片模型包是否允许在 adapter 未完成前先下载并做完整性校验 */
    private fun isDownloadOnlyImagePackage(modelInfo: ModelInfo): Boolean {
        return modelInfo.imageBackendType.isNotBlank() &&
            (modelInfo.capability == ModelCapability.IMAGE_GENERATION ||
                modelInfo.capability == ModelCapability.IMAGE_UPSCALING)
    }

    /** 清理模型内部控制文本，避免思考块或模板标记直接显示到聊天 UI */
    private fun sanitizeModelOutput(raw: String): String {
        var text = raw
        while (true) {
            val start = text.indexOf("<think>")
            if (start < 0) break
            val end = text.indexOf("</think>", startIndex = start + "<think>".length)
            text = if (end >= 0) {
                text.removeRange(start, end + "</think>".length) // 删除完整思考块
            } else {
                text.substring(0, start)                         // 未闭合思考块暂不展示
            }
        }
        return text
            .replace("<|im_start|>", "")
            .replace("<|im_end|>", "")
            .trimStart()
    }

    override fun onCleared() {
        super.onCleared()
        stopGenerating()
        model?.close()
        imageGenerator?.close()
        Log.d(TAG, "ViewModel 销毁，资源已释放")
    }
}

/** 根据图片后端事件更新生图 UI 状态，作为纯函数便于 JVM 单测锁定状态契约 */
internal fun reduceImageBackendEventForState(
    currentState: ImageGenerationUiState,
    event: ImageBackendEvent
): ImageGenerationUiState {
    return when (event.type) {
        ImageBackendEventType.STARTING -> currentState.copy(
            backendStatusMessage = event.message.ifBlank { "正在启动图片后端" },
            backendReady = false,
            errorMessage = ""
        )
        ImageBackendEventType.READY -> currentState.copy(
            backendStatusMessage = event.message.ifBlank { "图片后端已就绪" },
            backendReady = true,
            errorMessage = ""
        )
        ImageBackendEventType.STOPPED -> currentState.copy(
            backendStatusMessage = event.message.ifBlank { "图片后端已停止" },
            backendReady = false
        )
        ImageBackendEventType.FAILED -> currentState.copy(
            isGenerating = false,
            backendStatusMessage = event.message.ifBlank { "图片后端启动失败" },
            backendReady = false,
            errorMessage = event.message.ifBlank { "图片后端启动失败" }
        )
    }
}

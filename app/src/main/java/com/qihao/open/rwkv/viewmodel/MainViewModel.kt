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
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.ModelRegistry
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.TextGenerationEngine
import com.qihao.open.rwkv.model.adapter.ModelRuntimeAdapterFactory
import com.qihao.open.rwkv.model.adapter.RuntimeLoadResult
import com.qihao.open.rwkv.service.ModelDownloadEventType
import com.qihao.open.rwkv.service.ModelDownloadEvents
import com.qihao.open.rwkv.service.ModelDownloadService
import com.qihao.open.rwkv.util.DownloadProgressFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 聊天消息数据类 */
data class ChatMessage(
    val content: String,            // 消息内容
    val isUser: Boolean,            // 是否为用户消息
    val isGenerating: Boolean = false, // 是否正在生成中
    val durationMillis: Long = 0L    // AI 回复生成耗时，生成中实时更新，结束后冻结
)

/** 应用状态 */
enum class AppState {
    MODEL_SELECT,   // 模型选择界面
    NEED_DOWNLOAD,  // 需要下载选中的模型
    DOWNLOADING,    // 正在下载
    LOADING_MODEL,  // 正在加载模型
    READY,          // 就绪，可以对话
    ERROR           // 出错
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val downloader = ModelDownloader(application)

    // 当前文本生成模型（懒初始化）
    private var model: TextGenerationEngine? = null
    private var generateJob: Job? = null // 当前生成任务

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

    // 聊天消息列表
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

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
    }

    /** 刷新已下载模型列表 */
    private fun refreshDownloadedModels() {
        _downloadedModels.value = downloader.getDownloadedModels()
        refreshAvailableStorage()                              // 模型包状态变化后同步剩余容量
        Log.d(TAG, "已下载模型: ${_downloadedModels.value}")
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

        if (!modelInfo.adapterAvailable) {
            _appState.value = AppState.ERROR
            _errorMessage.value = modelInfo.unavailableReason.ifBlank {
                "当前模型需要 ${modelInfo.adapterType} adapter，暂未接入"
            }
            return
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

        if (downloader.isModelReady(modelInfo)) {
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
        if (!modelInfo.adapterAvailable) {
            _appState.value = AppState.ERROR
            _errorMessage.value = modelInfo.unavailableReason.ifBlank {
                "当前模型需要 ${modelInfo.adapterType} adapter，暂未接入"
            }
            return
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
                _loadedModelId.value = null

                val adapter = ModelRuntimeAdapterFactory.create(modelInfo)
                val loadResult = adapter.load(getApplication(), modelInfo, downloader)
                when (loadResult) {
                    is RuntimeLoadResult.Text -> {
                        model = loadResult.engine                  // 仅文本 adapter 可进入聊天页
                    }
                    is RuntimeLoadResult.Unsupported -> {
                        throw IllegalStateException(loadResult.reason)
                    }
                }
                _loadedModelId.value = modelInfo.id

                _appState.value = AppState.READY
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
    fun switchModel() {
        stopGenerating()         // 停止当前生成
        _messages.value = emptyList() // 清空消息
        model?.resetState()      // 重置模型状态
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
    }

    /** 重置对话（清空消息和模型状态） */
    fun resetChat() {
        stopGenerating()
        _messages.value = emptyList()
        model?.resetState()
        Log.d(TAG, "对话已重置")
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
                            Log.d(TAG, "下载完成且用户仍在下载页，开始加载模型: ${event.modelId}")
                            loadModel(_selectedModel.value ?: return@collect)
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
        Log.d(TAG, "ViewModel 销毁，资源已释放")
    }
}

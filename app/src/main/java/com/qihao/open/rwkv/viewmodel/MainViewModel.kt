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
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qihao.open.rwkv.model.HFTokenizer
import com.qihao.open.rwkv.model.ITokenizer
import com.qihao.open.rwkv.model.ModelArch
import com.qihao.open.rwkv.model.ModelDownloader
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.ModelRegistry
import com.qihao.open.rwkv.model.RWKVModel
import com.qihao.open.rwkv.model.RWKVTokenizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 聊天消息数据类 */
data class ChatMessage(
    val content: String,            // 消息内容
    val isUser: Boolean,            // 是否为用户消息
    val isGenerating: Boolean = false // 是否正在生成中
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

    // 分词器和模型（懒初始化）
    private var tokenizer: ITokenizer? = null            // 分词器接口（RWKV 或 HF）
    private var model: RWKVModel? = null
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
    }

    /** 刷新已下载模型列表 */
    private fun refreshDownloadedModels() {
        _downloadedModels.value = downloader.getDownloadedModels()
        Log.d(TAG, "已下载模型: ${_downloadedModels.value}")
    }

    /**
     * 选择模型
     * @param modelInfo 选中的模型信息
     */
    fun selectModel(modelInfo: ModelInfo) {
        _selectedModel.value = modelInfo
        Log.d(TAG, "选中模型: ${modelInfo.id} (${modelInfo.name})")

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

        _appState.value = AppState.DOWNLOADING
        _downloadProgress.value = 0
        _downloadInfo.value = "准备下载..."

        viewModelScope.launch {
            val success = downloader.downloadModel(modelInfo) { downloaded, total, percent ->
                _downloadProgress.value = percent
                _downloadInfo.value = formatSize(downloaded, total)
            }

            if (success) {
                Log.d(TAG, "下载完成: ${modelInfo.id}，开始加载模型")
                refreshDownloadedModels()                              // 刷新已下载列表
                loadModel(modelInfo)                                   // 加载模型
            } else {
                _appState.value = AppState.ERROR
                _errorMessage.value = "模型下载失败，请检查网络后重试"
            }
        }
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
                tokenizer = null
                _loadedModelId.value = null

                // 根据模型架构选择分词器
                tokenizer = createTokenizer(modelInfo)
                Log.d(TAG, "分词器初始化完成: ${tokenizer!!::class.simpleName}")

                // 初始化并加载模型
                val rwkv = RWKVModel(tokenizer!!)
                val modelPath = downloader.getModelPath(modelInfo.id)
                rwkv.loadModel(modelPath)
                model = rwkv
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
     * 根据模型架构创建对应的分词器
     * - RWKV 模型：使用内置 vocab.json 的 RWKVTokenizer
     * - Transformer 模型：使用下载的 tokenizer.json 的 HFTokenizer
     */
    private fun createTokenizer(modelInfo: ModelInfo): ITokenizer {
        return if (modelInfo.arch == ModelArch.RWKV) {
            RWKVTokenizer(getApplication())              // RWKV 内置分词器
        } else {
            // Transformer 模型：使用 HuggingFace tokenizer.json
            val tokenizerPath = downloader.getTokenizerPath(modelInfo.id)
            if (tokenizerPath != null) {
                Log.d(TAG, "加载 HF 分词器: $tokenizerPath")
                HFTokenizer(tokenizerPath, modelInfo.chatTemplate)
            } else {
                // Transformer tokenizer 是必需资产，缺失时必须阻止错误加载
                val message = "Transformer 模型缺少 tokenizer.json: ${modelInfo.id}"
                Log.e(TAG, message)
                throw IllegalStateException(message)
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

        // 添加空的 AI 消息占位
        val aiMsg = ChatMessage(content = "", isUser = false, isGenerating = true)
        _messages.value = _messages.value + aiMsg

        _isGenerating.value = true

        generateJob = viewModelScope.launch {
            try {
                val buffer = StringBuilder()

                rwkv.generate(
                    prompt = userInput,
                    maxTokens = 256,
                    temperature = 1.0f,
                    topP = 0.1f
                ) { token ->
                    buffer.append(token)

                    // 更新最后一条消息的内容
                    val current = _messages.value.toMutableList()
                    current[current.lastIndex] = ChatMessage(
                        content = buffer.toString(),
                        isUser = false,
                        isGenerating = true
                    )
                    _messages.value = current
                }

                // 生成完毕，标记为非生成状态
                val current = _messages.value.toMutableList()
                current[current.lastIndex] = ChatMessage(
                    content = buffer.toString().ifEmpty { "(无输出)" },
                    isUser = false,
                    isGenerating = false
                )
                _messages.value = current
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动停止生成，不视为错误
                Log.d(TAG, "生成已被用户停止")
                val current = _messages.value.toMutableList()
                current[current.lastIndex] = ChatMessage(
                    content = current[current.lastIndex].content.ifEmpty { "(已停止)" },
                    isUser = false,
                    isGenerating = false
                )
                _messages.value = current
            } catch (e: Exception) {
                Log.e(TAG, "生成失败: ${e.message}", e)
                val current = _messages.value.toMutableList()
                current[current.lastIndex] = ChatMessage(
                    content = "生成出错: ${e.message}",
                    isUser = false,
                    isGenerating = false
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

    /** 格式化文件大小显示 */
    private fun formatSize(downloaded: Long, total: Long): String {
        val dlMB = downloaded / 1024f / 1024f
        val totalMB = total / 1024f / 1024f
        return if (totalMB > 1024) {
            "%.1f GB / %.1f GB".format(dlMB / 1024f, totalMB / 1024f) // GB 显示
        } else {
            "%.1f MB / %.1f MB".format(dlMB, totalMB) // MB 显示
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopGenerating()
        model?.close()
        Log.d(TAG, "ViewModel 销毁，资源已释放")
    }
}

/**
 * MainActivity - RWKV 模型交互主界面
 *
 * 功能：
 * - ModelSelectScreen: 模型选择网格（仅展示已验证模型卡片）
 * - DownloadScreen: 模型下载进度界面
 * - ChatScreen: 聊天对话界面（消息列表 + 输入框）
 * - LoadingScreen: 模型加载中界面
 * - ErrorScreen: 错误提示界面
 *
 * 使用 Jetpack Compose + Material3 构建
 */
package com.qihao.open.rwkv

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qihao.open.rwkv.model.ModelArch
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.ModelRegistry
import com.qihao.open.rwkv.ui.theme.RWKVTheme
import com.qihao.open.rwkv.viewmodel.AppState
import com.qihao.open.rwkv.viewmodel.ChatMessage
import com.qihao.open.rwkv.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 边到边显示
        setContent {
            RWKVTheme {
                MainApp()
            }
        }
    }
}

/** 主应用入口 Composable */
@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current                         // 当前 Activity 上下文，用于请求通知权限
    val appState by viewModel.appState.collectAsState()             // 应用状态
    val selectedModel by viewModel.selectedModel.collectAsState()   // 选中模型
    val downloadedModels by viewModel.downloadedModels.collectAsState() // 已下载模型
    val downloadProgress by viewModel.downloadProgress.collectAsState() // 下载进度
    val downloadProgressByModelId by viewModel.downloadProgressByModelId.collectAsState() // 首页模型下载进度
    val availableStorageBytes by viewModel.availableStorageBytes.collectAsState() // 本机可用存储
    val downloadInfo by viewModel.downloadInfo.collectAsState()     // 下载信息
    val messages by viewModel.messages.collectAsState()             // 聊天消息
    val errorMessage by viewModel.errorMessage.collectAsState()     // 错误信息
    val isGenerating by viewModel.isGenerating.collectAsState()     // 是否生成中
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }                                                      // 权限回调不再启动下载，避免重复触发服务

    val startDownloadWithNotificationPrompt = {
        viewModel.downloadModel()                              // 先启动前台下载，权限弹窗不阻断用户点击
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true } // 将 testTag 暴露为 resource-id，便于真机自动化验收
            .testTag("magicwx-root")
    ) {
        // 根据应用状态显示不同界面
        when (appState) {
            AppState.MODEL_SELECT -> ModelSelectScreen(
                downloadedModels = downloadedModels,
                downloadProgressByModelId = downloadProgressByModelId,
                availableStorageBytes = availableStorageBytes,
                onSelectModel = { viewModel.selectModel(it) },
                onDeleteModel = { viewModel.deleteModel(it) }
            )
            AppState.NEED_DOWNLOAD -> DownloadConfirmScreen(
                modelInfo = selectedModel,
                onConfirm = startDownloadWithNotificationPrompt,
                onBack = { viewModel.goToModelSelect() }
            )
            AppState.DOWNLOADING -> DownloadingScreen(
                modelInfo = selectedModel,
                progress = downloadProgress,
                info = downloadInfo,
                onBackground = { viewModel.goToModelSelect() }
            )
            AppState.LOADING_MODEL -> LoadingScreen(
                modelName = selectedModel?.name ?: "模型"
            )
            AppState.READY -> ChatScreen(
                modelName = selectedModel?.name ?: "AI",
                messages = messages,
                isGenerating = isGenerating,
                onSend = { viewModel.sendMessage(it) },
                onStop = { viewModel.stopGenerating() },
                onReset = { viewModel.resetChat() },
                onRetryLast = { viewModel.retryLastResponse() },
                onSwitchModel = { viewModel.switchModel() }
            )
            AppState.ERROR -> ErrorScreen(
                message = errorMessage,
                onRetry = { viewModel.goToModelSelect() }
            )
        }
    }
}

// =============================================================================
// 模型选择界面
// =============================================================================

/** 模型选择网格界面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectScreen(
    downloadedModels: Set<String>,
    downloadProgressByModelId: Map<String, Int>,
    availableStorageBytes: Long,
    onSelectModel: (ModelInfo) -> Unit,
    onDeleteModel: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择模型") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 顶部说明 + 本机剩余容量，便于用户下载大模型前判断空间是否足够
            StorageHeader(
                availableStorageBytes = availableStorageBytes,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 模型网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(ModelRegistry.models) { modelInfo ->
                    ModelCard(
                        modelInfo = modelInfo,
                        isDownloaded = modelInfo.id in downloadedModels,
                        downloadProgress = downloadProgressByModelId[modelInfo.id],
                        onClick = { onSelectModel(modelInfo) },
                        onDelete = { onDeleteModel(modelInfo.id) }
                    )
                }
            }
        }
    }
}

/** 首页顶部存储提示条 */
@Composable
fun StorageHeader(
    availableStorageBytes: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "选择一个模型开始对话",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            modifier = Modifier.testTag("available-storage-badge"),
            shape = RoundedCornerShape(9999.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Text(
                text = "本机剩余 ${formatStorageSize(availableStorageBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/** 单个模型卡片 */
@Composable
fun ModelCard(
    modelInfo: ModelInfo,
    isDownloaded: Boolean,
    downloadProgress: Int?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isDownloading = downloadProgress != null && !isDownloaded // 已下载后不再显示下载中

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model-card-${modelInfo.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDownloaded) {
                MaterialTheme.colorScheme.primaryContainer // 已下载用主色容器
            } else {
                MaterialTheme.colorScheme.surfaceVariant   // 未下载用表面变体
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 第一行：模型名称
            Text(
                text = modelInfo.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 参数量 + 量化方式
            Row {
                Text(
                    text = modelInfo.paramSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = modelInfo.quantization,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 模型描述
            Text(
                text = modelInfo.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 底部信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 文件大小
                Text(
                    text = displayModelSize(modelInfo),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 状态标签
                if (isDownloading) {
                    Text(
                        text = displayDownloadStatus(downloadProgress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else if (modelInfo.arch == ModelArch.BUILTIN) {
                    Text(
                        text = "内置可用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else if (isDownloaded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "已下载",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // 删除按钮（小文字）
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .testTag("delete-model-${modelInfo.id}")
                                .clickable { onDelete() }
                        )
                    }
                } else {
                    Text(
                        text = if (modelInfo.isFullySupported) "完全支持" else "实验性",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (modelInfo.isFullySupported) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                // 首页实时下载进度条，后台下载时也保持可见
                if (downloadProgress > 0) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0, 100) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .testTag("model-download-progress-${modelInfo.id}")
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .testTag("model-download-progress-${modelInfo.id}")
                    )
                }
            }
        }
    }
}

// =============================================================================
// 下载确认界面
// =============================================================================

/** 下载确认界面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadConfirmScreen(
    modelInfo: ModelInfo?,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val info = modelInfo ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载模型") },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("download-back-button")
                    ) { Text("返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 模型名称
            Text(
                text = info.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 模型描述
            Text(
                text = info.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 模型参数信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow("架构", displayModelArch(info))
                    InfoRow("参数量", info.paramSize)
                    InfoRow("量化", info.quantization)
                    InfoRow("预估大小", displayModelSize(info))
                    InfoRow("支持等级", if (info.isFullySupported) "完全支持" else "实验性")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 下载按钮
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("start-download-button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("开始下载", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 返回按钮
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("return-model-select-button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("返回选择", fontSize = 16.sp)
            }
        }
    }
}

/** 信息行 */
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 格式化首页模型下载状态，0% 阶段不再误导为卡死 */
private fun displayDownloadStatus(progress: Int): String {
    return if (progress > 0) "下载中 $progress%" else "下载中"
}

/** 格式化下载页百分比，已有字节但不足 1% 时显示 <1% */
private fun displayDownloadPercent(progress: Int, info: String): String {
    val hasTransferredBytes = info.contains(" MB /") || info.contains(" GB /")
    return if (progress == 0 && hasTransferredBytes) "<1%" else "$progress%"
}

/** 格式化模型大小显示 */
private fun displayModelSize(modelInfo: ModelInfo): String {
    if (modelInfo.arch == ModelArch.BUILTIN) return "内置"      // 内置体验模型不占外部下载空间
    return if (modelInfo.fileSizeMB >= 1024) {
        "%.1f GB".format(modelInfo.fileSizeMB / 1024f)
    } else {
        "${modelInfo.fileSizeMB} MB"
    }
}

/** 格式化本机存储容量，按 1024 进位自动选择 B/KB/MB/GB/TB */
private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "未知"                            // 系统读取失败时给出明确兜底文案
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0                                      // 使用二进制容量口径，贴近 Android 存储显示
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes} B"
    } else {
        "%.1f %s".format(value, units[unitIndex])
    }
}

/** 格式化模型架构显示 */
private fun displayModelArch(modelInfo: ModelInfo): String {
    return when (modelInfo.arch) {
        ModelArch.BUILTIN -> "内置体验"
        ModelArch.RWKV -> "RWKV"
        ModelArch.TRANSFORMER -> "Transformer"
    }
}

// =============================================================================
// 下载进度界面
// =============================================================================

/** 下载中界面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadingScreen(
    modelInfo: ModelInfo?,
    progress: Int,
    info: String,
    onBackground: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正在下载") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 模型名称
            Text(
                text = modelInfo?.name ?: "模型",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 进度百分比
            Text(
                text = displayDownloadPercent(progress, info),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 进度条
            if (progress > 0) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 下载信息
            Text(
                text = info,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 提示
            Text(
                text = "请保持网络连接，下载支持断点续传",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 后台下载按钮：只关闭下载页，前台服务和通知继续显示进度
            OutlinedButton(
                onClick = onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("background-download-button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("后台下载，返回模型选择", fontSize = 16.sp)
            }
        }
    }
}

// =============================================================================
// 模型加载界面
// =============================================================================

/** 模型加载中界面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingScreen(modelName: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("加载模型") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "正在加载 $modelName",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "首次加载可能需要较长时间",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================================
// 聊天界面
// =============================================================================

/** 聊天对话界面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modelName: String,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onRetryLast: () -> Unit,
    onSwitchModel: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }     // 输入框文本
    val listState = rememberLazyListState()               // 列表滚动状态
    val context = LocalContext.current                     // 当前上下文，用于复制成功提示
    val clipboardManager = context.getSystemService(ClipboardManager::class.java) // 系统剪贴板

    // 新消息时自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(modelName) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    // 重置对话按钮
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.testTag("reset-chat-button")
                    ) { Text("重置") }
                    // 切换模型按钮
                    TextButton(
                        onClick = onSwitchModel,
                        modifier = Modifier.testTag("switch-model-button")
                    ) { Text("切换") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 消息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 空消息提示
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "发送消息开始对话",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 消息列表
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        onCopy = {
                            clipboardManager.setPrimaryClip(
                                ClipData.newPlainText("MagicWX 回答", message.content)
                            )
                            Toast.makeText(context, "已复制回答", Toast.LENGTH_SHORT).show()
                        },
                        onRetry = onRetryLast
                    )
                }
            }

            // 底部输入区域
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 输入框
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat-input"),
                        placeholder = { Text("输入消息...") },
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3,
                        enabled = !isGenerating
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 发送 / 停止按钮
                    if (isGenerating) {
                        Button(
                            onClick = onStop,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("stop-generation-button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("停止")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onSend(inputText)
                                    inputText = "" // 清空输入框
                                }
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("send-message-button"),
                            shape = RoundedCornerShape(12.dp),
                            enabled = inputText.isNotBlank()
                        ) {
                            Text("发送")
                        }
                    }
                }
            }
        }
    }
}

/** 消息气泡 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRetry: () -> Unit
) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) {
                            MaterialTheme.colorScheme.primary           // 用户消息用主色
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer // AI 消息用次色容器
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (message.content.isEmpty() && message.isGenerating) {
                    // 生成中占位动画
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    Text(
                        text = message.content,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary              // 用户消息文字色
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer   // AI 消息文字色
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (!isUser) {
                // AI 回复底部操作栏：弱化信息层级，接近 ChatGPT 移动端的轻量消息操作区
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(message.durationMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("message-duration-label")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistActionChip(
                            text = "复制",
                            enabled = message.content.isNotBlank(),
                            modifier = Modifier.testTag("copy-message-button"),
                            onClick = onCopy
                        )
                        AssistActionChip(
                            text = "重试",
                            enabled = !message.isGenerating,
                            modifier = Modifier.testTag("retry-message-button"),
                            onClick = onRetry
                        )
                    }
                }
            }
        }
    }
}

/** AI 消息底部轻量操作胶囊 */
@Composable
private fun AssistActionChip(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9999.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(9999.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 格式化回复耗时，短任务显示毫秒，长任务显示秒 */
private fun formatDuration(durationMillis: Long): String {
    return if (durationMillis < 1000L) {
        "用时 ${durationMillis.coerceAtLeast(0L)} 毫秒"
    } else {
        "用时 %.1f 秒".format(durationMillis / 1000f)
    }
}

// =============================================================================
// 错误界面
// =============================================================================

/** 错误提示界面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("出错了") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("error-return-model-select-button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("返回选择模型", fontSize = 16.sp)
            }
        }
    }
}

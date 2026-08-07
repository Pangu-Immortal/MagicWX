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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qihao.open.rwkv.data.db.HistoryEntity
import com.qihao.open.rwkv.data.db.HistoryFilter as DbHistoryFilter
import com.qihao.open.rwkv.data.db.HistoryBackup
import com.qihao.open.rwkv.data.db.HistoryRepository
import com.qihao.open.rwkv.data.db.GenerationMode
import com.qihao.open.rwkv.data.db.FavoriteFilter
import com.qihao.open.rwkv.data.settings.ImageGenerationParams
import com.qihao.open.rwkv.data.settings.ImageGenerationPreferences
import com.qihao.open.rwkv.data.TagAutocompleteRepository
import com.qihao.open.rwkv.ui.screens.rememberPromptFieldController
import com.qihao.open.rwkv.ui.screens.ControlledPromptTagTextField
import com.qihao.open.rwkv.ui.screens.PromptFieldController
import com.qihao.open.rwkv.ui.screens.ReproduceParametersDialog
import com.qihao.open.rwkv.model.ModelArch
import com.qihao.open.rwkv.model.ModelCapability
import com.qihao.open.rwkv.model.ModelInfo
import com.qihao.open.rwkv.model.ModelRegistry
import com.qihao.open.rwkv.model.CustomModelImporter
import com.qihao.open.rwkv.model.PinnedModels
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.image.DeviceSocCapability
import com.qihao.open.rwkv.model.image.ImageInferenceBackendChoice
import com.qihao.open.rwkv.model.image.ImageInferenceBackendPlanner
import com.qihao.open.rwkv.model.image.LocalDreamDefaults
import com.qihao.open.rwkv.model.image.LocalDreamGenerationMode
import com.qihao.open.rwkv.model.image.LocalDreamImageRequest
import com.qihao.open.rwkv.model.image.LocalDreamImageWireFormat
import com.qihao.open.rwkv.ui.image.CropImageScreen
import com.qihao.open.rwkv.ui.image.MaskToolMode
import com.qihao.open.rwkv.ui.image.ZoomableImageOverlay
import com.qihao.open.rwkv.service.ImageTokenizeClient
import com.qihao.open.rwkv.ui.image.MaskDrawingCanvas
import com.qihao.open.rwkv.ui.image.rememberMaskDrawingController
import com.qihao.open.rwkv.ui.theme.RWKVTheme
import com.qihao.open.rwkv.util.ImageExportUtils
import com.qihao.open.rwkv.util.InpaintBlendUtils
import com.qihao.open.rwkv.util.ReportImageUtils
import com.qihao.open.rwkv.viewmodel.AppState
import com.qihao.open.rwkv.viewmodel.ChatMessage
import com.qihao.open.rwkv.viewmodel.ImageGenerationUiState
import com.qihao.open.rwkv.viewmodel.InpaintBlendContext
import com.qihao.open.rwkv.viewmodel.MainViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** LocalDream 模型统一前缀，首页生图主入口只展示该体系内的快路径目录 */
private const val LOCALDREAM_MODEL_ID_PREFIX = "localdream-"

/** 日志标签，统一追踪界面导入/裁剪链路 */
private const val TAG = "MainActivity"

/** 裁剪源图最长边上限：相册超大图降采样到该边长再进入裁剪，防止整图解码 OOM */
private const val MAX_CROP_SOURCE_EDGE = 2048

/** 蒙版编辑器画笔粗细范围与默认值（dp），对齐参照 InpaintScreen 滑条 5f..50f */
private const val MASK_BRUSH_MIN_DP = 5f
private const val MASK_BRUSH_MAX_DP = 50f
private const val MASK_BRUSH_DEFAULT_DP = 24f

/** 首页底部三栏模型分区 */
internal enum class HomeModelTab(
    val title: String,
    val headerText: String
) {
    LANGUAGE("语言模型", "选择一个语言模型开始对话"),
    CPU_IMAGE("CPU 生图", "选择一个 CPU 生图模型离线生成图片"),
    // NPU 分区按设备 SoC 门禁展示真实模型目录（DeviceSocCapability）：
    // 骁龙 QNN 设备可下载运行；非骁龙设备卡片显示"本机不支持"并阻断下载
    NPU_IMAGE("NPU 生图", "选择一个 NPU 生图模型离线生成图片")
}

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
    val imageInferenceBackend by viewModel.imageInferenceBackend.collectAsState() // 启动时选择的图片推理架构
    val downloadInfo by viewModel.downloadInfo.collectAsState()     // 下载信息
    val messages by viewModel.messages.collectAsState()             // 聊天消息
    val imageGenerationState by viewModel.imageGenerationState.collectAsState() // 生图状态
    val inpaintBlendContext by viewModel.inpaintBlendContext.collectAsState() // inpaint 贴回原图临时上下文
    val history by viewModel.history.collectAsState()   // Room 生图历史
    val errorMessage by viewModel.errorMessage.collectAsState()     // 错误信息
    val isGenerating by viewModel.isGenerating.collectAsState()     // 是否生成中
    var selectedHomeTab by remember { mutableStateOf(HomeModelTab.LANGUAGE) } // 首页底部三栏选中状态
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
                imageInferenceBackend = imageInferenceBackend,
                selectedTab = selectedHomeTab,
                onTabSelected = { selectedHomeTab = it },
                onSelectModel = { viewModel.selectModel(it) },
                onDeleteModel = { viewModel.deleteModel(it) },
                needsModelUpgrade = { id, isNpu -> viewModel.needsModelUpgrade(id, isNpu) }
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
            AppState.READY_IMAGE -> ImageGenerationScreen(
                modelId = selectedModel?.id.orEmpty(),   // 参数持久化按 modelId 键控；未选中时为空不落盘
                modelName = selectedModel?.name ?: "生图模型",
                state = imageGenerationState,
                inpaintBlendContext = inpaintBlendContext, // inpaint 贴回上下文：结果页保存时消费
                imageInferenceBackend = imageInferenceBackend,
                lastReturnedSeed = viewModel.lastReturnedSeed.collectAsState().value,
                onGenerate = { viewModel.generateImage(it) },
                onSetInpaintBlendContext = { viewModel.setInpaintBlendContext(it) },
                onStop = { viewModel.stopGenerating() },
                onSwitchModel = { viewModel.switchModel() },
                history = history,
                onDeleteHistory = { viewModel.deleteHistory(it) },
                onClearHistory = { viewModel.clearHistory() },
                onSetFavorite = { id, fav -> viewModel.setFavorite(id, fav) },
                onUpscale = { viewModel.upscaleCurrent() }
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
private fun ModelSelectScreen(
    downloadedModels: Set<String>,
    downloadProgressByModelId: Map<String, Int>,
    availableStorageBytes: Long,
    imageInferenceBackend: ImageInferenceBackendChoice,
    selectedTab: HomeModelTab,
    onTabSelected: (HomeModelTab) -> Unit,
    onSelectModel: (ModelInfo) -> Unit,
    onDeleteModel: (String) -> Unit,
    needsModelUpgrade: (String, Boolean) -> Boolean = { _, _ -> false } // P2: NPU 模型升级检查
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择模型") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            HomeBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected
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
                title = selectedTab.headerText,
                availableStorageBytes = availableStorageBytes,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 上部提示精简：各 tab 只保留单行说明（TabIntroCard 或 NPU 非骁龙 NpuGateBanner），
            // 移除 ImageInferenceBackendBanner 能力检测条（之前占 2 行，用户反馈上部提示太多）

            // NPU tab 非骁龙由 NpuGateBanner 单行提示，不再显示 TabIntroCard（避免 NPU 提示占多行）
            // 其余 tab（语言/CPU/NPU 骁龙）显示 TabIntroCard 说明
            if (!(selectedTab == HomeModelTab.NPU_IMAGE && !DeviceSocCapability.qnnSupported())) {
                TabIntroCard(
                    selectedTab = selectedTab,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (selectedTab == HomeModelTab.NPU_IMAGE && !DeviceSocCapability.qnnSupported()) {
                // 非骁龙设备：网格仍完整展示 NPU/SDXL/Anima/超分目录，但统一挂门禁横幅，
                // 卡片显示"本机不支持"且点击只 Toast 门禁原因，不提供下载
                NpuGateBanner(modifier = Modifier.padding(bottom = 12.dp))
            }

            // ---- 自定义模型导入：仅 CPU 生图 tab 展示 ----
            var customModels by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
            val context = LocalContext.current
            if (selectedTab == HomeModelTab.CPU_IMAGE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val scanned = CustomModelImporter.scanCustomModels(context)
                            customModels = scanned
                            if (scanned.isEmpty()) {
                                Toast.makeText(context, "未发现自定义模型（请将模型目录放入 files/models/）", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "已导入 ${scanned.size} 个自定义模型", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text("导入自定义模型") }
                }
            }

            // PinnedModels 观察：置顶状态变化时触发列表重排
            val pinnedIds by PinnedModels.observePinned().collectAsState(initial = emptyList())
            val tabModels = remember(selectedTab, pinnedIds) {
                PinnedModels.sort(modelsForHomeTab(selectedTab))
            }
            // 门禁卡片点击反馈用 Toast 承载原因，避免跳 ERROR 页造成"出错"误解
            val gatedToastContext = LocalContext.current

            // 模型网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (tabModels.isEmpty() && customModels.isEmpty()) {
                    item {
                        EmptyTabCard(
                            selectedTab = selectedTab,
                            imageInferenceBackend = imageInferenceBackend
                        )
                    }
                }
                items(tabModels) { modelInfo ->
                    val imageRuntimeStatus = ImageInferenceBackendPlanner.stableDiffusionRuntimeStatus(imageInferenceBackend)
                    val runtimeUnavailableReason = when {
                        !modelInfo.adapterAvailable -> modelInfo.unavailableReason.ifBlank {
                            "该模型暂不支持生成，敬请期待。"
                        }
                        modelInfo.capability == ModelCapability.IMAGE_GENERATION &&
                            modelInfo.adapterType == RuntimeAdapterType.MNN &&
                            !imageRuntimeStatus.canRun -> imageRuntimeStatus.reason
                        else -> null
                    }
                    ModelCard(
                        modelInfo = modelInfo,
                        isDownloaded = modelInfo.id in downloadedModels,
                        downloadProgress = downloadProgressByModelId[modelInfo.id],
                        isPinned = modelInfo.id in pinnedIds,
                        needsUpgrade = needsModelUpgrade(modelInfo.id, modelInfo.adapterType == RuntimeAdapterType.QNN_IMAGE_GENERATION),
                        runtimeUnavailableReason = runtimeUnavailableReason,
                        // QNN 族不可用属于设备物理门禁：角标用"本机不支持"而非排期性的"即将支持"
                        unavailableBadge = if (modelInfo.isDeviceGatedQnnUnavailable) "本机不支持" else "即将支持",
                        onClick = {
                            if (runtimeUnavailableReason != null) {
                                // 门禁模型：点击只给原因 Toast，不进入选择/下载流程
                                Toast.makeText(gatedToastContext, runtimeUnavailableReason, Toast.LENGTH_LONG).show()
                            } else {
                                onSelectModel(modelInfo)
                            }
                        },
                        onDelete = { onDeleteModel(modelInfo.id) },
                        onPinToggle = {
                            if (modelInfo.id in pinnedIds) {
                                PinnedModels.unpin(modelInfo.id)
                            } else {
                                PinnedModels.pin(modelInfo.id)
                            }
                        }
                    )
                }
                // 自定义模型：已验证/已适配，无需门禁检查
                items(customModels, key = { "custom_${it.id}" }) { modelInfo ->
                    ModelCard(
                        modelInfo = modelInfo,
                        isDownloaded = modelInfo.id in downloadedModels,
                        downloadProgress = downloadProgressByModelId[modelInfo.id],
                        isPinned = modelInfo.id in pinnedIds,
                        needsUpgrade = false,                    // 自定义模型不检查升级
                        runtimeUnavailableReason = null,
                        unavailableBadge = "",
                        onClick = { onSelectModel(modelInfo) },
                        onDelete = { onDeleteModel(modelInfo.id) },
                        onPinToggle = {
                            if (modelInfo.id in pinnedIds) {
                                PinnedModels.unpin(modelInfo.id)
                            } else {
                                PinnedModels.pin(modelInfo.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 首页底部三 TAB 导航 */
@Composable
private fun HomeBottomNavigation(
    selectedTab: HomeModelTab,
    onTabSelected: (HomeModelTab) -> Unit
) {
    NavigationBar(modifier = Modifier.testTag("home-bottom-tabs")) {
        HomeModelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = { Text(tabIcon(tab)) },
                label = { Text(tab.title) },
                modifier = Modifier.testTag("home-tab-${tab.name.lowercase()}")
            )
        }
    }
}

/** 返回首页指定 TAB 应展示的模型集合 */
internal fun modelsForHomeTab(tab: HomeModelTab): List<ModelInfo> {
    return when (tab) {
        HomeModelTab.LANGUAGE -> ModelRegistry.models.filter {
            it.capability == ModelCapability.TEXT_CHAT
        }
        HomeModelTab.CPU_IMAGE -> ModelRegistry.allModels.filter {
            isLocalDreamImageModel(it) &&
                it.capability == ModelCapability.IMAGE_GENERATION &&
                it.adapterType != RuntimeAdapterType.QNN_IMAGE_GENERATION
        }
        HomeModelTab.NPU_IMAGE -> ModelRegistry.allModels.filter {
            isLocalDreamImageModel(it) &&
                (it.capability == ModelCapability.IMAGE_GENERATION || it.capability == ModelCapability.IMAGE_UPSCALING) &&
                it.adapterType == RuntimeAdapterType.QNN_IMAGE_GENERATION
        }
    }
}

/** 判断模型是否属于 LocalDream 生图体系，避免慢速 SD1.5 / MediaPipe 候选误入首页 */
private fun isLocalDreamImageModel(modelInfo: ModelInfo): Boolean {
    return modelInfo.id.startsWith(LOCALDREAM_MODEL_ID_PREFIX)
}

/** 首页 TAB 图标使用文本符号，避免新增图标资源 */
private fun tabIcon(tab: HomeModelTab): String {
    return when (tab) {
        HomeModelTab.LANGUAGE -> "💬"
        HomeModelTab.CPU_IMAGE -> "🎨"
        HomeModelTab.NPU_IMAGE -> "⚡"
    }
}

/** TAB 说明卡，降低用户对 CPU/NPU 生图能力边界的误解 */
@Composable
private fun TabIntroCard(
    selectedTab: HomeModelTab,
    modifier: Modifier = Modifier
) {
    val text = when (selectedTab) {
        HomeModelTab.LANGUAGE -> "本页只展示已接入运行时的本地语言模型；内置体验模型无需下载。"
        HomeModelTab.CPU_IMAGE -> "本地离线生图：下载模型后即可在本机生成，全程无需联网。"
        // NPU 文案按设备 SoC 门禁分支（DeviceSocCapability）：
        // 骁龙 QNN 设备 → 可下载即生成；非骁龙设备 → 明确无 QNN/NPU 能力并指向 CPU 生图
        HomeModelTab.NPU_IMAGE -> if (DeviceSocCapability.qnnSupported()) {
            "NPU 高速生图：下载模型后即可在本机生成"
        } else {
            "本设备无 QNN/NPU 能力（需骁龙平台）；当前可使用 CPU 生图"
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-tab-intro-${selectedTab.name.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/** 空 TAB 状态，明确说明没有可安全运行的模型，不展示未验证模型卡片 */
@Composable
private fun EmptyTabCard(
    selectedTab: HomeModelTab,
    imageInferenceBackend: ImageInferenceBackendChoice
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty-tab-${selectedTab.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${selectedTab.title}暂未开放可用模型",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (selectedTab == HomeModelTab.NPU_IMAGE) {
                    "NPU 生图只会在匹配的骁龙 QNN 设备和模型包通过真机验证后显示下载卡片。当前设备摘要：${imageInferenceBackend.hardwareSummary}"
                } else {
                    "该分区没有通过下载、加载和真机运行验证的模型。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * NPU 分区设备门禁横幅：非骁龙设备无 QNN/NPU 能力时展示。
 * 语义迁移说明：原 NpuComingSoonCard（testTag "npu-coming-soon"）是"即将支持"占位卡；
 * NPU 分区已改为按设备门禁的真实模型目录，本横幅承接其位置与提示职责，
 * testTag 更名为 "npu-gate-banner"（设备门禁横幅），旧 tag 不再使用。
 */
@Composable
private fun NpuGateBanner(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("npu-gate-banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // 精简单行提示：只占一行，明确本机无 NPU 能力 + 替代 CPU 生图
        Text(
            text = "本设备不支持 QNN/NPU 生图，请使用 CPU 生图",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )
    }
}

/** 生图候选说明卡；未真实出图前只展示状态，生成入口仍由运行时门禁阻断 */
@Composable
fun UnavailableImageModelNotice(
    imageModels: List<ModelInfo>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("unavailable-image-model-notice"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "生图模型（暂不可用）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            imageModels.forEach { modelInfo ->
                Text(
                    text = "${modelInfo.name}：真机未完成稳定出图，允许下载校验但暂不开放生成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "等替换成手机端可真实生成图片的模型后，这里会恢复为可用生图入口。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/** 本地离线能力说明条：向用户说明语言与生图模型都在本机运行 */
@Composable
fun ImageInferenceBackendBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("image-inference-backend-badge"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "图片推理：CPU / GPU / NPU 能力检测",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "语言模型与生图模型均为本地离线运行；生图模型按本机能力展示。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 首页顶部存储提示条 */
@Composable
fun StorageHeader(
    title: String,
    availableStorageBytes: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
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
    isPinned: Boolean = false,                       // P2: 是否已置顶（PinnedModels）
    needsUpgrade: Boolean = false,                   // P2: NPU 模型是否需要升级到 v3 格式
    runtimeUnavailableReason: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPinToggle: () -> Unit = {},                    // P2: 置顶/取消置顶回调
    // 运行时不可用角标文案：默认"即将支持"（排期性）；设备物理门禁传"本机不支持"
    unavailableBadge: String = "即将支持"
) {
    val isDownloading = downloadProgress != null && !isDownloaded // 已下载后不再显示下载中
    val isRuntimeUnavailable = runtimeUnavailableReason != null // 运行时不安全时禁止绿色可用态误导用户
    val isAvailable = !isRuntimeUnavailable && (modelInfo.arch == ModelArch.BUILTIN || isDownloaded) // 已下载且本机可运行才显示可用

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model-card-${modelInfo.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) {
                availableModelCardContainerColor()       // 可用卡片使用浅绿色半透明背景
            } else {
                MaterialTheme.colorScheme.surfaceVariant   // 未下载用表面变体
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 第一行：模型名称 + CPU/NPU 角标 + 置顶按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 模型名称 + CPU/NPU 角标（对齐 local-dream ModelCard L2147-2166）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = modelInfo.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // 生图模型显示 CPU/NPU 角标（对齐 local-dream ModelCard badge）
                    if (modelInfo.arch == ModelArch.STABLE_DIFFUSION) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (modelInfo.runOnCpu) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            contentColor = if (modelInfo.runOnCpu) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ) {
                            Text(
                                text = if (modelInfo.runOnCpu) "CPU" else "NPU",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("model-backend-badge-${modelInfo.id}")
                            )
                        }
                    }
                }
                // P2: 置顶/取消置顶图标按钮
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("pin-toggle-${modelInfo.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = if (isPinned) "取消置顶" else "置顶",
                        tint = if (isPinned) {
                            Color(0xFFFFC107)                // 琥珀色：已置顶
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) // 灰色：未置顶
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 参数量 + 量化方式 + 生图模型额外信息行
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
                // 生图模型显示最大分辨率（对齐 local-dream ModelCard L2218-2226）
                if (modelInfo.arch == ModelArch.STABLE_DIFFUSION && modelInfo.generationSize > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${modelInfo.generationSize}x${modelInfo.generationSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

            // P2: NPU 模型升级提示（已下载但缺少 v3 marker）
            if (isDownloaded && needsUpgrade) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFF3E0)                // 浅橙色背景
                    ) {
                        Text(
                            text = "可升级（v3）",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE65100),           // 深橙色文字
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("model-upgrade-hint-${modelInfo.id}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 底部信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 文件大小：生图模型优先使用 approximateSize（对齐 local-dream），否则自动换算
                Text(
                    text = if (modelInfo.approximateSize.isNotBlank()) {
                        modelInfo.approximateSize
                    } else {
                        displayModelSize(modelInfo)
                    },
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
                } else if (isRuntimeUnavailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            // 角标文案由调用方按门禁性质传入：排期性"即将支持" / 设备物理门禁"本机不支持"
                            text = unavailableBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("model-runtime-unavailable-${modelInfo.id}")
                        )
                        if (isDownloaded) {
                            Spacer(modifier = Modifier.width(4.dp))
                            // 模型文件已下载但当前设备不能安全运行时，仍允许用户删除释放空间。
                            Text(
                                text = "删除",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .testTag("delete-model-${modelInfo.id}")
                                    .clickable { onDelete() }
                            )
                        }
                    }
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
                        text = displayModelSupportStatus(modelInfo),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
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
// 生图界面
// =============================================================================

/** 图片生成界面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    modelId: String,                    // 当前生图模型 ID：参数持久化按该 key 存取
    modelName: String,
    state: ImageGenerationUiState,
    inpaintBlendContext: InpaintBlendContext?, // inpaint 贴回原图上下文：结果页保存时消费，无则保存原结果
    imageInferenceBackend: ImageInferenceBackendChoice,
    lastReturnedSeed: Long,             // 最近一次成功生成的 seed，供种子回填按钮
    onGenerate: (LocalDreamImageRequest) -> Unit,
    onSetInpaintBlendContext: (InpaintBlendContext?) -> Unit, // 发起生图时写入/清除贴回上下文
    onStop: () -> Unit,
    onSwitchModel: () -> Unit,
    history: List<HistoryEntity>,            // Room 持久化生图历史（ViewModel 提供）
    onDeleteHistory: (Long) -> Unit,         // 删除单条历史
    onClearHistory: () -> Unit,              // 清空全部历史
    onSetFavorite: (Long, Boolean) -> Unit,  // 切换收藏状态
    onUpscale: () -> Unit                    // /upscale 超分执行入口
) {
    val context = LocalContext.current
    // 生成期间保持亮屏：组合进入时置 keepScreenOn，离开生图页立即恢复，避免长任务被息屏打断
    val hostView = LocalView.current
    DisposableEffect(Unit) {
        hostView.keepScreenOn = true
        onDispose {
            hostView.keepScreenOn = false
        }
    }
    var showStopConfirmDialog by remember { mutableStateOf(false) }  // 停止生成确认弹窗
    // 生成中拦截系统返回键：弹停止确认而不是直接退出，防误触丢失长任务
    BackHandler(enabled = state.isGenerating) {
        showStopConfirmDialog = true
    }
    var selectedRunTab by remember { mutableStateOf(LocalDreamRunTab.PROMPT) }
    // 模型专属默认：按 ModelInfo.defaultPrompt/defaultNegativePrompt 下发（对齐参照
    // Model.kt 各 create*Model 的 codeDefaults）；模型未声明时回退 LocalDreamDefaults
    // ANYTHING_V5 全局兜底。初始化三级回退：持久化存档 > 模型专属默认 > 全局默认
    // （对齐参照 ModelConfig.withFallback(...).resolve() 的逐字段优先级语义）。
    val currentModelInfo = remember(modelId) { ModelRegistry.findById(modelId) }
    val modelDefaultPrompt = currentModelInfo?.defaultPrompt ?: LocalDreamDefaults.ANYTHING_V5_PROMPT
    val modelDefaultNegativePrompt = currentModelInfo?.defaultNegativePrompt
        ?: LocalDreamDefaults.ANYTHING_V5_NEGATIVE_PROMPT
    var promptText by remember(modelId) { mutableStateOf(modelDefaultPrompt) }
    var negativePromptText by remember(modelId) { mutableStateOf(modelDefaultNegativePrompt) }
    var generationMode by remember { mutableStateOf(LocalDreamGenerationMode.TEXT_TO_IMAGE) }
    var steps by remember { mutableStateOf(20) }
    var cfg by remember { mutableStateOf(7f) }
    var seedText by remember { mutableStateOf("") }
    var width by remember { mutableStateOf(512) }
    var height by remember { mutableStateOf(512) }
    var scheduler by remember { mutableStateOf("dpm") }
    var aspectRatio by remember { mutableStateOf("1:1") }
    var denoiseStrength by remember { mutableStateOf(0.6f) }
    var batchCount by remember { mutableStateOf(1) }
    var showDiffusionProcess by remember { mutableStateOf(false) }
    var previewStride by remember { mutableStateOf(1) }
    var outputFormat by remember { mutableStateOf(LocalDreamImageWireFormat.JPEG) }
    var ultrafixSteps by remember { mutableStateOf(10) }
    var ultrafixDenoiseSteps by remember { mutableStateOf(4) }
    var ultrafixQualityDenoise by remember { mutableStateOf(true) }
    var lowram by remember { mutableStateOf(false) }                     // SDXL/Anima 低内存模式
    var seqDit by remember { mutableStateOf(false) }                     // Anima 序列化 DiT
    var patch by remember { mutableStateOf("") }                         // 当前分辨率 patch 文件路径：NPU 模型非 512 尺寸时由后端回填
    var importedImage by remember { mutableStateOf<LocalDreamImportedImage?>(null) }
    var importedMask by remember { mutableStateOf<LocalDreamImportedImage?>(null) }

    // ---- Tag 自动补全控制器：绑定到正向提示词输入框，提供 tag 补全/撤销/重做/权重调整 ----
    val tagRepository = remember { TagAutocompleteRepository.getInstance(context.applicationContext) }
    val promptTagController = rememberPromptFieldController(
        repository = tagRepository,
        suggestionCount = 12
    )
    // 初始化 controller 文本：进页时用当前 promptText 填充
    LaunchedEffect(Unit) { promptTagController.replaceText(promptText) }
    // 双向绑定：controller 文本变更时回写 promptText state（生成请求 + DataStore 持久化用）
    LaunchedEffect(Unit) {
        promptTagController.onTextCommitted = { promptText = promptTagController.text }
    }
    // 外部 promptText 变更（历史复用/导入参数/重置）时同步回 controller
    LaunchedEffect(promptText) {
        if (promptTagController.text != promptText) {
            promptTagController.replaceText(promptText)
        }
    }
    // tag 候选是本地 CSV 查询（assets 内置），不依赖后端就绪；token 计数才需后端
    LaunchedEffect(Unit) {
        promptTagController.autocompleteAvailable = true
    }

    // ---- 重置参数到模型默认值：按当前模型 Info 的 defaultPrompt/defaultNegativePrompt
    // 与 LocalDreamDefaults 全局默认逐字段恢复，弹确认后执行 ----
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    fun resetGenerationParamsToModelDefault() {
        promptText = modelDefaultPrompt
        negativePromptText = modelDefaultNegativePrompt
        generationMode = LocalDreamGenerationMode.TEXT_TO_IMAGE
        steps = LocalDreamDefaults.STEPS
        cfg = LocalDreamDefaults.CFG
        seedText = LocalDreamDefaults.SEED
        width = 512
        height = 512
        scheduler = LocalDreamDefaults.SCHEDULER
        aspectRatio = LocalDreamDefaults.ASPECT_RATIO
        denoiseStrength = LocalDreamDefaults.DENOISE_STRENGTH
        batchCount = LocalDreamDefaults.BATCH_COUNT
        showDiffusionProcess = false
        previewStride = 1
        outputFormat = LocalDreamImageWireFormat.JPEG
        ultrafixSteps = 10
        ultrafixDenoiseSteps = 4
        ultrafixQualityDenoise = true
        lowram = false                                                 // 重置低内存模式
        seqDit = false                                                 // 重置序列化 DiT
        patch = ""                                                     // 清空 patch 路径
        importedImage = null
        importedMask = null
        Log.d(TAG, "已重置生图参数到模型默认值: modelId=$modelId")
    }

    // ---- 导入参数（剪贴板）：进提示词页时检测剪贴板是否含共享参数文本 ----
    // 复用 copyLocalDreamParamsToClipboard 的 key=value 格式做逆解析，匹配到即弹窗让用户勾选字段
    var importParamsDialogVisible by remember { mutableStateOf(false) }
    var clipboardParams by remember { mutableStateOf<Map<String, String>?>(null) }
    var showAdvancedSettings by remember { mutableStateOf(false) }              // 高级设置弹窗：对齐 local-dream AdvancedSettingsDialog
    var lastProcessedClipboardHash by remember { mutableStateOf(0) }
    // 各字段勾选状态：默认全选
    var importSelectedFields by remember { mutableStateOf<Set<String>>(emptySet()) }
    fun parseLocalDreamParamsFromClipboard(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        text.lines().forEach { line ->
            val eqIndex = line.indexOf('=')
            if (eqIndex > 0) {
                val key = line.substring(0, eqIndex).trim()
                val value = line.substring(eqIndex + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    result[key] = value
                }
            }
        }
        return result
    }
    LaunchedEffect(selectedRunTab) {
        if (selectedRunTab != LocalDreamRunTab.PROMPT) return@LaunchedEffect
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip ?: return@LaunchedEffect
        if (clip.itemCount == 0) return@LaunchedEffect
        val text = clip.getItemAt(0).text?.toString() ?: return@LaunchedEffect
        // 检测是否含共享参数格式：至少包含 prompt= 和 steps= 两个字段
        if (!text.contains("prompt=") || !text.contains("steps=")) return@LaunchedEffect
        val hash = text.hashCode()
        if (hash == lastProcessedClipboardHash) return@LaunchedEffect  // 已处理过，不重复弹
        lastProcessedClipboardHash = hash
        val params = parseLocalDreamParamsFromClipboard(text)
        if (params.isEmpty()) return@LaunchedEffect
        clipboardParams = params
        importSelectedFields = params.keys.toSet()                      // 默认全选
        importParamsDialogVisible = true
        Log.d(TAG, "检测到剪贴板共享参数: ${params.size} 字段")
    }

    // ---- C1 参数持久化：按 modelId 键控的 DataStore 读写 ----
    // 进入页面用存档初始化；无存档时保持上方模型专属默认初始值（第三级全局默认由
    // ImageGenerationParams 构造默认承载）；参数变化走 500ms 防抖自动保存，覆盖滑条
    // 连续拖动与历史复用等所有变更路径，无需给每个控件单独接 onCommit。
    val generationPrefs = remember { ImageGenerationPreferences(context.applicationContext) }
    var prefsLoaded by remember(modelId) { mutableStateOf(false) }   // modelId 变化时重置加载标记
    LaunchedEffect(modelId) {
        if (modelId.isBlank()) return@LaunchedEffect                  // 无模型 ID：保持默认值，不落盘
        // 存档存在时逐字段覆盖；缺失字段回退模型专属默认（仍缺失再落全局默认）。
        // 无存档返回 null：保持初始模型专属默认值，不用全局默认覆盖提示词
        val params = generationPrefs.loadIfExists(
            modelId,
            fallback = ImageGenerationParams(
                prompt = modelDefaultPrompt,
                negativePrompt = modelDefaultNegativePrompt
            )
        )
        if (params != null) {
            promptText = params.prompt
            negativePromptText = params.negativePrompt
            steps = params.steps
            cfg = params.cfg
            seedText = params.seedText
            width = params.width
            height = params.height
            scheduler = params.scheduler
            aspectRatio = params.aspectRatio
            denoiseStrength = params.denoiseStrength
            batchCount = params.batchCount
            showDiffusionProcess = params.showDiffusionProcess
            previewStride = params.previewStride
            outputFormat = LocalDreamImageWireFormat.entries
                .firstOrNull { it.wireValue == params.outputFormatWire }
                ?: LocalDreamImageWireFormat.JPEG
            ultrafixSteps = params.ultrafixSteps
            ultrafixDenoiseSteps = params.ultrafixDenoiseSteps
            ultrafixQualityDenoise = params.ultrafixQualityDenoise
            lowram = params.lowram
            seqDit = params.seqDit
            patch = params.patch
            Log.d(TAG, "已加载生图参数存档: modelId=$modelId")
        } else {
            Log.d(TAG, "无参数存档，使用模型专属默认: modelId=$modelId")
        }
        prefsLoaded = true
    }
    // ---- NPU 分辨率 patch 检测：按当前模型目录和宽高查找可用的 patch 文件 ----
    LaunchedEffect(modelId, width, height, prefsLoaded) {
        if (!prefsLoaded || modelId.isBlank()) return@LaunchedEffect
        val backendType = currentModelInfo?.imageBackendType.orEmpty()
        if (backendType !in listOf("sd15npu", "sdxl", "anima")) {
            patch = ""
            return@LaunchedEffect
        }
        // 仅在非 512 尺寸时检查 patch 文件（对齐参照 BackendService 的 patch 逻辑）
        if (width == 512 && height == 512) {
            patch = ""
            return@LaunchedEffect
        }
        val downloader = com.qihao.open.rwkv.model.ModelDownloader(context.applicationContext)
        val modelDir = runCatching { downloader.getRuntimeDirectory(currentModelInfo!!) }.getOrNull()
        if (modelDir == null) {
            patch = ""
            return@LaunchedEffect
        }
        // 按参照命名规则查找 patch 文件：<size>.patch 或 <width>x<height>.patch
        val patchFile = when {
            width == height -> {
                val squarePatch = java.io.File(modelDir, "${width}.patch")
                if (squarePatch.isFile) squarePatch else java.io.File(modelDir, "${width}x${height}.patch")
            }
            else -> java.io.File(modelDir, "${width}x${height}.patch")
        }
        patch = if (patchFile.isFile) patchFile.absolutePath else ""
        Log.d(TAG, "NPU patch 检测: size=${width}x${height}, found=${patch.ifBlank { "无" }}")
    }
    LaunchedEffect(
        modelId, prefsLoaded, promptText, negativePromptText, steps, cfg, seedText,
        width, height, scheduler, aspectRatio, denoiseStrength, batchCount,
        showDiffusionProcess, previewStride, outputFormat,
        ultrafixSteps, ultrafixDenoiseSteps, ultrafixQualityDenoise,
        lowram, seqDit, patch
    ) {
        if (!prefsLoaded || modelId.isBlank()) return@LaunchedEffect  // 加载完成前不保存，避免默认值覆盖存档
        kotlinx.coroutines.delay(500)                                 // 防抖：停止编辑 500ms 后落盘
        generationPrefs.save(
            modelId,
            ImageGenerationParams(
                prompt = promptText,
                negativePrompt = negativePromptText,
                steps = steps,
                cfg = cfg,
                seedText = seedText,
                width = width,
                height = height,
                scheduler = scheduler,
                aspectRatio = aspectRatio,
                denoiseStrength = denoiseStrength,
                batchCount = batchCount,
                showDiffusionProcess = showDiffusionProcess,
                previewStride = previewStride,
                outputFormatWire = outputFormat.wireValue,
                ultrafixSteps = ultrafixSteps,
                ultrafixDenoiseSteps = ultrafixDenoiseSteps,
                ultrafixQualityDenoise = ultrafixQualityDenoise,
                lowram = lowram,
                seqDit = seqDit,
                patch = patch
            )
        )
    }
    var showMaskEditor by remember { mutableStateOf(false) }   // 是否展示 mask 涂抹 overlay
    var showUltrafixConfirmDialog by remember { mutableStateOf(false) } // UltraFix 确认对话框：结果页点击 Ultrafix 后弹出
    // 相册选图后先落到裁剪态：持有待裁剪源图与显示名；为空表示不在裁剪流中
    var pendingCrop by remember { mutableStateOf<PendingCropSource?>(null) }
    var pendingCropMode by remember { mutableStateOf(LocalDreamGenerationMode.IMAGE_TO_IMAGE) } // 裁剪确认后进入的生成模式
    // 裁剪上下文：裁剪导入成功后记录"裁剪前完整原图 + 原图坐标系裁剪矩形"，
    // 供 inpaint 结果保存时羽化贴回原图（InpaintBlendUtils）；输入图一经其它路径变更即失效
    var lastCropImport by remember { mutableStateOf<CroppedImportContext?>(null) }
    val maskController = rememberMaskDrawingController()       // 承载蒙版笔画状态与编码能力
    // 把已导入输入图的 base64 解码为 Bitmap，作为涂抹画布背景源图
    val maskSourceBitmap = remember(importedImage) {
        importedImage?.let { image ->
            runCatching {
                val bytes = Base64.decode(image.base64, Base64.NO_WRAP)   // 与导入编码对称
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
    // 把已导入蒙版的 base64 解码为 Bitmap：进入涂抹 overlay 时作为半透明底版再编辑（参照 existingMaskBitmap）
    val existingMaskBitmap = remember(importedMask) {
        importedMask?.let { mask -> decodeMaskBase64ToBitmap(mask.base64) }
    }
    var showParamsDialog by remember { mutableStateOf(false) }
    var lastRequest by remember { mutableStateOf<LocalDreamImageRequest?>(null) }
    // 生成完成自动跳转结果页：只响应进入本屏之后新产生的 outputPath，避免初次进入被旧结果劫持
    val initialOutputPath = remember { state.outputPath }
    LaunchedEffect(state.outputPath) {
        if (state.outputPath.isNotBlank() && state.outputPath != initialOutputPath) {
            selectedRunTab = LocalDreamRunTab.RESULT
        }
    }
    // Token 计数：后端就绪时走真实 /tokenize（CLIP 分词），否则本地词数估算兜底
    // P2: 将 tokenize 结果回填到 PromptFieldController，驱动 overflow 灰度提示
    var promptTokenEstimate by remember { mutableStateOf(estimatePromptTokens(promptText)) }
    var negativeTokenEstimate by remember { mutableStateOf(estimatePromptTokens(negativePromptText)) }
    LaunchedEffect(promptText, state.backendReady) {
        if (!state.backendReady) {
            promptTokenEstimate = estimatePromptTokens(promptText)   // 后端未启动：保持估算
            promptTagController.tokenCount = estimatePromptTokens(promptText) // 桩值：本地估算
            promptTagController.tokenMax = 77                        // SD1.5 CLIP 上限
            promptTagController.overflowOffset = -1                  // 无后端时不触发溢出
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)                                // 防抖：停止输入 300ms 后再请求
        val result = runCatching {
            ImageTokenizeClient.tokenize(promptText)
        }.getOrNull()
        if (result != null) {
            promptTokenEstimate = result.count
            promptTagController.tokenCount = result.count            // P2: 真实 token 数
            promptTagController.tokenMax = result.maxLength          // P2: 模型上限
            promptTagController.overflowOffset = result.overflowOffset // P2: 溢出偏移（-1=未溢出）
        } else {
            promptTokenEstimate = estimatePromptTokens(promptText)   // 失败降级估算，不打断输入
            promptTagController.tokenCount = estimatePromptTokens(promptText)
            promptTagController.tokenMax = 77
            promptTagController.overflowOffset = -1
        }
    }
    LaunchedEffect(negativePromptText, state.backendReady) {
        if (!state.backendReady) {
            negativeTokenEstimate = estimatePromptTokens(negativePromptText)
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        negativeTokenEstimate = runCatching {
            ImageTokenizeClient.tokenize(negativePromptText).count
        }.getOrDefault(estimatePromptTokens(negativePromptText))
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // 选中相册图片后先进入全屏裁剪，确认裁剪结果才正式导入
        if (uri == null) return@rememberLauncherForActivityResult          // 用户取消选择：不做任何处理
        Log.d(TAG, "相册选图完成，进入裁剪流: $uri")
        val bitmap = decodePickedImageForCrop(context, uri)                // IO 读取并降采样解码，防止大图 OOM
        if (bitmap == null) {
            Toast.makeText(context, "没有读取到图片", Toast.LENGTH_SHORT).show()
        } else {
            pendingCropMode = LocalDreamGenerationMode.IMAGE_TO_IMAGE      // 普通导入确认后进入图生图
            pendingCrop = PendingCropSource(bitmap, localDreamLabelFromUri(uri))
        }
    }
    val ultrafixPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // UltraFix 导入复用同一裁剪流，仅确认后的目标生成模式不同
        if (uri == null) return@rememberLauncherForActivityResult          // 用户取消选择：不做任何处理
        Log.d(TAG, "UltraFix 选图完成，进入裁剪流: $uri")
        val bitmap = decodePickedImageForCrop(context, uri)                // 与普通导入同一解码路径
        if (bitmap == null) {
            Toast.makeText(context, "没有读取到 UltraFix 图片", Toast.LENGTH_SHORT).show()
        } else {
            pendingCropMode = LocalDreamGenerationMode.ULTRAFIX            // UltraFix 导入确认后进入修复模式
            pendingCrop = PendingCropSource(bitmap, localDreamLabelFromUri(uri))
        }
    }

    fun currentRequest(): LocalDreamImageRequest {
        // C3：UltraFix 的语义是对输入图原尺寸做 tiled 修复，width/height 必须用导入图
        // 实际尺寸（参照 ModelRunScreen startUltrafix 用 bmp.width/bmp.height）；
        // 无输入图时退回页面值，由 Service 尺寸门禁拦截并给出明确报错。
        val isUltrafix = generationMode == LocalDreamGenerationMode.ULTRAFIX
        val requestWidth = if (isUltrafix) importedImage?.width?.takeIf { it > 0 } ?: width else width
        val requestHeight = if (isUltrafix) importedImage?.height?.takeIf { it > 0 } ?: height else height
        val parsedSeed = seedText.toLongOrNull()?.coerceIn(0L, Int.MAX_VALUE.toLong())
        return LocalDreamImageRequest(
            prompt = promptText,
            negativePrompt = negativePromptText,
            steps = steps,
            cfg = cfg,
            seed = parsedSeed ?: (System.currentTimeMillis() % Int.MAX_VALUE).toLong(),
            width = requestWidth,
            height = requestHeight,
            scheduler = scheduler,
            mode = generationMode,
            denoiseStrength = denoiseStrength,
            aspectRatio = aspectRatio,
            showDiffusionProcess = showDiffusionProcess,
            showDiffusionStride = previewStride,
            previewFormat = LocalDreamImageWireFormat.JPEG,
            outputFormat = outputFormat,
            imageBase64 = if (generationMode == LocalDreamGenerationMode.TEXT_TO_IMAGE) "" else importedImage?.base64.orEmpty(),
            maskBase64 = if (generationMode == LocalDreamGenerationMode.INPAINT) importedMask?.base64.orEmpty() else "",
            batchCount = batchCount,
            seedFixed = parsedSeed != null,                          // 批量闭环：固定 seed 时只跑 1 张
            // UltraFix tile 取输入图最长边（与参照一致）；其余模式沿用页面尺寸最长边
            ultrafixTileSize = maxOf(requestWidth, requestHeight),
            ultrafixSteps = ultrafixSteps,
            ultrafixDenoiseSteps = ultrafixDenoiseSteps,
            ultrafixQualityDenoise = ultrafixQualityDenoise,
            lowram = lowram,
            seqDit = seqDit,
            patch = patch
        )
    }

    fun submitGeneration() {
        val request = currentRequest()
        lastRequest = request
        // inpaint 贴回上下文：仅当「裁剪导入的输入图 + 蒙版」齐备时写入，其余情况传 null 清除旧值，
        // 保证结果页保存时只对本次 inpaint 结果贴回，不会误贴 txt2img/img2img/UltraFix 结果。
        // 上下文只存进程内临时态（ViewModel），不落盘：进程重启后保存自动回退为保存原始结果图。
        val cropContext = lastCropImport
        val mask = importedMask                                       // 委托属性先落局部变量，供智能转换
        val blendContext = if (
            generationMode == LocalDreamGenerationMode.INPAINT &&
            cropContext != null &&
            importedImage != null &&
            mask != null
        ) {
            InpaintBlendContext(
                originalBitmap = cropContext.originalBitmap,
                cropRect = cropContext.cropRect,
                maskBitmap = decodeMaskBase64ToBitmap(mask.base64),   // 解码失败为 null：贴回退化为整块粘贴
            )
        } else {
            null
        }
        onSetInpaintBlendContext(blendContext)
        onGenerate(request)
    }

    /**
     * 把当前结果图喂回输入图状态，并切到目标生成模式 + 提示词页。
     * 对齐参照 ModelRunScreen.startUltrafix 对当前 bitmap 的处理：解码当前图 →
     * JPEG 字节 base64（质量 95 与参照 bitmapToBase64Jpeg 一致）→ 宽高取图片实际尺寸。
     *
     * @return true 表示喂图成功；false 表示无结果图或解码失败，调用方保留原行为
     */
    fun feedOutputImageTo(targetMode: LocalDreamGenerationMode): Boolean {
        val outputPath = state.outputPath
        if (outputPath.isBlank()) return false                  // 无结果图：调用方走原有跳页行为
        val bitmap = runCatching { BitmapFactory.decodeFile(outputPath) }.getOrNull() ?: return false
        val jpegBytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, jpegBytes)  // 与参照编码口径一致
        importedImage = LocalDreamImportedImage(
            base64 = Base64.encodeToString(jpegBytes.toByteArray(), Base64.NO_WRAP),
            label = "当前结果图",
            width = bitmap.width,                               // UltraFix 需要按输入图原尺寸出请求
            height = bitmap.height
        )
        importedMask = null                                     // 输入图变更，旧蒙版失效
        lastCropImport = null                                   // 输入图不再是裁剪导入，贴回上下文失效
        generationMode = targetMode
        selectedRunTab = LocalDreamRunTab.PROMPT                // 跳提示词页，用户可直接开始生成
        Log.d(TAG, "结果图已喂入 ${targetMode.wireValue}: ${bitmap.width}x${bitmap.height}")
        return true
    }

    // 外层 Box 承载蒙版涂抹 overlay，overlay 需要覆盖整个生图页（含顶栏）
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(modelName) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    TextButton(
                        onClick = onSwitchModel,
                        modifier = Modifier.testTag("switch-image-model-button")
                    ) { Text("切换") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LocalDreamRunTabs(
                selectedTab = selectedRunTab,
                onTabSelected = { selectedRunTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (selectedRunTab) {
                LocalDreamRunTab.PROMPT -> LocalDreamPromptPage(
                    promptText = promptText,
                    onPromptChange = { promptText = it },
                    negativePromptText = negativePromptText,
                    onNegativePromptChange = { negativePromptText = it },
                    promptTokenEstimate = promptTokenEstimate,
                    negativeTokenEstimate = negativeTokenEstimate,
                    generationMode = generationMode,
                    onGenerationModeChange = { generationMode = it },
                    batchCount = batchCount,
                    importedImage = importedImage,
                    importedMask = importedMask,
                    onSelectImage = { imagePickerLauncher.launch("image/*") },
                    onImportMask = {
                        if (importedImage == null) {
                            Toast.makeText(context, "请先导入输入图，再涂抹蒙版", Toast.LENGTH_SHORT).show()
                        } else {
                            generationMode = LocalDreamGenerationMode.INPAINT   // 涂抹蒙版只服务局部重绘
                            maskController.clear()                              // 每次进入清空上次笔画
                            // 蒙版再编辑：已有蒙版（手涂/全图/历史）转半透明底版显示并参与编码，
                            // 支持"导入蒙版后再涂抹"与"全图蒙版局部擦除"（对齐参照 existingMaskBitmap）
                            maskController.setExistingMask(existingMaskBitmap)
                            showMaskEditor = true
                        }
                    },
                    onUseFullMask = {
                        val sourceImage = importedImage
                        if (sourceImage == null) {
                            Toast.makeText(context, "请先选择一张输入图", Toast.LENGTH_SHORT).show()
                        } else {
                            importedMask = createFullLocalDreamMask(sourceImage)
                            generationMode = LocalDreamGenerationMode.INPAINT
                            Toast.makeText(context, "已生成全图重绘蒙版", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onImportUltrafix = { ultrafixPickerLauncher.launch("image/*") },
                    onClearInputImage = {
                        importedImage = null
                        importedMask = null
                        lastCropImport = null                                   // 输入图清空，贴回上下文同步失效
                        if (generationMode != LocalDreamGenerationMode.TEXT_TO_IMAGE) {
                            generationMode = LocalDreamGenerationMode.TEXT_TO_IMAGE
                        }
                    },
                    state = state,
                    onGenerate = { submitGeneration() },
                    // 停止按钮不直接中断：先弹"停止本次生成？"确认，防长任务误触丢失
                    onStop = { showStopConfirmDialog = true },
                    onShowParams = { showParamsDialog = true },
                    onShowAdvancedSettings = { showAdvancedSettings = true },
                    promptTagController = promptTagController,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("localdream-prompt-page")
                )
                LocalDreamRunTab.RESULT -> LocalDreamResultPage(
                    state = state,
                    lastRequest = lastRequest,
                    inpaintBlendContext = inpaintBlendContext,  // 保存 inpaint 结果时羽化贴回原图
                    history = history,                           // P2: 生图历史，供底部缩略图条
                    imageBackendType = currentModelInfo?.imageBackendType.orEmpty(), // NPU 模型才显示 Ultrafix 按钮
                    onGoToPrompt = { selectedRunTab = LocalDreamRunTab.PROMPT },
                    onRetry = { submitGeneration() },
                    onCopyParams = { copyLocalDreamParamsToClipboard(context, lastRequest ?: currentRequest()) },
                    onShowParams = { showParamsDialog = true },
                    onUpscale = onUpscale,
                    onUltrafix = {
                        // 结果页点击 Ultrafix：先弹出确认对话框展示 UltraFix 参数，确认后再喂图生成
                        if (state.outputPath.isNotBlank()) {
                            showUltrafixConfirmDialog = true
                        }
                    },
                    onSendToImg2Img = {
                        // 结果图一键转图生图输入：喂图失败兜底为仅切模式跳页
                        if (!feedOutputImageTo(LocalDreamGenerationMode.IMAGE_TO_IMAGE)) {
                            generationMode = LocalDreamGenerationMode.IMAGE_TO_IMAGE
                            selectedRunTab = LocalDreamRunTab.PROMPT
                        }
                    },
                    onSendToInpaint = {
                        // 结果图一键转局部重绘输入：喂图失败兜底为仅切模式跳页
                        if (!feedOutputImageTo(LocalDreamGenerationMode.INPAINT)) {
                            generationMode = LocalDreamGenerationMode.INPAINT
                            selectedRunTab = LocalDreamRunTab.PROMPT
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("localdream-result-page")
                )
                LocalDreamRunTab.HISTORY -> LocalDreamHistoryPage(
                    history = history,
                    onApplyParams = { request ->
            promptText = request.prompt
            negativePromptText = request.negativePrompt
            generationMode = request.mode
            importedImage = request.imageBase64.takeIf { it.isNotBlank() }?.let {
                LocalDreamImportedImage(it, "历史输入图", request.width, request.height)
            }
            // 历史复用的输入图没有本机裁剪上下文，贴回链路不可用，显式失效
            lastCropImport = null
            importedMask = request.maskBase64.takeIf { it.isNotBlank() }?.let {
                LocalDreamImportedImage(it, "历史蒙版", request.width, request.height)
            }
            steps = request.steps
                        cfg = request.cfg
                        seedText = request.seed.toString()
                        width = request.width
                        height = request.height
                        scheduler = request.scheduler
                        aspectRatio = request.aspectRatio
                        denoiseStrength = request.denoiseStrength
                        showDiffusionProcess = request.showDiffusionProcess
                        previewStride = request.showDiffusionStride
                        outputFormat = request.outputFormat
                        ultrafixSteps = request.ultrafixSteps
                        ultrafixDenoiseSteps = request.ultrafixDenoiseSteps
                        ultrafixQualityDenoise = request.ultrafixQualityDenoise
                        lowram = request.lowram
                        seqDit = request.seqDit
                        patch = request.patch
                        selectedRunTab = LocalDreamRunTab.PROMPT
                    },
                    onCopyParams = { request -> copyLocalDreamParamsToClipboard(context, request) },
                    onDeleteHistory = onDeleteHistory,
                    onClearHistory = onClearHistory,
                    onSetFavorite = onSetFavorite,
                    onReproduce = { request -> onGenerate(request) },
                    modelId = modelId,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("localdream-history-page")
                )
            }
        }
    }

    // 蒙版涂抹 overlay：覆盖整个生图页，在输入图上涂抹，确认后按请求宽高编码 PNG mask
    if (showMaskEditor && maskSourceBitmap != null) {
        // 编辑器工具状态：画笔粗细（5-50dp，对齐参照滑条范围）与当前工具（画笔/橡皮）
        var brushSizeDp by remember { mutableFloatStateOf(MASK_BRUSH_DEFAULT_DP) }
        var toolMode by remember { mutableStateOf(MaskToolMode.PEN) }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            Column(modifier = Modifier.fillMaxSize()) {
                MaskDrawingCanvas(
                    sourceBitmap = maskSourceBitmap,
                    controller = maskController,
                    brushSizeDp = brushSizeDp.dp,               // 粗细滑条实时驱动画笔/橡皮尺寸
                    toolMode = toolMode,                        // 橡皮=按笔画擦除（controller 记录 erase 标志）
                    showClearButton = false,                    // 清除入口收敛到底部工具栏，避免重复按钮
                    modifier = Modifier.weight(1f)
                )
                // 底部工具栏：工具切换 + 粗细滑条（实时圆点指示）+ 撤销/重做/清除 + 取消/确认
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "涂抹要重绘的区域（白色）；橡皮可擦除已涂部分",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 一行排 5 个按钮：统一紧凑内边距，避免默认 16dp 内边距把末尾"清除"挤出可视宽度
                            val toolButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            // 工具切换：选中态用填充按钮，未选中用描边按钮（对齐参照 ToggleButton 语义）
                            if (toolMode == MaskToolMode.PEN) {
                                Button(
                                    onClick = {},
                                    contentPadding = toolButtonPadding,
                                    modifier = Modifier.testTag("mask-tool-pen")
                                ) { Text("画笔") }
                            } else {
                                OutlinedButton(
                                    onClick = { toolMode = MaskToolMode.PEN },
                                    contentPadding = toolButtonPadding,
                                    modifier = Modifier.testTag("mask-tool-pen")
                                ) { Text("画笔") }
                            }
                            if (toolMode == MaskToolMode.ERASER) {
                                Button(
                                    onClick = {},
                                    contentPadding = toolButtonPadding,
                                    modifier = Modifier.testTag("mask-tool-eraser")
                                ) { Text("橡皮") }
                            } else {
                                OutlinedButton(
                                    onClick = { toolMode = MaskToolMode.ERASER },
                                    contentPadding = toolButtonPadding,
                                    modifier = Modifier.testTag("mask-tool-eraser")
                                ) { Text("橡皮") }
                            }
                            OutlinedButton(
                                onClick = { maskController.undo() },
                                enabled = maskController.canUndo,
                                contentPadding = toolButtonPadding,
                                modifier = Modifier.testTag("mask-undo-button")
                            ) { Text("撤销") }
                            OutlinedButton(
                                onClick = { maskController.redo() },
                                enabled = maskController.canRedo,
                                contentPadding = toolButtonPadding,
                                modifier = Modifier.testTag("mask-redo-button")
                            ) { Text("重做") }
                            OutlinedButton(
                                onClick = { maskController.clear() },
                                enabled = maskController.hasStrokes,
                                contentPadding = toolButtonPadding,
                                modifier = Modifier.testTag("mask-clear-button")
                            ) { Text("清除") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 粗细滑条 5-50dp，对齐参照 valueRange
                            Slider(
                                value = brushSizeDp,
                                onValueChange = { brushSizeDp = it },
                                valueRange = MASK_BRUSH_MIN_DP..MASK_BRUSH_MAX_DP,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mask-brush-size-slider")
                            )
                            // 实时圆点指示：尺寸随滑条变化，橡皮用灰色描边圆环区分
                            Box(
                                modifier = Modifier
                                    .width(50.dp)
                                    .aspectRatio(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(brushSizeDp.dp.coerceAtMost(50.dp))
                                        .clip(CircleShape)
                                        .background(
                                            if (toolMode == MaskToolMode.PEN) {
                                                Color.White
                                            } else {
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            OutlinedButton(
                                onClick = { showMaskEditor = false },
                                modifier = Modifier.testTag("mask-cancel-button")
                            ) { Text("取消") }
                            Button(
                                onClick = {
                                    // 按请求宽高编码 mask（含已有蒙版底版+本次笔画）：
                                    // backend 在 width/8 的 latent 尺寸解码，尺寸必须与请求一致
                                    importedMask = LocalDreamImportedImage(
                                        base64 = maskController.encodeMaskToBase64(width, height),
                                        label = "手涂蒙版",
                                        width = width,
                                        height = height
                                    )
                                    showMaskEditor = false
                                    Toast.makeText(context, "蒙版已设置", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("mask-confirm-button")
                            ) { Text("确认蒙版") }
                        }
                    }
                }
            }
        }
    }

    // 裁剪 overlay：相册选图后全屏裁剪，确认用裁剪结果导入，取消则放弃本次导入
    val cropSource = pendingCrop
    if (cropSource != null) {
        CropImageScreen(
            bitmap = cropSource.bitmap,
            onCropReady = { cropped, cropRect ->
                pendingCrop = null                                         // 先退出裁剪态，再执行导入
                val nextImage = importCroppedLocalDreamImage(context, cropped, cropSource.label)
                if (nextImage == null) {
                    Toast.makeText(context, "没有读取到图片", Toast.LENGTH_SHORT).show()
                } else {
                    importedImage = nextImage                              // 裁剪结果作为新的输入图
                    importedMask = null                                    // 输入图变更，旧蒙版失效
                    // 记录裁剪上下文（裁剪前完整原图 + 原图坐标系裁剪矩形）：
                    // inpaint 结果保存时按该矩形羽化贴回原图，产出"修改后的原图"
                    lastCropImport = CroppedImportContext(cropSource.bitmap, cropRect)
                    generationMode = pendingCropMode                       // 按来源入口进入对应生成模式
                    Log.d(TAG, "裁剪导入完成: ${nextImage.width}x${nextImage.height} -> ${pendingCropMode.wireValue}, cropRect=$cropRect")
                    val toastPrefix = if (pendingCropMode == LocalDreamGenerationMode.ULTRAFIX) {
                        "已导入 UltraFix 图片："
                    } else {
                        "已导入图片："
                    }
                    Toast.makeText(context, "$toastPrefix${nextImage.label}", Toast.LENGTH_SHORT).show()
                }
            },
            onCancel = {
                Log.d(TAG, "取消裁剪，放弃本次导入")
                pendingCrop = null                                         // 取消裁剪 = 放弃本次导入
            },
            modifier = Modifier.fillMaxSize()
        )
    }
    }

    if (showParamsDialog) {
        LocalDreamParamsDialog(
            request = currentRequest(),
            runtimeText = imageInferenceBackend.backend.displayName,
            onDismiss = { showParamsDialog = false },
            onCopy = { copyLocalDreamParamsToClipboard(context, currentRequest()) }
        )
    }

    // 高级设置弹窗：对齐 local-dream AdvancedSettingsDialog，参数控件从 prompt 页移入
    if (showAdvancedSettings) {
        AdvancedSettingsDialog(
            steps = steps,
            onStepsChange = { steps = it },
            cfg = cfg,
            onCfgChange = { cfg = it },
            seedText = seedText,
            onSeedChange = { seedText = it },
            width = width,
            height = height,
            onSizeChange = { w, h -> width = w; height = h },
            scheduler = scheduler,
            onSchedulerChange = { scheduler = it },
            denoiseStrength = denoiseStrength,
            onDenoiseStrengthChange = { denoiseStrength = it },
            showDenoise = generationMode != LocalDreamGenerationMode.TEXT_TO_IMAGE,
            batchCount = batchCount,
            onBatchCountChange = { batchCount = it },
            showDiffusionProcess = showDiffusionProcess,
            onShowDiffusionProcessChange = { showDiffusionProcess = it },
            previewStride = previewStride,
            onPreviewStrideChange = { previewStride = it },
            outputFormat = outputFormat,
            onOutputFormatChange = { outputFormat = it },
            ultrafixSteps = ultrafixSteps,
            onUltrafixStepsChange = { nextSteps ->
                ultrafixSteps = nextSteps
                ultrafixDenoiseSteps = ultrafixDenoiseSteps.coerceAtMost(nextSteps)
            },
            ultrafixDenoiseSteps = ultrafixDenoiseSteps,
            onUltrafixDenoiseStepsChange = { ultrafixDenoiseSteps = it },
            ultrafixQualityDenoise = ultrafixQualityDenoise,
            onUltrafixQualityDenoiseChange = { ultrafixQualityDenoise = it },
            lowram = lowram,
            onLowramChange = { lowram = it },
            seqDit = seqDit,
            onSeqDitChange = { seqDit = it },
            patch = patch,
            imageBackendType = currentModelInfo?.imageBackendType.orEmpty(),
            lastReturnedSeed = lastReturnedSeed,
            onUseLastSeed = { seedText = lastReturnedSeed.toString() },
            onImportFromClipboard = {
                // 复用现有剪贴板检测逻辑，弹窗让用户勾选字段
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: ""
                    val params = parseLocalDreamParamsFromClipboard(text)
                    if (params.isNotEmpty()) {
                        clipboardParams = params
                        importSelectedFields = params.keys.toSet()
                        importParamsDialogVisible = true
                        Log.d(TAG, "高级设置导入剪贴板参数: ${params.size} 字段")
                    } else {
                        Toast.makeText(context, "剪贴板未检测到共享参数", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                // 分享当前参数为文本：对齐 local-dream onShare
                val text = buildString {
                    appendLine("prompt=${promptText}")
                    appendLine("negative_prompt=${negativePromptText}")
                    appendLine("mode=${generationMode.wireValue}")
                    appendLine("steps=${steps}")
                    appendLine("cfg=${cfg}")
                    appendLine("seed=${seedText.ifBlank { "0" }}")
                    appendLine("size=${width}x${height}")
                    appendLine("scheduler=${scheduler}")
                    appendLine("aspect_ratio=${aspectRatio}")
                    appendLine("denoise_strength=${denoiseStrength}")
                    appendLine("show_diffusion_process=${showDiffusionProcess}")
                    appendLine("show_diffusion_stride=${previewStride}")
                    appendLine("output_format=${outputFormat.wireValue}")
                    appendLine("batch_count=${batchCount}")
                    appendLine("ultrafix_steps=${ultrafixSteps}")
                    appendLine("ultrafix_denoise_steps=${ultrafixDenoiseSteps}")
                    appendLine("ultrafix_quality_denoise=${ultrafixQualityDenoise}")
                    if (lowram) appendLine("lowram=${lowram}")
                    if (seqDit) appendLine("seq_dit=${seqDit}")
                    if (patch.isNotBlank()) appendLine("patch=${patch}")
                }
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, "分享生成参数"))
                Log.d(TAG, "已分享生图参数: ${text.lines().size} 字段")
            },
            onReset = { showResetConfirmDialog = true },
            onDismiss = { showAdvancedSettings = false }
        )
    }

    // 停止生成确认弹窗：停止按钮与生成中返回键共用，确认后才真正中断后端任务
    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = { Text("停止生成") },
            text = { Text("停止本次生成？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirmDialog = false
                        onStop()                                 // 确认停止：调用 ViewModel 中断生成
                    },
                    modifier = Modifier.testTag("stop-generation-confirm-button")
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStopConfirmDialog = false }, // 继续生成：仅关闭弹窗
                    modifier = Modifier.testTag("stop-generation-cancel-button")
                ) { Text("继续生成") }
            },
            modifier = Modifier.testTag("stop-generation-confirm-dialog")
        )
    }
    // UltraFix 确认对话框：结果页点击 Ultrafix 后弹出，展示当前 UltraFix 参数并确认
    if (showUltrafixConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUltrafixConfirmDialog = false },
            title = { Text("UltraFix 修复") },
            text = {
                Column {
                    Text("对当前结果图执行 tiled img2img 修复。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("修复步数: $ultrafixSteps")
                    Text("降噪步数: $ultrafixDenoiseSteps")
                    Text("质量提示词: ${if (ultrafixQualityDenoise) "开启" else "关闭"}")
                    Text("Tile 大小: ${maxOf(
                        importedImage?.width ?: width,
                        importedImage?.height ?: height
                    )}")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUltrafixConfirmDialog = false
                        // 喂图 + 切模式 + 生成：对齐参照 ModelRunScreen.startUltrafix
                        if (feedOutputImageTo(LocalDreamGenerationMode.ULTRAFIX)) {
                            submitGeneration()
                        } else {
                            // 无结果图/解码失败兜底：仅切模式跳页
                            generationMode = LocalDreamGenerationMode.ULTRAFIX
                            selectedRunTab = LocalDreamRunTab.PROMPT
                        }
                    },
                    modifier = Modifier.testTag("ultrafix-confirm-button")
                ) { Text("开始修复") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUltrafixConfirmDialog = false },
                    modifier = Modifier.testTag("ultrafix-cancel-button")
                ) { Text("取消") }
            },
            modifier = Modifier.testTag("ultrafix-confirm-dialog")
        )
    }
    // 重置参数确认弹窗：对齐参照 AdvancedSettingsDialog.onReset，弹确认后恢复全部参数
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("重置参数") },
            text = { Text("重置全部参数？此操作不可撤销") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        resetGenerationParamsToModelDefault()
                    },
                    modifier = Modifier.testTag("reset-params-confirm-button")
                ) { Text("确认重置") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false },
                    modifier = Modifier.testTag("reset-params-cancel-button")
                ) { Text("取消") }
            },
            modifier = Modifier.testTag("reset-params-confirm-dialog")
        )
    }
    // 导入参数（剪贴板）弹窗：检测到共享参数文本后弹出，用户勾选字段后应用到当前参数状态
    if (importParamsDialogVisible && clipboardParams != null) {
        val params = clipboardParams!!
        // 字段显示名映射：key -> 中文标签
        val fieldLabels = mapOf(
            "prompt" to "正向提示词",
            "negative_prompt" to "负向提示词",
            "mode" to "生成模式",
            "steps" to "采样步数",
            "cfg" to "CFG 强度",
            "seed" to "随机种子",
            "size" to "图片尺寸",
            "scheduler" to "调度器",
            "aspect_ratio" to "宽高比",
            "denoise_strength" to "降噪强度",
            "show_diffusion_process" to "显示扩散过程",
            "show_diffusion_stride" to "预览步长",
            "output_format" to "输出格式",
            "batch_count" to "批次数量",
            "ultrafix_tile_size" to "UltraFix Tile",
            "ultrafix_steps" to "UltraFix 步数",
            "ultrafix_denoise_steps" to "UltraFix 降噪步数",
            "ultrafix_quality_denoise" to "UltraFix 质量提示词"
        )
        AlertDialog(
            onDismissRequest = { importParamsDialogVisible = false },
            title = { Text("应用共享参数") },
            text = {
                Column {
                    Text(
                        "剪贴板检测到共享参数，选择要应用的字段：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(params.keys.toList().sorted()) { key ->
                            val label = fieldLabels[key] ?: key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        importSelectedFields = if (key in importSelectedFields) {
                                            importSelectedFields - key
                                        } else {
                                            importSelectedFields + key
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Switch(
                                    checked = key in importSelectedFields,
                                    onCheckedChange = { checked ->
                                        importSelectedFields = if (checked) {
                                            importSelectedFields + key
                                        } else {
                                            importSelectedFields - key
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$label: ${params[key]}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 应用选中的字段到 UI 参数状态
                        params.forEach { (key, value) ->
                            if (key !in importSelectedFields) return@forEach
                            when (key) {
                                "prompt" -> promptText = value
                                "negative_prompt" -> negativePromptText = value
                                "mode" -> generationMode = LocalDreamGenerationMode.entries
                                    .firstOrNull { it.wireValue == value }
                                    ?: LocalDreamGenerationMode.TEXT_TO_IMAGE
                                "steps" -> steps = value.toIntOrNull() ?: steps
                                "cfg" -> cfg = value.toFloatOrNull() ?: cfg
                                "seed" -> seedText = value
                                "size" -> {
                                    val parts = value.split("x")
                                    if (parts.size == 2) {
                                        width = parts[0].toIntOrNull() ?: width
                                        height = parts[1].toIntOrNull() ?: height
                                    }
                                }
                                "scheduler" -> scheduler = value
                                "aspect_ratio" -> aspectRatio = value
                                "denoise_strength" -> denoiseStrength = value.toFloatOrNull() ?: denoiseStrength
                                "show_diffusion_process" -> showDiffusionProcess = value.toBooleanStrictOrNull() ?: showDiffusionProcess
                                "show_diffusion_stride" -> previewStride = value.toIntOrNull() ?: previewStride
                                "output_format" -> outputFormat = LocalDreamImageWireFormat.entries
                                    .firstOrNull { it.wireValue == value }
                                    ?: LocalDreamImageWireFormat.JPEG
                                "batch_count" -> batchCount = value.toIntOrNull() ?: batchCount
                                "ultrafix_steps" -> ultrafixSteps = value.toIntOrNull() ?: ultrafixSteps
                                "ultrafix_denoise_steps" -> ultrafixDenoiseSteps = value.toIntOrNull() ?: ultrafixDenoiseSteps
                                "ultrafix_quality_denoise" -> ultrafixQualityDenoise = value.toBooleanStrictOrNull() ?: ultrafixQualityDenoise
                                "lowram" -> lowram = value.toBooleanStrictOrNull() ?: lowram
                                "seq_dit" -> seqDit = value.toBooleanStrictOrNull() ?: seqDit
                                "patch" -> patch = value
                            }
                        }
                        importParamsDialogVisible = false
                        Log.d(TAG, "已应用剪贴板共享参数: ${importSelectedFields.size} 字段")
                    },
                    modifier = Modifier.testTag("import-params-apply-button")
                ) { Text("应用选中") }
            },
            dismissButton = {
                TextButton(
                    onClick = { importParamsDialogVisible = false },
                    modifier = Modifier.testTag("import-params-cancel-button")
                ) { Text("取消") }
            },
            modifier = Modifier.testTag("import-params-dialog")
        )
    }
}

/** LocalDream 生图页三分区 */
private enum class LocalDreamRunTab(val title: String) {
    PROMPT("提示词"),
    RESULT("结果"),
    HISTORY("历史")
}

/** LocalDream 输入图或蒙版的内存态，避免 UI 按钮只是占位入口 */
private data class LocalDreamImportedImage(
    val base64: String, // 传给后端的图片 base64 内容
    val label: String,  // UI 展示名称
    val width: Int,     // 图片宽度
    val height: Int     // 图片高度
)

/** 待裁剪源图：相册降采样解码结果与导入显示名，裁剪确认后沿用该显示名 */
private data class PendingCropSource(
    val bitmap: Bitmap, // 供 CropImageScreen 展示与裁剪的源图
    val label: String   // 导入后的显示名，来自相册文件名
)

/**
 * 裁剪导入上下文：裁剪前的完整原图 + 原图坐标系的裁剪矩形。
 * inpaint 结果保存时用它把结果羽化贴回原图（InpaintBlendUtils），
 * 输入图一经其它路径（历史复用/结果喂图/清除）变更即置空失效。
 */
private data class CroppedImportContext(
    val originalBitmap: Bitmap,   // 裁剪前的完整原图（降采样解码结果，与裁剪输入同源）
    val cropRect: Rect            // 裁剪矩形（originalBitmap 像素坐标系）
)

/** LocalDream 生图页顶部标签 */
@Composable
private fun LocalDreamRunTabs(
    selectedTab: LocalDreamRunTab,
    onTabSelected: (LocalDreamRunTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("localdream-run-tabs"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LocalDreamRunTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            if (selected) {
                Button(
                    onClick = { onTabSelected(tab) },
                    shape = RoundedCornerShape(9999.dp),
                    modifier = Modifier.testTag("localdream-tab-${tab.name.lowercase()}")
                ) { Text(tab.title) }
            } else {
                OutlinedButton(
                    onClick = { onTabSelected(tab) },
                    shape = RoundedCornerShape(9999.dp),
                    modifier = Modifier.testTag("localdream-tab-${tab.name.lowercase()}")
                ) { Text(tab.title) }
            }
        }
    }
}

/** LocalDream 提示词与参数页 */
/**
 * 提示词页（简化版）：对齐 local-dream PromptPage，参数控件移入 AdvancedSettingsDialog
 * 仅保留：介绍 + banner + mode 选择 + prompt 输入 + negative 输入 + inputImage + 高级设置按钮 + 生成按钮区
 */
@Composable
private fun LocalDreamPromptPage(
    promptText: String,
    onPromptChange: (String) -> Unit,
    negativePromptText: String,
    onNegativePromptChange: (String) -> Unit,
    promptTokenEstimate: Int,
    negativeTokenEstimate: Int,
    generationMode: LocalDreamGenerationMode,
    onGenerationModeChange: (LocalDreamGenerationMode) -> Unit,
    batchCount: Int,                                                         // 生成按钮文案用
    importedImage: LocalDreamImportedImage?,
    importedMask: LocalDreamImportedImage?,
    onSelectImage: () -> Unit,
    onImportMask: () -> Unit,
    onUseFullMask: () -> Unit,
    onImportUltrafix: () -> Unit,
    onClearInputImage: () -> Unit,
    state: ImageGenerationUiState,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
    onShowParams: () -> Unit,
    onShowAdvancedSettings: () -> Unit,                                      // 打开高级设置弹窗
    promptTagController: PromptFieldController,                              // Tag 自动补全控制器
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "LocalDream 工作台：提示词、参数、img2img、inpaint、UltraFix、预览进度和历史复用都保留入口。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { ImageInferenceBackendBanner() }
        item {
            LocalDreamModeSelector(
                selectedMode = generationMode,
                onModeChange = onGenerationModeChange
            )
        }
        item {
            // 正向提示词：用 Tag 自动补全输入框替换原 OutlinedTextField
            // 双向绑定：controller.text 变更回写 promptText，外部 promptText 变更同步回 controller
            ControlledPromptTagTextField(
                controller = promptTagController,
                autocompleteAvailable = promptTagController.autocompleteAvailable,
                label = { Text("图片生成提示词 · 约 $promptTokenEstimate / 77 tokens") },
                modifier = Modifier.testTag("image-prompt-input")
            )
        }
        item {
            LocalDreamTextFieldCard(
                title = "负向提示词",
                value = negativePromptText,
                onValueChange = onNegativePromptChange,
                tokenEstimate = negativeTokenEstimate,
                placeholder = "例如：low quality, blurry, watermark",
                testTag = "image-negative-prompt-input"
            )
        }
        item {
            LocalDreamInputImageCard(
                mode = generationMode,
                importedImage = importedImage,
                importedMask = importedMask,
                onModeChange = onGenerationModeChange,
                onSelectImage = onSelectImage,
                onImportMask = onImportMask,
                onUseFullMask = onUseFullMask,
                onImportUltrafix = onImportUltrafix,
                onClearInputImage = onClearInputImage
            )
        }
        // 高级设置按钮：对齐 local-dream PromptPage :1778-1795
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onShowAdvancedSettings,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("localdream-advanced-settings-button")
                ) {
                    Text(
                        text = "高级设置",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "高级设置",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        item {
            LocalDreamProgressCard(
                state = state,
                onShowParams = onShowParams
            )
        }
        item {
            if (state.isGenerating) {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("stop-image-generation-button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("停止生成")
                }
            } else {
                Button(
                    onClick = onGenerate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate-image-button"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = promptText.isNotBlank()
                ) {
                    Text(if (batchCount > 1) "生成图片（$batchCount 批）" else "生成图片")
                }
            }
        }
    }
}

/** 生成模式选择条 */
@Composable
private fun LocalDreamModeSelector(
    selectedMode: LocalDreamGenerationMode,
    onModeChange: (LocalDreamGenerationMode) -> Unit
) {
    LocalDreamSectionCard(title = "生成模式") {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LocalDreamGenerationMode.entries.forEach { mode ->
                val label = when (mode) {
                    LocalDreamGenerationMode.TEXT_TO_IMAGE -> "文生图"
                    LocalDreamGenerationMode.IMAGE_TO_IMAGE -> "图生图"
                    LocalDreamGenerationMode.INPAINT -> "局部重绘"
                    LocalDreamGenerationMode.ULTRAFIX -> "UltraFix"
                }
                if (selectedMode == mode) {
                    Button(
                        onClick = { onModeChange(mode) },
                        shape = RoundedCornerShape(9999.dp),
                        modifier = Modifier.testTag("localdream-mode-${mode.wireValue}")
                    ) { Text(label) }
                } else {
                    OutlinedButton(
                        onClick = { onModeChange(mode) },
                        shape = RoundedCornerShape(9999.dp),
                        modifier = Modifier.testTag("localdream-mode-${mode.wireValue}")
                    ) { Text(label) }
                }
            }
        }
    }
}

/** 提示词输入卡 */
@Composable
private fun LocalDreamTextFieldCard(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    tokenEstimate: Int,
    placeholder: String,
    testTag: String
) {
    LocalDreamSectionCard(title = title, trailing = "约 $tokenEstimate / 77 tokens") {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            placeholder = { Text(placeholder) },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

/** img2img/inpaint/UltraFix 输入图入口卡 */
@Composable
private fun LocalDreamInputImageCard(
    mode: LocalDreamGenerationMode,
    importedImage: LocalDreamImportedImage?,
    importedMask: LocalDreamImportedImage?,
    onModeChange: (LocalDreamGenerationMode) -> Unit,
    onSelectImage: () -> Unit,
    onImportMask: () -> Unit,
    onUseFullMask: () -> Unit,
    onImportUltrafix: () -> Unit,
    onClearInputImage: () -> Unit
) {
    val needsImage = mode != LocalDreamGenerationMode.TEXT_TO_IMAGE
    LocalDreamSectionCard(title = "输入图像与蒙版") {
        Text(
            text = if (needsImage) {
                "当前模式需要输入图片；已支持从相册导入图片、导入蒙版或生成全图重绘蒙版。"
            } else {
                "文生图不需要输入图片。可切换到图生图、局部重绘或 UltraFix。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (importedImage != null) {
            Text(
                text = "输入图：${importedImage.label} · ${importedImage.width}×${importedImage.height}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("localdream-input-image-status")
            )
        }
        if (mode == LocalDreamGenerationMode.INPAINT) {
            Text(
                text = importedMask?.let { "蒙版：${it.label} · ${it.width}×${it.height}" } ?: "蒙版：未设置",
                style = MaterialTheme.typography.labelMedium,
                color = if (importedMask == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("localdream-mask-status")
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onModeChange(LocalDreamGenerationMode.IMAGE_TO_IMAGE)
                    onSelectImage()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("localdream-select-img2img")
            ) { Text("选择图片") }
            OutlinedButton(
                onClick = {
                    onModeChange(LocalDreamGenerationMode.INPAINT)
                    onImportMask()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("localdream-set-inpaint-mask")
            ) { Text("导入蒙版") }
            OutlinedButton(
                onClick = onUseFullMask,
                enabled = importedImage != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("localdream-use-full-mask")
            ) { Text("全图蒙版") }
            OutlinedButton(
                onClick = {
                    onModeChange(LocalDreamGenerationMode.ULTRAFIX)
                    onImportUltrafix()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("localdream-import-ultrafix")
            ) { Text("UltraFix 导入") }
            OutlinedButton(
                onClick = onClearInputImage,
                enabled = importedImage != null || importedMask != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("localdream-clear-input-image")
            ) { Text("清除") }
        }
    }
}

/** 核心采样参数卡 */
@Composable
private fun LocalDreamParameterCard(
    steps: Int,
    onStepsChange: (Int) -> Unit,
    cfg: Float,
    onCfgChange: (Float) -> Unit,
    seedText: String,
    onSeedChange: (String) -> Unit,
    width: Int,
    height: Int,
    onSizeChange: (Int, Int) -> Unit,
    scheduler: String,
    onSchedulerChange: (String) -> Unit,
    denoiseStrength: Float,
    onDenoiseStrengthChange: (Float) -> Unit,
    showDenoise: Boolean
) {
    LocalDreamSectionCard(title = "采样参数") {
        LocalDreamSliderRow(
            label = "步数: $steps",
            value = steps.toFloat(),
            range = 1f..60f,
            steps = 58,
            onValueChange = { onStepsChange(it.toInt().coerceIn(1, 60)) }
        )
        LocalDreamSliderRow(
            label = "CFG: ${"%.1f".format(cfg)}",
            value = cfg,
            // 上限对齐参照 AdvancedSettingsDialog 的 1f..30f（0.5 步进共 57 个中间档位）
            range = 1f..30f,
            steps = 57,
            onValueChange = { onCfgChange((it * 10).toInt() / 10f) }
        )
        if (showDenoise) {
            LocalDreamSliderRow(
                label = "降噪强度: ${"%.2f".format(denoiseStrength)}",
                value = denoiseStrength,
                range = 0.1f..1f,
                steps = 8,
                onValueChange = { onDenoiseStrengthChange((it * 100).toInt() / 100f) }
            )
        }
        OutlinedTextField(
            value = seedText,
            onValueChange = { onSeedChange(it.filter { char -> char.isDigit() }.take(18)) },
            label = { Text("随机种子（留空自动随机）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            // 尾部清除图标：有内容时显示，点击一键清空恢复随机
            trailingIcon = {
                if (seedText.isNotEmpty()) {
                    IconButton(
                        onClick = { onSeedChange("") },
                        modifier = Modifier.testTag("localdream-seed-clear")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "清除种子")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("localdream-seed-input"),
            shape = RoundedCornerShape(12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(512 to 512, 768 to 768, 1024 to 1024).forEach { size ->
                // CPU 阶段仅 512 可用：attention 内存随序列长度平方增长，768+ 会杀死后端进程；NPU 阶段解锁
                val enabled = size.first <= 512
                OutlinedButton(
                    onClick = { onSizeChange(size.first, size.second) },
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("${size.first}") }
            }
        }
        Text(
            text = "尺寸：${width}×${height}（CPU 阶段仅支持 512，更大尺寸随 NPU 阶段开放）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // M4：aspect_ratio 在 SD1.5 CPU 阶段不被 native 消费（固定画布裁切是 SDXL/Anima 管线能力），
        // 比例选择行先行隐藏避免误导；比例状态仍保留在页面并随参数持久化，SDXL 阶段恢复渲染即可。
        LocalDreamChoiceRow(
            title = "调度器",
            values = listOf(
                "dpm",
                "dpm_karras",
                "dpm_sde",
                "dpm_sde_karras",
                "euler_a",
                "euler_a_karras",
                "euler",
                "euler_karras",
                "lcm"
            ),
            selected = scheduler,
            onSelected = onSchedulerChange
        )
    }
}

/** 高级生成选项卡 */
@Composable
private fun LocalDreamAdvancedCard(
    batchCount: Int,
    onBatchCountChange: (Int) -> Unit,
    showDiffusionProcess: Boolean,
    onShowDiffusionProcessChange: (Boolean) -> Unit,
    previewStride: Int,
    onPreviewStrideChange: (Int) -> Unit,
    outputFormat: LocalDreamImageWireFormat,
    onOutputFormatChange: (LocalDreamImageWireFormat) -> Unit,
    ultrafixSteps: Int,
    onUltrafixStepsChange: (Int) -> Unit,
    ultrafixDenoiseSteps: Int,
    onUltrafixDenoiseStepsChange: (Int) -> Unit,
    ultrafixQualityDenoise: Boolean,
    onUltrafixQualityDenoiseChange: (Boolean) -> Unit,
    lowram: Boolean,                                                         // SDXL/Anima 低内存模式
    onLowramChange: (Boolean) -> Unit,                                       // 低内存模式切换回调
    seqDit: Boolean,                                                         // Anima 序列化 DiT
    onSeqDitChange: (Boolean) -> Unit,                                       // 序列化 DiT 切换回调
    patch: String,                                                           // 当前分辨率 patch 文件路径
    imageBackendType: String,                                                // 当前模型后端类型：用于条件显示 NPU 专属控件
    onReset: () -> Unit                                                       // 重置参数到模型默认值
) {
    LocalDreamSectionCard(title = "高级与预览") {
        LocalDreamSliderRow(
            label = "批次数量: $batchCount",
            value = batchCount.toFloat(),
            range = 1f..10f,
            steps = 8,
            onValueChange = { onBatchCountChange(it.toInt().coerceIn(1, 10)) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("显示扩散过程", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "开启后请求后端按步返回中间图。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = showDiffusionProcess,
                onCheckedChange = onShowDiffusionProcessChange,
                modifier = Modifier.testTag("localdream-preview-switch")
            )
        }
        LocalDreamSliderRow(
            label = "预览步长: $previewStride",
            value = previewStride.toFloat(),
            range = 1f..10f,
            steps = 8,
            onValueChange = { onPreviewStrideChange(it.toInt().coerceIn(1, 10)) }
        )
        LocalDreamChoiceRow(
            title = "输出格式",
            values = listOf("jpeg", "png"),
            selected = outputFormat.wireValue,
            onSelected = { wire ->
                onOutputFormatChange(
                    if (wire == "png") LocalDreamImageWireFormat.PNG else LocalDreamImageWireFormat.JPEG
                )
            }
        )
        LocalDreamSliderRow(
            label = "UltraFix 步数: $ultrafixSteps",
            value = ultrafixSteps.toFloat(),
            range = 1f..20f,
            steps = 18,
            onValueChange = { onUltrafixStepsChange(it.toInt().coerceIn(1, 20)) }
        )
        LocalDreamSliderRow(
            label = "UltraFix 降噪步数: $ultrafixDenoiseSteps",
            value = ultrafixDenoiseSteps.toFloat(),
            range = 1f..ultrafixSteps.toFloat().coerceAtLeast(1f),
            steps = (ultrafixSteps - 2).coerceAtLeast(0),
            onValueChange = { onUltrafixDenoiseStepsChange(it.toInt().coerceIn(1, ultrafixSteps)) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("UltraFix 质量提示词", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "开启后使用中性质量提示词进行 tile 修复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = ultrafixQualityDenoise,
                onCheckedChange = onUltrafixQualityDenoiseChange,
                modifier = Modifier.testTag("localdream-ultrafix-quality-switch")
            )
        }
        // ---- NPU 专属控件：仅 SDXL/Anima/sd15npu 后端显示 ----
        val isNpuBackend = imageBackendType in listOf("sdxl", "anima", "sd15npu")
        if (isNpuBackend) {
            // SDXL 低内存模式开关：仅 SDXL 模型显示
            if (imageBackendType == "sdxl") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SDXL 低内存模式", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "逐阶段加载释放模型，降低 peak 内存占用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lowram,
                        onCheckedChange = onLowramChange,
                        modifier = Modifier.testTag("localdream-lowram-switch")
                    )
                }
            }
            // Anima 低内存模式 + 序列化 DiT 开关：仅 Anima 模型显示
            if (imageBackendType == "anima") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Anima 低内存模式", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "逐阶段加载释放模型，降低 peak 内存占用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lowram,
                        onCheckedChange = onLowramChange,
                        modifier = Modifier.testTag("localdream-lowram-switch")
                    )
                }
                // 序列化 DiT：仅在 lowram 开启时显示（对齐参照 anima_seq_dit 仅在 anima_lowram 开启后可见）
                if (lowram) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("序列化 DiT", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "两张 DiT 分片不共存，12GB 设备可运行 Anima 低内存。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = seqDit,
                            onCheckedChange = onSeqDitChange,
                            modifier = Modifier.testTag("localdream-seq-dit-switch")
                        )
                    }
                }
            }
            // 分辨率 patch 状态：显示当前 patch 文件路径（如有）
            if (patch.isNotBlank()) {
                Text(
                    text = "分辨率 patch: ${java.io.File(patch).name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("localdream-patch-status")
                )
            }
        }
        // 重置参数按钮：弹确认后恢复全部参数到模型默认值，对齐参照 AdvancedSettingsDialog.onReset
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("localdream-reset-params-button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("重置参数")
        }
    }
}

/**
 * 高级设置弹窗：对齐 local-dream AdvancedSettingsDialog，包含所有参数控件
 * 复用现有 LocalDreamParameterCard 和 LocalDreamAdvancedCard 的控件逻辑
 * title 行含 import（剪贴板）/share/reset/使用上次种子 按钮
 */
@Composable
private fun AdvancedSettingsDialog(
    steps: Int,
    onStepsChange: (Int) -> Unit,
    cfg: Float,
    onCfgChange: (Float) -> Unit,
    seedText: String,
    onSeedChange: (String) -> Unit,
    width: Int,
    height: Int,
    onSizeChange: (Int, Int) -> Unit,
    scheduler: String,
    onSchedulerChange: (String) -> Unit,
    denoiseStrength: Float,
    onDenoiseStrengthChange: (Float) -> Unit,
    showDenoise: Boolean,
    batchCount: Int,
    onBatchCountChange: (Int) -> Unit,
    showDiffusionProcess: Boolean,
    onShowDiffusionProcessChange: (Boolean) -> Unit,
    previewStride: Int,
    onPreviewStrideChange: (Int) -> Unit,
    outputFormat: LocalDreamImageWireFormat,
    onOutputFormatChange: (LocalDreamImageWireFormat) -> Unit,
    ultrafixSteps: Int,
    onUltrafixStepsChange: (Int) -> Unit,
    ultrafixDenoiseSteps: Int,
    onUltrafixDenoiseStepsChange: (Int) -> Unit,
    ultrafixQualityDenoise: Boolean,
    onUltrafixQualityDenoiseChange: (Boolean) -> Unit,
    lowram: Boolean,
    onLowramChange: (Boolean) -> Unit,
    seqDit: Boolean,
    onSeqDitChange: (Boolean) -> Unit,
    patch: String,
    imageBackendType: String,
    lastReturnedSeed: Long,
    onUseLastSeed: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onShare: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "高级设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // 导入参数（剪贴板）：对齐 local-dream :100-105；material-icons-core 不含 ContentPaste，用 TextButton 替代
                TextButton(
                    onClick = onImportFromClipboard,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("advanced-settings-import-button")
                ) {
                    Text("导入", style = MaterialTheme.typography.labelMedium)
                }
                // 分享参数：对齐 local-dream :106-111
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.testTag("advanced-settings-share-button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "分享参数"
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                // 使用上次种子按钮：对齐 local-dream :425-443
                if (lastReturnedSeed > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "上次种子: $lastReturnedSeed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onUseLastSeed,
                            modifier = Modifier.testTag("advanced-settings-use-last-seed")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "使用上次种子",
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp)
                            )
                            Text("使用上次种子")
                        }
                    }
                }
                // 采样参数卡：复用现有控件逻辑
                LocalDreamParameterCard(
                    steps = steps,
                    onStepsChange = onStepsChange,
                    cfg = cfg,
                    onCfgChange = onCfgChange,
                    seedText = seedText,
                    onSeedChange = onSeedChange,
                    width = width,
                    height = height,
                    onSizeChange = onSizeChange,
                    scheduler = scheduler,
                    onSchedulerChange = onSchedulerChange,
                    denoiseStrength = denoiseStrength,
                    onDenoiseStrengthChange = onDenoiseStrengthChange,
                    showDenoise = showDenoise
                )
                // 高级与预览卡：复用现有控件逻辑
                LocalDreamAdvancedCard(
                    batchCount = batchCount,
                    onBatchCountChange = onBatchCountChange,
                    showDiffusionProcess = showDiffusionProcess,
                    onShowDiffusionProcessChange = onShowDiffusionProcessChange,
                    previewStride = previewStride,
                    onPreviewStrideChange = onPreviewStrideChange,
                    outputFormat = outputFormat,
                    onOutputFormatChange = onOutputFormatChange,
                    ultrafixSteps = ultrafixSteps,
                    onUltrafixStepsChange = onUltrafixStepsChange,
                    ultrafixDenoiseSteps = ultrafixDenoiseSteps,
                    onUltrafixDenoiseStepsChange = onUltrafixDenoiseStepsChange,
                    ultrafixQualityDenoise = ultrafixQualityDenoise,
                    onUltrafixQualityDenoiseChange = onUltrafixQualityDenoiseChange,
                    lowram = lowram,
                    onLowramChange = onLowramChange,
                    seqDit = seqDit,
                    onSeqDitChange = onSeqDitChange,
                    patch = patch,
                    imageBackendType = imageBackendType,
                    onReset = onReset
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 重置按钮：对齐 local-dream :453-467
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("advanced-settings-reset-button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重置",
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp)
                    )
                    Text("重置", color = MaterialTheme.colorScheme.error)
                }
                // 确认按钮：对齐 local-dream :469-471
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("advanced-settings-confirm-button")
                ) {
                    Text("确认")
                }
            }
        }
    )
}

/** 生成进度卡，保留 LocalDream 式中间预览位置 */
@Composable
private fun LocalDreamProgressCard(
    state: ImageGenerationUiState,
    onShowParams: () -> Unit
) {
    LocalDreamSectionCard(title = "进度监控", trailing = "${state.progress.coerceIn(0, 100)}%") {
        // C4 批量信息：批量生成时展示当前进度位于第几张
        if (state.batchTotal > 1) {
            Text(
                text = "第 %d/%d 张".format(state.batchIndex.coerceAtLeast(1), state.batchTotal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("image-generation-batch-label")
            )
        }
        // C2 中间预览位（180dp）：previewPath 非空时解码展示 progress SSE 落盘的预览图。
        // 预览文件为同一路径覆盖式写入，remember 的 key 必须带上 progress，
        // 否则路径不变时不会重新解码；CPU 管线 previewSupported=false 时 native 不发
        // 预览图，previewPath 恒为空，保留占位文案兜底。
        val previewBitmap = remember(state.previewPath, state.progress) {
            if (state.previewPath.isBlank()) null else BitmapFactory.decodeFile(state.previewPath)
        }
        // C2 中间预览位：仅当后端真的返回中间预览图（previewBitmap 非空）时才占 180dp 渲染。
        // CPU 管线 previewSupported=false，native 从不发预览图 → previewBitmap 恒空 →
        // 此处不渲染 180dp 占位框，避免留大空白；只在 NPU/SDXL/Anima 等支持预览的管线显示。
        if (previewBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("image-generation-preview-slot"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "中间预览画面",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (state.isGenerating) {
            // 不支持中间预览的管线（CPU）：不留空白，仅一行提示结果去向
            Text(
                text = "扩散中…完成后自动跳转结果页",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        if (state.backendStatusMessage.isNotBlank()) {
            Text(
                text = state.backendStatusMessage,
                style = MaterialTheme.typography.labelMedium,
                color = if (state.backendReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("image-backend-status")
            )
        }
        // 后端加载中遮罩（轻量）：生成中且后端未就绪（状态文案为启动中类）时显示转圈，
        // 明确告知用户后端进程正在拉起，而不是卡在 0% 无反馈
        if (state.isGenerating && !state.backendReady) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("image-backend-loading-indicator"),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "图片后端启动中，请稍候…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.isGenerating && state.progress <= 1) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("image-generation-progress")
            )
        } else {
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("image-generation-progress")
            )
        }
        Text(
            text = when {
                state.isGenerating && state.progress <= 1 -> "后台生成中 · ${formatDuration(state.durationMillis)} · 当前 adapter 暂无逐步百分比"
                state.isGenerating -> "后台生成中 · ${formatDuration(state.durationMillis)} · 通知栏持续显示进度"
                state.errorMessage.isNotBlank() -> "失败 · ${state.errorMessage}"
                state.outputPath.isNotBlank() -> "完成 · ${formatDuration(state.durationMillis)} · 已自动切换到结果页"
                else -> "等待生成"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.errorMessage.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("image-generation-progress-label")
        )
        TextButton(onClick = onShowParams) {
            Text("查看本次参数")
        }
    }
}

/** LocalDream 结果页：分类+分模型展示 + 魔法棒超分 FAB */
@Composable
private fun LocalDreamResultPage(
    state: ImageGenerationUiState,
    lastRequest: LocalDreamImageRequest?,
    inpaintBlendContext: InpaintBlendContext?, // inpaint 贴回上下文：非空时保存前把结果羽化贴回原图
    history: List<HistoryEntity> = emptyList(), // P2: 生图历史，供底部缩略图条
    imageBackendType: String = "",              // 当前模型后端类型：NPU 模型才显示 Ultrafix 按钮
    onGoToPrompt: () -> Unit,
    onRetry: () -> Unit,
    onCopyParams: () -> Unit,
    onShowParams: () -> Unit,
    onUpscale: () -> Unit,
    onUltrafix: () -> Unit,
    onSendToImg2Img: () -> Unit,      // 结果图一键喂入图生图模式
    onSendToInpaint: () -> Unit,     // 结果图一键喂入局部重绘模式
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current                 // 导出到相册/分享需要 Context
    val exportScope = rememberCoroutineScope()         // 保存到相册是 IO 挂起操作，需要协程作用域
    // 全屏预览目标：null 不显示遮罩；"output"=结果图，"upscaled"=超分图
    var fullscreenTarget by remember { mutableStateOf<String?>(null) }
    // 模式过滤：null=全部，非空=按模式筛选历史缩略图
    var resultModeFilter by remember { mutableStateOf<GenerationMode?>(null) }
    // 按模式过滤后的历史记录
    val filteredHistory = remember(history, resultModeFilter) {
        val modeFilter = resultModeFilter
        if (modeFilter == null) history
        else history.filter { it.mode == modeFilter.name }
    }
    // 按模型 ID 分组历史记录
    val groupedHistory = remember(filteredHistory) {
        filteredHistory.groupBy { it.modelId.ifBlank { "未命名模型" } }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 模式分类 chips：对齐 local-dream 结果页分类展示
            if (history.isNotEmpty()) {
                item {
                    LocalDreamSectionCard(title = "按模式筛选") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 全部 chip
                            if (resultModeFilter == null) {
                                Button(
                                    onClick = { resultModeFilter = null },
                                    shape = RoundedCornerShape(9999.dp),
                                    modifier = Modifier.testTag("result-mode-filter-all")
                                ) { Text("全部") }
                            } else {
                                OutlinedButton(
                                    onClick = { resultModeFilter = null },
                                    shape = RoundedCornerShape(9999.dp)
                                ) { Text("全部") }
                            }
                            // 各模式 chip
                            listOf(
                                GenerationMode.TXT2IMG to "文生图",
                                GenerationMode.IMG2IMG to "图生图",
                                GenerationMode.INPAINT to "局部重绘",
                                GenerationMode.ULTRAFIX to "UltraFix"
                            ).forEach { (mode, label) ->
                                if (resultModeFilter == mode) {
                                    Button(
                                        onClick = { resultModeFilter = null },
                                        shape = RoundedCornerShape(9999.dp),
                                        modifier = Modifier.testTag("result-mode-filter-${mode.name.lowercase()}")
                                    ) { Text(label) }
                                } else {
                                    OutlinedButton(
                                        onClick = { resultModeFilter = mode },
                                        shape = RoundedCornerShape(9999.dp)
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                LocalDreamSectionCard(title = "生成结果") {
                    when {
                        state.outputPath.isNotBlank() -> {
                            val bitmap = remember(state.outputPath) {
                                BitmapFactory.decodeFile(state.outputPath)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "生成图片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        // 点击结果图进入全屏可缩放预览（ZoomableImageOverlay）
                                        .clickable { fullscreenTarget = "output" }
                                        .testTag("generated-image-preview")
                                )
                            }
                            Text(
                                text = "生成完成 · ${formatDuration(state.durationMillis)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = state.outputPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("generated-image-path")
                            )
                        }
                        state.errorMessage.isNotBlank() -> {
                            Text(
                                text = "生成失败：${state.errorMessage}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("image-generation-error")
                            )
                        }
                        state.isGenerating -> {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Text("生成中，请在提示词页查看实时进度。")
                        }
                        else -> {
                            Text("暂无结果。先回到提示词页开始生成。")
                            Button(onClick = onGoToPrompt, shape = RoundedCornerShape(12.dp)) {
                                Text("去生成")
                            }
                        }
                    }
                }
            }
            item {
                LocalDreamSectionCard(title = "快捷操作") {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // H1 结果导出闭环：MediaStore 写入相册 Pictures/MagicWX 目录。
                        // inpaint 且具备贴回上下文时：先把结果按裁剪矩形羽化贴回原图再保存，
                        // 让用户拿到"修改后的原照片"而不是孤立 512 结果块；贴回失败自动回退保存原结果
                        OutlinedButton(
                            onClick = {
                                val outputPath = state.outputPath
                                val blend = inpaintBlendContext
                                exportScope.launch {
                                    val blendedBitmap = if (blend != null) {
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                val resultBitmap = BitmapFactory.decodeFile(outputPath)
                                                    ?: error("无法解码结果图: $outputPath")
                                                val composited = InpaintBlendUtils.blendInpaintResult(
                                                    originalBitmap = blend.originalBitmap,
                                                    cropRect = blend.cropRect,
                                                    maskBitmap = blend.maskBitmap,
                                                    resultBitmap = resultBitmap
                                                )
                                                resultBitmap.recycle()          // 合成完成即回收解码产物
                                                composited
                                            }.onFailure { error ->
                                                Log.w(TAG, "inpaint 贴回原图失败，回退保存原结果: ${error.message}")
                                            }.getOrNull()
                                        }
                                    } else {
                                        null
                                    }
                                    if (blendedBitmap != null) {
                                        val saved = ImageExportUtils.saveBitmapToGallery(context, blendedBitmap)
                                        blendedBitmap.recycle()
                                        Toast.makeText(
                                            context,
                                            if (saved) "已贴回原图并保存到相册 Pictures/MagicWX" else "保存到相册失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val saved = ImageExportUtils.saveToGallery(context, File(outputPath))
                                        Toast.makeText(
                                            context,
                                            if (saved) "已保存到相册 Pictures/MagicWX" else "保存到相册失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = state.outputPath.isNotBlank(),
                            modifier = Modifier.testTag("save-image-to-gallery-button")
                        ) { Text("保存到相册") }
                        // H1 结果导出闭环：FileProvider 授权 content:// Uri 后 ACTION_SEND 分享
                        OutlinedButton(
                            onClick = {
                                val shared = ImageExportUtils.shareImage(context, File(state.outputPath))
                                if (!shared) {
                                    Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = state.outputPath.isNotBlank(),
                            modifier = Modifier.testTag("share-image-button")
                        ) { Text("分享") }
                        OutlinedButton(onClick = onRetry, enabled = lastRequest != null) { Text("重试") }
                        OutlinedButton(onClick = onCopyParams, enabled = lastRequest != null) { Text("复制参数") }
                        OutlinedButton(onClick = onShowParams, enabled = lastRequest != null) { Text("参数详情") }
                        OutlinedButton(
                            onClick = onUpscale,
                            enabled = state.outputPath.isNotBlank() && !state.isUpscaling  // 超分中禁止重复触发
                        ) { Text(if (state.isUpscaling) "超分中…" else "超分") }
                        // UltraFix 按钮：仅 NPU 模型（sd15npu/sdxl/anima）显示，CPU 隐藏
                        if (imageBackendType in listOf("sd15npu", "sdxl", "anima")) {
                            OutlinedButton(
                                onClick = onUltrafix,
                                enabled = state.outputPath.isNotBlank(),
                                modifier = Modifier.testTag("localdream-result-ultrafix-button")
                            ) { Text("UltraFix") }
                        }
                        // 结果图一键转图生图输入：免去重新选图步骤（内部喂图并跳提示词页）
                        OutlinedButton(
                            onClick = onSendToImg2Img,
                            enabled = state.outputPath.isNotBlank(),
                            modifier = Modifier.testTag("send-to-img2img")
                        ) { Text("发到图生图") }
                        OutlinedButton(
                            onClick = onSendToInpaint,
                            enabled = state.outputPath.isNotBlank(),
                            modifier = Modifier.testTag("send-to-inpaint")
                        ) { Text("发到局部重绘") }
                    }
                    // 超分失败原因紧跟操作区展示
                    if (state.upscaleErrorMessage.isNotBlank()) {
                        Text(
                            text = "超分失败：${state.upscaleErrorMessage}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            // 超分成功后展示放大图与输出路径
            if (state.upscaleOutputPath.isNotBlank()) {
                item {
                    LocalDreamSectionCard(title = "超分结果") {
                        val upscaledBitmap = remember(state.upscaleOutputPath) {
                            BitmapFactory.decodeFile(state.upscaleOutputPath)  // 解码失败时降级为纯文本路径
                        }
                        if (upscaledBitmap != null) {
                            Image(
                                bitmap = upscaledBitmap.asImageBitmap(),
                                contentDescription = "超分图片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    // 超分图同样支持点击全屏缩放预览
                                    .clickable { fullscreenTarget = "upscaled" }
                            )
                        }
                        Text(
                            text = "超分输出：${state.upscaleOutputPath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // 按模型分组的历史缩略图：每组标题模型名 + 该模型历史缩略图横向滚动条
            if (groupedHistory.isNotEmpty()) {
                groupedHistory.forEach { (modelId, entries) ->
                    item(key = "history-group-$modelId") {
                        val modelName = ModelRegistry.findById(modelId)?.name
                            ?: modelId.ifBlank { "未命名模型" }
                        LocalDreamSectionCard(
                            title = "历史记录",
                            trailing = modelName
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                entries.take(20).forEach { entry ->
                                    val thumbPath = entry.thumbnailPath
                                    val thumbBitmap = remember(thumbPath) {
                                        if (thumbPath != null) {
                                            runCatching {
                                                BitmapFactory.decodeFile(thumbPath)
                                            }.getOrNull()
                                        } else null
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                // tap 跳全屏预览：直接设置 fullscreenTarget 为历史条目路径
                                                fullscreenTarget = entry.outputPath
                                            }
                                            .testTag("history-thumb-${entry.id}")
                                    ) {
                                        if (thumbBitmap != null) {
                                            Image(
                                                bitmap = thumbBitmap.asImageBitmap(),
                                                contentDescription = "历史缩略图",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            // 无缩略图时显示占位图标
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "?",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 魔法棒超分 FAB：对齐 local-dream SmallFloatingActionButton（AutoFixHigh 图标）
        SmallFloatingActionButton(
            onClick = {
                if (state.outputPath.isNotBlank() && !state.isUpscaling) {
                    onUpscale()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("result-upscale-fab"),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "超分"
            )
        }
    }

    // 全屏可缩放预览遮罩：结果图与超分图共用 ZoomableImageOverlay
    val target = fullscreenTarget
    if (target != null) {
        val overlayPath = if (target == "upscaled") state.upscaleOutputPath else state.outputPath
        // 历史条目路径（非"output"/"upscaled"）直接作为路径
        val finalPath = when (target) {
            "output" -> state.outputPath
            "upscaled" -> state.upscaleOutputPath
            else -> target
        }
        val overlayBitmap = remember(finalPath) {
            if (finalPath.isBlank()) null else BitmapFactory.decodeFile(finalPath)
        }
        ZoomableImageOverlay(
            bitmap = overlayBitmap,
            onDismiss = { fullscreenTarget = null }
        )
    }
}

/** 历史过滤状态：全部/当前模型/收藏/未收藏 */
private enum class HistoryFilter(val label: String) {
    ALL("全部"),
    CURRENT_MODEL("当前模型"),
    FAVORITES("收藏"),
    NOT_FAVORITE("未收藏")
}

/** LocalDream 生图历史页（数据源为 Room 持久化历史，按模型分组展示） */
@Composable
private fun LocalDreamHistoryPage(
    history: List<HistoryEntity>,
    onApplyParams: (LocalDreamImageRequest) -> Unit,
    onCopyParams: (LocalDreamImageRequest) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onSetFavorite: (Long, Boolean) -> Unit,  // 切换收藏状态
    onReproduce: (LocalDreamImageRequest) -> Unit,  // 参数复现：直接用历史参数发起生成
    modelId: String,                         // 当前生图模型 ID，供"当前模型"筛选
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()                       // 保存到相册是 IO 挂起操作
    val timestampFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    // 全屏 overlay 正在查看的历史条目：null 表示遮罩未展示
    var overlayEntity by remember { mutableStateOf<HistoryEntity?>(null) }
    // 待删除确认的历史条目：null 表示删除确认弹窗未展示
    var pendingDeleteEntity by remember { mutableStateOf<HistoryEntity?>(null) }
    // 参数复现弹窗：null 表示弹窗未展示
    var reproduceEntity by remember { mutableStateOf<HistoryEntity?>(null) }

    // ---- 历史备份 zip 导出/导入 Launcher ----
    val backupScope = rememberCoroutineScope()
    // 导出：创建 zip 文件目标
    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            try {
                val imageDir = File(context.filesDir, "generated_images")
                val result = HistoryBackup.exportHistoryZip(
                    outputFile = File(context.cacheDir, "temp_export.zip").also {
                        // 先写入临时文件，再通过 ContentResolver 复制到用户选择的 URI
                    },
                    historyList = history,
                    imageDir = imageDir
                )
                // 把临时文件复制到用户选择的 URI
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    File(context.cacheDir, "temp_export.zip").inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                File(context.cacheDir, "temp_export.zip").delete()
                Toast.makeText(context, "已导出 ${result.exported} 条记录", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "导出历史失败: ${e.message}", e)
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // 导入：选择 zip 文件
    val importZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            try {
                // 把选中的 zip 复制到临时文件
                val tempZip = File(context.cacheDir, "temp_import.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempZip.outputStream().use { output -> input.copyTo(output) }
                }
                val result = HistoryBackup.importHistoryZip(tempZip, HistoryRepository.create(context))
                tempZip.delete()
                Toast.makeText(
                    context,
                    "导入完成: ${result.imported} 条，跳过重复 ${result.duplicates} 条",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "导入历史失败: ${e.message}", e)
                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var historyFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    // ---- 高级过滤：从 ViewModel 获取可用选项 Flow ----
    val viewModel = viewModel<MainViewModel>()
    val availableSchedulers by viewModel.observeSchedulers().collectAsState(initial = emptyList<String>())
    val availableSizes by viewModel.observeSizes().collectAsState(initial = emptyList<String>())

    // 高级过滤选中状态：null 表示该维度不筛选
    var selectedMode by remember { mutableStateOf<GenerationMode?>(null) }
    var selectedScheduler by remember { mutableStateOf<String?>(null) }
    var selectedSize by remember { mutableStateOf<String?>(null) }

    // 构建高级过滤条件：合并简单过滤分类 + 高级维度过滤
    val dbFilter = remember(historyFilter, modelId, selectedMode, selectedScheduler, selectedSize) {
        DbHistoryFilter(
            modelIds = when (historyFilter) {
                HistoryFilter.CURRENT_MODEL -> setOf(modelId)
                else -> null
            },
            favorites = when (historyFilter) {
                HistoryFilter.FAVORITES -> setOf(FavoriteFilter.FAVORITE)
                HistoryFilter.NOT_FAVORITE -> setOf(FavoriteFilter.NOT_FAVORITE)
                else -> null
            },
            modes = selectedMode?.let { setOf(it) },
            schedulers = selectedScheduler?.let { setOf(it) },
            sizes = selectedSize?.let { setOf(it) }
        )
    }

    // 是否有高级过滤维度激活
    val hasAdvancedFilter = selectedMode != null || selectedScheduler != null || selectedSize != null

    // 按高级过滤条件查询历史
    val queriedHistory by produceState(initialValue = emptyList<HistoryEntity>(), dbFilter) {
        viewModel.queryHistory(dbFilter).collect { value = it }
    }

    // 展示列表：有高级过滤时用数据库查询结果，否则用客户端简单过滤
    val filteredHistory = if (hasAdvancedFilter) {
        queriedHistory
    } else {
        remember(history, historyFilter, modelId) {
            when (historyFilter) {
                HistoryFilter.ALL -> history
                HistoryFilter.CURRENT_MODEL -> history.filter { it.modelId == modelId }
                HistoryFilter.FAVORITES -> history.filter { it.favorite }
                HistoryFilter.NOT_FAVORITE -> history.filter { !it.favorite }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (filteredHistory.isEmpty() && history.isEmpty()) {
            LocalDreamSectionCard(title = "生成历史") {
                Text(
                    text = "暂无历史。生成完成后会自动记录缩略图、参数、耗时和结果路径。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 顶部操作卡：过滤 chips + 清空全部入口
            LocalDreamSectionCard(title = "生成历史") {
                // 过滤 chips 行：水平滚动，选中态为 filled 按钮，未选中态为 outlined
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        if (historyFilter == filter) {
                            Button(
                                onClick = { historyFilter = filter },
                                shape = RoundedCornerShape(9999.dp),
                                modifier = Modifier.testTag("history-filter-${filter.name.lowercase()}")
                            ) { Text(filter.label) }
                        } else {
                            OutlinedButton(
                                onClick = { historyFilter = filter },
                                shape = RoundedCornerShape(9999.dp)
                            ) { Text(filter.label) }
                        }
                    }
                }
                // ---- 高级过滤 chips：模式 / 调度器 / 尺寸 ----
                // 模式 chips：固定枚举值
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "模式",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GenerationMode.entries.forEach { mode ->
                        val label = when (mode) {
                            GenerationMode.TXT2IMG -> "文生图"
                            GenerationMode.IMG2IMG -> "图生图"
                            GenerationMode.INPAINT -> "局部重绘"
                            GenerationMode.ULTRAFIX -> "UltraFix"
                            GenerationMode.UNKNOWN -> "未知"
                        }
                        if (selectedMode == mode) {
                            Button(
                                onClick = { selectedMode = null },
                                shape = RoundedCornerShape(9999.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    selectedMode = mode
                                    // 切换模式时重置简单过滤为"全部"，避免两套过滤互相干扰
                                    historyFilter = HistoryFilter.ALL
                                },
                                shape = RoundedCornerShape(9999.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                // 调度器 chips：从数据库 DISTINCT 值动态生成
                if (availableSchedulers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "调度器",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableSchedulers.forEach { sched ->
                            if (selectedScheduler == sched) {
                                Button(
                                    onClick = { selectedScheduler = null },
                                    shape = RoundedCornerShape(9999.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) { Text(sched, style = MaterialTheme.typography.labelSmall) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedScheduler = sched
                                        historyFilter = HistoryFilter.ALL
                                    },
                                    shape = RoundedCornerShape(9999.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) { Text(sched, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
                // 尺寸 chips：从数据库 DISTINCT 值动态生成
                if (availableSizes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "尺寸",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableSizes.forEach { size ->
                            if (selectedSize == size) {
                                Button(
                                    onClick = { selectedSize = null },
                                    shape = RoundedCornerShape(9999.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) { Text(size, style = MaterialTheme.typography.labelSmall) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedSize = size
                                        historyFilter = HistoryFilter.ALL
                                    },
                                    shape = RoundedCornerShape(9999.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) { Text(size, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
                // 清空全部入口 + 导出/导入按钮
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 导出/导入按钮组（左）
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            exportZipLauncher.launch("magicwx_history_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.zip")
                        }) { Text("导出") }
                        TextButton(onClick = { importZipLauncher.launch("application/zip") }) { Text("导入") }
                    }
                    // 清空全部（右）
                    TextButton(onClick = onClearHistory) { Text("清空全部") }
                }
            }
            // 按模型分组展示：每组标题模型名 + 该模型历史，2 列缩略图网格
            val groupedList = remember(filteredHistory) {
                filteredHistory.groupBy { it.modelId.ifBlank { "未命名模型" } }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history-grid"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedList.forEach { (modelId, entries) ->
                    val modelName = ModelRegistry.findById(modelId)?.name
                        ?: modelId.ifBlank { "未命名模型" }
                    // 模型分组标题：对齐 local-dream 分组布局
                    item(key = "history-section-$modelId") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = modelName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${entries.size} 张",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // 2 列网格：每行两个卡片
                    items(
                        entries.chunked(2),
                        key = { chunk -> "history-row-${modelId}-${chunk.first().id}" }
                    ) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    HistoryGridCard(
                                        item = item,
                                        timestampFormat = timestampFormat,
                                        onClick = { overlayEntity = item },
                                        onSetFavorite = onSetFavorite
                                    )
                                }
                            }
                            // 单数行补齐占位，保持网格对齐
                            if (row.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // 全屏查看 overlay：显示原图 outputPath，底部动作栏承载 复用/复制/保存到相册/删除
    val entity = overlayEntity
    if (entity != null) {
        val fullBitmap = remember(entity.outputPath) {
            if (entity.outputPath.isBlank()) null else BitmapFactory.decodeFile(entity.outputPath)
        }
        ZoomableImageOverlay(
            bitmap = fullBitmap,
            onDismiss = { overlayEntity = null },
            // 既有"复用/复制/删除"文字按钮行为全部保留并承载在 overlay 底部动作栏：
            // 网格卡片本身不再放按钮，点开后动作一次可见，避免 1:1 小卡片塞三行按钮
            bottomContent = {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(9999.dp))
                        .padding(horizontal = 4.dp)
                ) {
                    TextButton(
                        onClick = {
                            onApplyParams(entity.toImageRequest())
                            overlayEntity = null                     // 复用后已跳提示词页，同步关闭遮罩
                        },
                        modifier = Modifier.testTag("history-overlay-apply")
                    ) { Text("复用", color = Color.White) }
                    TextButton(
                        onClick = { onCopyParams(entity.toImageRequest()) },
                        modifier = Modifier.testTag("history-overlay-copy")
                    ) { Text("复制", color = Color.White) }
                    TextButton(
                        onClick = {
                            // 保存到相册复用现有 ImageExportUtils（MediaStore 写入 Pictures/MagicWX）
                            exportScope.launch {
                                val saved = ImageExportUtils.saveToGallery(context, File(entity.outputPath))
                                Toast.makeText(
                                    context,
                                    if (saved) "已保存到相册 Pictures/MagicWX" else "保存到相册失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.testTag("history-overlay-save")
                    ) { Text("保存到相册", color = Color.White) }
                    TextButton(
                        onClick = { pendingDeleteEntity = entity },  // 删除走确认弹窗，防误触
                        modifier = Modifier.testTag("history-overlay-delete")
                    ) { Text("删除", color = Color.White) }
                    TextButton(
                        onClick = { reproduceEntity = entity },       // 参数复现弹窗
                        modifier = Modifier.testTag("history-overlay-reproduce")
                    ) { Text("复现", color = Color.White) }
                    TextButton(
                        onClick = {
                            exportScope.launch {
                                val reportFile = ReportImageUtils.reportImage(context, entity)
                                if (reportFile != null) {
                                    Toast.makeText(context, "问题报告已生成", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "报告生成失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("history-overlay-report")
                    ) { Text("报告问题", color = Color.White) }
                }
            }
        )
    }

    // 删除确认弹窗：确认才真正删行并联动清理磁盘文件
    val deleteTarget = pendingDeleteEntity
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteEntity = null },
            title = { Text("删除历史") },
            text = { Text("删除这条生成记录？对应图片与缩略图文件会一并删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteEntity = null
                        overlayEntity = null                         // 原图将被删除，遮罩同步关闭
                        onDeleteHistory(deleteTarget.id)
                    },
                    modifier = Modifier.testTag("history-delete-confirm-button")
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteEntity = null },
                    modifier = Modifier.testTag("history-delete-cancel-button")
                ) { Text("取消") }
            },
            modifier = Modifier.testTag("history-delete-confirm-dialog")
        )
    }

    // 参数复现弹窗：展示历史参数并一键复现生成
    val reproduceTarget = reproduceEntity
    if (reproduceTarget != null) {
        ReproduceParametersDialog(
            historyItem = reproduceTarget,
            onReproduce = { request ->
                onReproduce(request)
                reproduceEntity = null
                overlayEntity = null           // 复现后关闭遮罩，跳转提示词/结果页
            },
            onDismiss = { reproduceEntity = null }
        )
    }
}

/**
 * 历史网格单项：1:1 卡片。
 * - 有缩略图：裁剪填充展示（BitmapFactory decode + remember(thumbnailPath)），底部渐变条显示时间
 * - 无缩略图（旧记录或生成失败）：回退既有文字卡样式（提示词摘要 + 参数摘要 + 时间）
 */
@Composable
private fun HistoryGridCard(
    item: HistoryEntity,
    timestampFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onSetFavorite: (Long, Boolean) -> Unit  // 切换收藏状态回调
) {
    val thumbnailBitmap = remember(item.thumbnailPath) {
        // 路径为空或解码失败都回退 null，由下方文字卡分支兜底
        item.thumbnailPath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    val timestampText = remember(item.createdAt, timestampFormat) {
        timestampFormat.format(Date(item.createdAt))
    }
    Card(
        modifier = Modifier
            .aspectRatio(1f)                                         // 1:1 网格单元，对齐参照网格
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("history-item-${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap.asImageBitmap(),
                    contentDescription = "历史缩略图",
                    contentScale = ContentScale.Crop,                // 1:1 裁剪填充
                    modifier = Modifier.fillMaxSize()
                )
                // 底部渐变条显示生成时间（MM-dd HH:mm）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = timestampText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            } else {
                // 缩略图缺失回退：文字卡样式（与旧版历史卡信息一致）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.prompt,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.mode} · ${item.width}×${item.height} · seed ${item.seed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = timestampText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 收藏星标切换：右上角 overlay，点击切换收藏状态，不触发卡片点击
            IconButton(
                onClick = { onSetFavorite(item.id, !item.favorite) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("history-favorite-${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (item.favorite) "取消收藏" else "收藏",
                    tint = if (item.favorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 把 Room 历史记录还原为可复用生成请求；未持久化字段取协议默认值 */
private fun HistoryEntity.toImageRequest(): LocalDreamImageRequest {
    val mode = LocalDreamGenerationMode.entries.firstOrNull { it.wireValue == this.mode }
        ?: LocalDreamGenerationMode.TEXT_TO_IMAGE              // 未知模式回退文生图
    return LocalDreamImageRequest(
        prompt = prompt,
        negativePrompt = negativePrompt,
        steps = steps,
        cfg = cfg,
        seed = seed,
        width = width,
        height = height,
        scheduler = scheduler,
        mode = mode,
        denoiseStrength = denoiseStrength
    )
}

/** 参数详情弹窗 */
@Composable
private fun LocalDreamParamsDialog(
    request: LocalDreamImageRequest,
    runtimeText: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成参数") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("运行时：$runtimeText")
                Text("模式：${request.mode.wireValue}")
                Text("尺寸：${request.width}×${request.height} · 比例 ${request.aspectRatio}")
                Text("步数：${request.steps} · CFG ${request.cfg} · Seed ${request.seed} · Batch ${request.batchCount}")
                Text("调度器：${LocalDreamDefaults.schedulerLabel(request.scheduler)}")
                Text("降噪：${request.denoiseStrength}")
                Text("预览：${if (request.showDiffusionProcess) "开启，每 ${request.showDiffusionStride} 步" else "关闭"}")
                Text("输出格式：${request.outputFormat.wireValue}")
                Text("UltraFix：tile ${request.ultrafixTileSize} · steps ${request.ultrafixSteps} · denoise ${request.ultrafixDenoiseSteps} · quality ${request.ultrafixQualityDenoise}")
                Text("提示词：${request.prompt}")
                Text("负向提示词：${request.negativePrompt}")
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text("复制") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/** 通用参数区块卡 */
@Composable
private fun LocalDreamSectionCard(
    title: String,
    trailing: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (trailing.isNotBlank()) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

/** 通用滑条行 */
@Composable
private fun LocalDreamSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}

/** 横向单选按钮组 */
@Composable
private fun LocalDreamChoiceRow(
    title: String,
    values: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            values.forEach { value ->
                val displayText = if (title == "调度器") LocalDreamDefaults.schedulerLabel(value) else value
                if (selected == value) {
                    Button(
                        onClick = { onSelected(value) },
                        shape = RoundedCornerShape(9999.dp)
                    ) { Text(displayText) }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(value) },
                        shape = RoundedCornerShape(9999.dp)
                    ) { Text(displayText) }
                }
            }
        }
    }
}

/** Token 数本地估算兜底：后端未就绪或 /tokenize 请求失败时使用（真实计数走 ImageTokenizeClient） */
private fun estimatePromptTokens(text: String): Int {
    if (text.isBlank()) return 0
    return text.trim().split(Regex("\\s+|,")).filter { it.isNotBlank() }.size.coerceAtMost(999)
}

/** 从相册 Uri 生成导入显示名：取文件名末段，取不到时给兜底名 */
private fun localDreamLabelFromUri(uri: Uri): String {
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "相册图片"
}

/**
 * 把图片字节数据转为 LocalDream 请求可用的导入态：读取宽高并做 base64 编码。
 * 相册直读与裁剪结果两条导入路径共享该核心逻辑，保证编码口径一致。
 */
private fun importLocalDreamImage(bytes: ByteArray, label: String): LocalDreamImportedImage {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true                                       // 只读尺寸，避免导入阶段占用大内存
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = bounds.outWidth.takeIf { it > 0 } ?: 0
    val height = bounds.outHeight.takeIf { it > 0 } ?: 0
    return LocalDreamImportedImage(
        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),           // NO_WRAP 避免换行破坏 JSON 请求
        label = label,
        width = width,
        height = height
    )
}

/**
 * 解码相册选中图片为裁剪用 Bitmap：按最长边降采样，防止超大相册图整图解码 OOM。
 * 后端按 512 级别画布消费输入图，降采样到 2048 不损失可用细节。
 */
private fun decodePickedImageForCrop(context: android.content.Context, uri: Uri): Bitmap? {
    return runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()                                           // 读取用户选择的图片原始字节
        } ?: return null
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true                                   // 先只读原始尺寸，用于计算降采样率
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxEdge / sampleSize > MAX_CROP_SOURCE_EDGE) {
            sampleSize *= 2                                             // 2 的幂采样率解码最快且兼容性最好
        }
        Log.d(TAG, "裁剪源图解码: 原始 ${bounds.outWidth}x${bounds.outHeight}, sampleSize=$sampleSize")
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sampleSize                                   // 超限图降采样，其余原尺寸解码
        })
    }.onFailure { error ->
        Log.w(TAG, "裁剪源图解码失败: ${error.message}")
        Toast.makeText(context, "读取图片失败：${error.message}", Toast.LENGTH_SHORT).show()
    }.getOrNull()
}

/** 把裁剪结果 Bitmap 编码为 PNG 并走字节导入核心逻辑；PNG 无损口径与 local-dream 输入图编码一致 */
private fun importCroppedLocalDreamImage(
    context: android.content.Context,
    bitmap: Bitmap,
    label: String
): LocalDreamImportedImage? {
    return runCatching {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)          // 无损编码，避免二次 JPEG 压缩损伤输入图
        importLocalDreamImage(output.toByteArray(), label)
    }.onFailure { error ->
        Log.w(TAG, "裁剪结果编码失败: ${error.message}")
        Toast.makeText(context, "读取图片失败：${error.message}", Toast.LENGTH_SHORT).show()
    }.getOrNull()
}

/** 为当前输入图生成全白 PNG 蒙版，作为局部重绘的可执行兜底交互 */
private fun createFullLocalDreamMask(image: LocalDreamImportedImage): LocalDreamImportedImage {
    val width = image.width.takeIf { it > 0 } ?: 512
    val height = image.height.takeIf { it > 0 } ?: 512
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.WHITE)                     // 白色区域表示允许重绘
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    bitmap.recycle()
    return LocalDreamImportedImage(
        base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP),
        label = "全图蒙版",
        width = width,
        height = height
    )
}

/**
 * 把蒙版 base64 解码为 Bitmap：
 * - 涂抹 overlay 打开时作为半透明底版再编辑（existingMaskBitmap）
 * - inpaint 发起生成时作为贴回羽化权重（InpaintBlendContext.maskBitmap）
 * 解码失败返回 null，调用方分别回退为"无底版涂抹"与"整块粘贴贴回"。
 */
private fun decodeMaskBase64ToBitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.onFailure { error ->
        Log.w(TAG, "蒙版解码失败: ${error.message}")
    }.getOrNull()
}

/** 把当前参数复制到剪贴板 */
private fun copyLocalDreamParamsToClipboard(context: android.content.Context, request: LocalDreamImageRequest) {
    val text = buildString {
        appendLine("prompt=${request.prompt}")
        appendLine("negative_prompt=${request.negativePrompt}")
        appendLine("mode=${request.mode.wireValue}")
        appendLine("steps=${request.steps}")
        appendLine("cfg=${request.cfg}")
        appendLine("seed=${request.seed}")
        appendLine("size=${request.width}x${request.height}")
        appendLine("scheduler=${request.scheduler}")
        appendLine("aspect_ratio=${request.aspectRatio}")
        appendLine("denoise_strength=${request.denoiseStrength}")
        appendLine("show_diffusion_process=${request.showDiffusionProcess}")
        appendLine("show_diffusion_stride=${request.showDiffusionStride}")
        appendLine("output_format=${request.outputFormat.wireValue}")
        appendLine("batch_count=${request.batchCount}")
        appendLine("ultrafix_tile_size=${request.ultrafixTileSize}")
        appendLine("ultrafix_steps=${request.ultrafixSteps}")
        appendLine("ultrafix_denoise_steps=${request.ultrafixDenoiseSteps}")
        appendLine("ultrafix_quality_denoise=${request.ultrafixQualityDenoise}")
        if (request.lowram) appendLine("lowram=${request.lowram}")
        if (request.seqDit) appendLine("seq_dit=${request.seqDit}")
        if (request.patch.isNotBlank()) appendLine("patch=${request.patch}")
    }
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("LocalDream 参数", text))
    Toast.makeText(context, "参数已复制", Toast.LENGTH_SHORT).show()
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
                    // 生图模型显示最大分辨率（对齐 local-dream ModelCard）
                    if (info.arch == ModelArch.STABLE_DIFFUSION && info.generationSize > 0) {
                        InfoRow("最大分辨率", "${info.generationSize}x${info.generationSize}")
                    }
                    InfoRow("预估大小", if (info.approximateSize.isNotBlank()) info.approximateSize else displayModelSize(info))
                    InfoRow("可用状态", displayModelSupportStatus(info))
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

/** 已下载或内置可用模型卡片背景色，使用明确的浅绿色半透明可用态 token */
@Composable
private fun availableModelCardContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        Color(0x5540D98F)                                // 深色模式降低亮度，避免绿色过曝
    } else {
        Color(0x6634C759)                                // 亮色模式浅绿色半透明，突出“已可用”
    }
}

/** 格式化模型可用状态；首页只展示已验证模型，不再使用不确定性弱提示 */
private fun displayModelSupportStatus(modelInfo: ModelInfo): String {
    if (modelInfo.arch == ModelArch.BUILTIN) return "内置可用"    // 内置模型无需下载，首次打开即可使用
    if (modelInfo.capability == ModelCapability.IMAGE_GENERATION) return "离线生图"
    if (modelInfo.capability == ModelCapability.IMAGE_UPSCALING) return "离线超分"
    return "已验证"                                             // 外部模型已完成基础下载、加载、问答验收
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
        ModelArch.STABLE_DIFFUSION -> "Stable Diffusion"
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

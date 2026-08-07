/**
 * PromptTagTextField.kt - 带 Tag 自动补全弹窗的 Prompt 输入框组件
 *
 * 功能：
 * - 基于 BasicTextField + OutlinedTextField DecorationBox 的 Material 3 风格输入框
 * - 光标位置感知的补全建议弹出面板（位于输入行上方，不遮挡正在输入的行）
 * - 建议列表：fzf 模糊匹配高亮 + 分类圆点 + 人气计数 + 匹配类型 badge
 * - 固定工具栏：添加 tag / 清除 tag / 增减权重(+0.1/-0.1) / 撤销/重做 / 关闭
 * - 支持展开/折叠（多行模式）、token 溢出灰度提示、IME 自适应定位
 * - BackHandler 拦截返回手势关闭弹窗
 *
 * 移植自：modules/local-dream app/.../ui/components/PromptTagTextField.kt
 * 适配：中文硬编码 badge 标签，移除 R.string 依赖
 */
package com.qihao.open.rwkv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.qihao.open.rwkv.data.FuzzyMatcher
import com.qihao.open.rwkv.data.TagMatchType
import com.qihao.open.rwkv.data.TagSuggestion
import com.qihao.open.rwkv.data.tagUnderscoresToSpaces
import kotlin.math.roundToInt

/** 固定工具栏高度，从建议列表高度预算中扣除 */
private val SuggestionToolbarHeight = 48.dp

/**
 * Prompt 标签输入框：带 tag 自动补全弹窗、工具栏和 token 溢出提示。
 *
 * 使用 BasicTextField 而非 OutlinedTextField，以便通过 onTextLayout 获取光标矩形，
 * 实现弹窗精确定位在输入行上方。Outlined 外观通过 DecorationBox 复现。
 *
 * @param value 当前 TextFieldValue（文本+光标位置）
 * @param onValueChange 文本变更回调
 * @param label 输入框标签（Composable）
 * @param suggestions 当前补全建议列表
 * @param onSuggestionClick 建议点击回调
 * @param modifier 修饰符
 * @param enabled 是否启用输入
 * @param showSuggestions 是否显示建议弹窗
 * @param onFocusChanged 焦点变更回调
 * @param onDismissSuggestions 关闭建议弹窗回调
 * @param showToolbar 是否显示工具栏（即使无建议也保持工具栏可见）
 * @param onAddTag 添加 tag 按钮回调
 * @param onClearTag 清除 tag 按钮回调
 * @param onIncreaseWeight 增加权重按钮回调
 * @param onDecreaseWeight 减少权重按钮回调
 * @param onUndo 撤销按钮回调
 * @param onRedo 重做按钮回调
 * @param undoEnabled 撤销是否可用
 * @param redoEnabled 重做是否可用
 * @param highlightQuery 查询高亮文本（用于模糊匹配着色）
 * @param overflowOffset token 溢出位置（UTF-16 偏移），-1 表示无溢出
 * @param maxCollapsedLines 折叠模式最大行数
 * @param minCollapsedLines 折叠模式最小行数
 * @param minExpandedLines 展开模式最小行数
 */
@Composable
fun PromptTagTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: @Composable (() -> Unit),
    suggestions: List<TagSuggestion>,
    onSuggestionClick: (TagSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showSuggestions: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
    onDismissSuggestions: () -> Unit = {},
    showToolbar: Boolean = false,
    onAddTag: () -> Unit = {},
    onClearTag: () -> Unit = {},
    onIncreaseWeight: () -> Unit = {},
    onDecreaseWeight: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    undoEnabled: Boolean = false,
    redoEnabled: Boolean = false,
    highlightQuery: String? = null,
    overflowOffset: Int = -1,
    maxCollapsedLines: Int = 2,
    minCollapsedLines: Int = 2,
    minExpandedLines: Int = 3,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    var anchorTopPx by remember { mutableFloatStateOf(0f) }
    // 光标追踪：文本布局 + 内部输入框窗口位置 + 高度，用于定位弹窗
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var innerTopPx by remember { mutableFloatStateOf(0f) }
    var innerHeightPx by remember { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val outlinedColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )
    val inputTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)

    // Token 溢出灰度提示：超出 CLIP token 限制的部分以半透明灰色显示
    val overflowColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val overflowTransformation = remember(overflowOffset, overflowColor) {
        VisualTransformation { text ->
            if (overflowOffset in 0 until text.length) {
                val styled = buildAnnotatedString {
                    append(text.subSequence(0, overflowOffset))
                    withStyle(SpanStyle(color = overflowColor)) {
                        append(text.subSequence(overflowOffset, text.length))
                    }
                }
                TransformedText(styled, OffsetMapping.Identity)
            } else {
                TransformedText(text, OffsetMapping.Identity)
            }
        }
    }

    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .onGloballyPositioned { coords ->
                    anchorWidthPx = coords.size.width
                    anchorTopPx = coords.positionInWindow().y
                },
            enabled = enabled,
            textStyle = inputTextStyle,
            visualTransformation = overflowTransformation,
            maxLines = if (expanded) Int.MAX_VALUE else maxCollapsedLines,
            minLines = if (expanded) minExpandedLines else minCollapsedLines,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            onTextLayout = { textLayout = it },
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value.text,
                    innerTextField = {
                        // 测量内部输入框的窗口坐标，用于光标位置转换
                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                innerTopPx = coords.positionInWindow().y
                                innerHeightPx = coords.size.height.toFloat()
                            },
                        ) {
                            innerTextField()
                        }
                    },
                    enabled = enabled,
                    singleLine = false,
                    visualTransformation = overflowTransformation,
                    interactionSource = interactionSource,
                    label = label,
                    trailingIcon = {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = null,
                            )
                        }
                    },
                    colors = outlinedColors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = outlinedColors,
                            shape = MaterialTheme.shapes.medium,
                        )
                    },
                )
            },
        )

        // 弹窗：有建议或工具栏可见时显示，位于输入框内部 Column 以避免零尺寸节点产生额外间距
        Log.d("TagAuto", "popup cond show=$showSuggestions sug=${suggestions.size} toolbar=$showToolbar anchor=$anchorWidthPx")
        if (showSuggestions && (suggestions.isNotEmpty() || showToolbar) && anchorWidthPx > 0) {
            // BackHandler 拦截返回手势关闭弹窗（Popup 本身不可聚焦，dismissOnBackPress 不触发）
            BackHandler(enabled = true) { onDismissSuggestions() }

            // 建议列表变化时自动滚动到顶部
            val listState = rememberLazyListState()
            LaunchedEffect(suggestions) {
                listState.scrollToItem(0)
            }

            val widthDp = with(density) { anchorWidthPx.toDp() }
            val gapDp = 8.dp
            val gapPx = with(density) { gapDp.toPx() }

            // 实时 IME 和导航栏边距，使弹窗随键盘动画移动
            val imeBottomPx = WindowInsets.ime.getBottom(density)
            val statusTopPx = WindowInsets.statusBars.getTop(density)
            val navBottomPx = WindowInsets.navigationBars.getBottom(density)
            val bottomInsetPx = maxOf(imeBottomPx, navBottomPx)

            // 计算光标行顶部在窗口中的 Y 坐标
            val caretLineTopPx = run {
                val layout = textLayout
                if (layout != null && innerHeightPx > 0f && layout.size.height <= innerHeightPx + 0.5f) {
                    // 文本适配输入框（展开或短文本折叠）：光标矩形直接映射到屏幕行
                    val offset = value.selection.start.coerceIn(0, layout.layoutInput.text.length)
                    innerTopPx + layout.getCursorRect(offset).top
                } else {
                    // 输入框内部滚动（长文本折叠）或尚未布局：锚定到整个输入框顶部
                    anchorTopPx
                }
            }
            val popupBottomPx = (caretLineTopPx - gapPx).roundToInt()

            // 单行文本高度，用于在 IME 上移时保持弹窗在光标上方
            val lineHeightPx = run {
                val layout = textLayout
                if (layout != null && layout.lineCount > 0) {
                    layout.getLineBottom(0) - layout.getLineTop(0)
                } else {
                    with(density) {
                        val lh = inputTextStyle.lineHeight
                        if (lh.isSp) lh.toPx() else 24.sp.toPx()
                    }
                }
            }.roundToInt()

            // 光标行上方的可见空间（排除状态栏），弹窗列表高度不能超过此预算
            val availableAbovePx = (caretLineTopPx - statusTopPx - gapPx).coerceAtLeast(0f)
            val capDp = with(density) { minOf(280.dp, availableAbovePx.toDp()) }
            val maxHeightDp = (capDp - SuggestionToolbarHeight - 1.dp).coerceAtLeast(0.dp)

            Popup(
                // 底部锚定到光标行上方：popupBottomPx 仅在光标跨行时变化，同行输入时保持稳定
                popupPositionProvider = remember(popupBottomPx, lineHeightPx, statusTopPx, bottomInsetPx) {
                    CaretAnchorPositionProvider(
                        popupBottomPx = popupBottomPx,
                        lineHeightPx = lineHeightPx,
                        safeTopPx = statusTopPx,
                        bottomInsetPx = bottomInsetPx,
                    )
                },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                ),
            ) {
                // 固定尺寸 Box 覆盖整个预留区域，弹窗窗口不会因列表增缩而跳动
                Box(
                    modifier = Modifier
                        .width(widthDp)
                        .height(capDp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Card(
                        modifier = Modifier.width(widthDp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    ) {
                        // 建议列表（向上增长）
                        if (suggestions.isNotEmpty()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = maxHeightDp),
                            ) {
                                items(
                                    items = suggestions,
                                    key = { it.replacementTag },
                                ) { suggestion ->
                                    SuggestionRow(
                                        suggestion = suggestion,
                                        highlightQuery = highlightQuery,
                                        onClick = { onSuggestionClick(suggestion) },
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        // 工具栏：固定在卡片底部，紧贴光标行，列表变化时位置不变
                        TagActionToolbar(
                            onAddTag = onAddTag,
                            onClearTag = onClearTag,
                            onIncreaseWeight = onIncreaseWeight,
                            onDecreaseWeight = onDecreaseWeight,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            onClose = onDismissSuggestions,
                            undoEnabled = undoEnabled,
                            redoEnabled = redoEnabled,
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 工具栏
// ============================================================

/**
 * Tag 操作工具栏：添加/清除/权重调整/撤销/重做/关闭。
 * 按色调分组：tag 编辑（secondary）、权重步进（tertiary）、历史操作（primary）、关闭（neutral）。
 */
@Composable
private fun TagActionToolbar(
    onAddTag: () -> Unit,
    onClearTag: () -> Unit,
    onIncreaseWeight: () -> Unit,
    onDecreaseWeight: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SuggestionToolbarHeight)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarAction(
            icon = Icons.Default.Add,
            contentDescription = "添加 tag",
            container = MaterialTheme.colorScheme.secondaryContainer,
            onColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onAddTag,
        )
        ToolbarAction(
            icon = Icons.Default.Clear,
            contentDescription = "清除 tag",
            container = MaterialTheme.colorScheme.secondaryContainer,
            onColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onClearTag,
        )
        ToolbarAction(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "增加权重",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onIncreaseWeight,
        )
        ToolbarAction(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "减少权重",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onDecreaseWeight,
        )
        ToolbarAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "撤销",
            container = MaterialTheme.colorScheme.primaryContainer,
            onColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onUndo,
            enabled = undoEnabled,
        )
        ToolbarAction(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "重做",
            container = MaterialTheme.colorScheme.primaryContainer,
            onColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onRedo,
            enabled = redoEnabled,
        )
        ToolbarAction(
            icon = Icons.Default.Close,
            contentDescription = "关闭建议",
            container = MaterialTheme.colorScheme.surfaceVariant,
            onColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onClose,
        )
    }
}

/**
 * 单个工具栏按钮：Surface 包裹的图标按钮，等宽分布。
 * 禁用时以 0.38 alpha 灰显。
 */
@Composable
private fun RowScope.ToolbarAction(
    icon: ImageVector,
    contentDescription: String,
    container: Color,
    onColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val disabledAlpha = 0.38f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = if (enabled) container else container.copy(alpha = disabledAlpha),
        contentColor = if (enabled) onColor else onColor.copy(alpha = disabledAlpha),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ============================================================
// 建议行
// ============================================================

/**
 * 单条补全建议行：分类圆点 + 主文本（高亮）+ 辅助文本（翻译/别名）+ 人气计数 + 匹配类型 badge。
 * 嵌入类型保持下划线格式（用于文件名匹配），其他类型显示为空格分隔。
 */
@Composable
private fun SuggestionRow(suggestion: TagSuggestion, highlightQuery: String?, onClick: () -> Unit) {
    val displayPrimary = if (suggestion.matchType == TagMatchType.Embedding) {
        suggestion.primaryText
    } else {
        tagUnderscoresToSpaces(suggestion.primaryText)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 分类圆点
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (suggestion.matchType == TagMatchType.Embedding) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        categoryColor(suggestion.category)
                    },
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 主文本：模糊匹配高亮
            Text(
                text = highlightMatches(displayPrimary, highlightQuery, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 辅助文本：翻译或别名
            suggestion.secondaryText?.takeIf { it.isNotBlank() }?.let { secondary ->
                Text(
                    text = highlightMatches(secondary, highlightQuery, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 人气计数
        if (suggestion.postCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatPostCount(suggestion.postCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MatchTypeBadge(suggestion.matchType)
    }
}

/**
 * 匹配类型 badge：别名(蓝色) / 纠错(红色) / 嵌入(紫色) / 前缀/翻译无 badge。
 * 中文硬编码，不依赖 stringResource。
 */
@Composable
private fun MatchTypeBadge(matchType: TagMatchType) {
    val label = when (matchType) {
        TagMatchType.Alias -> "别名"
        TagMatchType.Correction -> "纠错"
        TagMatchType.Embedding -> "嵌入"
        else -> return
    }
    val container = when (matchType) {
        TagMatchType.Correction -> MaterialTheme.colorScheme.errorContainer
        TagMatchType.Embedding -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = when (matchType) {
        TagMatchType.Correction -> MaterialTheme.colorScheme.onErrorContainer
        TagMatchType.Embedding -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Spacer(Modifier.width(8.dp))
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = onContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/**
 * 分类颜色映射（Danbooru 分类）：
 * 0=general: 轮廓色, 1=artist: 红色, 3=copyright: 紫色, 4=character: 绿色, 5=meta: 橙色
 */
@Composable
private fun categoryColor(category: Int): Color = when (category) {
    // artist
    1 -> Color(0xFFE53935)
    // copyright
    3 -> Color(0xFFAB47BC)
    // character
    4 -> Color(0xFF43A047)
    // meta
    5 -> Color(0xFFFB8C00)
    // general / unknown
    else -> MaterialTheme.colorScheme.outline
}

/** 格式化人气计数：>=1M 用 M，>=1k 用 k，保留一位小数 */
private fun formatPostCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "${n / 1_000}k"
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

/** 高亮用归一化：小写、空格→下划线、连字符→下划线 */
private fun normalizeForHighlight(value: String): String =
    value.lowercase().replace(' ', '_').replace('-', '_')

/**
 * 对文本中模糊匹配的字符进行加粗+着色高亮。
 * 匹配位置由 FuzzyMatcher.positions 计算，与评分算法一致。
 */
private fun highlightMatches(text: String, query: String?, highlightColor: Color): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val normQuery = normalizeForHighlight(query.trim())
    if (normQuery.isEmpty()) return AnnotatedString(text)
    // normalizeForHighlight 只做单字符替换，normText 与 text 的字符位置一一对应
    val normText = normalizeForHighlight(text)
    val positions = FuzzyMatcher.positions(normQuery.toCharArray(), normText)
    if (positions == null || positions.isEmpty()) return AnnotatedString(text)
    val matchStyle = SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)
    return buildAnnotatedString {
        var idx = 0
        var p = 0
        while (idx < text.length) {
            if (p < positions.size && positions[p] == idx) {
                // 连续匹配字符段：合并为一个 styled span
                val start = idx
                while (p < positions.size && positions[p] == idx) {
                    p++
                    idx++
                }
                withStyle(matchStyle) {
                    append(text.substring(start, idx))
                }
            } else {
                // 非匹配字符段
                val start = idx
                while (idx < text.length && (p >= positions.size || positions[p] != idx)) idx++
                append(text.substring(start, idx))
            }
        }
    }
}

// ============================================================
// 弹窗定位器
// ============================================================

/**
 * 光标锚点定位器：弹窗底部对齐到光标行上方，IME 上移时自动夹取。
 * 优先放在光标上方，空间不足时回退到输入框下方。
 */
private class CaretAnchorPositionProvider(
    private val popupBottomPx: Int,
    private val lineHeightPx: Int,
    private val safeTopPx: Int,
    private val bottomInsetPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 8
        // 可见底部 = 屏幕高度 - 底部遮挡（IME/导航栏）
        val visibleBottom = (windowSize.height - bottomInsetPx).coerceAtLeast(0)

        // 弹窗底部对齐 popupBottomPx（光标行上方），但不超过可见区域
        val bottom = minOf(popupBottomPx, visibleBottom - lineHeightPx - gap)
        val aboveY = bottom - popupContentSize.height
        // 上方空间不足时回退到输入框下方
        val belowY = anchorBounds.bottom + gap

        val y = when {
            aboveY >= safeTopPx -> aboveY
            belowY + popupContentSize.height <= visibleBottom -> belowY
            else -> aboveY.coerceAtLeast(safeTopPx)
        }
        val x = anchorBounds.left.coerceIn(
            0,
            (windowSize.width - popupContentSize.width).coerceAtLeast(0),
        )
        return IntOffset(x, y)
    }
}
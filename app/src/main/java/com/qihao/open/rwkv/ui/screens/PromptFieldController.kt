/**
 * PromptFieldController.kt - Prompt 输入框状态控制器
 *
 * 功能：
 * - 管理单个 prompt 输入框的文本、光标、补全建议、撤销/重做历史
 * - 自动补全：输入变化时 200ms 防抖后调用 TagAutocompleteRepository 查询
 * - 撤销/重做：连续输入 600ms 内合并为一个步骤，补全选中和工具栏操作为独立步骤
 * - 提供 ControlledPromptTagTextField 便捷组合，将控制器绑定到 PromptTagTextField
 * - 嵌入建议：通过 embeddingSuggestionsFor 回调注入外表数据源
 *
 * 注意：本文件剥离了 token 计数功能（依赖后端 /tokenize 接口），
 * 后续由整合 Agent 在线路层注入。tokenCount/tokenMax/overflowOffset 保留为桩值。
 *
 * 移植自：modules/local-dream app/.../ui/screens/PromptFieldController.kt
 * 适配：移除 tokenizePromptRequest 后端依赖，token 计数置为桩值
 */
package com.qihao.open.rwkv.ui.screens

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.qihao.open.rwkv.data.TagAutocompleteRepository
import com.qihao.open.rwkv.data.TagSuggestion
import com.qihao.open.rwkv.ui.components.PromptTagTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 撤销/重做：历史步骤上限 */
private const val HISTORY_LIMIT = 100
/** 连续输入合并时间窗口（毫秒） */
private const val HISTORY_COALESCE_MS = 600L

/**
 * 单个 prompt 输入框的状态持有者：文本+光标、tag 补全建议、撤销/重做历史、token 计数信息。
 * 运行屏幕为 prompt 和 negative prompt 各创建一个实例，所有原本重复的状态变量和函数集中于此。
 */
@Stable
internal class PromptFieldController(
    private val scope: CoroutineScope,
    private val repository: TagAutocompleteRepository,
    private val suggestionCount: Int,
    private val embeddingSuggestionsFor: (String) -> List<TagSuggestion>,
) {
    /** 是否启用自动补全（由组合层注入，与后端服务状态挂钩） */
    var autocompleteAvailable: Boolean = false

    /** 文本变更时回调，屏幕层可挂接持久化逻辑 */
    var onTextCommitted: () -> Unit = {}

    /** 当前输入框文本值（含光标位置） */
    var fieldValue by mutableStateOf(TextFieldValue(""))
        private set
    val text: String get() = fieldValue.text

    /** 当前补全建议列表 */
    var suggestions by mutableStateOf<List<TagSuggestion>>(emptyList())
        private set

    /** 当前活跃查询 token（显示在弹窗中的查询文本） */
    var activeQuery by mutableStateOf<String?>(null)
        private set

    /** 输入框是否聚焦 */
    var isFocused by mutableStateOf(false)
        private set

    /** 返回手势是否已关闭弹窗（下次编辑时重置） */
    var popupDismissed by mutableStateOf(false)
        private set

    // ============================================================
    // 撤销/重做历史
    // ============================================================

    private var undoStack by mutableStateOf<List<String>>(emptyList())
    private var redoStack by mutableStateOf<List<String>>(emptyList())
    private var historyAt = 0L
    val undoEnabled: Boolean get() = undoStack.isNotEmpty()
    val redoEnabled: Boolean get() = redoStack.isNotEmpty()

    // ============================================================
    // Token 计数（桩值，后续由整合 Agent 注入）
    // ============================================================

    /** 当前 prompt 的 token 数（桩值，需后续整合 Agent 接入后端 /tokenize） */
    var tokenCount by mutableIntStateOf(2)

    /** 模型最大 token 数（桩值 77 = SD1.5 CLIP 上限） */
    var tokenMax by mutableIntStateOf(77)

    /** 超出 token 限制的 UTF-16 偏移（-1 = 未溢出），驱动输入框灰度提示 */
    var overflowOffset by mutableIntStateOf(-1)

    // ============================================================
    // 补全建议查询
    // ============================================================

    private var suggestJob: Job? = null

    /**
     * 记录 [snapshot] 为撤销检查点。
     * 连续输入在合并窗口内折叠为一个步骤；
     * 离散操作（补全选中、工具栏操作）传 coalesce=false 强制新建步骤。
     */
    private fun pushHistory(snapshot: String, coalesce: Boolean) {
        val now = System.currentTimeMillis()
        val skip = coalesce && undoStack.isNotEmpty() && now - historyAt < HISTORY_COALESCE_MS
        if (!skip) {
            undoStack = (undoStack + snapshot).takeLast(HISTORY_LIMIT)
        }
        redoStack = emptyList()
        // 离散操作关闭合并窗口，下次输入从新步骤开始
        historyAt = if (coalesce) now else 0L
    }

    /**
     * 更新输入框文本值。
     * 文本变更时自动记录撤销历史、触发防抖补全查询。
     *
     * @param value 新 TextFieldValue
     * @param recordHistory 是否记录撤销历史（控制器内部操作如 undo/redo 不记录）
     */
    fun update(value: TextFieldValue, recordHistory: Boolean = true) {
        val previousText = fieldValue.text
        val textChanged = value.text != previousText
        val selectionChanged = value.selection != fieldValue.selection
        if (textChanged && recordHistory) {
            pushHistory(previousText, coalesce = true)
        }
        if (textChanged || selectionChanged) {
            popupDismissed = false
        }
        fieldValue = value
        if (textChanged) {
            onTextCommitted()
        }
        // 自动补全未启用或未聚焦时清空建议
        Log.d("TagAuto", "update ac=$autocompleteAvailable focus=$isFocused chg=$textChanged sel=${value.selection.start} tail=${value.text.takeLast(15)}")
        if (!autocompleteAvailable || !isFocused) {
            suggestJob?.cancel()
            suggestions = emptyList()
            activeQuery = null
            return
        }
        if (!textChanged && !selectionChanged) return
        // 提取光标所在 tag 段作为查询 token
        val activeTag = TagAutocompleteRepository.extractActiveTag(value.text, value.selection.start)
        Log.d("TagAuto", "activeTag=${activeTag?.token} textLen=${value.text.length} sel=${value.selection.start}")
        if (activeTag == null) {
            suggestJob?.cancel()
            suggestions = emptyList()
            activeQuery = null
            return
        }
        activeQuery = activeTag.token
        // 200ms 防抖查询
        suggestJob?.cancel()
        suggestJob = scope.launch {
            delay(200)
            val embeddings = embeddingSuggestionsFor(activeTag.token)
            val results = repository.suggest(activeTag.token, suggestionCount)
            Log.d("TagAuto", "suggest done: embeddings=${embeddings.size} results=${results.size}")
            // 嵌入建议固定置顶：用户本地数据优先级高于词典
            suggestions = embeddings + results
        }
    }

    /**
     * 应用补全建议：替换光标所在 tag 段，清空建议列表。
     * 离散撤销步骤，可撤销。
     */
    fun applySuggestion(suggestion: TagSuggestion) {
        pushHistory(fieldValue.text, coalesce = false)
        val (updatedText, updatedSelection) = TagAutocompleteRepository.applySuggestion(
            fieldValue.text,
            fieldValue.selection.start,
            suggestion,
        )
        fieldValue = TextFieldValue(updatedText, TextRange(updatedSelection))
        suggestions = emptyList()
        activeQuery = null
        popupDismissed = false
        onTextCommitted()
    }

    /**
     * 执行工具栏 tag 操作（添加/清除/权重调整），作为离散撤销步骤。
     * 操作结果通过 update 回灌以刷新建议和弹窗。
     */
    fun runTagAction(action: (String, Int) -> Pair<String, Int>?) {
        val (updatedText, updatedSelection) = action(
            fieldValue.text,
            fieldValue.selection.start,
        ) ?: return
        pushHistory(fieldValue.text, coalesce = false)
        update(
            TextFieldValue(updatedText, TextRange(updatedSelection)),
            recordHistory = false,
        )
    }

    /** 撤销：弹出撤销栈顶，推入重做栈 */
    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.last()
        undoStack = undoStack.dropLast(1)
        redoStack = (redoStack + fieldValue.text).takeLast(HISTORY_LIMIT)
        historyAt = 0L
        update(
            TextFieldValue(previous, TextRange(previous.length)),
            recordHistory = false,
        )
    }

    /** 重做：弹出重做栈顶，推入撤销栈 */
    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.last()
        redoStack = redoStack.dropLast(1)
        undoStack = (undoStack + fieldValue.text).takeLast(HISTORY_LIMIT)
        historyAt = 0L
        update(
            TextFieldValue(next, TextRange(next.length)),
            recordHistory = false,
        )
    }

    /**
     * 整体替换文本（初始加载、重置、复制种子、导入），
     * 绕过撤销历史并清空建议。持久化由调用方负责。
     */
    fun replaceText(newText: String) {
        fieldValue = TextFieldValue(newText, TextRange(newText.length))
        suggestions = emptyList()
    }

    /** 焦点变更回调：失焦时清空建议 */
    fun onFocusChanged(focused: Boolean) {
        isFocused = focused
        if (!focused) suggestions = emptyList()
    }

    /** 关闭建议弹窗（返回手势触发） */
    fun dismissPopup() {
        popupDismissed = true
    }
}

/**
 * 在 Composable 作用域内 remember 一个 [PromptFieldController]。
 *
 * @param repository Tag 补全仓库实例
 * @param suggestionCount 建议最大返回条数
 * @param embeddingSuggestionsFor 嵌入建议回调（可为空 lambda）
 */
@Composable
internal fun rememberPromptFieldController(
    repository: TagAutocompleteRepository,
    suggestionCount: Int,
    embeddingSuggestionsFor: (String) -> List<TagSuggestion> = { emptyList() },
): PromptFieldController {
    val scope = rememberCoroutineScope()
    return remember {
        PromptFieldController(scope, repository, suggestionCount, embeddingSuggestionsFor)
    }
}

/**
 * 将 [PromptFieldController] 绑定到 [PromptTagTextField] 的便捷组合。
 * 自动处理弹窗可见性逻辑（补全启用 + 聚焦 + 未被返回手势关闭）。
 *
 * @param controller 输入框控制器
 * @param autocompleteAvailable 自动补全是否可用（由上层注入）
 * @param label 输入框标签
 * @param modifier 修饰符
 */
@Composable
internal fun ControlledPromptTagTextField(
    controller: PromptFieldController,
    autocompleteAvailable: Boolean,
    label: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
) {
    val popupVisible = autocompleteAvailable && controller.isFocused && !controller.popupDismissed
    PromptTagTextField(
        value = controller.fieldValue,
        onValueChange = { controller.update(it) },
        modifier = modifier,
        label = label,
        suggestions = controller.suggestions,
        onSuggestionClick = controller::applySuggestion,
        showSuggestions = popupVisible,
        // 工具栏即使在空 prompt 时也保持可见，使撤销/重做始终可访问
        showToolbar = popupVisible,
        highlightQuery = controller.activeQuery,
        overflowOffset = controller.overflowOffset,
        onFocusChanged = controller::onFocusChanged,
        onDismissSuggestions = controller::dismissPopup,
        onUndo = controller::undo,
        onRedo = controller::redo,
        undoEnabled = controller.undoEnabled,
        redoEnabled = controller.redoEnabled,
        onAddTag = {
            controller.runTagAction(TagAutocompleteRepository::appendTagAfterActive)
        },
        onClearTag = {
            controller.runTagAction(TagAutocompleteRepository::clearActiveTag)
        },
        onIncreaseWeight = {
            controller.runTagAction { text, sel ->
                TagAutocompleteRepository.adjustActiveTagWeight(text, sel, 0.1)
            }
        },
        onDecreaseWeight = {
            controller.runTagAction { text, sel ->
                TagAutocompleteRepository.adjustActiveTagWeight(text, sel, -0.1)
            }
        },
    )
}
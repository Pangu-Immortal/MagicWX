/**
 * MaskDrawingCanvas.kt - 局部重绘（inpaint）蒙版绘制画布
 *
 * 功能：
 * - 在源图之上叠加透明画布，用户手指拖动绘制彩色蒙版笔画
 * - 工具切换：画笔（PEN）涂色 / 橡皮（ERASER）按笔画擦除先前内容
 * - 笔画级撤销/重做：undo()/redo()/canUndo/canRedo，redo 栈在新笔画落笔时清空
 *   （对齐参照 InpaintScreen pathHistory + redoStack 语义，326-340 行）
 * - 蒙版再编辑：setExistingMask() 把已有蒙版转为半透明底版叠加显示，并参与最终编码，
 *   让"导入蒙版后再涂抹/擦除"可行（对齐参照 InpaintScreen existingMaskBitmap，77-78/117-126 行）
 * - encodeMaskToBase64 将底版+笔画光栅化为 width×height 白色 PNG 并 Base64 编码，
 *   直接作为后端 /generate 的 mask 字段（后端按 width 与 width/8 解码到潜空间）
 * - 双指缩放/平移画布：awaitEachGesture 手势仲裁，单指画、双指缩放平移，
 *   scale coerceIn(1f,5f)，offset 跟随 centroid 变化（对齐参照 InpaintScreen 473-602 行）
 * - 画笔颜色选择器：8 色调色板（白/红/橙/黄/绿/青/蓝/紫），
 *   颜色变更通过 Controller 驱动显示缓存失效，新笔画使用当前颜色（对齐参照 InpaintScreen 357-403 行）
 *
 * 设计依据：参考 local-dream InpaintScreen 的 笔画→Bitmap→PNG→Base64 链路，
 * 笔画以归一化坐标 [0,1] 存储，使绘制画布与目标生成分辨率彻底解耦。
 */
package com.qihao.open.rwkv.ui.image

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 蒙版绘制工具：PEN 画笔涂色，ERASER 橡皮按笔画擦除先前内容 */
enum class MaskToolMode { PEN, ERASER }

/** 单条蒙版笔画：points 为相对图像矩形的归一化坐标 [0,1]，brushFraction 为画笔宽度占图像宽度的比例，isErase 标记橡皮笔画 */
internal data class MaskStroke(
    val points: List<Offset>,
    val brushFraction: Float,
    val isErase: Boolean = false,
)

/** 蒙版亮度阈值：白底黑字蒙版中亮度≥128 视为已涂区域（与 InpaintBlendUtils 同口径） */
private const val MASK_PAINTED_LUMA_THRESHOLD = 128

/** 预览位图最长边上限：笔画叠加预览只用于显示，压到 1024 以内避免大源图全尺寸重绘开销 */
private const val MASK_DISPLAY_MAX_EDGE = 1024

/**
 * 蒙版绘制控制器：承载笔画状态、撤销/重做栈、已有蒙版底版、画笔颜色与编码能力。
 * 由调用方 remember 持有，绘制完成后通过 [encodeMaskToBase64] 取得后端 /generate 所需 mask 字段。
 *
 * 橡皮实现说明：不做像素级实时擦除，而是给每条笔画记录 erase 标志，
 * 光栅化时按落笔顺序回放——画笔 SRC 写色、橡皮 CLEAR 擦除，与参照 processMask 语义一致。
 *
 * 颜色说明：画笔颜色仅影响 UI 显示（renderDisplayBitmap），
 * mask 编码（encodeMaskToBase64）始终输出白色，以保证后端 inpaint 语义正确。
 */
class MaskDrawingController {
    /** 已完成笔画列表，归一化坐标，驱动 UI 实时重绘 */
    internal val strokes: SnapshotStateList<MaskStroke> = mutableStateListOf()

    /** 重做栈：undo 弹出的笔画进栈；新笔画落笔时清空（对齐参照 redoStack 语义） */
    internal val redoStack: SnapshotStateList<MaskStroke> = mutableStateListOf()

    /** 内容版本号：任何笔画/底版/颜色变更自增，用于预览位图缓存失效 */
    private var contentVersion = 0

    /** 画笔颜色：影响 UI 显示预览，变更时驱动显示缓存失效（对齐参照 brushColor + updateAllBrushPaths 语义） */
    var brushColor: ComposeColor = ComposeColor.White
        set(value) {
            if (field != value) {
                field = value
                contentVersion++ // 颜色变更触发预览缓存失效，所有已有笔画重绘为新颜色
            }
        }

    /** 预览位图缓存（底版+已提交笔画合成），按 尺寸+版本号 命中 */
    private var displayCache: Bitmap? = null
    private var displayCacheKey = -1L
    private var displayCacheVersion = -1

    /** 已有蒙版底版：半透明叠加显示并参与编码。调用方保证传入后不再 recycle 该位图 */
    private var existingMask: Bitmap? = null

    /** 是否已有笔画，供调用方控制"确认"按钮可用态 */
    val hasStrokes: Boolean get() = strokes.isNotEmpty()

    /** 是否可撤销：存在已绘制笔画 */
    val canUndo: Boolean get() = strokes.isNotEmpty()

    /** 是否可重做：重做栈非空 */
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** 追加一条笔画并清空重做栈（落笔新内容后旧重做链失效，对齐参照语义）；仅画布手势内部调用 */
    internal fun addStroke(stroke: MaskStroke) {
        strokes.add(stroke)
        redoStack.clear()
        contentVersion++
    }

    /** 撤销最近一笔：弹出进重做栈；无笔画时不动作 */
    fun undo() {
        if (strokes.isEmpty()) return
        redoStack.add(strokes.removeAt(strokes.lastIndex))
        contentVersion++
    }

    /** 重做最近撤销的一笔；重做栈为空时不动作 */
    fun redo() {
        if (redoStack.isEmpty()) return
        strokes.add(redoStack.removeAt(redoStack.lastIndex))
        contentVersion++
    }

    /** 清除全部已绘制笔画（保留已有蒙版底版：底版来自导入数据，清除只作用于本次涂抹） */
    fun clear() {
        strokes.clear()
        redoStack.clear()
        contentVersion++
    }

    /**
     * 设置已有蒙版底版（传 null 清除）。
     * 底版在画布上以半透明白色叠加显示，编码时按亮度转白并入最终 mask，
     * 使"导入蒙版后再涂抹"与"全图蒙版局部擦除"两条链路可行。
     */
    fun setExistingMask(bitmap: Bitmap?) {
        existingMask = bitmap
        contentVersion++
    }

    /**
     * 合成预览位图（底版+已提交笔画，透明背景），画笔颜色使用当前 [brushColor]：
     * - 底版按亮度转白色 Alpha（黑区透明，避免黑底盖住源图）
     * - 笔画按落笔顺序回放：画笔 SRC 写当前颜色、橡皮 CLEAR 擦除
     * 结果按 [width]×[height] 缓存，内容/尺寸/颜色变化才重建，避免每帧全量重绘。
     *
     * @return 无可显示内容（无底版且无笔画）时返回 null
     */
    internal fun renderDisplayBitmap(width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        if (strokes.isEmpty() && existingMask == null) {
            recycleDisplayCache()
            return null
        }
        val key = (width.toLong() shl 32) or height.toLong().and(0xFFFFFFFFL)
        val cached = displayCache
        if (cached != null && displayCacheKey == key && displayCacheVersion == contentVersion) {
            return cached
        }
        recycleDisplayCache()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) // 透明底
        val canvas = AndroidCanvas(bitmap)
        existingMask?.let { mask -> drawLuminanceMaskOverlay(canvas, mask, width, height) }
        // 显示预览使用当前画笔颜色；mask 编码使用白色（见 encodeMaskToBase64）
        rasterizeStrokes(canvas, width, height, useBrushColor = true)
        displayCache = bitmap
        displayCacheKey = key
        displayCacheVersion = contentVersion
        return bitmap
    }

    /**
     * 将底版+已绘制笔画光栅化为 [width]×[height] 位图（已涂处白色 255，其余黑色 0），
     * PNG 压缩后 Base64 编码，直接作为后端 /generate mask 字段。
     * 后端按 width 与 width/8 解码该蒙版（潜空间 8 倍下采样），因此位图分辨率需与生成目标一致。
     * 注意：mask 编码始终使用白色，画笔颜色仅影响 UI 显示。
     */
    fun encodeMaskToBase64(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return ""
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK) // 未涂抹区域为黑色 0
        existingMask?.let { mask -> drawOpaqueLuminanceMask(bitmap, mask, width, height) }
        // mask 编码始终使用白色（useBrushColor = false），保证后端 inpaint 语义正确
        rasterizeStrokes(AndroidCanvas(bitmap), width, height, useBrushColor = false)
        // 橡皮 CLEAR 出的透明孔洞回填不透明黑，保证 mask 是严格黑/白 PNG（后端按白色判定重绘区）
        AndroidCanvas(bitmap).drawColor(Color.BLACK, PorterDuff.Mode.DST_OVER)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    /** 释放预览缓存位图（控制器重建内容或尺寸变化时调用） */
    private fun recycleDisplayCache() {
        displayCache?.recycle()
        displayCache = null
        displayCacheKey = -1L
        displayCacheVersion = -1
    }

    /** 底版转半透明白色叠加层：亮度→Alpha，白色 RGB；黑区 Alpha=0 完全透明 */
    private fun drawLuminanceMaskOverlay(canvas: AndroidCanvas, mask: Bitmap, width: Int, height: Int) {
        val scaled = if (mask.width != width || mask.height != height) {
            Bitmap.createScaledBitmap(mask, width, height, true)
        } else {
            mask
        }
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val luma = luminanceOf(pixels[i])
            pixels[i] = if (luma >= MASK_PAINTED_LUMA_THRESHOLD) (luma shl 24) or 0xFFFFFF else 0
        }
        if (scaled !== mask) scaled.recycle()
        val overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        overlay.setPixels(pixels, 0, width, 0, 0, width, height)
        canvas.drawBitmap(overlay, 0f, 0f, null)
        overlay.recycle()
    }

    /** 底版转不透明黑/白写入目标位图（编码用）：亮度≥阈值写白，否则保持黑底 */
    private fun drawOpaqueLuminanceMask(target: Bitmap, mask: Bitmap, width: Int, height: Int) {
        val scaled = if (mask.width != width || mask.height != height) {
            Bitmap.createScaledBitmap(mask, width, height, true)
        } else {
            mask
        }
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            pixels[i] = if (luminanceOf(pixels[i]) >= MASK_PAINTED_LUMA_THRESHOLD) Color.WHITE else Color.BLACK
        }
        target.setPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== mask) scaled.recycle()
    }

    /** 取像素最大通道亮度（白底黑字蒙版的已涂判定） */
    private fun luminanceOf(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return maxOf(r, g, b)
    }

    /**
     * 按落笔顺序回放全部笔画：画笔 SRC 写色（useBrushColor? 当前颜色: 白色）、橡皮 CLEAR 擦除。
     * 顺序回放保证"后画的橡皮擦掉先前笔画、再后画的画笔覆盖擦除区"的正确语义。
     *
     * @param useBrushColor true=显示预览使用当前画笔颜色，false=mask 编码使用白色
     */
    private fun rasterizeStrokes(canvas: AndroidCanvas, width: Int, height: Int, useBrushColor: Boolean = false) {
        // 画笔颜色：显示用当前颜色，编码用白色
        val penColor = if (useBrushColor) {
            Color.argb(
                (brushColor.alpha * 255).toInt(),
                (brushColor.red * 255).toInt(),
                (brushColor.green * 255).toInt(),
                (brushColor.blue * 255).toInt(),
            )
        } else {
            Color.WHITE
        }
        val penPaint = Paint().apply {
            color = penColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        val eraserPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val path = AndroidPath()
        strokes.forEach { stroke ->
            val paint = if (stroke.isErase) eraserPaint else penPaint
            drawSingleStroke(canvas, paint, path, stroke, width, height)
        }
    }

    /** 绘制单条笔画到目标位图；单点笔画补一段 infinitesimal 线段以确保画出圆点而非空 */
    private fun drawSingleStroke(
        canvas: AndroidCanvas,
        paint: Paint,
        path: AndroidPath,
        stroke: MaskStroke,
        width: Int,
        height: Int,
    ) {
        if (stroke.points.isEmpty()) return
        paint.strokeWidth = (stroke.brushFraction * width).coerceAtLeast(1f)
        path.reset()
        val first = stroke.points[0]
        path.moveTo(first.x * width, first.y * height)
        for (i in 1 until stroke.points.size) {
            path.lineTo(stroke.points[i].x * width, stroke.points[i].y * height)
        }
        if (stroke.points.size == 1) {
            path.lineTo(first.x * width + 0.01f, first.y * height)
        }
        canvas.drawPath(path, paint)
    }
}

/** 便捷构造：在 Composable 作用域内 remember 一个 [MaskDrawingController] */
@Composable
fun rememberMaskDrawingController(): MaskDrawingController = remember { MaskDrawingController() }

/** 将画布坐标转为相对图像矩形的归一化坐标 [0,1]，超出矩形则夹取到边界 */
private fun Offset.toNormalized(rect: Rect): Offset {
    val nx = ((x - rect.left) / rect.width).coerceIn(0f, 1f)
    val ny = ((y - rect.top) / rect.height).coerceIn(0f, 1f)
    return Offset(nx, ny)
}

/**
 * 在 DrawScope 内绘制单条蒙版笔画（归一化坐标映射到图像矩形像素位置）。
 * 单点笔画补一段 infinitesimal 线段，确保画面上能看到圆点而非空。
 */
private fun DrawScope.drawMaskStroke(
    points: List<Offset>,
    brushFraction: Float,
    rect: Rect,
    color: ComposeColor,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    if (points.isEmpty() || rect.width <= 0f || rect.height <= 0f) return
    val strokeWidthPx = (brushFraction * rect.width).coerceAtLeast(1f)
    val path = Path().apply {
        val first = points.first()
        moveTo(rect.left + first.x * rect.width, rect.top + first.y * rect.height)
        for (i in 1 until points.size) {
            val p = points[i]
            lineTo(rect.left + p.x * rect.width, rect.top + p.y * rect.height)
        }
        if (points.size == 1) {
            lineTo(rect.left + first.x * rect.width + 0.01f, rect.top + first.y * rect.height)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = blendMode,
    )
}

/** 8 色调色板：白/红/橙/黄/绿/青/蓝/紫（对齐参照 InpaintScreen colorOptions，替换 MAGENTA/BLACK 为橙/紫） */
private val brushColorOptions = arrayOf(
    ComposeColor.White,
    ComposeColor.Red,
    ComposeColor(0xFFFFA500), // 橙
    ComposeColor.Yellow,
    ComposeColor.Green,
    ComposeColor.Cyan,
    ComposeColor.Blue,
    ComposeColor(0xFF800080), // 紫
)

/**
 * 蒙版绘制画布：在源图之上叠加可涂抹的透明画布，用户拖动手指绘制彩色 inpaint 蒙版。
 *
 * 手势仲裁（对齐参照 InpaintScreen 473-602 行）：
 * - 单指触摸：进入绘制模式，拖动产生笔画点
 * - 双指触摸：进入缩放/平移模式，打断当前绘制
 * - 缩放范围 coerceIn(1f, 5f)，平移跟随 centroid 变化
 * - graphicsLayer 变换同时作用于源图与蒙版叠加层
 *
 * 橡皮擦显示原理：进行中的橡皮笔画在 saveLayer 离屏层内以 BlendMode.Clear 打孔，
 * 把底版+已提交笔画的叠加层擦出透明洞，松手后由控制器按顺序回放固化到预览缓存。
 *
 * 画笔颜色：控制器 [MaskDrawingController.brushColor] 决定新笔画与全部已有笔画的显示颜色，
 * mask 编码始终输出白色以保证后端 inpaint 语义正确。
 *
 * @param sourceBitmap 源图（img2img / inpaint 输入图）
 * @param controller 承载笔画状态、撤销/重做、画笔颜色与编码能力，由 [rememberMaskDrawingController] 持有
 * @param brushSizeDp 画笔/橡皮粗细（调用方滑条控制，建议 5-50dp）
 * @param toolMode 当前工具：PEN 画笔 / ERASER 橡皮
 * @param showClearButton 是否在画布右上角叠加清除按钮（工具栏已有清除入口时传 false）
 */
@Composable
fun MaskDrawingCanvas(
    sourceBitmap: Bitmap,
    controller: MaskDrawingController,
    modifier: Modifier = Modifier,
    brushSizeDp: Dp = 24.dp,
    toolMode: MaskToolMode = MaskToolMode.PEN,
    showClearButton: Boolean = true,
) {
    val density = LocalDensity.current
    val brushPx = with(density) { brushSizeDp.toPx() }
    var imageRect by remember { mutableStateOf<Rect?>(null) }
    val currentPoints = remember { mutableStateListOf<Offset>() }
    var currentBrushFraction by remember { mutableFloatStateOf(0.02f) }
    var currentStrokeIsErase by remember { mutableStateOf(false) }
    // 双指缩放/平移状态（对齐参照 InpaintScreen 141-143 行）
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // 是否正在绘制中（用于手势仲裁与 UI 反馈）
    var isDrawing by remember { mutableStateOf(false) }
    // 颜色选择器弹窗控制
    var showColorPicker by remember { mutableStateOf(false) }

    // 预览位图尺寸：源图尺寸压到最长边 1024 以内，兼顾清晰度与重绘开销
    val displaySize = remember(sourceBitmap) {
        val maxEdge = maxOf(sourceBitmap.width, sourceBitmap.height)
        val scale = if (maxEdge > MASK_DISPLAY_MAX_EDGE) {
            MASK_DISPLAY_MAX_EDGE.toFloat() / maxEdge
        } else {
            1f
        }
        IntSize(
            (sourceBitmap.width * scale).roundToInt().coerceAtLeast(1),
            (sourceBitmap.height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { imageRect = computeFitRect(it.size, sourceBitmap) }
            // 手势仲裁层：单指画、双指缩放/平移（对齐参照 InpaintScreen 473-602 行）
            .pointerInput(controller, toolMode, brushSizeDp) {
                val containerWidth = size.width.toFloat()
                val containerHeight = size.height.toFloat()

                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    var isMultiTouch = false
                    var drawStarted = false

                    // 触摸坐标转内容坐标：逆 graphicsLayer 变换（对齐参照 touchToContent 482-489 行）
                    fun touchToContent(pos: Offset): Offset {
                        val pivotX = containerWidth / 2f
                        val pivotY = containerHeight / 2f
                        return Offset(
                            (pos.x - offsetX - pivotX) / scale + pivotX,
                            (pos.y - offsetY - pivotY) / scale + pivotY,
                        )
                    }

                    // 首点落笔：尝试进入绘制模式
                    val contentPos = touchToContent(firstDown.position)
                    val rect = imageRect
                    if (rect != null && rect.width > 0f && rect.height > 0f) {
                        val firstImgPt = contentPos.toNormalized(rect)
                        // 缩放时画笔宽度除以 scale，保持屏幕视觉粗细一致（对齐参照 515 行）
                        val scaledBrushFraction = ((brushPx / rect.width).coerceAtLeast(0.001f)) / scale
                        drawStarted = true
                        isDrawing = true
                        currentPoints.clear()
                        currentPoints.add(firstImgPt)
                        currentBrushFraction = scaledBrushFraction
                        currentStrokeIsErase = toolMode == MaskToolMode.ERASER
                    }

                    var prevPointers: List<Offset> = listOf(firstDown.position)

                    while (true) {
                        val event = awaitPointerEvent()
                        val activeChanges = event.changes.filter { it.pressed }

                        if (activeChanges.isEmpty()) {
                            // 所有手指抬起：提交或丢弃当前笔画
                            if (drawStarted && !isMultiTouch) {
                                isDrawing = false
                                if (currentPoints.isNotEmpty()) {
                                    controller.addStroke(
                                        MaskStroke(currentPoints.toList(), currentBrushFraction, currentStrokeIsErase)
                                    )
                                    currentPoints.clear()
                                }
                            } else {
                                isDrawing = false
                                currentPoints.clear()
                            }
                            break
                        }

                        if (activeChanges.size >= 2) {
                            // 双指触摸：缩放/平移模式，打断绘制（对齐参照 534-589 行）
                            if (!isMultiTouch) {
                                isMultiTouch = true
                                isDrawing = false
                                currentPoints.clear()
                                drawStarted = false
                                prevPointers = activeChanges.map { it.position }
                            }

                            val positions = activeChanges.map { it.position }
                            val prevCentroid = Offset(
                                prevPointers.map { it.x }.average().toFloat(),
                                prevPointers.map { it.y }.average().toFloat(),
                            )
                            val currCentroid = Offset(
                                positions.map { it.x }.average().toFloat(),
                                positions.map { it.y }.average().toFloat(),
                            )

                            // 计算双指间距变化作为缩放因子
                            val prevSpread = if (prevPointers.size >= 2) {
                                val dx = prevPointers[0].x - prevPointers[1].x
                                val dy = prevPointers[0].y - prevPointers[1].y
                                sqrt(dx * dx + dy * dy)
                            } else {
                                0f
                            }
                            val currSpread = if (positions.size >= 2) {
                                val dx = positions[0].x - positions[1].x
                                val dy = positions[0].y - positions[1].y
                                sqrt(dx * dx + dy * dy)
                            } else {
                                0f
                            }

                            val zoomFactor = if (prevSpread > 10f) currSpread / prevSpread else 1f
                            val panDelta = currCentroid - prevCentroid

                            val newScale = (scale * zoomFactor).coerceIn(1f, 5f)
                            val effectiveZoom = newScale / scale

                            val pivotX = containerWidth / 2f
                            val pivotY = containerHeight / 2f
                            val newOffsetX =
                                (currCentroid.x - pivotX) * (1f - effectiveZoom) + offsetX * effectiveZoom + panDelta.x
                            val newOffsetY =
                                (currCentroid.y - pivotY) * (1f - effectiveZoom) + offsetY * effectiveZoom + panDelta.y

                            scale = newScale
                            offsetX = newOffsetX
                            offsetY = newOffsetY

                            prevPointers = positions
                            activeChanges.forEach { it.consume() }
                        } else if (!isMultiTouch && drawStarted) {
                            // 单指拖动：绘制模式（对齐参照 590-599 行）
                            val change = activeChanges.first()
                            val cPos = touchToContent(change.position)
                            val imgPt = cPos.toNormalized(rect ?: continue)
                            currentPoints.add(imgPt)
                            change.consume()
                            prevPointers = listOf(change.position)
                        }
                    }
                }
            }
            // graphicsLayer 变换：缩放与平移同时作用于源图与蒙版叠加层（对齐参照 607-612 行）
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY,
            ),
    ) {
        // 源图
        Image(
            bitmap = sourceBitmap.asImageBitmap(),
            contentDescription = "源图",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // 蒙版绘制层（仅负责绘制，手势由外层 Box 统一仲裁）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rect = imageRect ?: return@Canvas
            val display = controller.renderDisplayBitmap(displaySize.width, displaySize.height)
            val dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt())
            val dstSize = IntSize(rect.width.toInt(), rect.height.toInt())
            if (currentStrokeIsErase && currentPoints.isNotEmpty()) {
                // 橡皮进行中：离屏层内先画叠加层，再用 Clear 混合模式擦除，松手后固化进控制器
                drawIntoCanvas { canvas -> canvas.saveLayer(rect, androidx.compose.ui.graphics.Paint()) }
                if (display != null) {
                    drawImage(
                        image = display.asImageBitmap(),
                        srcSize = IntSize(display.width, display.height),
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        alpha = 0.6f,
                    )
                }
                drawMaskStroke(currentPoints, currentBrushFraction, rect, ComposeColor.White, BlendMode.Clear)
                drawIntoCanvas { canvas -> canvas.restore() }
            } else {
                // 画笔进行中或无进行中笔画：叠加层直绘，当前笔画使用控制器画笔颜色预览
                if (display != null) {
                    drawImage(
                        image = display.asImageBitmap(),
                        srcSize = IntSize(display.width, display.height),
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        alpha = 0.6f,
                    )
                }
                if (currentPoints.isNotEmpty()) {
                    drawMaskStroke(
                        currentPoints, currentBrushFraction, rect,
                        controller.brushColor.copy(alpha = 0.6f),
                    )
                }
            }
        }
        // 画笔颜色指示器按钮（左下角，对齐参照 InpaintScreen 颜色选择器 357-403 行）
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Surface(
                onClick = { showColorPicker = true },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = controller.brushColor,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 4.dp,
            ) {}
        }
        // 清除按钮（右上角）
        if (showClearButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { controller.clear(); currentPoints.clear() },
            ) {
                Text(
                    "清除",
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }

    // 颜色选择器弹窗（对齐参照 InpaintScreen 357-403 行）
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("画笔颜色") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(brushColorOptions.size) { index ->
                            val color = brushColorOptions[index]
                            val isSelected = color == controller.brushColor
                            Surface(
                                onClick = {
                                    controller.brushColor = color
                                    showColorPicker = false
                                },
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(48.dp),
                                shape = CircleShape,
                                color = color,
                                border = BorderStroke(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                                shadowElevation = if (isSelected) 4.dp else 1.dp,
                            ) {}
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) {
                    Text("关闭")
                }
            },
        )
    }
}

/** 计算 ContentScale.Fit 下源图在容器内的实际显示矩形（像素），与 Image 渲染区域一致 */
internal fun computeFitRect(containerSize: IntSize, source: Bitmap): Rect {
    val boxW = containerSize.width.toFloat()
    val boxH = containerSize.height.toFloat()
    if (boxW <= 0f || boxH <= 0f || source.width <= 0 || source.height <= 0) {
        return Rect(0f, 0f, boxW, boxH)
    }
    val srcAspect = source.width.toFloat() / source.height.toFloat()
    val boxAspect = boxW / boxH
    val scaledW: Float
    val scaledH: Float
    if (srcAspect > boxAspect) {
        scaledW = boxW
        scaledH = boxW / srcAspect
    } else {
        scaledH = boxH
        scaledW = boxH * srcAspect
    }
    val left = (boxW - scaledW) / 2f
    val top = (boxH - scaledH) / 2f
    return Rect(left, top, left + scaledW, top + scaledH)
}
/**
 * CropImageScreen.kt - 自包含 Compose 图片裁剪界面
 *
 * 功能：
 * - 全屏展示源图，叠加可拖动 / 可缩放的裁剪矩形
 * - 四角手柄缩放，框体内拖动整体平移，框外区域半透明遮罩
 * - 确认按钮将裁剪区域映射回源图像素并产出 Bitmap 与裁剪矩形，经 onCropReady 回调返回
 * - 无第三方依赖，仅用 Material3 + Compose 原生 API
 *
 * 设计要点：裁剪框以归一化坐标 [0,1] 存于 State，绘制与手势均通过 State 读写，
 * 拖动只触发 Canvas 重绘而不 recompose 顶层 Scaffold/TopAppBar。
 * 回传裁剪矩形（源图像素坐标系）用于 inpaint 结果羽化贴回原图（InpaintBlendUtils）。
 */
package com.qihao.open.rwkv.ui.image

import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** 裁剪框四角，用于命中检测与缩放方向判定 */
enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** 当前手势模式：无 / 平移 / 缩放 */
private enum class CropGestureMode { NONE, MOVE, RESIZE }

/** 裁剪框最小归一化边长，防止缩成不可裁剪的点 */
private const val MIN_CROP = 0.1f

/**
 * 图片裁剪界面：拖动框体平移、拖动四角缩放，确认后返回裁剪 [Bitmap] 与源图像素坐标系的裁剪矩形。
 *
 * @param bitmap 待裁剪源图
 * @param onCropReady 用户点击确认后回调：第一个参数为按裁剪区域裁出的 [Bitmap]，
 *   第二个参数为裁剪矩形在源图像素坐标系中的位置（inpaint 结果贴回原图需要）。
 * @param onCancel 用户返回 / 取消时回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropImageScreen(
    bitmap: Bitmap,
    onCropReady: (Bitmap, AndroidRect) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 裁剪框与图像矩形均用 State 持有，绘制/手势直接读写，避免顶层 recompose
    val imageRectState = remember { mutableStateOf<Rect?>(null) }
    val cropRectState = remember { mutableStateOf(Rect(0.1f, 0.1f, 0.9f, 0.9f)) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("裁剪图片") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("返回") }
                },
                actions = {
                    TextButton(onClick = {
                        val (cropped, cropRect) = cropSourceBitmap(bitmap, cropRectState.value)
                        onCropReady(cropped, cropRect)
                    }) {
                        Text("确认")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            CropCanvas(bitmap, imageRectState, cropRectState)
        }
    }
}

/** 裁剪画布：渲染源图 + 裁剪遮罩，并承载拖动 / 缩放手势 */
@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    imageRectState: MutableState<Rect?>,
    cropRectState: MutableState<Rect>,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { imageRectState.value = computeFitRect(it.size, bitmap) },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "待裁剪图片",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageRectState, cropRectState) {
                    var mode = CropGestureMode.NONE
                    var activeCorner: CropCorner? = null
                    detectDragGestures(
                        onDragStart = { offset ->
                            val rect = imageRectState.value
                            val crop = cropRectState.value
                            if (rect != null && rect.width > 0f && rect.height > 0f) {
                                mode = resolveGesture(rect, crop, offset).let { (m, c) ->
                                    activeCorner = c
                                    m
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val rect = imageRectState.value
                            if (rect != null && rect.width > 0f && rect.height > 0f) {
                                val dx = dragAmount.x / rect.width
                                val dy = dragAmount.y / rect.height
                                val current = cropRectState.value
                                cropRectState.value = when (mode) {
                                    CropGestureMode.MOVE -> moveCrop(current, dx, dy)
                                    CropGestureMode.RESIZE -> activeCorner
                                        ?.let { resizeCrop(current, it, dx, dy) } ?: current
                                    CropGestureMode.NONE -> current
                                }
                                change.consume()
                            }
                        },
                        onDragEnd = { mode = CropGestureMode.NONE; activeCorner = null },
                        onDragCancel = { mode = CropGestureMode.NONE; activeCorner = null },
                    )
                },
        ) {
            val rect = imageRectState.value ?: return@Canvas
            drawCropOverlay(rect, cropRectState.value)
        }
    }
}

/** 根据按下位置判定是缩放某角、平移框体，还是框外无操作 */
private fun Density.resolveGesture(
    rect: Rect,
    crop: Rect,
    offset: Offset,
): Pair<CropGestureMode, CropCorner?> {
    val nx = (offset.x - rect.left) / rect.width
    val ny = (offset.y - rect.top) / rect.height
    val rX = 48.dp.toPx() / rect.width // 命中半径按图像宽度归一化；PointerInputScope 即 Density
    val rY = 48.dp.toPx() / rect.height
    val corner = hitCorner(crop, nx, ny, rX, rY)
    return when {
        corner != null -> CropGestureMode.RESIZE to corner
        nx in crop.left..crop.right && ny in crop.top..crop.bottom -> CropGestureMode.MOVE to null
        else -> CropGestureMode.NONE to null
    }
}

/** 命中四角手柄：归一化坐标距离在半径内即判定该角 */
private fun hitCorner(crop: Rect, nx: Float, ny: Float, rX: Float, rY: Float): CropCorner? {
    val corners = listOf(
        CropCorner.TOP_LEFT to Offset(crop.left, crop.top),
        CropCorner.TOP_RIGHT to Offset(crop.right, crop.top),
        CropCorner.BOTTOM_LEFT to Offset(crop.left, crop.bottom),
        CropCorner.BOTTOM_RIGHT to Offset(crop.right, crop.bottom),
    )
    return corners.firstOrNull { abs(nx - it.second.x) <= rX && abs(ny - it.second.y) <= rY }?.first
}

/** 平移裁剪框：整体位移并夹取到 [0,1]，保持宽高不变 */
private fun moveCrop(crop: Rect, dx: Float, dy: Float): Rect {
    val w = crop.width
    val h = crop.height
    val left = (crop.left + dx).coerceIn(0f, 1f - w)
    val top = (crop.top + dy).coerceIn(0f, 1f - h)
    return Rect(left, top, left + w, top + h)
}

/** 缩放裁剪框：按命中角调整对应两条边，约束最小边长与边界 */
private fun resizeCrop(crop: Rect, corner: CropCorner, dx: Float, dy: Float): Rect {
    var left = crop.left; var top = crop.top; var right = crop.right; var bottom = crop.bottom
    when (corner) {
        CropCorner.TOP_LEFT -> {
            left = (left + dx).coerceIn(0f, right - MIN_CROP)
            top = (top + dy).coerceIn(0f, bottom - MIN_CROP)
        }
        CropCorner.TOP_RIGHT -> {
            right = (right + dx).coerceIn(left + MIN_CROP, 1f)
            top = (top + dy).coerceIn(0f, bottom - MIN_CROP)
        }
        CropCorner.BOTTOM_LEFT -> {
            left = (left + dx).coerceIn(0f, right - MIN_CROP)
            bottom = (bottom + dy).coerceIn(top + MIN_CROP, 1f)
        }
        CropCorner.BOTTOM_RIGHT -> {
            right = (right + dx).coerceIn(left + MIN_CROP, 1f)
            bottom = (bottom + dy).coerceIn(top + MIN_CROP, 1f)
        }
    }
    return Rect(left, top, right, bottom)
}

/**
 * 按裁剪框归一化坐标从源图裁出 [Bitmap]，并同步回传源图像素坐标系的裁剪矩形。
 * 坐标夹取保证不越界；裁剪矩形与裁出位图严格一致，供 inpaint 结果贴回原图时对齐。
 */
private fun cropSourceBitmap(source: Bitmap, crop: Rect): Pair<Bitmap, AndroidRect> {
    val srcW = source.width
    val srcH = source.height
    val x = (crop.left * srcW).roundToInt().coerceIn(0, srcW - 1)
    val y = (crop.top * srcH).roundToInt().coerceIn(0, srcH - 1)
    val w = ((crop.right - crop.left) * srcW).roundToInt().coerceIn(1, srcW - x)
    val h = ((crop.bottom - crop.top) * srcH).roundToInt().coerceIn(1, srcH - y)
    return Bitmap.createBitmap(source, x, y, w, h) to AndroidRect(x, y, x + w, y + h)
}

/** 绘制裁剪遮罩：框外半透明、框线高亮、四角手柄 */
private fun DrawScope.drawCropOverlay(imageRect: Rect, crop: Rect) {
    val left = imageRect.left + crop.left * imageRect.width
    val top = imageRect.top + crop.top * imageRect.height
    val right = imageRect.left + crop.right * imageRect.width
    val bottom = imageRect.top + crop.bottom * imageRect.height
    val scrim = ComposeColor.Black.copy(alpha = 0.5f)
    // 四周遮罩：上、下、左、右
    drawRect(scrim, topLeft = Offset(imageRect.left, imageRect.top), size = Size(imageRect.width, top - imageRect.top))
    drawRect(scrim, topLeft = Offset(imageRect.left, bottom), size = Size(imageRect.width, imageRect.bottom - bottom))
    drawRect(scrim, topLeft = Offset(imageRect.left, top), size = Size(left - imageRect.left, bottom - top))
    drawRect(scrim, topLeft = Offset(right, top), size = Size(imageRect.right - right, bottom - top))
    // 裁剪框边线
    drawRect(
        color = ComposeColor.White,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = 2.dp.toPx()),
    )
    // 四角手柄
    val hs = 24.dp.toPx()
    listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach { p ->
        drawRect(ComposeColor.White, topLeft = Offset(p.x - hs / 2, p.y - hs / 2), size = Size(hs, hs))
    }
}

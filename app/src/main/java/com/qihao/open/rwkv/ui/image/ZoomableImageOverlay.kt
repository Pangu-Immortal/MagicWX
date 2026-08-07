/**
 * ZoomableImageOverlay - 全屏可缩放图片预览遮罩
 *
 * 功能：
 * - ZoomableImageOverlay(): 全屏黑底 overlay，结果页大图与历史页全屏查看共用
 *   · 双指缩放 0.5x ~ 5x（detectTransformGestures）
 *   · 缩放 > 1 时单指平移图片
 *   · 双击复位缩放与位移
 *   · 右上角关闭按钮；点击图片以外空白区域同样关闭
 *   · 底部实时显示当前缩放百分比
 *   · bottomContent 插槽：历史页在底部挂载"复用/复制/保存/删除"动作栏
 *
 * 设计说明：交互模型对齐 modules/local-dream/ui/components/ZoomableImageOverlay.kt
 * （焦点缩放的位移补偿、点击空白关闭的命中几何均照搬该实现），
 * 并按 MagicWX 验收口径补齐双击复位、右上角关闭与缩放百分比文案。
 */
package com.qihao.open.rwkv.ui.image

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** 缩放系数下限：允许轻微缩小查看全貌，低于该值图片过小无意义 */
private const val MIN_ZOOM_SCALE = 0.5f

/** 缩放系数上限：防止无限放大导致像素糊化与位移失控 */
private const val MAX_ZOOM_SCALE = 5f

/**
 * 全屏可缩放图片预览遮罩
 *
 * @param bitmap 待预览图片；为 null 时仅显示黑底遮罩（点击即关闭）
 * @param onDismiss 关闭遮罩回调（系统返回键、关闭按钮、点击空白均触发）
 * @param bottomContent 底部动作区插槽，默认无；历史页用于承载操作按钮栏
 */
@Composable
fun ZoomableImageOverlay(
    bitmap: Bitmap?,
    onDismiss: () -> Unit,
    bottomContent: (@Composable () -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }          // 当前缩放系数
    var offsetX by remember { mutableFloatStateOf(0f) }        // 水平位移（相对中心）
    var offsetY by remember { mutableFloatStateOf(0f) }        // 垂直位移（相对中心）

    // 系统返回键直接关闭遮罩，而不是退出整个页面
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))       // 全屏黑底，突出图片本身
            .pointerInput(Unit) {
                // 双指缩放 + 平移：焦点缩放时补偿位移，保证手指捏合中心下的图像内容稳定
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    scale = (scale * zoom).coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)

                    val centerX = size.width / 2f
                    val centerY = size.height / 2f

                    // 把焦点坐标换算到未缩放图像坐标系，再按新旧缩放差补回位移
                    val focusX = (centroid.x - centerX - offsetX) / oldScale
                    val focusY = (centroid.y - centerY - offsetY) / oldScale
                    offsetX += focusX * oldScale - focusX * scale
                    offsetY += focusY * oldScale - focusY * scale

                    // 单指平移只在放大状态生效：未放大时图片完整可见，平移没有意义
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            }
            .pointerInput(bitmap) {
                detectTapGestures(
                    onDoubleTap = {
                        // 双击复位：回到初始缩放与居中位置
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onTap = { offset ->
                        val bmp = bitmap
                        if (bmp == null) {
                            onDismiss()                        // 无图遮罩：任意点击关闭
                            return@detectTapGestures
                        }
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        // 图片按居中正方形容器 + ContentScale.Fit 摆放，再叠加 graphicsLayer
                        // 的缩放与位移。这里复现同一几何，算出当前可见图片矩形，
                        // 点击图片外空白（含 Fit 留边）即关闭遮罩
                        val square = minOf(size.width, size.height).toFloat()
                        val aspect = bmp.width.toFloat() / bmp.height.toFloat()
                        val baseWidth = if (aspect >= 1f) square else square * aspect
                        val baseHeight = if (aspect >= 1f) square / aspect else square
                        val scaledWidth = baseWidth * scale
                        val scaledHeight = baseHeight * scale

                        val left = centerX + offsetX - scaledWidth / 2f
                        val right = centerX + offsetX + scaledWidth / 2f
                        val top = centerY + offsetY - scaledHeight / 2f
                        val bottom = centerY + offsetY + scaledHeight / 2f

                        if (offset.x < left || offset.x > right || offset.y < top || offset.y > bottom) {
                            onDismiss()
                        }
                    }
                )
            },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "全屏预览图片",
                contentScale = ContentScale.Fit,               // 与命中几何一致：Fit 留边，点击留边区可关闭
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .testTag("zoomable-overlay-image")
            )
        }

        // 右上角关闭按钮：半透明深色底白图标，任何背景下都清晰可点
        FilledTonalIconButton(
            onClick = onDismiss,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
                .testTag("zoomable-overlay-close")
        ) {
            Icon(Icons.Default.Close, contentDescription = "关闭预览")
        }

        // 底部区域：可选动作栏 + 当前缩放百分比
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bottomContent?.invoke()
            Text(
                text = "缩放 ${(scale * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("zoomable-overlay-scale")
            )
        }
    }
}

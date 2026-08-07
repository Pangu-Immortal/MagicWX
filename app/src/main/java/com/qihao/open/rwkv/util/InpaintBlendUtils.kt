/**
 * InpaintBlendUtils.kt - inpaint 结果羽化贴回原图工具
 *
 * 功能：
 * - blendInpaintResult(): 把 inpaint 小尺寸结果缩放到裁剪矩形大小，羽化混合贴回裁剪前完整原图，
 *   产出"修改后的原图"，而不是孤立的 512 结果块
 * - drawInpaintPatch(): 按蒙版已涂区域做羽化混合；蒙版无已涂像素时退化为整块粘贴
 * - buildFeatheredAlphaMap(): 生成 patch 的 Alpha 图——蒙版内部不透明、外侧羽化带 smoothstep 衰减到
 *   透明、朝向原图内部的 patch 边缘额外衰减到 0
 * - chamferDistanceFromMask(): 两遍 3-4 chamfer 距离变换（每个像素到最近已涂像素的距离）
 *
 * 移植来源：modules/local-dream .../ui/screens/ModelRunSupport.kt
 * drawInpaintPatch / buildFeatheredAlphaMap / chamferDistanceFromMask（308-439 行），
 * 保存时贴回逻辑对齐 ModelRunScreen.handleSaveImage（1058-1126 行）。
 *
 * 相对参照的唯一口径改动：参照的蒙版是"透明底白笔画"（alpha≥128 判已涂），
 * MagicWX 的蒙版是"黑底白笔画"不透明 PNG，因此已涂判定改为
 * 「alpha≥128 且亮度≥128」，同时兼容两种格式。
 *
 * 算法关键步骤（与参照逐行对齐）：
 * 1. FadeEdges：patch 位于原图内部的四条边标记为需衰减（fade）。与图像外沿齐平的边保持全强度，
 *    防止蒙版贴着裁剪边界涂抹时留下一条直线接缝；
 * 2. 蒙版缩放到 patch 的低分辨率（长边≤ALPHA_MAP_MAX_DIM）读像素，
 *    两遍 3-4 chamfer 扫描得到每个像素到最近已涂像素的距离（横竖邻 +3、对角邻 +4），
 *    全图无已涂像素返回 null → 调用方退化为整块不透明粘贴；
 * 3. 羽化带宽度 = max(MIN_FEATHER_PX, patch 长边 / FEATHER_DIVISOR)（全分辨率像素），
 *    换算低分辨率 featherLow，再乘 CHAMFER_STRAIGHT 得 chamfer 单位阈值 feather；
 * 4. 逐像素：maskOpacity = 1 - smoothstep(min(dist/feather,1))
 *    （蒙版内 dist=0 → 不透明，向外羽化带内平滑降到 0）；
 *    edgeOpacity = smoothstep(min(最近 fade 边距离/featherLow,1))
 *    （朝 patch 内部边缘平滑降到 0，无 fade 边时恒 1）；
 *    最终 alpha = maskOpacity × edgeOpacity；
 * 5. patch 拷贝为可变 ARGB 并 setHasAlpha(true)（否则 DST_IN 结果被当不透明黑绘制），
 *    用 DST_IN 把低分辨率 Alpha 图双线性放大后乘进 patch，再按裁剪矩形坐标画到原图。
 */
package com.qihao.open.rwkv.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.Log
import kotlin.math.roundToInt

/** inpaint 结果羽化贴回原图工具，全部方法不持有状态 */
object InpaintBlendUtils {

    private const val TAG = "InpaintBlendUtils"

    /** Alpha 图低分辨率最长边上限：距离场在低分辨率上计算，贴回时双线性放大，控制逐像素运算量 */
    private const val ALPHA_MAP_MAX_DIM = 1024

    /** 蒙版像素 Alpha 阈值：低于该值视为未涂（兼容参照透明底蒙版格式） */
    private const val MASK_PAINTED_MIN_ALPHA = 128

    /** 蒙版像素亮度阈值：MagicWX 黑底白字蒙版按亮度判已涂，黑底亮度 0 不满足 */
    private const val MASK_PAINTED_MIN_LUMA = 128

    /** chamfer 距离权重：横竖相邻代价 3、对角相邻代价 4（3-4 chamfer 近似欧氏距离） */
    private const val CHAMFER_STRAIGHT = 3
    private const val CHAMFER_DIAGONAL = 4

    /** 羽化带宽度（全分辨率像素）= max(MIN_FEATHER_PX, patch 长边 / FEATHER_DIVISOR) */
    private const val FEATHER_DIVISOR = 32
    private const val MIN_FEATHER_PX = 16

    /**
     * 把 inpaint 结果贴回裁剪前原图，返回"修改后的原图"新位图（不修改入参）。
     *
     * @param originalBitmap 裁剪前的完整原图
     * @param cropRect 生成输入图对应的裁剪矩形（originalBitmap 像素坐标系）
     * @param maskBitmap 本次生成使用的 inpaint 蒙版（任意尺寸，内部缩放到 patch 空间）；
     *   null 或无已涂像素时整块粘贴
     * @param resultBitmap inpaint 生成的结果图（通常是 512 级别小图）
     * @return 与 originalBitmap 同尺寸的合成位图（ARGB_8888）
     */
    fun blendInpaintResult(
        originalBitmap: Bitmap,
        cropRect: Rect,
        maskBitmap: Bitmap?,
        resultBitmap: Bitmap,
    ): Bitmap {
        require(cropRect.width() > 0 && cropRect.height() > 0) { "cropRect 无效: $cropRect" }
        require(
            cropRect.left >= 0 && cropRect.top >= 0 &&
                cropRect.right <= originalBitmap.width && cropRect.bottom <= originalBitmap.height,
        ) { "cropRect 越界: $cropRect, 原图 ${originalBitmap.width}x${originalBitmap.height}" }
        val composited = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)   // 只改副本，不动入参
        // 结果缩放到裁剪矩形尺寸（双线性过滤），保证贴回区域与原图像素一一对齐
        val patch = Bitmap.createScaledBitmap(resultBitmap, cropRect.width(), cropRect.height(), true)
        val patchDesc = "${patch.width}x${patch.height}"                     // 先记录尺寸，回收后不可再读
        try {
            drawInpaintPatch(composited, patch, maskBitmap, cropRect.left, cropRect.top)
        } finally {
            if (patch !== resultBitmap) patch.recycle()                        // 尺寸恰好一致时复用入参，不回收
        }
        Log.d(TAG, "已贴回原图: cropRect=$cropRect, patch=$patchDesc")
        return composited
    }

    /**
     * 把 inpaint 结果 [patch] 画到 [target] 的 ([left], [top]) 位置。
     * [mask] 存在已涂像素时走羽化混合（见文件头算法说明），避免矩形接缝；
     * 否则退化为整块不透明粘贴。[mask] 尺寸任意，内部缩放到 patch 比例空间。
     */
    internal fun drawInpaintPatch(target: Bitmap, patch: Bitmap, mask: Bitmap?, left: Int, top: Int) {
        val canvas = Canvas(target)
        // 位于原图内部的 patch 四边强制衰减到 0：蒙版贴着裁剪边界涂抹时也不会留下直线接缝；
        // 与图像外沿齐平的边保持全强度
        val fade = FadeEdges(
            left = left > 0,
            top = top > 0,
            right = left + patch.width < target.width,
            bottom = top + patch.height < target.height,
        )
        val alphaMap = mask?.let { buildFeatheredAlphaMap(it, patch.width, patch.height, fade) }
        if (alphaMap == null) {
            Log.d(TAG, "蒙版无已涂区域，退化为整块粘贴: left=$left, top=$top")
            canvas.drawBitmap(patch, left.toFloat(), top.toFloat(), null)
            return
        }
        val maskedPatch = patch.copy(Bitmap.Config.ARGB_8888, true)
        // 解码出的结果图不透明，copy() 保留 hasAlpha=false；不补这一步 DST_IN 结果会被画成不透明黑
        maskedPatch.setHasAlpha(true)
        Canvas(maskedPatch).drawBitmap(
            alphaMap,
            null,
            Rect(0, 0, maskedPatch.width, maskedPatch.height),
            Paint(Paint.FILTER_BITMAP_FLAG).apply {          // 双线性放大低分辨率 Alpha 图
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)   // dst.alpha *= src.alpha
            },
        )
        canvas.drawBitmap(maskedPatch, left.toFloat(), top.toFloat(), null)
        alphaMap.recycle()
        maskedPatch.recycle()
    }

    /** patch 四条边中哪些需要衰减到零 Alpha（位于原图内部的边） */
    private data class FadeEdges(
        val left: Boolean,
        val top: Boolean,
        val right: Boolean,
        val bottom: Boolean,
    )

    /** smoothstep 缓动：t∈[0,1] → 0→1 平滑过渡，用于羽化带与边缘衰减 */
    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    /**
     * 为 [patchW]×[patchH] 的 patch 构造混合 Alpha 图：蒙版已涂区域不透明，
     * 外侧羽化带 smoothstep 衰减到透明，并按 [fade] 朝向 patch 内部边缘额外衰减到 0。
     * 以低分辨率返回（调用方双线性放大贴回）。蒙版无已涂像素时返回 null。
     */
    private fun buildFeatheredAlphaMap(mask: Bitmap, patchW: Int, patchH: Int, fade: FadeEdges): Bitmap? {
        val downscale = minOf(1f, ALPHA_MAP_MAX_DIM.toFloat() / maxOf(patchW, patchH))
        val w = (patchW * downscale).roundToInt().coerceAtLeast(1)
        val h = (patchH * downscale).roundToInt().coerceAtLeast(1)
        val scaledMask = if (mask.width != w || mask.height != h) {
            Bitmap.createScaledBitmap(mask, w, h, true)
        } else {
            mask
        }
        val pixels = IntArray(w * h)
        scaledMask.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaledMask !== mask) scaledMask.recycle()

        val dist = chamferDistanceFromMask(pixels, w, h) ?: return null

        val featherPx = maxOf(MIN_FEATHER_PX, maxOf(patchW, patchH) / FEATHER_DIVISOR)
        // 羽化带宽换算：低分辨率像素 featherLow 与 chamfer 单位 feather
        val featherLow = (featherPx * downscale).coerceAtLeast(1f)
        val feather = featherLow * CHAMFER_STRAIGHT
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                // 蒙版外羽化：距离越大越透明，羽化带外全透明
                val maskOpacity = 1f - smoothstep((dist[i] / feather).coerceAtMost(1f))
                // 边缘衰减：到最近 fade 边的低分辨率像素距离，带内平滑降到 0
                var edgePx = Int.MAX_VALUE
                if (fade.left) edgePx = minOf(edgePx, x)
                if (fade.top) edgePx = minOf(edgePx, y)
                if (fade.right) edgePx = minOf(edgePx, w - 1 - x)
                if (fade.bottom) edgePx = minOf(edgePx, h - 1 - y)
                val edgeOpacity = if (edgePx == Int.MAX_VALUE) {
                    1f                                                          // patch 覆盖整图，无内部边
                } else {
                    smoothstep((edgePx / featherLow).coerceAtMost(1f))
                }
                val opacity = maskOpacity * edgeOpacity
                pixels[i] = ((opacity * 0xFF).roundToInt() shl 24) or 0xFFFFFF
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    /**
     * 对蒙版已涂区域做两遍 3-4 chamfer 距离变换：
     * 每个像素到最近已涂像素的距离，横竖邻代价 [CHAMFER_STRAIGHT]、对角邻 [CHAMFER_DIAGONAL]。
     * 正向（左上→右下）与反向（右下→左上）各扫描一遍即收敛到全局最近距离。
     * 全图无已涂像素时返回 null。
     */
    internal fun chamferDistanceFromMask(maskPixels: IntArray, w: Int, h: Int): IntArray? {
        val far = Int.MAX_VALUE / 2
        val dist = IntArray(maskPixels.size)
        var anyPainted = false
        for (i in dist.indices) {
            if (isMaskPainted(maskPixels[i])) {
                anyPainted = true                                  // 已涂像素距离 0（IntArray 默认值）
            } else {
                dist[i] = far
            }
        }
        if (!anyPainted) return null
        // 正向扫描：用左、上、左上、右上邻居松弛
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                var d = dist[i]
                if (d == 0) continue
                if (x > 0) d = minOf(d, dist[i - 1] + CHAMFER_STRAIGHT)
                if (y > 0) {
                    d = minOf(d, dist[i - w] + CHAMFER_STRAIGHT)
                    if (x > 0) d = minOf(d, dist[i - w - 1] + CHAMFER_DIAGONAL)
                    if (x < w - 1) d = minOf(d, dist[i - w + 1] + CHAMFER_DIAGONAL)
                }
                dist[i] = d
            }
        }
        // 反向扫描：用右、下、右下、左下邻居松弛
        for (y in h - 1 downTo 0) {
            for (x in w - 1 downTo 0) {
                val i = y * w + x
                var d = dist[i]
                if (d == 0) continue
                if (x < w - 1) d = minOf(d, dist[i + 1] + CHAMFER_STRAIGHT)
                if (y < h - 1) {
                    d = minOf(d, dist[i + w] + CHAMFER_STRAIGHT)
                    if (x < w - 1) d = minOf(d, dist[i + w + 1] + CHAMFER_DIAGONAL)
                    if (x > 0) d = minOf(d, dist[i + w - 1] + CHAMFER_DIAGONAL)
                }
                dist[i] = d
            }
        }
        return dist
    }

    /**
     * 蒙版像素是否已涂：Alpha≥128 且亮度≥128。
     * 同时覆盖参照的透明底白笔画（Alpha 判定）与 MagicWX 的黑底白笔画 PNG（亮度判定）。
     */
    internal fun isMaskPainted(pixel: Int): Boolean {
        if ((pixel ushr 24) < MASK_PAINTED_MIN_ALPHA) return false
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return maxOf(r, g, b) >= MASK_PAINTED_MIN_LUMA
    }
}

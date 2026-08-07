/**
 * InpaintBlendUtilsTest - inpaint 羽化贴回算法契约测试
 *
 * 功能：
 * - 验证 chamfer 距离变换的边界行为：无已涂像素返回 null / 全已涂全零 / 单点距离扩散 / 多源最近距离
 * - 验证已涂判定同时覆盖 MagicWX 黑底白字 PNG 与参照透明底白笔画两种蒙版格式
 *
 * 说明：blendInpaintResult/drawInpaintPatch/buildFeatheredAlphaMap 依赖 Android Bitmap，
 * JVM 单测无法实例化，由真机验收覆盖；本测试锁定纯像素数组的算法内核。
 */
package com.qihao.open.rwkv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 不透明纯白像素（MagicWX 蒙版已涂区域） */
private val OPAQUE_WHITE = 0xFFFFFFFF.toInt()

/** 不透明纯黑像素（MagicWX 蒙版未涂区域） */
private val OPAQUE_BLACK = 0xFF000000.toInt()

/** 全透明像素（参照透明底蒙版未涂区域） */
private val TRANSPARENT = 0x00000000

/** 透明但 RGB 为白：验证 Alpha 门禁优先于亮度判定 */
private val TRANSPARENT_WHITE = 0x00FFFFFF

class InpaintBlendUtilsTest {

    @Test
    fun chamferReturnsNullWhenNoPixelPainted() {
        // 全黑（未涂）蒙版：无距离可算，返回 null，调用方退化为整块粘贴
        val pixels = IntArray(9) { OPAQUE_BLACK }
        assertNull(InpaintBlendUtils.chamferDistanceFromMask(pixels, 3, 3))
    }

    @Test
    fun chamferAllZeroWhenFullyPainted() {
        // 全白（全图重绘蒙版）：每个像素自身即已涂，距离全 0
        val pixels = IntArray(4) { OPAQUE_WHITE }
        val dist = InpaintBlendUtils.chamferDistanceFromMask(pixels, 2, 2)
        assertEquals(intArrayOf(0, 0, 0, 0).toList(), dist?.toList())
    }

    @Test
    fun chamferSinglePaintedPixelSpreads34Distances() {
        // 3x3 中心已涂：上下左右距离 3（横竖代价），四角距离 4（对角代价）
        val pixels = intArrayOf(
            OPAQUE_BLACK, OPAQUE_BLACK, OPAQUE_BLACK,
            OPAQUE_BLACK, OPAQUE_WHITE, OPAQUE_BLACK,
            OPAQUE_BLACK, OPAQUE_BLACK, OPAQUE_BLACK,
        )
        val dist = InpaintBlendUtils.chamferDistanceFromMask(pixels, 3, 3)
        assertEquals(listOf(4, 3, 4, 3, 0, 3, 4, 3, 4), dist?.toList())
    }

    @Test
    fun chamferPaintedColumnGrowsHorizontallyByStraightCost() {
        // 3x3 左列已涂：距离沿水平方向每列 +3，取最近源
        val pixels = intArrayOf(
            OPAQUE_WHITE, OPAQUE_BLACK, OPAQUE_BLACK,
            OPAQUE_WHITE, OPAQUE_BLACK, OPAQUE_BLACK,
            OPAQUE_WHITE, OPAQUE_BLACK, OPAQUE_BLACK,
        )
        val dist = InpaintBlendUtils.chamferDistanceFromMask(pixels, 3, 3)
        assertEquals(listOf(0, 3, 6, 0, 3, 6, 0, 3, 6), dist?.toList())
    }

    @Test
    fun paintedDetectionCoversOpaqueAndTransparentMaskFormats() {
        // MagicWX 黑底白字 PNG：白已涂、黑未涂
        assertTrue(InpaintBlendUtils.isMaskPainted(OPAQUE_WHITE))
        assertFalse(InpaintBlendUtils.isMaskPainted(OPAQUE_BLACK))
        // 参照透明底格式：全透明未涂；透明但 RGB 白也不判已涂（Alpha 门禁）
        assertFalse(InpaintBlendUtils.isMaskPainted(TRANSPARENT))
        assertFalse(InpaintBlendUtils.isMaskPainted(TRANSPARENT_WHITE))
        // Alpha 阈值边界：127 不足、128 达标
        assertFalse(InpaintBlendUtils.isMaskPainted((127 shl 24) or 0xFFFFFF))
        assertTrue(InpaintBlendUtils.isMaskPainted((128 shl 24) or 0xFFFFFF))
        // 亮度阈值边界：Alpha 充足但亮度 127 不判已涂、128 达标
        assertFalse(InpaintBlendUtils.isMaskPainted((255 shl 24) or (127 shl 16)))
        assertTrue(InpaintBlendUtils.isMaskPainted((255 shl 24) or (128 shl 16)))
    }
}

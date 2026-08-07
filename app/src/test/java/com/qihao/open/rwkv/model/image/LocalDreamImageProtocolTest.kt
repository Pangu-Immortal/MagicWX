/**
 * LocalDreamImageProtocolTest - LocalDream 图片协议层单测
 *
 * 功能：
 * - 验证 /generate JSON 包含完整生成参数
 * - 验证 img2img / inpaint / ultrafix 模式的最小输入门禁
 * - 验证 prompt 和路径转义不会破坏 JSON 结构
 * - 验证 UltraFix 参数映射（steps/denoise/prompt 替换，C3）
 *
 * 说明：原 generationDraftUsesFallbackSeedAndKarrasScheduler 测试随
 * LocalDreamGenerationDraft 死代码清理一并删除（该类的 toRequest/missingInputHint
 * 无调用方；schedulerLabel 已迁移至 LocalDreamDefaults）。
 */
package com.qihao.open.rwkv.model.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDreamImageProtocolTest {

    @Test
    fun generateJsonContainsFullLocalDreamFields() {
        val request = LocalDreamImageRequest(
            prompt = "cat \"room\"\nsoft light",
            negativePrompt = "low quality",
            steps = 12,
            cfg = 6.5f,
            seed = 123L,
            width = 640,
            height = 512,
            scheduler = "euler",
            denoiseStrength = 0.45f,
            aspectRatio = "5:4",
            showDiffusionProcess = true,
            showDiffusionStride = 2,
            previewFormat = LocalDreamImageWireFormat.PNG,
            outputFormat = LocalDreamImageWireFormat.JPEG,
            batchCount = 2
        )

        val json = LocalDreamBackendProtocol.buildGenerateJson(
            request = request,
            useOpencl = true
        )

        assertTrue(json.contains("\"prompt\":\"cat \\\"room\\\"\\nsoft light\""))
        assertTrue(json.contains("\"negative_prompt\":\"low quality\""))
        assertTrue(json.contains("\"steps\":12"))
        assertTrue(json.contains("\"cfg\":6.5"))
        assertTrue(json.contains("\"seed\":123"))
        assertTrue(json.contains("\"width\":640"))
        assertTrue(json.contains("\"height\":512"))
        assertTrue(json.contains("\"scheduler\":\"euler\""))
        assertTrue(json.contains("\"denoise_strength\":0.45"))
        assertTrue(json.contains("\"aspect_ratio\":\"5:4\""))
        assertTrue(json.contains("\"show_diffusion_process\":true"))
        assertTrue(json.contains("\"show_diffusion_stride\":2"))
        assertTrue(json.contains("\"preview_format\":\"png\""))
        assertTrue(json.contains("\"output_format\":\"jpeg\""))
        assertTrue(json.contains("\"use_opencl\":true"))
        // native 不读 output_path/mode/batch_count/backend_type/memory_mode，协议已移除
        assertFalse(json.contains("\"output_path\""))
        assertFalse(json.contains("\"mode\""))
        assertFalse(json.contains("\"batch_count\""))
        assertFalse(json.contains("\"backend_type\""))
        assertFalse(json.contains("\"memory_mode\""))
    }

    @Test
    fun inpaintRequiresMaskImage() {
        val request = LocalDreamImageRequest(
            prompt = "repair wall",
            mode = LocalDreamGenerationMode.INPAINT,
            imageBase64 = "base64-image"
        )

        assertEquals("inpaint 需要输入 mask", request.validate())
    }

    @Test
    fun imageToImageRequiresSourceImage() {
        val request = LocalDreamImageRequest(
            prompt = "restyle",
            mode = LocalDreamGenerationMode.IMAGE_TO_IMAGE
        )

        assertEquals("img2img 需要输入图片", request.validate())
    }

    @Test
    fun ultrafixWritesTileFields() {
        val request = LocalDreamImageRequest(
            prompt = "enhance",
            mode = LocalDreamGenerationMode.ULTRAFIX,
            imageBase64 = "base64-image",
            ultrafixTileSize = 768,
            ultrafixSteps = 12,
            ultrafixDenoiseSteps = 5,
            ultrafixQualityDenoise = false
        )

        val json = LocalDreamBackendProtocol.buildGenerateJson(
            request = request,
            useOpencl = false
        )

        assertEquals("", request.validate())
        assertTrue(json.contains("\"ultrafix\":true"))
        assertTrue(json.contains("\"tile_size\":768"))
        assertTrue(json.contains("\"image\":\"base64-image\""))
        // native RequestParser 只读 ultrafix + tile_size；ultrafix_steps/denoise_steps/quality_denoise 已移除
        assertFalse(json.contains("\"ultrafix_steps\""))
        assertFalse(json.contains("\"ultrafix_denoise_steps\""))
        assertFalse(json.contains("\"ultrafix_quality_denoise\""))
        // ultrafixQualityDenoise=false 时保留页面 prompt 与负向提示词
        assertTrue(json.contains("\"prompt\":\"enhance\""))
    }

    @Test
    fun ultrafixUsesUltrafixStepsAndDerivedDenoiseStrength() {
        // C3：ultrafix 模式 steps 发 ultrafixSteps，denoise_strength 按参照
        // ModelRunSupport.ultrafixDenoiseStrength 公式推导，主页面 steps/denoise 不得下发
        val request = LocalDreamImageRequest(
            prompt = "page prompt",
            negativePrompt = "page negative",
            steps = 30,
            denoiseStrength = 0.6f,
            mode = LocalDreamGenerationMode.ULTRAFIX,
            imageBase64 = "base64-image",
            ultrafixSteps = 10,
            ultrafixDenoiseSteps = 4,
            ultrafixQualityDenoise = false
        )

        val json = LocalDreamBackendProtocol.buildGenerateJson(
            request = request,
            useOpencl = false
        )

        assertTrue("ultrafix 应发 ultrafixSteps", json.contains("\"steps\":10"))
        // (4 - 0.5) / 10 = 0.35
        assertTrue("denoise_strength 应为推导值 0.35", json.contains("\"denoise_strength\":0.35"))
        assertFalse("不得下发主页面步数", json.contains("\"steps\":30"))
        assertFalse("不得下发主页面降噪强度", json.contains("\"denoise_strength\":0.6"))
    }

    @Test
    fun ultrafixQualityDenoiseReplacesPromptAndClearsNegative() {
        // C3：ultrafixQualityDenoise=true 时 prompt 替换为中性质量提示词，negative_prompt 置空
        val request = LocalDreamImageRequest(
            prompt = "page prompt",
            negativePrompt = "page negative",
            mode = LocalDreamGenerationMode.ULTRAFIX,
            imageBase64 = "base64-image",
            ultrafixSteps = 10,
            ultrafixDenoiseSteps = 4,
            ultrafixQualityDenoise = true
        )

        val json = LocalDreamBackendProtocol.buildGenerateJson(
            request = request,
            useOpencl = false
        )

        assertTrue(json.contains("\"prompt\":\"${LocalDreamDefaults.ULTRAFIX_QUALITY_PROMPT}\""))
        assertTrue(json.contains("\"negative_prompt\":\"\""))
        assertFalse("页面 prompt 不得出现在 ultrafix 质量修复请求中", json.contains("page prompt"))
    }

    @Test
    fun ultrafixDenoiseStrengthMatchesReferenceFormula() {
        // 与参照 ModelRunSupport.ultrafixDenoiseStrength 边界行为一致
        assertEquals(0f, LocalDreamBackendProtocol.ultrafixDenoiseStrength(0, 0))
        assertEquals(0f, LocalDreamBackendProtocol.ultrafixDenoiseStrength(0, 10))
        assertEquals(0.35f, LocalDreamBackendProtocol.ultrafixDenoiseStrength(4, 10))
        assertEquals(0.95f, LocalDreamBackendProtocol.ultrafixDenoiseStrength(10, 10))
        // 降噪步数越界时收敛到总步数内
        assertEquals(0.95f, LocalDreamBackendProtocol.ultrafixDenoiseStrength(99, 10))
    }

    @Test
    fun tokenizeJsonEscapesPrompt() {
        val json = LocalDreamBackendProtocol.buildTokenizeJson("hello \"客官\"\n")

        assertEquals("{\"prompt\":\"hello \\\"客官\\\"\\n\"}", json)
    }
}

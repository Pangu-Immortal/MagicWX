/**
 * ModelRegistryTest - 模型注册表基础约束测试
 *
 * 功能：
 * - 验证模型 ID 唯一
 * - 验证默认模型为无需下载的内置体验模型
 * - 验证 Transformer 模型都声明 tokenizer 下载地址
 * - 验证可见模型卡片描述是唯一能力短句
 * - 验证候选池不污染首页可见模型
 * - 验证候选模型必须声明能力、adapter 和不可用原因
 * - 验证生图模型不得把未跑通设备标为真机生成通过
 */
package com.qihao.open.rwkv.model

import com.qihao.open.rwkv.model.image.DeviceSocCapability
import com.qihao.open.rwkv.model.image.LocalDreamDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegistryTest {

    companion object {
        private val VERIFIED_MODEL_IDS = setOf(
            "magicwx-builtin-demo",
            "rwkv7-world-0.4b",
            "rwkv7-g1a-0.1b-gguf",
            "rwkv7-miss-roleplay-1.5b-gguf",
            "qwen3-0.6b",
            "qwen25-0.5b",
            "smollm2-360m",
            "tinyllama-1.1b-task",
            "creative-rp-1b-gguf",
            "triangulum-rp-1b-gguf"
        )
        private val DISABLED_MODEL_IDS = setOf(
            "deepseek-r1-1.5b",
            "gemma3-1b",
            "phi3-mini",
            "llama32-1b",
            "tinyllama-1.1b",
            "llama32-1b-uncensored-gguf",
            "minicpm5-1b-uncensored-gguf",
            "companioncat-chatbot-gguf",
            "stablelm2-1.6b",
            "minicpm-2b",
            "qwen2-0.5b",
            "smollm2-135m",
            "smollm2-135m-mha"
        )
    }

    @Test
    fun modelIdsAreUnique() {
        val ids = ModelRegistry.allModels.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun defaultModelIsFullySupportedBuiltin() {
        val defaultModel = ModelRegistry.getDefault()

        assertEquals(ModelArch.BUILTIN, defaultModel.arch)
        assertTrue(defaultModel.isFullySupported)
    }

    @Test
    fun builtinModelDeclaresInnPersona() {
        val defaultModel = ModelRegistry.getDefault()

        assertTrue("内置体验模型描述应声明小女子人设", defaultModel.description.contains("小女子"))
        assertTrue("内置体验模型描述应声明客官称呼", defaultModel.description.contains("客官"))
        assertTrue("内置体验模型描述应声明古风客栈口吻", defaultModel.description.contains("古风客栈"))
    }

    @Test
    fun visibleModelsDeclareUniqueCapabilityDescriptions() {
        val descriptions = ModelRegistry.models.map { it.description }
        val blockedWords = listOf("候选", "需验证", "待接入", "标称", "实验性")

        assertEquals("可见模型描述应保持一模型一句独特能力文案", descriptions.size, descriptions.toSet().size)
        ModelRegistry.models.forEach { modelInfo ->
            assertTrue("可见模型描述不能为空: ${modelInfo.id}", modelInfo.description.isNotBlank())
            assertTrue("可见模型描述应是卡片短句: ${modelInfo.id}", modelInfo.description.length <= 80)
            assertTrue("可见模型描述应突出主要能力: ${modelInfo.id}", modelInfo.description.contains("："))
            blockedWords.forEach { blockedWord ->
                assertFalse(
                    "可见模型描述不应包含工程占位词 $blockedWord: ${modelInfo.id}",
                    modelInfo.description.contains(blockedWord)
                )
            }
        }
    }

    @Test
    fun onnxTransformerModelsDeclareTokenizerUrl() {
        val transformerModels = ModelRegistry.models.filter {
            it.arch == ModelArch.TRANSFORMER &&
                it.adapterType == RuntimeAdapterType.ONNX_TEXT_GENERATION
        }

        assertTrue(transformerModels.isNotEmpty())
        transformerModels.forEach { modelInfo ->
            assertNotNull("ONNX Transformer 模型缺少 tokenizerUrl: ${modelInfo.id}", modelInfo.tokenizerUrl)
        }
    }

    @Test
    fun registryOnlyExposesRuntimeVerifiedModels() {
        val visibleIds = ModelRegistry.models.map { it.id }.toSet()

        assertEquals(VERIFIED_MODEL_IDS, visibleIds)
        DISABLED_MODEL_IDS.forEach { disabledId ->
            assertFalse("未通过验证的模型不应展示: $disabledId", visibleIds.contains(disabledId))
        }
    }

    @Test
    fun candidatePoolContainsMultimodalModelsWithoutShowingThem() {
        val candidateIds = ModelRegistry.getCandidates().map { it.id }.toSet()
        val visibleIds = ModelRegistry.models.map { it.id }.toSet()

        assertTrue("候选池应至少覆盖 30 个模型或模型族", candidateIds.size >= 30)
        assertTrue("候选池应包含 ASR 模型", ModelRegistry.getCandidates().any { it.capability == ModelCapability.ASR })
        assertTrue("候选池应包含 TTS 模型", ModelRegistry.getCandidates().any { it.capability == ModelCapability.TTS })
        assertTrue("候选池应包含视觉模型", ModelRegistry.getCandidates().any { it.capability == ModelCapability.OBJECT_DETECTION })
        assertTrue("候选池应保留未跑通的 MNN 图像生成模型", ModelRegistry.getCandidates().any { it.id == "sd15-mnn-opencl-8bit" })
        assertTrue("候选池应保留 MiniSD MediaPipe 下载源用于后续回归", ModelRegistry.getCandidates().any { it.id == "minisd-mediapipe" })
        assertFalse("未真实出图的 MNN 图像生成模型不应展示", ModelRegistry.models.any { it.id == "sd15-mnn-opencl-8bit" })
        assertFalse("性能未达标的 MiniSD MediaPipe 不应展示", ModelRegistry.models.any { it.id == "minisd-mediapipe" })
        assertTrue("候选池和首页模型必须分离", candidateIds.intersect(visibleIds).isEmpty())
    }

    @Test
    fun localDreamCatalogDeclaresAllModelFamilies() {
        val localDreamModels = ModelRegistry.allModels.filter { it.id.startsWith("localdream-") }

        assertEquals("LocalDream 应登记 25 个图片模型和后处理模型", 25, localDreamModels.size)
        assertEquals(4, localDreamModels.count { it.imageBackendType == "sdxl" })
        assertEquals(9, localDreamModels.count { it.imageBackendType == "anima" })
        assertEquals(5, localDreamModels.count { it.imageBackendType == "sd15npu" })
        assertEquals(5, localDreamModels.count { it.imageBackendType == "sd15cpu" })
        assertEquals(2, localDreamModels.count { it.imageBackendType == "upscaler" })
        localDreamModels.forEach { modelInfo ->
            assertEquals(ModelArch.STABLE_DIFFUSION, modelInfo.arch)
            assertEquals(ModelVisibility.CANDIDATE, modelInfo.visibility)
            // QNN 族 adapter 可用性由设备 SoC 门禁决定（DeviceSocCapability）：
            // sd15cpu 恒可用；sd15npu/upscaler 需 QNN 支持；sdxl/anima 还需 8 Gen 3 SoC。
            // JVM 单测无 Build.SOC_MODEL，deviceSoc() 返回空串，等价非骁龙设备。
            val expectAdapterAvailable = expectLocalDreamAdapterAvailable(modelInfo.imageBackendType)
            assertEquals(
                "adapter 可用性应与设备 SoC 门禁一致: ${modelInfo.id}",
                expectAdapterAvailable,
                modelInfo.adapterAvailable
            )
            // 设备门禁拦截时必须给出可读原因；放行时原因为空
            if (!modelInfo.adapterAvailable) {
                assertTrue("被门禁拦截必须说明原因: ${modelInfo.id}", modelInfo.unavailableReason.isNotBlank())
            }
            assertTrue("LocalDream 模型必须声明运行时文件门禁: ${modelInfo.id}", modelInfo.requiredRuntimeFiles.isNotEmpty())
            assertTrue("LocalDream 模型必须声明下载资产: ${modelInfo.id}", modelInfo.assets.isNotEmpty())
            assertTrue("LocalDream 模型必须声明 HF Mirror 兜底: ${modelInfo.id}", modelInfo.resolvedAssets().all { asset ->
                asset.mirrorUrls.any { it.startsWith("https://hf-mirror.com/") }
            })
        }
    }

    @Test
    fun candidatesDeclareAdapterGate() {
        ModelRegistry.getCandidates().forEach { modelInfo ->
            assertEquals(ModelVisibility.CANDIDATE, modelInfo.visibility)
            if (modelInfo.imageBackendType.isNotBlank()) {
                // LocalDream 图片候选：adapter 可用性与设备 SoC 门禁一致（同目录测试口径）
                val expectAdapterAvailable = expectLocalDreamAdapterAvailable(modelInfo.imageBackendType)
                assertEquals(
                    "LocalDream 候选 adapter 可用性应与设备门禁一致: ${modelInfo.id}",
                    expectAdapterAvailable,
                    modelInfo.adapterAvailable
                )
                if (!modelInfo.adapterAvailable) {
                    assertTrue("被门禁拦截的候选必须说明原因: ${modelInfo.id}", modelInfo.unavailableReason.isNotBlank())
                }
            } else {
                // 非图片候选：adapter 未接入，必须标记不可用并说明原因
                assertFalse("非图片候选模型 adapter 不应标记为已可用: ${modelInfo.id}", modelInfo.adapterAvailable)
                assertTrue("候选模型必须说明不可用原因: ${modelInfo.id}", modelInfo.unavailableReason.isNotBlank())
            }
            assertTrue("候选模型必须声明 runtime adapter: ${modelInfo.id}", modelInfo.adapterType != RuntimeAdapterType.UNSUPPORTED)
        }
    }

    @Test
    fun selectedImageGenerationModelDeclaresExactMnnAssets() {
        val imageModel = ModelRegistry.allModels.firstOrNull { it.id == "sd15-mnn-opencl-8bit" }

        assertNotNull("生图候选必须保留已选定的 MNN SD1.5 OpenCL 量化模型", imageModel)
        assertEquals(ModelCapability.IMAGE_GENERATION, imageModel!!.capability)
        assertEquals(RuntimeAdapterType.MNN, imageModel.adapterType)
        assertEquals(ModelVisibility.CANDIDATE, imageModel.visibility)
        assertFalse("未真实出图前 MNN 生图模型 adapter 不应标记可用", imageModel.adapterAvailable)
        assertFalse("未真实出图前 MNN 生图模型不应标记真机验证", imageModel.verifiedOnDevice)
        assertFalse("MNN 生图模型未完成当前三星真机真实出图，不得标记为真机生成通过", imageModel.verifiedOnDevice)
        assertTrue("MNN 生图模型必须声明真机失败原因", imageModel.unavailableReason.contains("真机"))
        assertEquals(9, imageModel.assets.size)
        assertTrue("MNN 生图模型必须声明 UNet 权重", imageModel.assets.any { it.filename == "unet.mnn.weight" })
        assertTrue("MNN 生图模型必须声明 VAE 解码器权重", imageModel.assets.any { it.filename == "vae_decoder.mnn.weight" })
        assertTrue("MNN 生图模型必须声明 tokenizer 词表", imageModel.assets.any { it.filename == "vocab.json" })
        assertTrue("MNN 生图模型必须声明 ModelScope 国内镜像", imageModel.assets.all { asset ->
            asset.mirrorUrls.any { it.startsWith("https://modelscope.cn/models/MNN/") }
        })
    }

    @Test
    fun mediaPipeImageModelIsCandidateArchiveAssetForRegressionOnly() {
        val imageModel = ModelRegistry.allModels.firstOrNull { it.id == "minisd-mediapipe" }

        assertNotNull("候选池必须保留 MediaPipe 离线生图模型下载源", imageModel)
        assertEquals(ModelCapability.IMAGE_GENERATION, imageModel!!.capability)
        assertEquals(RuntimeAdapterType.MEDIAPIPE_IMAGE_GENERATION, imageModel.adapterType)
        assertEquals(ModelVisibility.CANDIDATE, imageModel.visibility)
        assertFalse("MediaPipe 慢路径不得标记 adapter 可用", imageModel.adapterAvailable)
        assertTrue("MediaPipe 生图模型必须使用 zip 模型包", imageModel.assets.any { it.kind == ModelAssetKind.ARCHIVE })
        assertTrue("MediaPipe 生图模型应声明 SDAI 模型源", imageModel.downloadUrl.contains("sdai-models.moroz.cc"))
        assertTrue("MediaPipe 候选必须说明性能降级原因", imageModel.unavailableReason.contains("性能"))
    }

    @Test
    fun visibleModelsHaveAvailableAdaptersAndDeviceEvidence() {
        ModelRegistry.models.forEach { modelInfo ->
            assertEquals(ModelVisibility.VERIFIED, modelInfo.visibility)
            assertTrue("可见模型必须有可用 adapter: ${modelInfo.id}", modelInfo.adapterAvailable)
            if (modelInfo.capability == ModelCapability.IMAGE_GENERATION) {
                assertTrue("生图模型必须声明运行时门禁原因: ${modelInfo.id}", modelInfo.unavailableReason.isNotBlank())
            } else {
                assertTrue("可见文本模型必须有真机验证证据标记: ${modelInfo.id}", modelInfo.verifiedOnDevice)
            }
        }
    }

    @Test
    fun rwkvModelIsVisibleAndFullySupported() {
        val rwkv = ModelRegistry.models.firstOrNull { it.id == "rwkv7-world-0.4b" }

        assertNotNull("RWKV 是用户确认的必需模型，不能从首页可见模型中移除", rwkv)
        assertEquals(ModelArch.RWKV, rwkv!!.arch)
        assertEquals(RuntimeAdapterType.ONNX_TEXT_GENERATION, rwkv.adapterType)
        assertTrue("RWKV 应保持完全支持标记", rwkv.isFullySupported)
        assertTrue("RWKV 应保持 adapter 可用", rwkv.adapterAvailable)
    }

    @Test
    fun visibleLlamaCppModelsDeclareGgufAssetWithoutTokenizer() {
        val llamaCppModels = ModelRegistry.models.filter {
            it.adapterType == RuntimeAdapterType.LLAMA_CPP
        }

        assertTrue("至少应有一个已验证 GGUF / llama.cpp 模型", llamaCppModels.isNotEmpty())
        llamaCppModels.forEach { modelInfo ->
            assertTrue("GGUF 模型不应依赖 tokenizer.json: ${modelInfo.id}", modelInfo.tokenizerUrl == null)
            assertTrue("GGUF 模型必须声明显式资产: ${modelInfo.id}", modelInfo.assets.isNotEmpty())
            assertTrue(
                "GGUF 模型必须声明 .gguf 主文件: ${modelInfo.id}",
                modelInfo.assets.any { it.kind == ModelAssetKind.MODEL && it.filename.endsWith(".gguf") }
            )
        }
    }

    @Test
    fun qnnFamilyModelsGateByDeviceSocWithPhysicalReasons() {
        val qnnFamily = ModelRegistry.allModels.filter {
            it.adapterType == RuntimeAdapterType.QNN_IMAGE_GENERATION
        }
        assertTrue("QNN 生图族必须完整登记", qnnFamily.size >= 20)

        qnnFamily.forEach { modelInfo ->
            // 设备门禁标记与 adapter 可用性严格同步
            assertEquals(
                "isDeviceGatedQnnUnavailable 应与 adapterAvailable 互斥: ${modelInfo.id}",
                !modelInfo.adapterAvailable,
                modelInfo.isDeviceGatedQnnUnavailable
            )
            if (!modelInfo.adapterAvailable) {
                // 拦截原因必须是设备物理事实（无 QNN/NPU 或 SoC 低于 8 Gen 3），不是"即将支持"排期文案
                assertTrue(
                    "门禁原因必须是设备物理事实: ${modelInfo.id} -> ${modelInfo.unavailableReason}",
                    modelInfo.unavailableReason == DeviceSocCapability.REASON_NO_QNN ||
                        modelInfo.unavailableReason == DeviceSocCapability.REASON_SOC_BELOW_8GEN3
                )
                assertFalse("门禁原因不允许排期文案: ${modelInfo.id}", modelInfo.unavailableReason.contains("即将"))
            } else {
                assertEquals("放行的 QNN 模型不应携带不可用原因: ${modelInfo.id}", "", modelInfo.unavailableReason)
            }
        }
    }

    @Test
    fun localDreamModelSpecificDefaultsAlignWithReference() {
        // 逐模型对照参照 Model.kt codeDefaults：prompt/negativePrompt 必须已移植进 ModelInfo
        val expectations = mapOf(
            "localdream-anythingv5-npu" to (LocalDreamDefaults.ANYTHING_V5_PROMPT to LocalDreamDefaults.ANIME_NEGATIVE_PROMPT),
            "localdream-anythingv5-cpu" to (LocalDreamDefaults.ANYTHING_V5_PROMPT to LocalDreamDefaults.ANIME_NEGATIVE_PROMPT),
            "localdream-qteamix-npu" to (LocalDreamDefaults.QTEAMIX_PROMPT to LocalDreamDefaults.ANIME_NEGATIVE_PROMPT),
            "localdream-qteamix-cpu" to (LocalDreamDefaults.QTEAMIX_PROMPT to LocalDreamDefaults.ANIME_NEGATIVE_PROMPT),
            "localdream-cuteyukimix-npu" to (LocalDreamDefaults.CUTEYUKIMIX_PROMPT to LocalDreamDefaults.ANIME_NEGATIVE_PROMPT),
            "localdream-cuteyukimix-cpu" to (LocalDreamDefaults.CUTEYUKIMIX_PROMPT to LocalDreamDefaults.ANIME_NEGATIVE_PROMPT),
            "localdream-absolutereality-npu" to (LocalDreamDefaults.ABSOLUTE_REALITY_PROMPT to LocalDreamDefaults.ABSOLUTE_REALITY_NEGATIVE_PROMPT),
            "localdream-absolutereality-cpu" to (LocalDreamDefaults.ABSOLUTE_REALITY_PROMPT to LocalDreamDefaults.ABSOLUTE_REALITY_NEGATIVE_PROMPT),
            "localdream-chilloutmix-npu" to (LocalDreamDefaults.CHILLOUTMIX_PROMPT to LocalDreamDefaults.CHILLOUTMIX_NEGATIVE_PROMPT),
            "localdream-chilloutmix-cpu" to (LocalDreamDefaults.CHILLOUTMIX_PROMPT to LocalDreamDefaults.CHILLOUTMIX_NEGATIVE_PROMPT),
            "localdream-illustrious-v16" to (LocalDreamDefaults.ILLUSTRIOUS_PROMPT to LocalDreamDefaults.ANIME_NEGATIVE_PROMPT),
            "localdream-illustrious-v16-dmd2" to (LocalDreamDefaults.ILLUSTRIOUS_PROMPT to LocalDreamDefaults.ANIME_NEGATIVE_PROMPT),
            "localdream-cyber-realistic-v10" to (LocalDreamDefaults.CYBER_REALISTIC_PROMPT to LocalDreamDefaults.CYBER_REALISTIC_NEGATIVE_PROMPT),
            "localdream-cyber-realistic-v10-dmd2" to (LocalDreamDefaults.CYBER_REALISTIC_PROMPT to LocalDreamDefaults.CYBER_REALISTIC_NEGATIVE_PROMPT)
        )
        expectations.forEach { (modelId, defaults) ->
            val modelInfo = ModelRegistry.allModels.firstOrNull { it.id == modelId }
            assertNotNull("模型必须登记: $modelId", modelInfo)
            assertEquals("模型专属正向默认必须对齐参照: $modelId", defaults.first, modelInfo!!.defaultPrompt)
            assertEquals("模型专属负向默认必须对齐参照: $modelId", defaults.second, modelInfo.defaultNegativePrompt)
        }

        // 参照仓库中 Anima 与超分没有代码级默认：保持 null 走全局默认回退
        ModelRegistry.allModels
            .filter { it.imageBackendType == "anima" || it.imageBackendType == "upscaler" }
            .forEach { modelInfo ->
                assertNull("Anima/超分模型不应携带代码默认提示词: ${modelInfo.id}", modelInfo.defaultPrompt)
                assertNull("Anima/超分模型不应携带代码默认负向提示词: ${modelInfo.id}", modelInfo.defaultNegativePrompt)
            }
    }

    @Test
    fun upscalerModelsUseSocSuffixDownloadUrl() {
        // 对齐参照 UpscalerRepository：upscaler_<suffix>.bin，JVM 单测无 SoC → min 兜底
        val expectedSuffix = DeviceSocCapability.qnnSuffix() ?: "min"
        listOf("localdream-upscaler-anime", "localdream-upscaler-realistic").forEach { modelId ->
            val modelInfo = ModelRegistry.allModels.first { it.id == modelId }
            assertTrue(
                "超分下载地址必须按 SoC 后缀选择权重: ${modelInfo.downloadUrl}",
                modelInfo.downloadUrl.endsWith("/upscaler_$expectedSuffix.bin")
            )
            assertEquals("本地落盘文件名保持 upscaler.bin 供后端统一读取", "upscaler.bin", modelInfo.assets.single().filename)
        }
    }

    /** LocalDream 图片候选 adapter 可用性期望：按设备 SoC 门禁推导，供多个测试复用 */
    private fun expectLocalDreamAdapterAvailable(imageBackendType: String): Boolean {
        return when (imageBackendType) {
            "sd15cpu" -> true
            "sd15npu", "upscaler" -> DeviceSocCapability.qnnSupported()
            "sdxl" -> DeviceSocCapability.qnnSupported() && DeviceSocCapability.sdxlCapable()
            "anima" -> DeviceSocCapability.qnnSupported() && DeviceSocCapability.animaCapable()
            else -> false
        }
    }
}

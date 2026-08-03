/**
 * ModelRegistryTest - 模型注册表基础约束测试
 *
 * 功能：
 * - 验证模型 ID 唯一
 * - 验证默认模型为无需下载的内置体验模型
 * - 验证 Transformer 模型都声明 tokenizer 下载地址
 * - 验证候选池不污染首页可见模型
 * - 验证候选模型必须声明能力、adapter 和不可用原因
 */
package com.qihao.open.rwkv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegistryTest {

    companion object {
        private val VERIFIED_MODEL_IDS = setOf(
            "magicwx-builtin-demo",
            "rwkv7-world-0.4b",
            "qwen3-0.6b",
            "qwen25-0.5b",
            "smollm2-360m",
            "tinyllama-1.1b-task"
        )
        private val DISABLED_MODEL_IDS = setOf(
            "deepseek-r1-1.5b",
            "gemma3-1b",
            "phi3-mini",
            "llama32-1b",
            "tinyllama-1.1b",
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
        assertTrue("候选池应包含图像生成模型", ModelRegistry.getCandidates().any { it.capability == ModelCapability.IMAGE_GENERATION })
        assertTrue("候选池和首页模型必须分离", candidateIds.intersect(visibleIds).isEmpty())
    }

    @Test
    fun candidatesDeclareAdapterGate() {
        ModelRegistry.getCandidates().forEach { modelInfo ->
            assertEquals(ModelVisibility.CANDIDATE, modelInfo.visibility)
            assertFalse("候选模型 adapter 不应标记为已可用: ${modelInfo.id}", modelInfo.adapterAvailable)
            assertTrue("候选模型必须说明不可用原因: ${modelInfo.id}", modelInfo.unavailableReason.isNotBlank())
            assertTrue("候选模型必须声明 runtime adapter: ${modelInfo.id}", modelInfo.adapterType != RuntimeAdapterType.UNSUPPORTED)
        }
    }

    @Test
    fun visibleModelsHaveAvailableAdaptersAndDeviceEvidence() {
        ModelRegistry.models.forEach { modelInfo ->
            assertEquals(ModelVisibility.VERIFIED, modelInfo.visibility)
            assertTrue("可见模型必须有可用 adapter: ${modelInfo.id}", modelInfo.adapterAvailable)
            assertTrue("可见模型必须有真机验证证据标记: ${modelInfo.id}", modelInfo.verifiedOnDevice)
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
}

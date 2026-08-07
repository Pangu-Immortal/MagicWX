/**
 * HomeModelTabVisibilityTest - 首页模型 TAB 可见性契约测试
 *
 * 功能：
 * - 验证语言 TAB 只展示已验证且 adapter 已接入的文本模型
 * - 验证 CPU/NPU 生图 TAB 展示 LocalDream 完整图片目录
 * - 验证候选生图模型仍保持 adapter 门禁，不伪装为可生成
 * - 验证图片后端事件能映射到生图 UI 状态
 */
package com.qihao.open.rwkv

import com.qihao.open.rwkv.model.ModelCapability
import com.qihao.open.rwkv.model.ModelRegistry
import com.qihao.open.rwkv.model.ModelVisibility
import com.qihao.open.rwkv.model.RuntimeAdapterType
import com.qihao.open.rwkv.model.image.DeviceSocCapability
import com.qihao.open.rwkv.service.ImageBackendEvent
import com.qihao.open.rwkv.service.ImageBackendEventType
import com.qihao.open.rwkv.viewmodel.ImageGenerationUiState
import com.qihao.open.rwkv.viewmodel.reduceImageBackendEventForState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelTabVisibilityTest {

    @Test
    fun languageTabOnlyExposesVerifiedTextModelsWithAvailableAdapters() {
        val tabModels = modelsForHomeTab(HomeModelTab.LANGUAGE)

        tabModels.forEach { modelInfo ->
            assertEquals("语言 TAB 不得展示候选模型: ${modelInfo.id}", ModelVisibility.VERIFIED, modelInfo.visibility)
            assertEquals("语言 TAB 只能展示文本对话模型: ${modelInfo.id}", ModelCapability.TEXT_CHAT, modelInfo.capability)
            assertTrue("语言 TAB 模型必须已接入 adapter: ${modelInfo.id}", modelInfo.adapterAvailable)
        }
    }

    @Test
    fun cpuAndNpuImageTabsExposeLocalDreamCatalogWithAdapterGate() {
        val imageTabModelIds = (
            modelsForHomeTab(HomeModelTab.CPU_IMAGE) +
                modelsForHomeTab(HomeModelTab.NPU_IMAGE)
            ).map { it.id }.toSet()
        val localDreamModels = ModelRegistry.allModels.filter { it.id.startsWith("localdream-") }

        assertTrue("候选池应保留 LocalDream 模型用于后续迁移", ModelRegistry.getCandidates().any { it.id.startsWith("localdream-") })
        assertEquals(
            "首页生图 TAB 应展示完整 LocalDream 目录",
            localDreamModels.map { it.id }.toSet(),
            imageTabModelIds.filter { it.startsWith("localdream-") }.toSet()
        )
        localDreamModels.forEach { modelInfo ->
            // adapter 可用性 = 设备 SoC 门禁（DeviceSocCapability）：
            // sd15cpu 恒可用；sd15npu/upscaler 需 QNN；sdxl/anima 另需 8 Gen 3 SoC。
            // JVM 单测无 Build.SOC_MODEL，qnnSupported() 为 false，QNN 族全部被门禁拦截。
            val expectAdapterAvailable = when (modelInfo.imageBackendType) {
                "sd15cpu" -> true
                "sd15npu", "upscaler" -> DeviceSocCapability.qnnSupported()
                "sdxl" -> DeviceSocCapability.qnnSupported() && DeviceSocCapability.sdxlCapable()
                "anima" -> DeviceSocCapability.qnnSupported() && DeviceSocCapability.animaCapable()
                else -> false
            }
            assertEquals(
                "LocalDream adapter 可用性应与设备门禁一致: ${modelInfo.id}",
                expectAdapterAvailable,
                modelInfo.adapterAvailable
            )
            assertEquals("LocalDream 仍应保留候选状态: ${modelInfo.id}", ModelVisibility.CANDIDATE, modelInfo.visibility)
        }
    }

    @Test
    fun cpuImageTabContainsRunnableAndDownloadOnlyImageGenerationModels() {
        val cpuImageModels = modelsForHomeTab(HomeModelTab.CPU_IMAGE)

        assertTrue("CPU 生图 TAB 至少保留一个可运行或可下载校验的生图模型", cpuImageModels.isNotEmpty())
        assertTrue("CPU 生图 TAB 应展示 LocalDream CPU 模型", cpuImageModels.any { it.imageBackendType == "sd15cpu" })
        assertFalse("CPU 生图 TAB 不得展示 MiniSD MediaPipe 慢路径", cpuImageModels.any { it.id == "minisd-mediapipe" })
        assertFalse("CPU 生图 TAB 不得展示未跑通的 MNN SD1.5 候选", cpuImageModels.any { it.id == "sd15-mnn-opencl-8bit" })
        cpuImageModels.forEach { modelInfo ->
            assertTrue("CPU 生图 TAB 只能展示 LocalDream 模型: ${modelInfo.id}", modelInfo.id.startsWith("localdream-"))
            assertEquals("CPU 生图 TAB 只能展示文生图能力模型", ModelCapability.IMAGE_GENERATION, modelInfo.capability)
            assertTrue("CPU 生图 TAB 不得展示 QNN/NPU 模型: ${modelInfo.id}", modelInfo.adapterType != RuntimeAdapterType.QNN_IMAGE_GENERATION)
        }
    }

    @Test
    fun npuImageTabOnlyContainsLocalDreamQnnModels() {
        val npuImageModels = modelsForHomeTab(HomeModelTab.NPU_IMAGE)

        assertTrue("NPU 生图 TAB 至少展示 LocalDream NPU 或超分模型", npuImageModels.isNotEmpty())
        assertFalse("NPU 生图 TAB 不得展示 MiniSD MediaPipe 慢路径", npuImageModels.any { it.id == "minisd-mediapipe" })
        npuImageModels.forEach { modelInfo ->
            assertTrue("NPU 生图 TAB 只能展示 LocalDream 模型: ${modelInfo.id}", modelInfo.id.startsWith("localdream-"))
            assertEquals("NPU 生图 TAB 只能展示 QNN adapter: ${modelInfo.id}", RuntimeAdapterType.QNN_IMAGE_GENERATION, modelInfo.adapterType)
        }
    }

    @Test
    fun npuImageTabDeviceGateUsesPhysicalReasonsNotComingSoon() {
        // 设备门禁语义：被拦截的 QNN 模型原因是物理事实（无 QNN/NPU 或 SoC 低于 8 Gen 3），
        // 不再是"即将支持"排期文案；放行的模型不携带拦截原因
        val npuImageModels = modelsForHomeTab(HomeModelTab.NPU_IMAGE)

        npuImageModels.forEach { modelInfo ->
            if (!modelInfo.adapterAvailable) {
                assertTrue(
                    "门禁原因必须是设备物理事实: ${modelInfo.id} -> ${modelInfo.unavailableReason}",
                    modelInfo.unavailableReason == DeviceSocCapability.REASON_NO_QNN ||
                        modelInfo.unavailableReason == DeviceSocCapability.REASON_SOC_BELOW_8GEN3
                )
                assertFalse("门禁原因不允许排期文案: ${modelInfo.id}", modelInfo.unavailableReason.contains("即将"))
                assertTrue("设备门禁标记应生效: ${modelInfo.id}", modelInfo.isDeviceGatedQnnUnavailable)
            } else {
                assertFalse("放行的模型不应有设备门禁标记: ${modelInfo.id}", modelInfo.isDeviceGatedQnnUnavailable)
            }
        }
    }

    @Test
    fun backendEventsUpdateImageGenerationState() {
        val startingState = ImageGenerationUiState(prompt = "cat", isGenerating = true)
        val readyState = reduceImageBackendEventForState(
            currentState = startingState,
            event = ImageBackendEvent(
                modelId = "sd15-mnn-opencl-8bit",
                type = ImageBackendEventType.READY,
                message = "图片后端已就绪"
            )
        )

        assertTrue("READY 事件应标记后端可用", readyState.backendReady)
        assertEquals("图片后端已就绪", readyState.backendStatusMessage)

        val failedState = reduceImageBackendEventForState(
            currentState = readyState.copy(isGenerating = true),
            event = ImageBackendEvent(
                modelId = "sd15-mnn-opencl-8bit",
                type = ImageBackendEventType.FAILED,
                message = "图片后端启动失败"
            )
        )

        assertFalse("FAILED 事件应停止生成态", failedState.isGenerating)
        assertFalse("FAILED 事件应取消后端 ready 标记", failedState.backendReady)
        assertEquals("图片后端启动失败", failedState.errorMessage)
    }
}

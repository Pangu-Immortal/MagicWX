/**
 * DeviceSocCapabilityTest - SoC 门禁纯函数契约测试
 *
 * 功能：
 * - 逐条验证 chipsetModelSuffixes 表（对齐参照 local-dream Model.kt）
 * - 验证未列名骁龙（SM 前缀）→ min、非骁龙（Exynos/天玑）→ null 的降级语义
 * - 验证 SDXL/Anima 8 Gen 3 SoC 白名单
 * - 验证 QNN 运行时构建门禁（CPU-only 构建 + 管线族判定 + 报错文案契约）
 */
package com.qihao.open.rwkv.model.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSocCapabilityTest {

    /** 参照 local-dream Model.kt chipsetModelSuffixes 全表逐条对照 */
    private val referenceSuffixTable = mapOf(
        "SM8475" to "8gen1",
        "SM8450" to "8gen1",
        "SM8550" to "8gen2",
        "SM8550P" to "8gen2",
        "QCS8550" to "8gen2",
        "QCM8550" to "8gen2",
        "SM8650" to "8gen2",
        "SM8650P" to "8gen2",
        "SM8750" to "8gen2",
        "SM8750P" to "8gen2",
        "SM8850" to "8gen2",
        "SM8850P" to "8gen2",
        "SM8735" to "8gen2",
        "SM8845" to "8gen2"
    )

    @Test
    fun chipsetSuffixTableMatchesReferenceEntryByEntry() {
        referenceSuffixTable.forEach { (soc, expectedSuffix) ->
            assertEquals("SoC $soc 后缀必须对齐参照表", expectedSuffix, DeviceSocCapability.qnnSuffix(soc))
            assertTrue("表内 SoC 必须判定为支持 QNN: $soc", DeviceSocCapability.qnnSupported(soc))
        }
    }

    @Test
    fun chipsetSuffixLookupIsCaseAndWhitespaceInsensitive() {
        // Build.SOC_MODEL 大小写/空白不保证，判定必须归一化
        assertEquals("8gen2", DeviceSocCapability.qnnSuffix("sm8650"))
        assertEquals("8gen2", DeviceSocCapability.qnnSuffix("  SM8650  "))
        assertEquals("8gen1", DeviceSocCapability.qnnSuffix("sm8450"))
    }

    @Test
    fun unknownSnapdragonFallsBackToMinSuffix() {
        // 未列名但 SOC_MODEL 以 SM 开头（骁龙未登记型号）→ min，仍判定支持 QNN
        assertEquals("min", DeviceSocCapability.qnnSuffix("SM7775"))
        assertEquals("min", DeviceSocCapability.qnnSuffix("SM6450"))
        assertTrue(DeviceSocCapability.qnnSupported("SM7775"))
    }

    @Test
    fun nonQualcommSocHasNoQnnSupport() {
        // Exynos / 天玑 / 空串 → null，不支持 QNN（物理事实）
        assertNull(DeviceSocCapability.qnnSuffix("exynos2400"))
        assertNull(DeviceSocCapability.qnnSuffix("MT6989"))
        assertNull(DeviceSocCapability.qnnSuffix("s5e8855"))
        assertNull(DeviceSocCapability.qnnSuffix(""))
        assertFalse(DeviceSocCapability.qnnSupported("exynos2400"))
        assertFalse(DeviceSocCapability.qnnSupported(""))
    }

    @Test
    fun sdxlAndAnimaRequire8Gen3Whitelist() {
        // 对齐参照 isSdxlCapableSoc：仅 8 Gen 3 及以上白名单放行
        val capable = listOf("SM8750", "SM8750P", "SM8850", "SM8850P", "SM8845", "SM8650")
        capable.forEach { soc ->
            assertTrue("SDXL 白名单应放行: $soc", DeviceSocCapability.sdxlCapable(soc))
            assertTrue("Anima 与 SDXL 共用 8 Gen 3 白名单: $soc", DeviceSocCapability.animaCapable(soc))
        }
        // 8gen2/8gen1 机型支持 QNN 但跑不了 SDXL/Anima
        listOf("SM8550", "SM8450", "SM8475").forEach { soc ->
            assertTrue(DeviceSocCapability.qnnSupported(soc))
            assertFalse("低于 8 Gen 3 不得放行 SDXL: $soc", DeviceSocCapability.sdxlCapable(soc))
            assertFalse("低于 8 Gen 3 不得放行 Anima: $soc", DeviceSocCapability.animaCapable(soc))
        }
        assertFalse(DeviceSocCapability.sdxlCapable("exynos2400"))
    }

    @Test
    fun qnnRuntimeBuildGateAllowsQnnAfterSdkIntegrated() {
        // B 方案：改用 local-dream 预编译 libstable_diffusion_core.so（含 QNN 管线）+ assets/qnnlibs/
        // 20 个 QNN .so 运行时，SDK 已集成，QNN 管线族可执行，门禁放行
        assertTrue("B 方案 QNN 已集成，必须放行 QNN 管线", QnnRuntimeAvailability.canRunQnnPipeline())
        assertTrue(QnnRuntimeAvailability.SDK_INTEGRATED)
        listOf("sd15npu", "sdxl", "anima", "upscaler").forEach { pipeline ->
            assertTrue("QNN 管线族判定必须覆盖: $pipeline", QnnRuntimeAvailability.isQnnPipeline(pipeline))
        }
        assertFalse("CPU 管线不属于 QNN 族", QnnRuntimeAvailability.isQnnPipeline("sd15cpu"))
        assertFalse(QnnRuntimeAvailability.isQnnPipeline(""))
        assertTrue(
            "生成前门禁文案必须包含统一关键字",
            QnnRuntimeAvailability.QNN_RUNTIME_REQUIRED_MESSAGE.contains("需要 QNN 运行时支持")
        )
    }

    @Test
    fun gateReasonsExposeUserFacingCopy() {
        // 产品文案契约：UI/测试/自动化按这些固定文案断言
        assertEquals("本设备不支持 QNN/NPU（需骁龙移动平台）", DeviceSocCapability.REASON_NO_QNN)
        assertTrue(DeviceSocCapability.REASON_SOC_BELOW_8GEN3.contains("8 Gen 3"))
    }
}

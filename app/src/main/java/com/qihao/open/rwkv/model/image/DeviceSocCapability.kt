/**
 * DeviceSocCapability - QNN/NPU 设备能力门禁工具
 *
 * 功能：
 * - deviceSoc(): 读取 Build.SOC_MODEL（API 31+），低版本兜底 Build.HARDWARE
 * - chipsetModelSuffixes: SoC → QNN 模型文件后缀映射表（逐条对齐参照
 *   modules/local-dream .../data/Model.kt 的 chipsetModelSuffixes）
 * - qnnSupported()/qnnSuffix(): 当前设备是否具备 QNN/NPU 能力及应下载的模型包后缀
 * - sdxlCapable()/animaCapable(): SDXL/Anima 8gen3 模型包的 SoC 白名单门禁
 *   （对齐参照 ModelRepository.isSdxlCapableSoc：骁龙 8 Gen 3 及以上）
 * - QnnRuntimeAvailability: 构建侧 QNN SDK 集成状态门禁（当前 CPU-only 构建）
 *
 * 设计要点：
 * - 纯函数核心（带 soc 参数）+ Build 静态字段封装（无参重载），
 *   静态模型注册表（ModelInfo 工厂）与 JVM 单元测试均可直接调用，无需 Application
 * - 参照 getDeviceSoc/getChipsetSuffix 语义：表内命中返回对应后缀；
 *   未命中但 SOC_MODEL 以 "SM" 开头（骁龙未列名型号）→ "min"；
 *   其余（Exynos/天玑等非骁龙平台）→ null，即不支持 QNN
 */
package com.qihao.open.rwkv.model.image

import android.os.Build
import android.util.Log

/** QNN/NPU 设备能力判定：SoC 识别、模型包后缀与 8gen3 白名单门禁 */
object DeviceSocCapability {

    private const val TAG = "DeviceSocCapability"

    /** 设备门禁统一文案：非骁龙平台无 QNN/NPU 是物理事实，不是"即将支持" */
    const val REASON_NO_QNN = "本设备不支持 QNN/NPU（需骁龙移动平台）"

    /** SDXL/Anima 门禁文案：已是骁龙但低于 8 Gen 3，无法运行 8gen3 模型包 */
    const val REASON_SOC_BELOW_8GEN3 = "SDXL 生图需要骁龙 8 Gen 3 及以上平台，本设备暂不支持。"

    /**
     * SoC → QNN 模型文件后缀映射，逐条对齐参照 local-dream Model.kt chipsetModelSuffixes。
     * 后缀决定下载哪个 QNN 上下文包（如 AnythingV5_qnn2.28_8gen2.zip）。
     * 公开可见，供模型注册表 factory 方法按 SoC 直接选择正确模型包。
     */
    val chipsetModelSuffixes = mapOf(
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

    /**
     * SDXL/Anima 8gen3 模型包要求的 SoC 白名单，对齐参照 isSdxlCapableSoc：
     * SM8650（8 Gen 3）及 SM8750/SM8850/SM8845 等后续旗舰平台。
     */
    private val SDXL_CAPABLE_SOCS = setOf(
        "SM8750", "SM8750P", "SM8850", "SM8850P", "SM8845", "SM8650"
    )

    /**
     * 读取当前设备 SoC 型号。
     * API 31+ 用 Build.SOC_MODEL；更低版本或字段为空时兜底 Build.HARDWARE；
     * 任何异常（含 JVM 单测桩环境）返回空串，由上层按"非骁龙"处理。
     */
    fun deviceSoc(): String {
        return try {
            val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL
            } else {
                null
            }
            socModel?.takeIf { it.isNotBlank() } ?: Build.HARDWARE.orEmpty()
        } catch (error: Throwable) {
            // 桩环境/反射受限等场景不抛出：门禁判定必须永远有确定结果
            Log.w(TAG, "读取 SoC 型号失败，按未知设备处理: ${error.message}")
            ""
        }
    }

    /**
     * 纯函数：SoC 型号 → QNN 模型包后缀。
     * 对齐参照 getChipsetSuffix：表内命中 → 对应后缀；"SM" 开头未列名 → "min"；其余 → null。
     */
    fun qnnSuffix(soc: String): String? {
        val normalized = soc.trim().uppercase()
        if (normalized.isEmpty()) return null
        chipsetModelSuffixes[normalized]?.let { return it }
        if (normalized.startsWith("SM")) return "min"
        return null
    }

    /**
     * 按 SoC 型号查询 QNN 模型包后缀，命名对齐参照 local-dream Model.getChipsetSuffix。
     * 与 qnnSuffix(soc) 等价，提供语义更明确的公共入口。
     *
     * @param soc SoC 型号字符串（如 "SM8650"）
     * @return 对应模型包后缀（"8gen1"/"8gen2"/"min"），不支持时返回 null
     */
    fun getChipsetSuffix(soc: String): String? = qnnSuffix(soc)

    /** 当前设备应下载的 QNN 模型包后缀；null 表示不支持 QNN */
    fun qnnSuffix(): String? = qnnSuffix(deviceSoc())

    /** 纯函数：该 SoC 是否支持 QNN/NPU（能取到模型包后缀即支持） */
    fun qnnSupported(soc: String): Boolean = qnnSuffix(soc) != null

    /** 当前设备是否支持 QNN/NPU */
    fun qnnSupported(): Boolean = qnnSuffix() != null

    /** 纯函数：该 SoC 是否在 SDXL 8gen3 白名单内（骁龙 8 Gen 3 及以上） */
    fun sdxlCapable(soc: String): Boolean = soc.trim().uppercase() in SDXL_CAPABLE_SOCS

    /** 当前设备是否满足 SDXL 模型包的 SoC 要求 */
    fun sdxlCapable(): Boolean = sdxlCapable(deviceSoc())

    /**
     * 纯函数：该 SoC 是否可运行 Anima 模型包。
     * Anima 与 SDXL 同为大体量 NPU 格式（参照 BackendService 对两者的 lowram 处理一致），
     * 且现有 Anima 模型包全部为 *_8gen3.zip，因此共用 8 Gen 3 白名单。
     */
    fun animaCapable(soc: String): Boolean = sdxlCapable(soc)

    /** 当前设备是否满足 Anima 模型包的 SoC 要求 */
    fun animaCapable(): Boolean = sdxlCapable()

    /** 设备门禁摘要日志，便于真机问题定位 */
    fun describeGate(): String {
        val soc = deviceSoc()
        return "soc=$soc, qnnSuffix=${qnnSuffix(soc)}, sdxlCapable=${sdxlCapable(soc)}"
    }
}

/**
 * QNN 运行时构建侧可用性。
 *
 * 设备门禁（DeviceSocCapability）回答"硬件能不能"，本对象回答"当前构建有没有"：
 * QNN SDK 尚未集成，构建产物只含 CPU 管线；Kotlin 层先按完整形态接线，
 * QNN SDK 集成后把 SDK_INTEGRATED 置 true，并同步移除 ImageGenerationService
 * 中"该模型需要 QNN 运行时支持"的生成前门禁。
 */
object QnnRuntimeAvailability {

    /** QNN SDK 集成状态：false 表示当前构建为 CPU-only，QNN 管线不可执行 */
    const val SDK_INTEGRATED = false

    /** 生成前门禁统一报错文案（dex 核验关键字："需要 QNN 运行时支持"） */
    const val QNN_RUNTIME_REQUIRED_MESSAGE =
        "该模型需要 QNN 运行时支持（当前构建未集成 QNN SDK，待集成后开放）"

    /** 当前构建是否可执行 QNN 管线（sd15npu/sdxl/anima/QNN 超分） */
    fun canRunQnnPipeline(): Boolean = SDK_INTEGRATED

    /** 判断 localdream 图片后端类型是否属于 QNN 管线族 */
    fun isQnnPipeline(imageBackendType: String): Boolean {
        return imageBackendType in setOf("sd15npu", "sdxl", "anima", "upscaler")
    }
}

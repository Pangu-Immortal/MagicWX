/**
 * ImageInferenceBackend - 端侧图片生成推理后端选择器
 *
 * 功能：
 * - ImageInferenceBackend: 描述 MNN 生图可用推理后端
 * - ImageInferenceBackendChoice: 返回实际选择、原因和降级说明
 * - StableDiffusionRuntimeStatus: 返回当前设备是否允许进入 SD1.5 native 生图
 * - ImageInferenceBackendPlanner: App 启动时根据手机硬件能力选择 GPU/OpenCL 或 CPU
 */
package com.qihao.open.rwkv.model.image

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/** MNN Diffusion 当前已打包可用的推理后端 */
enum class ImageInferenceBackend(
    val mnnForwardType: Int,      // 对应 MNNForwardType.h 的整数值
    val displayName: String       // UI/日志展示名称
) {
    CPU(0, "CPU"),
    OPENCL(3, "GPU/OpenCL")
}

/** 图片推理后端选择结果 */
data class ImageInferenceBackendChoice(
    val backend: ImageInferenceBackend, // 当前请求优先使用的后端
    val reason: String,                 // 选择该后端的硬件依据
    val fallback: ImageInferenceBackend, // 保留历史兼容字段；SD1.5 不再执行失败后 CPU 回退
    val hardwareSummary: String          // 设备硬件摘要，便于跨机型问题定位
)

/** Stable Diffusion 运行时可用性，区分“模型已下载”和“本机可安全运行” */
data class StableDiffusionRuntimeStatus(
    val canRun: Boolean,     // true 表示允许进入 native MNN Diffusion 调用
    val label: String,       // 卡片短标签
    val reason: String       // 用户可读的不可用或可用原因
)

/** App 启动时根据设备能力选择 SD/扩散模型推理后端 */
object ImageInferenceBackendPlanner {
    private const val TAG = "ImageInferenceBackendPlanner"
    private const val GLES_3_0 = 0x00030000
    @Volatile private var stableDiffusionChoice: ImageInferenceBackendChoice? = null

    /** App 启动时初始化图片推理架构，后续生成直接复用该结果 */
    fun initializeAtAppStart(context: Context): ImageInferenceBackendChoice {
        val choice = chooseForStableDiffusion(context.applicationContext)
        stableDiffusionChoice = choice                         // 启动后缓存，避免每次生成重新漂移
        return choice
    }

    /** 返回启动时已选择的 SD 推理架构；异常场景下懒初始化兜底 */
    fun currentStableDiffusionChoice(context: Context): ImageInferenceBackendChoice {
        return stableDiffusionChoice ?: initializeAtAppStart(context)
    }

    /** 返回 SD1.5 MNN 当前设备运行门禁，避免 native 崩溃无法被 Kotlin 捕获 */
    fun currentStableDiffusionRuntimeStatus(context: Context): StableDiffusionRuntimeStatus {
        val choice = currentStableDiffusionChoice(context.applicationContext)
        return stableDiffusionRuntimeStatus(choice)
    }

    /**
     * 根据已缓存后端选择生成 UI/加载层共用的运行门禁。
     *
     * 旧的同进程 JNI MNN-Diffusion 在三星 s5e8855/a56x 上会崩溃并拖垮 App 进程，因此曾硬屏蔽。
     * Phase 1 起生图迁移到 local-dream 隔离后端进程（独立 libmagicwx_image_backend.so），
     * native 崩溃只杀后端进程、不杀 App，崩溃风险被进程边界隔离；
     * 为让 Samsung SM-A566E 等目标机能真实测试出图，不再硬屏蔽 s5e8855，改为允许尝试
     * （失败由 ImageBackendService 的进程退出监听 + logcat 证据反馈，不静默）。
     */
    fun stableDiffusionRuntimeStatus(choice: ImageInferenceBackendChoice): StableDiffusionRuntimeStatus {
        if (hasKnownBrokenStableDiffusionRuntime()) {
            return StableDiffusionRuntimeStatus(
                canRun = false,
                label = "本机不可用",
                reason = "当前设备命中已知不可用名单，已禁用以避免生图时崩溃。"
            )
        }
        if (choice.backend == ImageInferenceBackend.CPU) {
            return StableDiffusionRuntimeStatus(
                canRun = false,
                label = "本机不可用",
                reason = "当前 SD1.5 模型包不支持安全 CPU 回退，本机未命中可用 GPU/OpenCL 路径，已禁用生图。"
            )
        }
        return StableDiffusionRuntimeStatus(
            canRun = true,
            label = "设备适配",
            reason = "当前设备满足 MNN SD1.5 GPU/OpenCL 基线，将使用 ${choice.backend.displayName} 推理。"
        )
    }

    /** 为当前设备选择最合适的 MNN SD 推理后端，不在外部直接调用 */
    private fun chooseForStableDiffusion(context: Context): ImageInferenceBackendChoice {
        val summary = buildHardwareSummary(context)             // 收集可记录的硬件能力摘要
        val gpuUsable = supportsGpuAcceleration(context)        // 当前 MNN 包优先判断 OpenCL/GPU 路线
        val backend = if (gpuUsable) {
            ImageInferenceBackend.OPENCL
        } else {
            ImageInferenceBackend.CPU
        }
        val reason = if (gpuUsable) {
            "检测到移动端 GPU 能力，优先使用 MNN OpenCL；$summary"
        } else {
            "未确认可用 GPU 加速，使用 CPU 保底；$summary"
        }
        Log.d(TAG, "SD 推理后端选择: ${backend.displayName}; $reason")
        return ImageInferenceBackendChoice(
            backend = backend,
            reason = reason,
            fallback = ImageInferenceBackend.CPU,
            hardwareSummary = summary
        )
    }

    /** 判断当前包是否应尝试 GPU/OpenCL 加速 */
    private fun supportsGpuAcceleration(context: Context): Boolean {
        if (isProbablyEmulator()) return false                  // 模拟器 GPU/OpenCL 不稳定，默认 CPU 保底
        if (!Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")) return false
        if (hasKnownBrokenOpenClDevice()) return false          // 已知风险机型直接 CPU，避免启动后固定 GPU 崩溃
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val glesVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        val hasVulkan = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        return glesVersion >= GLES_3_0 || hasVulkan             // OpenCL 不在 Android feature 中暴露，用公开 GPU feature 做保守基线
    }

    /** 构建设备硬件摘要，便于日志定位 NPU/GPU/CPU 决策 */
    private fun buildHardwareSummary(context: Context): String {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val glesVersion = activityManager?.deviceConfigurationInfo?.glEsVersion ?: "unknown"
        val hasVulkan = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else "unknown"
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "unknown"
        return "hardware=${Build.HARDWARE}, board=${Build.BOARD}, soc=$socManufacturer/$socModel, gles=$glesVersion, vulkan=$hasVulkan"
    }

    /** 识别常见模拟器，避免误把桌面虚拟 GPU 当作手机可用加速 */
    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        return fingerprint.contains("generic") ||
            model.contains("emulator") ||
            hardware.contains("ranchu") ||
            hardware.contains("goldfish")
    }

    /** 已知 OpenCL 风险设备列表；后续真机发现崩溃机型只加这里，不污染调用点 */
    private fun hasKnownBrokenOpenClDevice(): Boolean {
        return isProbablyEmulatorHardware() || hasKnownBrokenStableDiffusionRuntime()
    }

    /**
     * 已经通过真机日志确认会导致 native 崩溃的设备名单。
     *
     * Phase 1 起生图走 local-dream 隔离后端进程，s5e8855/a56x 不再硬屏蔽（崩溃被进程边界隔离，
     * 允许真实测试出图）。如后续真机验证发现新机型 native 崩溃且无法恢复，在此追加。
     */
    private fun hasKnownBrokenStableDiffusionRuntime(): Boolean {
        return false
    }

    /** 使用硬件字段识别模拟器，供 OpenCL 风险名单复用 */
    private fun isProbablyEmulatorHardware(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        return hardware.contains("ranchu") ||
            hardware.contains("goldfish") ||
            board.contains("ranchu") ||
            board.contains("goldfish")
    }
}

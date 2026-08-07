/**
 * ImageGenerationPreferences - 生图参数按模型持久化
 *
 * 功能：
 * - ImageGenerationParams: 承载生图页全量可持久化参数的纯数据类（默认值对齐 LocalDreamDefaults）
 * - ImageGenerationPreferences: 基于 DataStore Preferences 的 save/load，
 *   key 统一带 "${modelId}_" 前缀，不同模型互不串参数（对齐 local-dream GenerationPreferences）
 *
 * 设计要点：
 * - 参照 modules/local-dream/data/Preferences.kt 的 key 前缀与 load/save 时机
 * - load 对每个字段单独回退默认值：旧版本存档缺字段时不整包丢失
 * - modelId 为空（未选中模型）时不落盘，避免产生无法归属的公共 key
 */
package com.qihao.open.rwkv.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qihao.open.rwkv.model.image.LocalDreamDefaults
import com.qihao.open.rwkv.model.image.LocalDreamImageWireFormat
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 进程内唯一 DataStore 实例，文件名与 local-dream generation_prefs 语义对齐 */
private val Context.imageGenerationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "image_generation_prefs"
)

/** 生图页全量可持久化参数；字段与 ImageGenerationScreen 的编辑态一一对应 */
data class ImageGenerationParams(
    val prompt: String = LocalDreamDefaults.ANYTHING_V5_PROMPT,              // 正向提示词
    val negativePrompt: String = LocalDreamDefaults.ANYTHING_V5_NEGATIVE_PROMPT, // 负向提示词
    val steps: Int = LocalDreamDefaults.STEPS,                                // 采样步数
    val cfg: Float = LocalDreamDefaults.CFG,                                  // CFG 强度
    val seedText: String = LocalDreamDefaults.SEED,                           // 空字符串表示本次随机
    val width: Int = 512,                                                     // 目标宽度
    val height: Int = 512,                                                    // 目标高度
    val scheduler: String = LocalDreamDefaults.SCHEDULER,                     // 采样器 wire id
    val aspectRatio: String = LocalDreamDefaults.ASPECT_RATIO,                // 宽高比预设（SDXL 阶段消费）
    val denoiseStrength: Float = LocalDreamDefaults.DENOISE_STRENGTH,         // 图生图/重绘降噪强度
    val batchCount: Int = LocalDreamDefaults.BATCH_COUNT,                     // 批量张数
    val showDiffusionProcess: Boolean = false,                                // 是否请求中间预览
    val previewStride: Int = 1,                                               // 中间预览步长
    val outputFormatWire: String = LocalDreamImageWireFormat.JPEG.wireValue,  // 输出格式 wire id
    val ultrafixSteps: Int = 10,                                              // UltraFix 修复步数
    val ultrafixDenoiseSteps: Int = 4,                                        // UltraFix 降噪步数
    val ultrafixQualityDenoise: Boolean = true                                // UltraFix 是否用质量提示词
)

/** 按 modelId 读写生图参数的 DataStore 封装 */
class ImageGenerationPreferences(private val context: Context) {

    companion object {
        private const val TAG = "ImageGenPrefs"
    }

    // ---- 按模型加前缀的 key 工厂，"${modelId}_" 下划线防止 id 前缀互相污染 ----
    private fun promptKey(modelId: String) = stringPreferencesKey("${modelId}_prompt")
    private fun negativePromptKey(modelId: String) = stringPreferencesKey("${modelId}_negative_prompt")
    private fun stepsKey(modelId: String) = intPreferencesKey("${modelId}_steps")
    private fun cfgKey(modelId: String) = floatPreferencesKey("${modelId}_cfg")
    private fun seedTextKey(modelId: String) = stringPreferencesKey("${modelId}_seed")
    private fun widthKey(modelId: String) = intPreferencesKey("${modelId}_width")
    private fun heightKey(modelId: String) = intPreferencesKey("${modelId}_height")
    private fun schedulerKey(modelId: String) = stringPreferencesKey("${modelId}_scheduler")
    private fun aspectRatioKey(modelId: String) = stringPreferencesKey("${modelId}_aspect_ratio")
    private fun denoiseStrengthKey(modelId: String) = floatPreferencesKey("${modelId}_denoise_strength")
    private fun batchCountKey(modelId: String) = intPreferencesKey("${modelId}_batch_count")
    private fun showDiffusionProcessKey(modelId: String) = booleanPreferencesKey("${modelId}_show_diffusion_process")
    private fun previewStrideKey(modelId: String) = intPreferencesKey("${modelId}_preview_stride")
    private fun outputFormatKey(modelId: String) = stringPreferencesKey("${modelId}_output_format")
    private fun ultrafixStepsKey(modelId: String) = intPreferencesKey("${modelId}_ultrafix_steps")
    private fun ultrafixDenoiseStepsKey(modelId: String) = intPreferencesKey("${modelId}_ultrafix_denoise_steps")
    private fun ultrafixQualityDenoiseKey(modelId: String) = booleanPreferencesKey("${modelId}_ultrafix_quality_denoise")

    /**
     * 读取指定模型的全量参数；完全没有存档（该模型从未保存过）时返回 null，
     * 供 UI 区分"无存档走模型专属默认"与"有存档逐字段覆盖"两种初始化路径。
     *
     * @param fallback 存档缺失字段的回退值；默认构造即全局默认（LocalDreamDefaults）。
     *                 调用方可传入模型专属默认，实现「存档 > 模型默认 > 全局默认」三级回退
     *                 （对齐参照 ModelConfig.withFallback(...).resolve() 语义）。
     */
    suspend fun loadIfExists(
        modelId: String,
        fallback: ImageGenerationParams = ImageGenerationParams()
    ): ImageGenerationParams? {
        if (modelId.isBlank()) {
            Log.d(TAG, "loadIfExists 跳过：modelId 为空，返回 null")
            return null
        }
        return runCatching {
            context.imageGenerationDataStore.data
                .catch { exception ->
                    // 文件损坏等 IO 异常按空存档处理，避免生图页打不开
                    if (exception is IOException) {
                        Log.w(TAG, "读取参数存档失败，按无存档处理: ${exception.message}")
                        emit(androidx.datastore.preferences.core.emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .map { preferences ->
                    // save() 每次全量写入，prompt key 存在即代表该模型有过存档；
                    // 无存档返回 null，由调用方使用模型专属默认初始化
                    if (!preferences.contains(promptKey(modelId))) {
                        null
                    } else {
                        ImageGenerationParams(
                            prompt = preferences[promptKey(modelId)] ?: fallback.prompt,
                            negativePrompt = preferences[negativePromptKey(modelId)] ?: fallback.negativePrompt,
                            steps = preferences[stepsKey(modelId)] ?: fallback.steps,
                            cfg = preferences[cfgKey(modelId)] ?: fallback.cfg,
                            seedText = preferences[seedTextKey(modelId)] ?: fallback.seedText,
                            width = preferences[widthKey(modelId)] ?: fallback.width,
                            height = preferences[heightKey(modelId)] ?: fallback.height,
                            scheduler = preferences[schedulerKey(modelId)] ?: fallback.scheduler,
                            aspectRatio = preferences[aspectRatioKey(modelId)] ?: fallback.aspectRatio,
                            denoiseStrength = preferences[denoiseStrengthKey(modelId)] ?: fallback.denoiseStrength,
                            batchCount = preferences[batchCountKey(modelId)] ?: fallback.batchCount,
                            showDiffusionProcess = preferences[showDiffusionProcessKey(modelId)] ?: fallback.showDiffusionProcess,
                            previewStride = preferences[previewStrideKey(modelId)] ?: fallback.previewStride,
                            outputFormatWire = preferences[outputFormatKey(modelId)] ?: fallback.outputFormatWire,
                            ultrafixSteps = preferences[ultrafixStepsKey(modelId)] ?: fallback.ultrafixSteps,
                            ultrafixDenoiseSteps = preferences[ultrafixDenoiseStepsKey(modelId)] ?: fallback.ultrafixDenoiseSteps,
                            ultrafixQualityDenoise = preferences[ultrafixQualityDenoiseKey(modelId)] ?: fallback.ultrafixQualityDenoise
                        )
                    }
                }
                .first()
        }.getOrElse { error ->
            // DataStore 首次创建文件等异常不阻断 UI：按无存档处理并记录日志
            Log.w(TAG, "loadIfExists 失败，按无存档处理: modelId=$modelId, ${error.message}")
            null
        }
    }

    /** 读取指定模型的全量参数；无存档或字段缺失时逐字段回退默认值（保留兼容入口） */
    suspend fun load(modelId: String): ImageGenerationParams {
        val defaults = ImageGenerationParams()                              // 统一默认值来源
        return loadIfExists(modelId, defaults) ?: defaults
    }

    /** 保存指定模型的全量参数；modelId 为空时跳过落盘 */
    suspend fun save(modelId: String, params: ImageGenerationParams) {
        if (modelId.isBlank()) {
            Log.d(TAG, "save 跳过：modelId 为空")
            return
        }
        runCatching {
            context.imageGenerationDataStore.edit { preferences ->
                preferences[promptKey(modelId)] = params.prompt
                preferences[negativePromptKey(modelId)] = params.negativePrompt
                preferences[stepsKey(modelId)] = params.steps
                preferences[cfgKey(modelId)] = params.cfg
                preferences[seedTextKey(modelId)] = params.seedText
                preferences[widthKey(modelId)] = params.width
                preferences[heightKey(modelId)] = params.height
                preferences[schedulerKey(modelId)] = params.scheduler
                preferences[aspectRatioKey(modelId)] = params.aspectRatio
                preferences[denoiseStrengthKey(modelId)] = params.denoiseStrength
                preferences[batchCountKey(modelId)] = params.batchCount
                preferences[showDiffusionProcessKey(modelId)] = params.showDiffusionProcess
                preferences[previewStrideKey(modelId)] = params.previewStride
                preferences[outputFormatKey(modelId)] = params.outputFormatWire
                preferences[ultrafixStepsKey(modelId)] = params.ultrafixSteps
                preferences[ultrafixDenoiseStepsKey(modelId)] = params.ultrafixDenoiseSteps
                preferences[ultrafixQualityDenoiseKey(modelId)] = params.ultrafixQualityDenoise
            }
            Log.d(TAG, "已保存生图参数: modelId=$modelId")
        }.onFailure { error ->
            // 落盘失败只记录日志：参数仍保留在内存态，不阻断生成
            Log.w(TAG, "save 失败: modelId=$modelId, ${error.message}")
        }
    }
}

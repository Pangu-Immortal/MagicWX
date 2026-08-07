/**
 * HistoryEntity - 图片生成历史记录的 Room 实体
 *
 * 功能：
 * - 定义 generation_history 表结构，持久化每一次本地生图的完整参数与产物路径
 * - 字段语义与 LocalDream 的历史实体保持一致，便于后续迁移与能力对齐
 *
 * 字段说明：
 * - id: 自增主键
 * - prompt / negativePrompt: 正向 / 负向提示词
 * - mode: 生图模式（txt2img / img2img / inpaint 等）
 * - steps / cfg / seed: 采样步数、CFG 引导强度、随机种子
 * - width / height: 输出图像分辨率
 * - scheduler: 采样器名称
 * - denoiseStrength: 重绘强度（img2img / inpaint 使用）
 * - outputPath: 生成图片落盘路径
 * - durationMillis: 生图耗时（毫秒）
 * - createdAt: 记录创建时间戳（毫秒）
 * - thumbnailPath: 缩略图路径，可选
 * - modelId: 生成该结果的模型 ID（schema v2 新增，旧行迁移默认空串）
 * - runOnCpu: 是否 CPU 推理（schema v4 新增，默认 false=NPU）
 * - useOpenCL: 是否启用 OpenCL 加速（schema v4 新增，默认 false）
 */
package com.qihao.open.rwkv.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "generation_history",
    indices = [
        Index(value = ["createdAt"]), // 加速按时间倒序查询
        Index(value = ["mode"]),      // 加速按模式筛选
        Index(value = ["modelId"]),   // 加速按模型筛选
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val prompt: String,
    val negativePrompt: String,
    val mode: String,
    val steps: Int,
    val cfg: Float,
    val seed: Long,
    val width: Int,
    val height: Int,
    val scheduler: String,
    val denoiseStrength: Float,
    val outputPath: String,
    val durationMillis: Long,
    val createdAt: Long,
    val thumbnailPath: String? = null,
    val modelId: String = "", // schema v2：记录生成模型 ID，默认空串兼容旧数据与既有单测构造
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false, // schema v3：收藏标记，默认 0 兼容旧数据
    @ColumnInfo(defaultValue = "0") val runOnCpu: Boolean = false, // schema v4：CPU 推理标记，默认 false=NPU
    @ColumnInfo(defaultValue = "0") val useOpenCL: Boolean = false, // schema v4：OpenCL 加速标记，默认 false
)

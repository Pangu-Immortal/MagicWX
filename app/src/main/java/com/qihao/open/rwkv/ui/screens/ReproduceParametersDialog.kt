/**
 * ReproduceParametersDialog - 参数复现对话框
 *
 * 功能：
 * - 从历史记录 HistoryEntity 一键复现所有生成参数（含 seed/width/height/modelId）
 * - 展示全部参数键值对，点击"复现"按钮构建 LocalDreamImageRequest 回调 onReproduce
 * - 对齐 local-dream 原仓库 ReproduceParametersDialog 的展示逻辑，
 *   但简化为无字段选择的一键复现模式
 *
 * 移植来源：modules/local-dream/.../ParamShareDialogs.kt ReproduceParametersDialog
 * 对齐参照行号：198-300（原仓库 ReproduceParametersDialog）
 *
 * 调用方：MainActivity 历史记录 overlay 底部动作栏，需新增"复现"按钮接入
 */
package com.qihao.open.rwkv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.open.rwkv.R
import com.qihao.open.rwkv.data.db.HistoryEntity
import com.qihao.open.rwkv.model.image.LocalDreamDefaults
import com.qihao.open.rwkv.model.image.LocalDreamGenerationMode
import com.qihao.open.rwkv.model.image.LocalDreamImageRequest

/** 单行参数展示：标签 + 值，右侧值可多行省略 */
@Composable
private fun ParamRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)                       // 标签占 35% 宽度
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.65f)                       // 值占 65% 宽度，长文本省略
        )
    }
}

/**
 * 参数复现对话框
 *
 * @param historyItem 历史记录实体，承载全部生成参数
 * @param onReproduce 点击"复现"回调，传入根据 historyItem 构建的 LocalDreamImageRequest
 * @param onDismiss   关闭对话框回调
 */
@Composable
fun ReproduceParametersDialog(
    historyItem: HistoryEntity,
    onReproduce: (LocalDreamImageRequest) -> Unit,
    onDismiss: () -> Unit
) {
    // 解析 mode wireValue 为枚举，对齐 MainActivity.toImageRequest() 的容错逻辑
    val mode = remember(historyItem.mode) {
        LocalDreamGenerationMode.entries.firstOrNull { it.wireValue == historyItem.mode }
            ?: LocalDreamGenerationMode.TEXT_TO_IMAGE              // 未知模式回退文生图
    }

    // 调度器展示名，对齐 LocalDreamDefaults.schedulerLabel
    val schedulerLabel = remember(historyItem.scheduler) {
        LocalDreamDefaults.schedulerLabel(historyItem.scheduler)
    }

    // 构建复现请求，闭包延迟构造避免每次重组都新建对象
    val buildRequest: () -> LocalDreamImageRequest = remember(historyItem) {
        {
            LocalDreamImageRequest(
                prompt = historyItem.prompt,
                negativePrompt = historyItem.negativePrompt,
                steps = historyItem.steps,
                cfg = historyItem.cfg,
                seed = historyItem.seed,
                width = historyItem.width,
                height = historyItem.height,
                scheduler = historyItem.scheduler,
                mode = mode,
                denoiseStrength = historyItem.denoiseStrength
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.reproduce_params_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 提示文案
                Text(
                    text = stringResource(R.string.reproduce_params_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // 提示词区域：正/负向提示词占据更多空间，用独立区块展示
                Text(
                    text = stringResource(R.string.field_prompt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Text(
                    text = historyItem.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.field_negative_prompt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Text(
                    text = historyItem.negativePrompt.ifBlank { "（无）" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 数值参数区：步数 / CFG / 种子 / 尺寸 / 调度器 / 降噪 / 模式 / 模型
                ParamRow(
                    label = stringResource(R.string.field_steps),
                    value = historyItem.steps.toString()
                )
                ParamRow(
                    label = stringResource(R.string.field_cfg),
                    value = "%.1f".format(historyItem.cfg)
                )
                ParamRow(
                    label = stringResource(R.string.field_seed),
                    value = historyItem.seed.toString()
                )
                ParamRow(
                    label = stringResource(R.string.field_width),
                    value = "${historyItem.width} px"
                )
                ParamRow(
                    label = stringResource(R.string.field_height),
                    value = "${historyItem.height} px"
                )
                ParamRow(
                    label = stringResource(R.string.field_scheduler),
                    value = schedulerLabel
                )
                // 图生图/重绘模式才展示降噪强度，对齐原仓库逻辑（参照 ParamShareDialogs.kt:211-213）
                if (mode != LocalDreamGenerationMode.TEXT_TO_IMAGE) {
                    ParamRow(
                        label = stringResource(R.string.field_denoise),
                        value = "%.2f".format(historyItem.denoiseStrength)
                    )
                }
                ParamRow(
                    label = stringResource(R.string.field_mode),
                    value = mode.wireValue
                )
                // 模型 ID 非空才展示，对齐原仓库不展示空 modelId 的行为
                if (historyItem.modelId.isNotBlank()) {
                    ParamRow(
                        label = stringResource(R.string.field_model_id),
                        value = historyItem.modelId
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onReproduce(buildRequest()) }
            ) {
                Text(
                    text = stringResource(R.string.reproduce_button),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
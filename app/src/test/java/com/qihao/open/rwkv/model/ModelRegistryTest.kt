/**
 * ModelRegistryTest - 模型注册表基础约束测试
 *
 * 功能：
 * - 验证模型 ID 唯一
 * - 验证默认模型为完全支持的 RWKV 模型
 * - 验证 Transformer 模型都声明 tokenizer 下载地址
 */
package com.qihao.open.rwkv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegistryTest {

    @Test
    fun modelIdsAreUnique() {
        val ids = ModelRegistry.models.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun defaultModelIsFullySupportedRwkv() {
        val defaultModel = ModelRegistry.getDefault()

        assertEquals(ModelArch.RWKV, defaultModel.arch)
        assertTrue(defaultModel.isFullySupported)
    }

    @Test
    fun transformerModelsDeclareTokenizerUrl() {
        val transformerModels = ModelRegistry.models.filter { it.arch == ModelArch.TRANSFORMER }

        assertTrue(transformerModels.isNotEmpty())
        transformerModels.forEach { modelInfo ->
            assertNotNull("Transformer 模型缺少 tokenizerUrl: ${modelInfo.id}", modelInfo.tokenizerUrl)
        }
    }
}

/**
 * ModelRuntimeAdapterFactoryTest - runtime adapter 分发测试
 *
 * 功能：
 * - 验证内置模型路由到内置文本 adapter
 * - 验证已验证 ONNX 文本模型路由到 ONNX 文本 adapter
 * - 验证候选模型路由到未支持 adapter，避免假支持
 */
package com.qihao.open.rwkv.model

import com.qihao.open.rwkv.model.adapter.BuiltinTextAdapter
import com.qihao.open.rwkv.model.adapter.ModelRuntimeAdapterFactory
import com.qihao.open.rwkv.model.adapter.OnnxTextGenerationAdapter
import com.qihao.open.rwkv.model.adapter.UnsupportedRuntimeAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRuntimeAdapterFactoryTest {

    @Test
    fun builtinModelUsesBuiltinTextAdapter() {
        val modelInfo = ModelRegistry.models.first { it.adapterType == RuntimeAdapterType.BUILTIN_TEXT }
        val adapter = ModelRuntimeAdapterFactory.create(modelInfo)

        assertTrue(adapter is BuiltinTextAdapter)
        assertTrue(adapter.canLoad(modelInfo))
    }

    @Test
    fun verifiedOnnxTextModelUsesOnnxTextAdapter() {
        val modelInfo = ModelRegistry.models.first { it.adapterType == RuntimeAdapterType.ONNX_TEXT_GENERATION }
        val adapter = ModelRuntimeAdapterFactory.create(modelInfo)

        assertTrue(adapter is OnnxTextGenerationAdapter)
        assertTrue(adapter.canLoad(modelInfo))
    }

    @Test
    fun candidateModelUsesUnsupportedAdapter() {
        val modelInfo = ModelRegistry.getCandidates().first()
        val adapter = ModelRuntimeAdapterFactory.create(modelInfo)

        assertTrue(adapter is UnsupportedRuntimeAdapter)
        assertFalse(adapter.canLoad(modelInfo))
    }
}

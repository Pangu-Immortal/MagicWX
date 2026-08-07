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
import com.qihao.open.rwkv.model.adapter.LlamaCppAdapter
import com.qihao.open.rwkv.model.adapter.ModelRuntimeAdapterFactory
import com.qihao.open.rwkv.model.image.MnnImageGenerationAdapter
import com.qihao.open.rwkv.model.adapter.OnnxTextGenerationAdapter
import com.qihao.open.rwkv.model.adapter.UnsupportedRuntimeAdapter
import com.google.protobuf.Any
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
    fun rwkvModelUsesOnnxTextAdapter() {
        val modelInfo = ModelRegistry.models.first { it.id == "rwkv7-world-0.4b" }
        val adapter = ModelRuntimeAdapterFactory.create(modelInfo)

        assertTrue(adapter is OnnxTextGenerationAdapter)
        assertTrue(adapter.canLoad(modelInfo))
        assertTrue(modelInfo.arch == ModelArch.RWKV)
    }

    @Test
    fun verifiedGgufModelUsesLlamaCppAdapter() {
        val modelInfo = ModelRegistry.models.first { it.adapterType == RuntimeAdapterType.LLAMA_CPP }
        val adapter = ModelRuntimeAdapterFactory.create(modelInfo)

        assertTrue(adapter is LlamaCppAdapter)
        assertTrue(adapter.canLoad(modelInfo))
    }

    @Test
    fun unverifiedMnnImageModelUsesUnsupportedAdapter() {
        val modelInfo = ModelRegistry.allModels.first { it.id == "sd15-mnn-opencl-8bit" }
        val adapter = ModelRuntimeAdapterFactory.create(modelInfo)

        assertTrue(adapter is UnsupportedRuntimeAdapter)
        assertFalse(adapter.canLoad(modelInfo))
    }

    @Test
    fun mediaPipeImageCandidateUsesUnsupportedAdapter() {
        val modelInfo = ModelRegistry.allModels.first { it.id == "minisd-mediapipe" }
        val adapter = ModelRuntimeAdapterFactory.create(modelInfo)

        assertTrue(adapter is UnsupportedRuntimeAdapter)
        assertFalse(adapter.canLoad(modelInfo))
    }

    @Test
    fun mediaPipeImageGeneratorUsesFullProtobufRuntime() {
        val hasFullBuildMethod = Any.newBuilder().javaClass.declaredMethods.any {
            it.name == "build" && it.parameterTypes.isEmpty() && it.returnType == Any::class.java
        }

        assertTrue(hasFullBuildMethod)
    }

    @Test
    fun candidateModelUsesUnsupportedAdapter() {
        // 取 adapter 未接入的候选（设备门禁放行的 QNN 候选在骁龙设备上会路由到真实 adapter，
        // 用 !adapterAvailable 过滤保证两种设备环境下断言都稳定）
        val modelInfo = ModelRegistry.getCandidates().first { !it.adapterAvailable }
        val adapter = ModelRuntimeAdapterFactory.create(modelInfo)

        assertTrue(adapter is UnsupportedRuntimeAdapter)
        assertFalse(adapter.canLoad(modelInfo))
    }

    @Test
    fun deviceGatedQnnModelUsesUnsupportedAdapterWithDeviceReason() {
        // 设备门禁拦截的 QNN 模型：工厂必须路由到未支持 adapter 并携带设备原因，
        // 而不是泛化的"adapter 未接入"文案（JVM 单测无 SoC，QNN 族全部被门禁拦截）
        val gated = ModelRegistry.allModels.first { it.isDeviceGatedQnnUnavailable }
        val adapter = ModelRuntimeAdapterFactory.create(gated)

        assertTrue("门禁 QNN 模型必须走未支持 adapter: ${gated.id}", adapter is UnsupportedRuntimeAdapter)
        assertFalse(adapter.canLoad(gated))
    }
}

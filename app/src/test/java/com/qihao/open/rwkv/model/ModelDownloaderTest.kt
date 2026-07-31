/**
 * ModelDownloaderTest - 模型包就绪门禁单元测试
 *
 * 功能：
 * - 验证 RWKV 模型只需要非空 ONNX 主文件
 * - 验证 Transformer 模型必须同时具备 ONNX 与 tokenizer.json
 * - 验证零字节文件、临时文件和半包目录不会进入已下载列表
 */
package com.qihao.open.rwkv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class ModelDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rwkvModelReady_whenNonEmptyOnnxExists() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.models.first { it.arch == ModelArch.RWKV }

        writeSizedModelFile(filesDir, modelInfo.id, "model.onnx", 11L * 1024L * 1024L)

        assertTrue(downloader.isModelReady(modelInfo))
    }

    @Test
    fun builtinModelReady_withoutOnnxFile() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.models.first { it.arch == ModelArch.BUILTIN }

        assertTrue(downloader.isModelReady(modelInfo))
        assertTrue(downloader.getDownloadedModels().contains(modelInfo.id))
    }

    @Test
    fun transformerModelNotReady_whenTokenizerMissing() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.models.first { it.arch == ModelArch.TRANSFORMER }

        writeSizedModelFile(filesDir, modelInfo.id, "model_q4.onnx", 11L * 1024L * 1024L)

        assertFalse(downloader.isModelReady(modelInfo))
    }

    @Test
    fun transformerModelReady_whenOnnxAndTokenizerExist() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.models.first { it.arch == ModelArch.TRANSFORMER }

        writeSizedModelFile(filesDir, modelInfo.id, "model_q4.onnx", 11L * 1024L * 1024L)
        writeModelFile(filesDir, modelInfo.id, "tokenizer.json", "{}")

        assertTrue(downloader.isModelReady(modelInfo))
    }

    @Test
    fun largeModelNotReady_whenSmallOnnxExternalDataMissing() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.models.first { it.id == "gemma3-1b" }

        writeModelFile(filesDir, modelInfo.id, "model_q4.onnx", "small-onnx-graph")
        writeModelFile(filesDir, modelInfo.id, "tokenizer.json", "{}")

        assertFalse(downloader.isModelReady(modelInfo))
    }

    @Test
    fun largeModelReady_whenSmallOnnxExternalDataAndTokenizerExist() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.models.first { it.id == "gemma3-1b" }

        writeModelFile(filesDir, modelInfo.id, "model_q4.onnx", "small-onnx-graph")
        writeModelFile(filesDir, modelInfo.id, "model_q4.onnx_data", "external-data")
        writeModelFile(filesDir, modelInfo.id, "tokenizer.json", "{}")

        assertTrue(downloader.isModelReady(modelInfo))
    }

    @Test
    fun modelNotReady_whenOnnxIsEmptyOrTemporary() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.models.first { it.arch == ModelArch.RWKV }
        val modelDir = File(File(filesDir, "models"), modelInfo.id)

        modelDir.mkdirs()
        File(modelDir, "model.onnx").writeText("")
        File(modelDir, "other.onnx.downloading").writeText("partial")

        assertFalse(downloader.isModelReady(modelInfo))
    }

    @Test
    fun downloadedModelsOnlyContainsReadyPackages() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val builtinInfo = ModelRegistry.models.first { it.arch == ModelArch.BUILTIN }
        val rwkvInfo = ModelRegistry.models.first { it.arch == ModelArch.RWKV }
        val transformerInfo = ModelRegistry.models.first { it.arch == ModelArch.TRANSFORMER }

        writeSizedModelFile(filesDir, rwkvInfo.id, "model.onnx", 11L * 1024L * 1024L)
        writeModelFile(filesDir, transformerInfo.id, "model_q4.onnx", "onnx")

        assertEquals(setOf(builtinInfo.id, rwkvInfo.id), downloader.getDownloadedModels())
    }

    /** 写入测试模型文件，并自动创建模型目录 */
    private fun writeModelFile(filesDir: File, modelId: String, filename: String, content: String) {
        val modelDir = File(File(filesDir, "models"), modelId)
        modelDir.mkdirs()
        File(modelDir, filename).writeText(content)
    }

    /** 写入指定长度的测试模型文件，并自动创建模型目录 */
    private fun writeSizedModelFile(filesDir: File, modelId: String, filename: String, sizeBytes: Long) {
        val modelDir = File(File(filesDir, "models"), modelId)
        modelDir.mkdirs()
        RandomAccessFile(File(modelDir, filename), "rw").use { it.setLength(sizeBytes) }
    }
}

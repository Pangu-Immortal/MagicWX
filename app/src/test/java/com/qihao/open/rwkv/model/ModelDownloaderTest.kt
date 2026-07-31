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

class ModelDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rwkvModelReady_whenNonEmptyOnnxExists() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.getDefault()

        writeModelFile(filesDir, modelInfo.id, "model.onnx", "onnx")

        assertTrue(downloader.isModelReady(modelInfo))
    }

    @Test
    fun transformerModelNotReady_whenTokenizerMissing() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.models.first { it.arch == ModelArch.TRANSFORMER }

        writeModelFile(filesDir, modelInfo.id, "model_q4.onnx", "onnx")

        assertFalse(downloader.isModelReady(modelInfo))
    }

    @Test
    fun transformerModelReady_whenOnnxAndTokenizerExist() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.models.first { it.arch == ModelArch.TRANSFORMER }

        writeModelFile(filesDir, modelInfo.id, "model_q4.onnx", "onnx")
        writeModelFile(filesDir, modelInfo.id, "tokenizer.json", "{}")

        assertTrue(downloader.isModelReady(modelInfo))
    }

    @Test
    fun modelNotReady_whenOnnxIsEmptyOrTemporary() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = ModelRegistry.getDefault()
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
        val rwkvInfo = ModelRegistry.getDefault()
        val transformerInfo = ModelRegistry.models.first { it.arch == ModelArch.TRANSFORMER }

        writeModelFile(filesDir, rwkvInfo.id, "model.onnx", "onnx")
        writeModelFile(filesDir, transformerInfo.id, "model_q4.onnx", "onnx")

        assertEquals(setOf(rwkvInfo.id), downloader.getDownloadedModels())
    }

    /** 写入测试模型文件，并自动创建模型目录 */
    private fun writeModelFile(filesDir: File, modelId: String, filename: String, content: String) {
        val modelDir = File(File(filesDir, "models"), modelId)
        modelDir.mkdirs()
        File(modelDir, filename).writeText(content)
    }
}

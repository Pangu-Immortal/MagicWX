/**
 * ModelDownloaderTest - 模型包就绪门禁单元测试
 *
 * 功能：
 * - 验证 RWKV 模型只需要非空 ONNX 主文件
 * - 验证 Transformer 模型必须同时具备 ONNX 与 tokenizer.json
 * - 验证零字节文件、临时文件和半包目录不会进入已下载列表
 * - 验证未接入 adapter 的候选模型不会进入可加载状态
 * - 验证显式资产路径按模型目录隔离
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
        val modelInfo = rwkvTestModelInfo()

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
        val modelInfo = externalDataTestModelInfo()

        writeModelFile(filesDir, modelInfo.id, "model_q4.onnx", "small-onnx-graph")
        writeModelFile(filesDir, modelInfo.id, "tokenizer.json", "{}")

        assertFalse(downloader.isModelReady(modelInfo))
    }

    @Test
    fun largeModelReady_whenSmallOnnxExternalDataAndTokenizerExist() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = externalDataTestModelInfo()

        writeModelFile(filesDir, modelInfo.id, "model_q4.onnx", "small-onnx-graph")
        writeModelFile(filesDir, modelInfo.id, "model_q4.onnx_data", "external-data")
        writeModelFile(filesDir, modelInfo.id, "tokenizer.json", "{}")

        assertTrue(downloader.isModelReady(modelInfo))
    }

    @Test
    fun modelNotReady_whenOnnxIsEmptyOrTemporary() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = rwkvTestModelInfo()
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
        val transformerInfo = ModelRegistry.models.first { it.arch == ModelArch.TRANSFORMER }

        writeModelFile(filesDir, transformerInfo.id, "model_q4.onnx", "onnx")

        assertEquals(setOf(builtinInfo.id), downloader.getDownloadedModels())
    }

    @Test
    fun candidateModelNotReady_whenAdapterUnavailable() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val candidate = ModelRegistry.getCandidates().first()

        val readiness = downloader.getModelReadiness(candidate)

        assertFalse(readiness.isReady)
        assertTrue(readiness.reason.contains("候选模型") || readiness.reason.contains("adapter"))
    }

    @Test
    fun assetPathUsesModelScopedDirectory() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = explicitAssetTestModelInfo()
        val asset = modelInfo.assets.first()

        val assetPath = downloader.getAssetPath(modelInfo, asset)

        assertTrue(assetPath.endsWith("models/${modelInfo.id}/${asset.filename}"))
    }

    @Test
    fun explicitNonTextAssetsReady_whenAdapterAvailableAndRequiredFilesExist() {
        val filesDir = temporaryFolder.newFolder("files")
        val downloader = ModelDownloader(filesDir)
        val modelInfo = explicitAssetTestModelInfo().copy(
            adapterAvailable = true,
            adapterType = RuntimeAdapterType.MEDIAPIPE_TASK,
            capability = ModelCapability.IMAGE_SEGMENTATION
        )

        writeModelFile(filesDir, modelInfo.id, "encoder.onnx", "asset")

        assertTrue(downloader.isModelReady(modelInfo))
    }

    /** 构造测试专用 RWKV 模型，验证底层兼容分支但不进入用户可选注册表 */
    private fun rwkvTestModelInfo(): ModelInfo {
        return ModelInfo(
            id = "rwkv-test-only",
            name = "RWKV Test Only",
            description = "测试 RWKV 文件门禁",
            arch = ModelArch.RWKV,
            paramSize = "test",
            quantization = "test",
            downloadUrl = "https://example.invalid/model.onnx",
            fileSizeMB = 11,
            isFullySupported = false
        )
    }

    /** 构造测试专用外部数据模型，验证小 ONNX + onnx_data 完整性门禁 */
    private fun externalDataTestModelInfo(): ModelInfo {
        return ModelInfo(
            id = "external-data-test-only",
            name = "External Data Test Only",
            description = "测试 ONNX 外部数据门禁",
            arch = ModelArch.TRANSFORMER,
            paramSize = "test",
            quantization = "test",
            downloadUrl = "https://example.invalid/model_q4.onnx",
            fileSizeMB = 1024,
            isFullySupported = false,
            tokenizerUrl = "https://example.invalid/tokenizer.json"
        )
    }

    /** 构造测试专用显式资产模型，验证多资产路径契约 */
    private fun explicitAssetTestModelInfo(): ModelInfo {
        return ModelInfo(
            id = "asset-test-only",
            name = "Asset Test Only",
            description = "测试显式资产路径",
            arch = ModelArch.TRANSFORMER,
            paramSize = "test",
            quantization = "test",
            downloadUrl = "",
            fileSizeMB = 1,
            isFullySupported = false,
            adapterAvailable = false,
            assets = listOf(
                ModelAsset(
                    filename = "encoder.onnx",
                    url = "https://example.invalid/encoder.onnx",
                    kind = ModelAssetKind.ENCODER
                )
            )
        )
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

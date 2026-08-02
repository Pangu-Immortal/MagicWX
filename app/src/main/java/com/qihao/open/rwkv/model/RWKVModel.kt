/**
 * RWKVModel - 多架构模型推理引擎
 *
 * 功能：
 * - loadModel(): 加载 ONNX 模型（自动检测 RWKV/Transformer 架构）
 * - generate(): 流式文本生成（逐 token 回调）
 * - resetState(): 重置模型状态（RNN 状态或 KV-cache）
 * - close(): 释放资源
 *
 * 支持架构：
 * - RWKV: RNN 状态传递，适用于 RWKV-7 等模型
 * - Transformer: KV-cache 机制，适用于 SmolLM2、Qwen3、DeepSeek 等模型
 */
package com.qihao.open.rwkv.model

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.Arrays
import kotlin.coroutines.coroutineContext
import kotlin.math.exp

class RWKVModel(private val tokenizer: ITokenizer) : TextGenerationEngine {

    companion object {
        private const val TAG = "RWKVModel"
    }

    private var environment: OrtEnvironment? = null      // ONNX 运行环境
    private var session: OrtSession? = null               // ONNX 推理会话
    private var inputNames: List<String> = emptyList()   // 模型输入名
    private var outputNames: List<String> = emptyList()  // 模型输出名

    // === 架构检测 ===
    private var isTransformer = false                    // 是否为 Transformer 架构

    // === RWKV 状态 ===
    private var numLayers = 0                            // RWKV 层数
    private var embedDim = 0                             // RWKV 嵌入维度
    private var stateMap = LinkedHashMap<String, OnnxTensor>() // RWKV RNN 状态

    // === Transformer KV-cache ===
    private var numKvLayers = 0                          // KV-cache 层数
    private var numKvHeads = 0                           // Key/Value 头数
    private var headDim = 0                              // 每个头的维度
    private var kvIsFloat16 = false                      // KV-cache 是否为 float16
    private var kvCache = LinkedHashMap<String, OnnxTensor>() // KV-cache 张量
    private var pastSeqLen = 0                           // 已处理的序列长度
    private var previousResult: OrtSession.Result? = null // 上一次推理结果（持有 KV-cache 内存）

    override val isLoaded: Boolean get() = session != null

    // ==================== 加载模型 ====================

    /**
     * 加载 ONNX 模型（自动检测架构）
     * @param modelPath 模型文件绝对路径
     */
    suspend fun loadModel(modelPath: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始加载模型: $modelPath")

            environment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }

            session = environment!!.createSession(modelPath, options)

            inputNames = session!!.inputNames.toList()
            outputNames = session!!.outputNames.toList()

            Log.d(TAG, "模型输入数量: ${inputNames.size}")
            Log.d(TAG, "模型输出数量: ${outputNames.size}")

            // 检测架构类型：Transformer 有 input_ids 输入
            isTransformer = inputNames.contains("input_ids")
            Log.d(TAG, "架构类型: ${if (isTransformer) "Transformer" else "RWKV"}")

            if (isTransformer) {
                initTransformer()                        // 初始化 Transformer 参数
            } else {
                initRwkv()                               // 初始化 RWKV 参数
            }

            Log.d(TAG, "模型加载完成")
        } catch (e: Exception) {
            Log.e(TAG, "模型加载失败: ${e.message}", e)
            throw e
        }
    }

    // ==================== RWKV 初始化 ====================

    /** 初始化 RWKV 架构参数并重置状态 */
    private fun initRwkv() {
        if (inputNames.size > 1) {
            val stateInfo = session!!.inputInfo[inputNames[1]]
            val shape = (stateInfo?.info as? TensorInfo)?.shape
            if (shape != null && shape.size == 2 && shape[0] > 0 && shape[1] > 0) {
                numLayers = shape[0].toInt()
                embedDim = shape[1].toInt()
                Log.d(TAG, "RWKV 参数 - 层数: $numLayers, 嵌入维度: $embedDim")
            }
        }
        resetState()
    }

    // ==================== Transformer 初始化 ====================

    /** 初始化 Transformer 架构参数（从 KV-cache 输入推导维度） */
    private fun initTransformer() {
        // 统计 KV-cache 层数
        val kvKeyInputs = inputNames.filter {
            it.startsWith("past_key_values.") && it.endsWith(".key")
        }
        numKvLayers = kvKeyInputs.size
        Log.d(TAG, "Transformer KV-cache 层数: $numKvLayers")

        if (numKvLayers > 0) {
            // 从第一个 KV-cache 输入读取形状和类型
            val firstKvInfo = session!!.inputInfo[kvKeyInputs.first()]?.info as? TensorInfo
            if (firstKvInfo != null) {
                val shape = firstKvInfo.shape
                // 典型 shape: [batch=1, num_kv_heads, past_seq_len(-1), head_dim]
                if (shape.size == 4) {
                    numKvHeads = if (shape[1] > 0) shape[1].toInt() else 1
                    headDim = if (shape[3] > 0) shape[3].toInt() else 64
                }
                // 检测数据类型（TensorInfo.type 返回 OnnxJavaType）
                kvIsFloat16 = firstKvInfo.type == OnnxJavaType.FLOAT16
                Log.d(TAG, "KV-cache - heads: $numKvHeads, headDim: $headDim, float16: $kvIsFloat16")
            }
        }

        // 打印所有输入的形状（调试）
        for (name in inputNames.take(5)) {
            val info = session!!.inputInfo[name]?.info as? TensorInfo
            if (info != null) {
                Log.d(TAG, "  输入 '$name': shape=${info.shape.toList()}, type=${info.type}")
            }
        }

        resetState()                                     // 初始化空 KV-cache
    }

    // ==================== 状态管理 ====================

    /** 重置模型状态（根据架构类型） */
    override fun resetState() {
        if (isTransformer) {
            resetKvCache()
        } else {
            resetRwkvState()
        }
    }

    /** 重置 RWKV RNN 状态为零初始化 */
    private fun resetRwkvState() {
        stateMap.values.forEach { try { it.close() } catch (_: Exception) {} }
        stateMap.clear()

        val env = environment ?: return
        if (numLayers <= 0 || embedDim <= 0) return

        for (i in 1 until inputNames.size) {
            val name = inputNames[i]
            val buff = FloatArray(numLayers * embedDim)
            // pp_att（注意力惩罚状态）初始化为极小值
            if (name.contains("pp") || name.contains("att") && i == 1) {
                Arrays.fill(buff, -1e30f)
            }
            val tensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(buff),
                longArrayOf(numLayers.toLong(), embedDim.toLong())
            )
            stateMap[name] = tensor
        }
        Log.d(TAG, "RWKV 状态已重置")
    }

    /** 重置 Transformer KV-cache（清空为空序列） */
    private fun resetKvCache() {
        kvCache.values.forEach { try { it.close() } catch (_: Exception) {} }
        kvCache.clear()
        previousResult?.close()
        previousResult = null
        pastSeqLen = 0

        val env = environment ?: return
        val shape = longArrayOf(1, numKvHeads.toLong(), 0, headDim.toLong())

        // 为每层创建空的 KV-cache 张量（past_seq_len = 0）
        for (i in 0 until numKvLayers) {
            kvCache["past_key_values.$i.key"] = createEmptyKvTensor(env, shape)
            kvCache["past_key_values.$i.value"] = createEmptyKvTensor(env, shape)
        }
        Log.d(TAG, "Transformer KV-cache 已重置 (layers=$numKvLayers, heads=$numKvHeads, headDim=$headDim)")
    }

    /** 创建空的 KV-cache 张量（支持 float16 和 float32） */
    private fun createEmptyKvTensor(env: OrtEnvironment, shape: LongArray): OnnxTensor {
        return if (kvIsFloat16) {
            // float16: 使用 ByteBuffer + 显式类型
            val buffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
            OnnxTensor.createTensor(env, buffer, shape, OnnxJavaType.FLOAT16)
        } else {
            // float32: 使用 FloatBuffer
            OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(0)), shape)
        }
    }

    // ==================== 文本生成 ====================

    /**
     * 流式文本生成（自动选择架构对应的生成方式）
     * @param prompt 用户输入文本
     * @param maxTokens 最大生成 token 数
     * @param temperature 采样温度（越高越随机）
     * @param topP 核采样阈值
     * @param onToken 每生成一个 token 的回调
     */
    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (isTransformer) {
            generateTransformer(prompt, maxTokens, temperature, topP, onToken)
        } else {
            generateRwkv(prompt, maxTokens, temperature, topP, onToken)
        }
    }

    // ==================== RWKV 生成 ====================

    /** RWKV 架构文本生成（RNN 状态传递） */
    private suspend fun generateRwkv(
        prompt: String, maxTokens: Int,
        temperature: Float, topP: Float,
        onToken: (String) -> Unit
    ) {
        val env = environment ?: return
        val sess = session ?: return

        val promptTokens = tokenizer.encode(prompt).toMutableList()
        Log.d(TAG, "RWKV 输入编码: ${promptTokens.size} 个 token")

        val occurrence = mutableMapOf<Int, Float>()      // 重复惩罚记录
        var nextToken = 0
        val totalSteps = promptTokens.size + maxTokens

        for (step in 0 until totalSteps) {
            if (!coroutineContext.isActive) break

            nextToken = if (promptTokens.isNotEmpty()) {
                promptTokens.removeAt(0)                 // Prompt 处理阶段（兼容 API 24）
            } else {
                nextToken                                 // 生成阶段
            }

            // 创建 token 输入
            val tokenTensor = OnnxTensor.createTensor(
                env, IntBuffer.wrap(intArrayOf(nextToken)), longArrayOf(1)
            )

            // 组装输入（token + 状态）
            val inputMap = LinkedHashMap<String, OnnxTensor>()
            inputMap[inputNames[0]] = tokenTensor
            for ((name, tensor) in stateMap) { inputMap[name] = tensor }

            val result = sess.run(inputMap)

            // 提取 logits
            val logitsRaw = result.get(0).value
            val logits: FloatArray = when (logitsRaw) {
                is FloatArray -> logitsRaw
                is Array<*> -> {
                    val firstRow = logitsRaw.firstOrNull()
                    if (firstRow is FloatArray) {
                        firstRow                            // 常见输出 shape: [batch, vocab]
                    } else {
                        Log.e(TAG, "未知 logits 数组结构: ${firstRow?.javaClass}")
                        break
                    }
                }
                else -> { Log.e(TAG, "未知 logits 类型: ${logitsRaw?.javaClass}"); break }
            }

            // 更新 RNN 状态
            val newStateMap = LinkedHashMap<String, OnnxTensor>()
            for (i in 1 until inputNames.size) {
                if (i < result.size()) {
                    newStateMap[inputNames[i]] = result.get(i) as OnnxTensor
                }
            }
            stateMap = newStateMap
            tokenTensor.close()

            if (promptTokens.isNotEmpty()) continue      // Prompt 阶段不生成输出

            // 应用重复惩罚
            for ((tokenId, count) in occurrence) {
                logits[tokenId] -= (0.5f + count * 0.3f)
            }

            nextToken = sampleTopP(logits, temperature, topP)
            if (nextToken in tokenizer.stopTokenIds) {
                Log.d(TAG, "RWKV 遇到停止 token: $nextToken")
                break
            }

            occurrence[nextToken] = (occurrence[nextToken] ?: 0f) + 1f

            val text = tokenizer.decode(nextToken)
            if (text.isNotEmpty()) {
                withContext(Dispatchers.Main) { onToken(text) }
            }
        }
        Log.d(TAG, "RWKV 生成完成")
    }

    // ==================== Transformer 生成 ====================

    /** Transformer 架构文本生成（KV-cache 机制） */
    private suspend fun generateTransformer(
        prompt: String, maxTokens: Int,
        temperature: Float, topP: Float,
        onToken: (String) -> Unit
    ) {
        val env = environment ?: return
        val sess = session ?: return

        // 使用聊天模板格式化 prompt
        val formattedPrompt = tokenizer.formatChat(prompt)
        val promptTokens = tokenizer.encode(formattedPrompt)
        Log.d(TAG, "Transformer 输入编码: ${promptTokens.size} 个 token (格式化后)")

        // === Prefill 阶段：一次处理整个 prompt ===
        val seqLen = promptTokens.size
        val totalSeqLen = pastSeqLen + seqLen

        val inputIdsTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(LongArray(seqLen) { promptTokens[it].toLong() }),
            longArrayOf(1, seqLen.toLong())
        )
        val attMaskTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(LongArray(totalSeqLen) { 1L }),
            longArrayOf(1, totalSeqLen.toLong())
        )
        val posIdsTensor = createPositionIdsTensor(env, seqLen, pastSeqLen) // 仅模型声明 position_ids 时创建

        // 组装 Prefill 输入
        val inputMap = LinkedHashMap<String, OnnxTensor>()
        inputMap["input_ids"] = inputIdsTensor
        inputMap["attention_mask"] = attMaskTensor
        if (posIdsTensor != null) inputMap["position_ids"] = posIdsTensor // Gemma 等模型没有 position_ids
        for ((name, tensor) in kvCache) { inputMap[name] = tensor }

        Log.d(TAG, "Prefill 推理开始 (seq_len=$seqLen)")
        val result = sess.run(inputMap)

        // 提取最后一个 token 的 logits
        val logits = extractTransformerLogits(result, seqLen)

        // 更新 KV-cache
        updateKvCache(result)
        pastSeqLen = totalSeqLen

        // 释放 Prefill 输入张量
        inputIdsTensor.close()
        attMaskTensor.close()
        posIdsTensor?.close()

        // 采样第一个 token
        var nextToken = sampleTopP(logits, temperature, topP)
        Log.d(TAG, "Prefill 完成, 首个 token: $nextToken")

        // === Decode 阶段：逐 token 生成 ===
        val occurrence = mutableMapOf<Int, Float>()
        val generatedTokens = mutableListOf<Int>()       // Transformer 使用整体解码，避免 ByteLevel 半字符输出
        var renderedText = ""                            // 已经回调到 UI 的文本前缀

        for (step in 0 until maxTokens) {
            if (!coroutineContext.isActive) break
            if (nextToken in tokenizer.stopTokenIds) {
                Log.d(TAG, "遇到停止 token: $nextToken (step=$step)")
                break
            }

            // 解码并回调
            generatedTokens.add(nextToken)
            val fullText = tokenizer.decode(generatedTokens)
            val delta = if (fullText.startsWith(renderedText)) {
                fullText.substring(renderedText.length)        // 只把新增片段推给 UI
            } else {
                Log.w(TAG, "Transformer 解码前缀不连续，重置流式输出缓存")
                fullText
            }
            if (delta.isNotEmpty()) {
                renderedText = fullText
                withContext(Dispatchers.Main) { onToken(delta) }
            }

            occurrence[nextToken] = (occurrence[nextToken] ?: 0f) + 1f

            // 准备单 token 输入
            val newSeqLen = pastSeqLen + 1
            val newInputIds = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(longArrayOf(nextToken.toLong())),
                longArrayOf(1, 1)
            )
            val newAttMask = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(newSeqLen) { 1L }),
                longArrayOf(1, newSeqLen.toLong())
            )
            val newPosIds = createPositionIdsTensor(env, 1, pastSeqLen) // 仅模型声明 position_ids 时创建

            // 组装 Decode 输入
            val decodeInput = LinkedHashMap<String, OnnxTensor>()
            decodeInput["input_ids"] = newInputIds
            decodeInput["attention_mask"] = newAttMask
            if (newPosIds != null) decodeInput["position_ids"] = newPosIds // 按模型签名动态传入
            for ((name, tensor) in kvCache) { decodeInput[name] = tensor }

            val decodeResult = sess.run(decodeInput)

            // 提取 logits 和更新 KV-cache
            val decodeLogits = extractTransformerLogits(decodeResult, 1)
            updateKvCache(decodeResult)
            pastSeqLen = newSeqLen

            // 释放 Decode 输入
            newInputIds.close()
            newAttMask.close()
            newPosIds?.close()

            // 应用重复惩罚
            for ((tokenId, count) in occurrence) {
                decodeLogits[tokenId] -= (0.5f + count * 0.3f)
            }

            nextToken = sampleTopP(decodeLogits, temperature, topP)
        }

        Log.d(TAG, "Transformer 生成完成")
    }

    // ==================== Transformer 辅助方法 ====================

    /**
     * 从推理结果中提取 logits（取最后一个 token 的 logits）
     * 输出 shape: [batch=1, seq_len, vocab_size]
     */
    private fun extractTransformerLogits(result: OrtSession.Result, seqLen: Int): FloatArray {
        val logitsRaw = result.get(0).value
        return when (logitsRaw) {
            is Array<*> -> {
                val firstBatch = logitsRaw.firstOrNull()
                when (firstBatch) {
                    is Array<*> -> firstBatch.getOrNull(seqLen - 1) as? FloatArray ?: FloatArray(0)
                    is FloatArray -> logitsRaw.getOrNull(seqLen - 1) as? FloatArray ?: firstBatch
                    else -> {
                        Log.e(TAG, "未知 Transformer logits 数组结构: ${firstBatch?.javaClass}")
                        FloatArray(0)
                    }
                }
            }
            is FloatArray -> logitsRaw                   // 直接返回（单 token 情况）
            else -> {
                Log.e(TAG, "未知 logits 类型: ${logitsRaw?.javaClass}")
                FloatArray(0)
            }
        }
    }

    /** 按模型输入签名创建 position_ids，未声明该输入的模型直接返回 null */
    private fun createPositionIdsTensor(env: OrtEnvironment, seqLen: Int, startPosition: Int): OnnxTensor? {
        if (!inputNames.contains("position_ids")) return null
        val values = LongArray(seqLen) { (startPosition + it).toLong() }
        return OnnxTensor.createTensor(env, LongBuffer.wrap(values), longArrayOf(1, seqLen.toLong()))
    }

    /**
     * 更新 KV-cache：从推理输出中提取 present.*.key/value
     * 输出名映射：present.0.key → past_key_values.0.key
     */
    private fun updateKvCache(result: OrtSession.Result) {
        val newCache = LinkedHashMap<String, OnnxTensor>()

        for (i in 0 until numKvLayers) {
            val keyOutputName = "present.$i.key"         // 输出名
            val valueOutputName = "present.$i.value"
            val keyInputName = "past_key_values.$i.key"  // 输入名
            val valueInputName = "past_key_values.$i.value"

            // 按名称查找输出索引
            val keyIdx = outputNames.indexOf(keyOutputName)
            val valueIdx = outputNames.indexOf(valueOutputName)

            if (keyIdx >= 0) newCache[keyInputName] = result.get(keyIdx) as OnnxTensor
            if (valueIdx >= 0) newCache[valueInputName] = result.get(valueIdx) as OnnxTensor
        }

        // 关闭上一次的 Result（释放旧 KV-cache 内存）
        previousResult?.close()
        previousResult = result                          // 持有当前 Result（保持 KV-cache 有效）
        kvCache = newCache
    }

    // ==================== 采样算法 ====================

    /** Top-P 核采样 */
    private fun sampleTopP(logits: FloatArray, temperature: Float, topP: Float): Int {
        if (logits.isEmpty()) return 0
        val probs = softmax(logits, temperature)
        val indices = probs.indices.sortedByDescending { probs[it] }

        var cumProb = 0f
        var cutoffIdx = indices.size
        for (i in indices.indices) {
            cumProb += probs[indices[i]]
            if (cumProb > topP) { cutoffIdx = i + 1; break }
        }

        val candidates = indices.take(cutoffIdx)
        val candidateProbs = candidates.map { probs[it] }
        val sum = candidateProbs.sum()

        var r = Math.random().toFloat() * sum
        for (i in candidates.indices) {
            r -= candidateProbs[i]
            if (r <= 0f) return candidates[i]
        }
        return candidates.last()
    }

    /** Softmax 激活函数 */
    private fun softmax(logits: FloatArray, temperature: Float): FloatArray {
        val scaled = if (temperature != 1f) {
            FloatArray(logits.size) { logits[it] / temperature }
        } else { logits }

        val maxVal = scaled.max()
        var total = 0f
        val output = FloatArray(scaled.size)
        for (i in scaled.indices) {
            output[i] = exp((scaled[i] - maxVal).toDouble()).toFloat()
            total += output[i]
        }
        for (i in output.indices) { output[i] /= total }
        return output
    }

    // ==================== 资源管理 ====================

    /** 释放所有资源 */
    override fun close() {
        stateMap.values.forEach { try { it.close() } catch (_: Exception) {} }
        stateMap.clear()

        kvCache.values.forEach { try { it.close() } catch (_: Exception) {} }
        kvCache.clear()
        previousResult?.close()
        previousResult = null

        try { session?.close() } catch (_: Exception) {}
        session = null

        Log.d(TAG, "模型资源已释放")
    }
}

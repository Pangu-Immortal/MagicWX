/**
 * TopKLongs.kt - 有界最小堆，保留得分最高的 K 个 (score, index) 对
 *
 * 功能：
 * - 每对打包为单个 Long（score 在高 32 位，index 在低 32 位）
 * - 有符号 Long 比较天然按 score 排序（score 相同则按 index）
 * - Tag 补全建议收集最佳 K 个模糊匹配时使用，避免每个候选都分配 TagSuggestion
 *
 * 移植自：modules/local-dream app/.../data/TopKLongs.kt
 */
package com.qihao.open.rwkv.data

/**
 * 有界最小堆，保留得分最高的 [capacity] 个 (score, index) 对。
 *
 * 每对打包为单个 Long：score 在高 32 位，index 在低 32 位，
 * 有符号 Long 比较天然按 score 降序排列。Tag 补全用此收集最佳 K 个模糊匹配，
 * 避免为每个候选分配 TagSuggestion 或执行 O(n log n) 全量排序。
 */
internal class TopKLongs(private val capacity: Int) {
    private val heap = LongArray(if (capacity > 0) capacity else 1)
    private var size = 0

    /**
     * 将 (score, index) 打包为 Long 放入堆中。
     * 堆未满时直接插入并上浮；堆满时若新值大于堆顶（最小值）则替换堆顶并下沉。
     */
    fun offer(score: Int, index: Int) {
        if (capacity <= 0) return
        val packed = (score.toLong() shl 32) or (index.toLong() and 0xFFFFFFFFL)
        if (size < capacity) {
            heap[size] = packed
            siftUp(size)
            size++
        } else if (packed > heap[0]) {
            // 新值比堆顶（当前最小值）大，替换并下沉
            heap[0] = packed
            siftDown()
        }
    }

    /** 遍历堆中所有元素（不保证顺序），对每个元素执行 [action] */
    fun forEach(action: (score: Int, index: Int) -> Unit) {
        for (i in 0 until size) {
            val packed = heap[i]
            action((packed shr 32).toInt(), (packed and 0xFFFFFFFFL).toInt())
        }
    }

    /** 最小堆上浮：将 from 位置的元素向上移动到正确位置 */
    private fun siftUp(from: Int) {
        var child = from
        while (child > 0) {
            val parent = (child - 1) ushr 1
            if (heap[child] >= heap[parent]) break
            val tmp = heap[child]
            heap[child] = heap[parent]
            heap[parent] = tmp
            child = parent
        }
    }

    /** 最小堆下沉：将堆顶元素向下移动到正确位置 */
    private fun siftDown() {
        var parent = 0
        while (true) {
            val left = parent * 2 + 1
            val right = left + 1
            var smallest = parent
            if (left < size && heap[left] < heap[smallest]) smallest = left
            if (right < size && heap[right] < heap[smallest]) smallest = right
            if (smallest == parent) break
            val tmp = heap[parent]
            heap[parent] = heap[smallest]
            heap[smallest] = tmp
            parent = smallest
        }
    }
}
/**
 * App - RWKV Android 应用入口
 *
 * 职责：
 * - 全局 Application 初始化
 * - 提供全局 Context 访问
 */
package com.qihao.open.rwkv

import android.app.Application
import android.util.Log
import com.qihao.open.rwkv.model.PinnedModels
import com.qihao.open.rwkv.model.image.ImageInferenceBackendPlanner

class App : Application() {

    companion object {
        private const val TAG = "RWKVApp"
    }

    override fun onCreate() {
        super.onCreate()
        PinnedModels.initialize(this)  // 模型置顶持久化单例初始化（对齐参照 PinnedModels）
        val imageBackendChoice = ImageInferenceBackendPlanner.initializeAtAppStart(this)
        Log.d(TAG, "RWKV Android 应用启动，图片推理架构=${imageBackendChoice.backend.displayName}，原因=${imageBackendChoice.reason}")
    }
}

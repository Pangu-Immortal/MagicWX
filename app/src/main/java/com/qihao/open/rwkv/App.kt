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

class App : Application() {

    companion object {
        private const val TAG = "RWKVApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RWKV Android 应用启动")
    }
}

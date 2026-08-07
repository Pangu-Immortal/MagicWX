#ifndef MAGICWX_LOCALDREAM_LOGGER_HPP
#define MAGICWX_LOCALDREAM_LOGGER_HPP

// MagicWX 日志宏分发头。
//
// QNN 构建（MAGICWX_WITH_QNN）：委托 QAIRT SampleApp 的真实 Logger
// （third_party/SampleApp/src/Log/Logger.hpp，经 -I .../SampleApp/src 解析），
// 与参照 main.cpp 的 #include "Logger.hpp" 同源；它同时提供 QNN_* 宏与
// qnn::log::initializeLogging/setLogLevel。注意 CMakeLists 必须把 SampleApp
// 的 src 目录放在 localdream 目录之后加入 include 搜索路径。
//
// CPU-only 构建：真实 QNN SDK 的 Logger 不可用，SD1.5 CPU 路径不依赖 QNN
// runtime，只需 QNN_INFO/WARN/ERROR/DEBUG 宏能编译；这里把它们映射到 Android
// logcat，保留 local-dream pipeline 源码不动。
//
// include guard 刻意与 SDK Logger 不同名，避免 QNN 构建下两者互相屏蔽。

#ifdef MAGICWX_WITH_QNN

#include "Log/Logger.hpp"

#else  // !MAGICWX_WITH_QNN

#include <android/log.h>

#define MAGICWX_LOG_TAG "MagicWxImageBackend"

#define QNN_INFO(...) __android_log_print(ANDROID_LOG_INFO, MAGICWX_LOG_TAG, __VA_ARGS__)
#define QNN_WARN(...) __android_log_print(ANDROID_LOG_WARN, MAGICWX_LOG_TAG, __VA_ARGS__)
#define QNN_ERROR(...) __android_log_print(ANDROID_LOG_ERROR, MAGICWX_LOG_TAG, __VA_ARGS__)
#define QNN_DEBUG(...) __android_log_print(ANDROID_LOG_DEBUG, MAGICWX_LOG_TAG, __VA_ARGS__)

#endif  // MAGICWX_WITH_QNN

#endif  // MAGICWX_LOCALDREAM_LOGGER_HPP

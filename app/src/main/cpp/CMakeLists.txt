cmake_minimum_required(VERSION 3.22.1)
project("magicwx_image")

# === 强制 Release 级优化（即使 Debug 变体） ===
# AGP Debug 变体下 CMAKE_BUILD_TYPE=Debug，MNN 全部 CPU 内核与后端主程序按 -O0 编译，
# SD1.5 UNet 单步 ~30s（20 步 ≈ 11 分钟），远慢于 local-dream release 参照速度，
# 且长耗时触发 Android 16 dataSync FGS 限时强杀。追加 -O3 + NDEBUG 恢复 release 性能；
# 调试信息(-g)保留不影响运行速度。
string(APPEND CMAKE_C_FLAGS " -O3 -DNDEBUG")
string(APPEND CMAKE_CXX_FLAGS " -O3 -DNDEBUG")

# === QNN 后端门控（QNN_SDK_ROOT 存在性驱动，对齐 local-dream CMakeLists 的 QNN 集成） ===
# QNN SDK 安装根（如 /data/qairt/2.39.0.250926）：优先 cache 变量，缺省回退环境变量。
# SDK 存在 → option(MAGICWX_WITH_QNN) 默认 ON，构建全管线（sd15cpu/sd15npu/sdxl/anima
# + QNN 超分）；SDK 缺席 → 默认 OFF，CPU-only（sd15cpu + MNN 超分），现有构建不受影响。
if((NOT DEFINED QNN_SDK_ROOT OR QNN_SDK_ROOT STREQUAL "") AND DEFINED ENV{QNN_SDK_ROOT})
    set(QNN_SDK_ROOT "$ENV{QNN_SDK_ROOT}" CACHE PATH
        "Qualcomm QAIRT/QNN SDK 安装根目录（如 /data/qairt/2.39.0.250926）" FORCE)
endif()
if(QNN_SDK_ROOT AND EXISTS "${QNN_SDK_ROOT}")
    option(MAGICWX_WITH_QNN
        "构建 QNN/HTP 管线（sd15npu/sdxl/anima + QNN 超分），需要 QNN SDK" ON)
else()
    option(MAGICWX_WITH_QNN
        "构建 QNN/HTP 管线（sd15npu/sdxl/anima + QNN 超分），需要 QNN SDK" OFF)
endif()
# 显式开启 QNN 但 SDK 不完整时立即报清晰错误，避免中途 file(COPY) 失败难定位
if(MAGICWX_WITH_QNN)
    if(NOT QNN_SDK_ROOT
       OR NOT EXISTS "${QNN_SDK_ROOT}/lib/aarch64-android/libQnnHtp.so"
       OR NOT EXISTS "${QNN_SDK_ROOT}/include/QNN"
       OR NOT EXISTS "${QNN_SDK_ROOT}/examples/QNN/SampleApp/SampleApp")
        message(FATAL_ERROR
            "MAGICWX_WITH_QNN=ON 但 QNN SDK 不完整：QNN_SDK_ROOT='${QNN_SDK_ROOT}'。\n"
            "需要 lib/aarch64-android/libQnnHtp.so、include/QNN、"
            "examples/QNN/SampleApp/SampleApp。\n"
            "CPU-only 构建请 -DMAGICWX_WITH_QNN=OFF。")
    endif()
    message(STATUS "MagicWX: QNN 构建已启用，QNN_SDK_ROOT=${QNN_SDK_ROOT}")
else()
    message(STATUS
        "MagicWX: QNN 未启用（QNN_SDK_ROOT 未设置或不存在）——CPU-only：sd15cpu + MNN 超分")
endif()

# === Rust 工具链定位（tokenizers-cpp 的 cargo 交叉编译需要） ===
# rustup 装到 ~/.cargo/bin；AGP/Android Studio 构建环境 PATH 可能不含它，所以显式查找。
find_program(MAGICWX_CARGO cargo
    PATHS $ENV{HOME}/.cargo/bin /usr/local/bin /opt/homebrew/bin
    NO_DEFAULT_PATH)
find_program(MAGICWX_CARGO cargo)
if(NOT MAGICWX_CARGO)
    message(FATAL_ERROR
        "cargo 未找到。安装 Rust：\n"
        "  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y\n"
        "  source \"$HOME/.cargo/env\"\n"
        "  rustup target add aarch64-linux-android")
endif()
message(STATUS "MagicWX cargo: ${MAGICWX_CARGO}")

# tokenizers-cpp 的 cargo 交叉编译依赖 ANDROID_TOOLCHAIN_ROOT + ANDROID_NATIVE_API_LEVEL
# NDK 工具链通常已设置；缺失时从 ANDROID_NDK_ROOT 派生作为兜底。
if(ANDROID)
    if(NOT DEFINED ANDROID_TOOLCHAIN_ROOT)
        if(DEFINED ANDROID_NDK_ROOT)
            set(ANDROID_TOOLCHAIN_ROOT "${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/darwin-x86_64")
        endif()
    endif()
    if(NOT DEFINED ANDROID_NATIVE_API_LEVEL)
        set(ANDROID_NATIVE_API_LEVEL ${ANDROID_PLATFORM_LEVEL})
    endif()
    message(STATUS "MagicWX ANDROID_TOOLCHAIN_ROOT=${ANDROID_TOOLCHAIN_ROOT}")
    message(STATUS "MagicWX ANDROID_NATIVE_API_LEVEL=${ANDROID_NATIVE_API_LEVEL}")
endif()

# === 构建 tokenizers-cpp（Rust 静态库 + C++ 包装 + sentencepiece） ===
set(TOKENIZERS_CPP_CARGO_COMMAND "${MAGICWX_CARGO}" CACHE STRING "cargo full path" FORCE)
# rustc 1.97+ 把 dangerous_implicit_autorefs 等 edition-compat lint 升为 deny-by-default；
# tokenizers-c（xororz fork，edition 2018）+ 其依赖 HF tokenizers 0.21.1 旧代码命中。
# 用等号形式 --cap-lints=allow（无空格，避免 cmake -E env 拆断 RUSTFLAGS，对 cargo build
# 和 rustc metadata 查询都安全），等价恢复旧 rustc 行为，不改动上游 Rust 源码。
set(TOKENIZERS_CPP_RUST_FLAGS "--cap-lints=allow" CACHE STRING "" FORCE)
add_subdirectory(third_party/tokenizers-cpp tokenizers EXCLUDE_FROM_ALL)

# === magicwx_image_backend 可执行（libmagicwx_image_backend.so） ===
add_executable(magicwx_image_backend
        magicwx_image_backend.cpp
)

set_target_properties(magicwx_image_backend PROPERTIES
        OUTPUT_NAME "libmagicwx_image_backend.so"
)

target_include_directories(magicwx_image_backend PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/localdream"
        "${CMAKE_CURRENT_SOURCE_DIR}"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/cpp-httplib"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/json/include/nlohmann"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/json/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/stb"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/xtensor/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/xtl/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/xsimd/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/xtensor-blas/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/mnn/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/tokenizers-cpp/include"
)

target_compile_definitions(magicwx_image_backend PRIVATE
        XTENSOR_USE_XSIMD
)

# === 从源码构建 MNN（flag 与 local-dream 完全对齐） ===
# 旧 MagicWX 预构建 libMNN.so 未开 MNN_LOW_MEMORY：SD1.5 UNet 量化权重运行时反量化为
# fp32（约 3.4GB）导致 std::bad_alloc。low memory 模式权重保持量化、算子内按需反量化。
set(MNN_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)          # 静态链接进后端可执行，进程隔离更彻底
set(MNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)                 # 不构建 MNN 命令行工具
set(MNN_LOW_MEMORY ON CACHE BOOL "" FORCE)                   # 关键：量化权重低内存推理
set(MNN_CPU_WEIGHT_DEQUANT_GEMM ON CACHE BOOL "" FORCE)      # 关键：GEMM 内联反量化
set(MNN_BUILD_FOR_ANDROID ON CACHE BOOL "" FORCE)
set(MNN_BUILD_FOR_ANDROID_COMMAND ON CACHE BOOL "" FORCE)
set(MNN_USE_LOGCAT OFF CACHE BOOL "" FORCE)
set(MNN_OPENCL ON CACHE BOOL "" FORCE)                       # 与 local-dream 对齐；Phase 3 GPU 路径预留
set(MNN_SUPPORT_TRANSFORMER_FUSE ON CACHE BOOL "" FORCE)
add_subdirectory(third_party/mnn mnn_build EXCLUDE_FROM_ALL)

# Android 15+ 16KB page size 对齐
target_link_options(magicwx_image_backend PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(magicwx_image_backend
        android
        log
        MNN
        tokenizers_cpp
)

# === QNN 集成（MAGICWX_WITH_QNN=ON 时生效；逐项对齐 local-dream CMakeLists L7-60/L76-175） ===
# CPU-only 构建（MAGICWX_WITH_QNN=OFF）整块跳过，上方目标定义保持原样。
if(MAGICWX_WITH_QNN)
    # --- SampleApp 源码：从 SDK 拷贝 + Android 适配补丁（对齐参照 L10-20） ---
    # 参照补丁路径前缀为 3rdparty/，MagicWX 目录名为 third_party/，
    # 先重写补丁前缀再 git apply（补丁文件在本目录"third_party 补丁位"）。
    if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/SampleApp.patch")
        message(FATAL_ERROR "MAGICWX_WITH_QNN=ON 但缺少 SampleApp.patch（third_party 补丁位）")
    endif()
    file(COPY ${QNN_SDK_ROOT}/examples/QNN/SampleApp/SampleApp/
        DESTINATION ${CMAKE_CURRENT_SOURCE_DIR}/third_party/SampleApp)
    execute_process(COMMAND sed -e "s|3rdparty/|third_party/|g"
        "${CMAKE_CURRENT_SOURCE_DIR}/SampleApp.patch"
        OUTPUT_FILE "${CMAKE_CURRENT_BINARY_DIR}/SampleApp.magicwx.patch")
    # 仓库根 = cpp 目录上四级；--directory 前缀使补丁落到 app/src/main/cpp/third_party/
    execute_process(COMMAND git apply --directory=app/src/main/cpp/ --verbose
        "${CMAKE_CURRENT_BINARY_DIR}/SampleApp.magicwx.patch"
        WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/../../../.."
        RESULT_VARIABLE patch_result
        OUTPUT_VARIABLE patch_output
        ERROR_VARIABLE patch_error)
    message(STATUS "Patch: ${patch_result}")
    message(STATUS "Patch: ${patch_output}")
    message(STATUS "Patch: ${patch_error}")

    # --- QNN 运行时库拷贝到构建目录 qnnlibs（对齐参照 L22-62 全家） ---
    make_directory(${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/aarch64-android/libQnnHtp.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/aarch64-android/libQnnSystem.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/aarch64-android/libQnnHtpV68Stub.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/aarch64-android/libQnnHtpV69Stub.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/aarch64-android/libQnnHtpV73Stub.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/aarch64-android/libQnnHtpV75Stub.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/aarch64-android/libQnnHtpV79Stub.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/aarch64-android/libQnnHtpV81Stub.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v68/unsigned/libQnnHtpV68.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v68/unsigned/libQnnHtpV68Skel.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v69/unsigned/libQnnHtpV69.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v69/unsigned/libQnnHtpV69Skel.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v73/unsigned/libQnnHtpV73.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v73/unsigned/libQnnHtpV73Skel.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v75/unsigned/libQnnHtpV75.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v75/unsigned/libQnnHtpV75Skel.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v79/unsigned/libQnnHtpV79.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v81/unsigned/libQnnHtpV81.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)
    file(COPY ${QNN_SDK_ROOT}/lib/hexagon-v81/unsigned/libQnnHtpV81Skel.so
        DESTINATION ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs)

    # --- zstd（QNN 分辨率 patch 的 zstd 字典解压需要；对齐参照 L87-97） ---
    if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/zstd/build/cmake/CMakeLists.txt")
        message(FATAL_ERROR
            "MAGICWX_WITH_QNN=ON 但缺少 third_party/zstd：\n"
            "  git clone https://github.com/facebook/zstd app/src/main/cpp/third_party/zstd")
    endif()
    set(ZSTD_BUILD_PROGRAMS OFF CACHE BOOL "Build zstd programs" FORCE)
    set(ZSTD_BUILD_STATIC ON CACHE BOOL "Build static zstd library" FORCE)
    set(ZSTD_MULTITHREAD_SUPPORT OFF CACHE BOOL "Enable multithreading support" FORCE)
    set(ZSTD_BUILD_TESTS OFF CACHE BOOL "Build zstd tests" FORCE)
    add_subdirectory(third_party/zstd/build/cmake zstd EXCLUDE_FROM_ALL)

    # --- SampleApp/QNN 编译选项（对齐参照 L76-85/L170-175；仅作用于后端目标，
    #     MNN/tokenizers 子项目保持 CPU-only 路径原有 flag 不变） ---
    target_compile_options(magicwx_image_backend PRIVATE -fno-rtti -fvisibility=hidden)
    target_compile_definitions(magicwx_image_backend PRIVATE
        MAGICWX_WITH_QNN
        QNN_API=__attribute__\(\(visibility\(\"default\"\)\)\))

    # --- SampleApp 源文件（对齐参照 L150-168 的 SOURCES 收集） ---
    set(MAGICWX_SAMPLEAPP_SRC ${CMAKE_CURRENT_SOURCE_DIR}/third_party/SampleApp/src)
    file(GLOB MAGICWX_QNN_SOURCES
        "${MAGICWX_SAMPLEAPP_SRC}/QnnSampleApp.cpp"
        "${MAGICWX_SAMPLEAPP_SRC}/Log/*.cpp"
        "${MAGICWX_SAMPLEAPP_SRC}/PAL/src/linux/*.cpp"
        "${MAGICWX_SAMPLEAPP_SRC}/PAL/src/common/*.cpp"
        "${MAGICWX_SAMPLEAPP_SRC}/Utils/*.cpp"
        "${MAGICWX_SAMPLEAPP_SRC}/WrapperUtils/*.cpp")
    target_sources(magicwx_image_backend PRIVATE ${MAGICWX_QNN_SOURCES})

    # --- include 路径：QNN SDK 头 + SampleApp（对齐参照 PACKAGE_INCLUDES 的 QNN 部分）。
    #     必须追加在 localdream 目录之后：localdream/Logger.hpp 的 QNN 委托依赖该顺序。 ---
    target_include_directories(magicwx_image_backend PRIVATE
        "${QNN_SDK_ROOT}/include/QNN"
        "${MAGICWX_SAMPLEAPP_SRC}"
        "${MAGICWX_SAMPLEAPP_SRC}/CachingUtil"
        "${MAGICWX_SAMPLEAPP_SRC}/Log"
        "${MAGICWX_SAMPLEAPP_SRC}/PAL/include"
        "${MAGICWX_SAMPLEAPP_SRC}/Utils"
        "${MAGICWX_SAMPLEAPP_SRC}/WrapperUtils"
    )

    # --- 链接：平台库 + zstd + QNN 库全家（对齐参照 PLATFORM_LIBS + 任务要求） ---
    # 注意：QNN .so 同时被运行时按 --lib_dir dlopen；此处显式链接使其成为 DT_NEEDED，
    # QNN 变体打包时必须把 ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs 一并放入 jniLibs。
    target_link_libraries(magicwx_image_backend
        dl m c GLESv2 EGL
        libzstd
        ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs/libQnnHtp.so
        ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs/libQnnSystem.so
        ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs/libQnnHtpV68Stub.so
        ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs/libQnnHtpV69Stub.so
        ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs/libQnnHtpV73Stub.so
        ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs/libQnnHtpV75Stub.so
        ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs/libQnnHtpV79Stub.so
        ${CMAKE_CURRENT_BINARY_DIR}/qnnlibs/libQnnHtpV81Stub.so
    )
endif()

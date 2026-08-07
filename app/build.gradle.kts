import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)            // KSP：Room 编译器注解处理
    id("kotlin-parcelize")
}

val jllamaNative = configurations.create("jllamaNative") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations.configureEach {
    // MediaPipe ImageGenerator 当前字节码调用 full protobuf 的 Any.Builder.build(): Any。
    // protobuf-javalite 中没有该协变方法，真机会在 ImageGenerator.createFromOptions() 抛 NoSuchMethodError。
    exclude(group = "com.google.protobuf", module = "protobuf-javalite")
}

/** 解析 Android SDK 目录，优先使用 local.properties，其次使用环境变量 */
fun findAndroidSdkDirectory(): File {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        val properties = Properties()
        localPropertiesFile.inputStream().use { stream -> properties.load(stream) }
        val sdkDir = properties.getProperty("sdk.dir")
        if (!sdkDir.isNullOrBlank()) return file(sdkDir)
    }

    val envSdkDir = providers.environmentVariable("ANDROID_HOME").orNull
        ?: providers.environmentVariable("ANDROID_SDK_ROOT").orNull
    if (!envSdkDir.isNullOrBlank()) return file(envSdkDir)

    throw org.gradle.api.GradleException("未找到 Android SDK 目录，无法定位 NDK libomp.so")
}

/** 从已安装 NDK 中查找指定 arm64 native 运行库 */
fun findArm64NdkRuntimeLibrary(libraryName: String): File {
    val ndkRoot = File(findAndroidSdkDirectory(), "ndk")
    return ndkRoot.walkTopDown().firstOrNull { candidate ->
        candidate.name == libraryName &&
            (candidate.parentFile?.name == "aarch64" || candidate.parentFile?.name == "aarch64-linux-android")
    } ?: throw org.gradle.api.GradleException("未找到 Android NDK arm64 $libraryName，无法打包 llama.cpp runtime")
}

android {
    namespace = "com.qihao.open.rwkv"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.qihao.open.rwkv"
        minSdk = 24
        targetSdk = 37
        versionCode = 7
        versionName = "1.1.5"

        ndk {
            // MNN Diffusion 与 llama.cpp 当前只随包提供 arm64 真机运行库，避免其它 ABI 被误判可安装。
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                // 明确使用 C++17，匹配 MNN 官方 Android 构建产物的 STL ABI。
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 模型文件保持原始格式不压缩，ONNX / MediaPipe / GGUF / MNN Runtime 加载必需
    androidResources {
        noCompress += listOf("onnx", "onnx_data", "task", "tflite", "bin", "ggml", "gguf", "json", "txt", "ort", "mnn", "weight", "mtok")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("build/generated/jllamaJniLibs")
        }
    }

    packaging {
        jniLibs {
            // 图片后端以 lib*.so 形式打包但由 ProcessBuilder 执行，必须解压到 nativeLibraryDir。
            useLegacyPackaging = true
        }
    }

    externalNativeBuild {
        cmake {
            // MagicWX 自有 JNI 只负责桥接已验证的预编译 MNN Diffusion runtime。
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val extractJllamaAndroidNative = tasks.register("extractJllamaAndroidNative", Copy::class) {
    from(jllamaNative.map { artifact -> zipTree(artifact) }) {
        include("de/kherud/llama/Linux-Android/aarch64/libjllama.so")
        eachFile {
            relativePath = org.gradle.api.file.RelativePath(true, "arm64-v8a", "libjllama.so")
        }
        includeEmptyDirs = false
    }
    from({ findArm64NdkRuntimeLibrary("libomp.so") }) {
        into("arm64-v8a")
    }
    from({ findArm64NdkRuntimeLibrary("libc++_shared.so") }) {
        into("arm64-v8a")
    }
    into(layout.buildDirectory.dir("generated/jllamaJniLibs"))
}

tasks.matching { task ->
    task.name.startsWith("merge") && task.name.endsWith("JniLibFolders")
}.configureEach {
    dependsOn(extractJllamaAndroidNative)
}

dependencies {
    // AndroidX 核心
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Jetpack Compose（BOM 统一版本管理）
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // material3 1.4 起不再传递依赖 icons-core：历史/预览遮罩的关闭与清除图标需要显式引入
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Room：生图历史持久化（HistoryEntity/Dao/AppDatabase），KSP 处理编译器注解
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore Preferences：生图参数按 modelId 持久化（对齐 local-dream GenerationPreferences）
    implementation(libs.androidx.datastore.preferences)

    // ONNX Runtime 模型推理
    implementation(libs.onnxruntime.android)

    // MediaPipe LLM Inference，用于加载 .task / LiteRT 端侧大模型包
    implementation(libs.mediapipe.tasks.genai)

    // MediaPipe Image Generator，用于加载 SDAI 转换后的本地 Stable Diffusion 模型目录
    implementation(libs.mediapipe.tasks.vision.image.generator)

    // MediaPipe ImageGenerator 需要 full protobuf，避免 javalite 覆盖 Any.Builder.build(): Any
    implementation(libs.protobuf.java)

    // java-llama.cpp，用于加载 GGUF / llama.cpp 端侧文本模型
    implementation(libs.java.llama.cpp)
    add("jllamaNative", libs.java.llama.cpp)

    // JSON 序列化
    implementation(libs.gson)

    // JVM 单元测试
    testImplementation(libs.junit)
}

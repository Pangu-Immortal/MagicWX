## Round 1: Goal Layer

目标：把 MagicWX 生图改为独立 native backend 进程 + Android 前台服务 + SSE 进度链路，先保证 CPU/MNN 生图不因 native 崩溃直接杀 App。

成功标准：

- App 可启动图片后端服务并看到 `/health` ready。
- 生图请求通过后台服务发起，UI 可看到进度、完成文件路径或失败原因。
- 用户停止生成时能取消 HTTP 调用并停止前台通知。
- 三星等已知风险设备继续门禁，不进入 native 生图。
- 不复制 `local-dream` 自有源码，不迁移 RemoteHost，不接 safety checker。

失败标准：

- 仍由 ViewModel 直接调用 MNN JNI 生图。
- native 崩溃仍会直接杀 App 主进程。
- 生图无后台通知、无进度或无法取消。
- 未验证编译即声明完成。

## Round 2: Module Layer

1. Native backend
   - 输入：模型目录、后端类型、端口、prompt。
   - 输出：`/health` HTTP 状态、`/generate` SSE progress/complete/error。
   - 风险：C++ HTTP 实现、链接 executable、Android 私有可执行权限。

2. Backend service
   - 输入：模型 ID、模型目录、推理后端选择。
   - 输出：后端状态 StateFlow、native 进程生命周期、日志。
   - 风险：前台服务类型、重复启动、端口占用。

3. Generation service
   - 输入：prompt、当前已加载图片模型。
   - 输出：进度、通知、结果文件、错误状态。
   - 风险：取消语义、长任务超时、前后台状态。

4. Runtime adapter/ViewModel
   - 输入：ModelInfo、ModelDownloader readiness。
   - 输出：可加载 ImageGenerationEngine，UI 状态映射。
   - 风险：旧 JNI 路线残留、READY_IMAGE 状态断链。

5. UI/Test
   - 输入：ViewModel state。
   - 输出：三 TAB、后端标识、生图进度、错误与重试。
   - 风险：可见模型与未验证模型混淆。

依赖：Native backend → Backend service → Generation service → adapter/ViewModel → UI/Test。

## Round 3: Execution Layer

- Backend Worker：实现 native executable、后端服务、后台生图服务和协议类型。
- Lead：整合 ViewModel/UI、OpenSpec、测试和构建验证。
- QA/Reviewer：反空扫描、单元测试、assembleDebug、检查未复制 local-dream 源码。

验收标准：

- `./gradlew testDebugUnitTest` 通过或给出明确阻塞。
- `./gradlew assembleDebug` 通过或给出明确阻塞。
- 反空扫描无新增 P0 空实现。
- `git diff` 只包含阶段二范围文件。

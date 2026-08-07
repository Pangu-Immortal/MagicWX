## 1. Planning

- [x] 1.1 记录阶段二 clean-room 架构路线。
- [x] 1.2 拆分 native backend、Android service、generation service、ViewModel/UI、QA 验收。

## 2. Native Backend

- [x] 2.1 新增独立 native executable。
- [x] 2.2 实现 `/health`。
- [x] 2.3 实现 `/generate` SSE progress/complete/error。
- [x] 2.4 保留 MNN/OpenCL/CPU 后端选择参数。

## 3. Android Services

- [x] 3.1 新增图片后端前台服务。
- [x] 3.2 新增后台生图前台服务。
- [x] 3.3 Manifest 注册服务和必要权限。
- [x] 3.4 实现停止、取消、日志和状态流。

## 4. App Integration

- [x] 4.1 MNN 图片 adapter 改走隔离后端。
- [x] 4.2 ViewModel 改为观察后台生图状态。
- [x] 4.3 UI 显示生成进度、后端状态和失败原因。
- [x] 4.4 未验证 NPU/QNN 继续阻断。

## 5. Verification

- [x] 5.1 复用现有 adapter、registry、download JVM 单元测试覆盖基础分发门禁。
- [x] 5.2 运行 `./gradlew testDebugUnitTest`。
- [x] 5.3 运行 `./gradlew assembleDebug`。
- [x] 5.4 反空扫描和语义完整性二审。

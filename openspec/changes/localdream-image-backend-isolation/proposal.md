## Why

MagicWX 当前生图仍在 App 同进程内通过 JNI 调用 MNN Diffusion，三星 s5e8855/a56x 已出现真实崩溃风险。阶段一确认 `local-dream` 的核心价值是独立 native backend + localhost HTTP/SSE + Android 前台服务生命周期。阶段二需要按该架构 clean-room 自研重写，先把 CPU 生图稳定闭环，再保留 QNN/NPU 扩展边界。

## What Changes

- 新增 MagicWX 自研图片后端进程，提供 `/health` 与 `/generate` SSE 协议。
- 新增 Android 后端前台服务，负责启动、停止、健康检查和 native 日志转发。
- 新增后台生图前台服务，负责通知、取消、进度、完成和失败状态。
- 将 MNN SD1.5 生图从同进程 JNI 路线迁移到服务/进程隔离路线。
- 保留现有三 TAB、模型卡片、下载服务和设备后端展示；不迁移 RemoteHost，不接入 safety checker。
- 下载层保留当前镜像/断点续传能力，后续补 manifest/hash 门禁。

## Impact

- `app/src/main/cpp/`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/qihao/open/rwkv/model/image/`
- `app/src/main/java/com/qihao/open/rwkv/service/`
- `app/src/main/java/com/qihao/open/rwkv/viewmodel/`
- `app/src/main/java/com/qihao/open/rwkv/MainActivity.kt`
- `app/src/test/java/com/qihao/open/rwkv/`

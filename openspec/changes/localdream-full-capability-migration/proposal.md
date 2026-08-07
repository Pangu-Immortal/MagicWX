# LocalDream 全能力迁移

## 背景

当前 MagicWX 只有简化的图片后端隔离雏形，用户仍无法在首页看到 LocalDream 完整 CPU/NPU 模型族，也无法区分哪些能力只是已登记、哪些已经可生成。

## 参考仓库

- LocalDream 开源参考实现：https://github.com/xororz/local-dream（已克隆到 `modules/local-dream` 作为能力对齐和代码复用来源）

## 目标

- 将 LocalDream 的 SD1.5 CPU、SD1.5 NPU、SDXL NPU、Anima QNN、超分模型族纳入 MagicWX 模型注册表。
- 让首页 CPU 生图 / NPU 生图 TAB 展示完整图片模型目录，并对未完成运行时验收的模型显示“迁移中”状态。
- 下载器支持 LocalDream ZIP / BIN 模型包先下载、解压和运行时文件完整性校验。
- native pipeline 未完成前，所有未验证生成路径必须阻断生成并给出明确原因。

## 非目标

- 不直接复制 CC BY-NC 4.0 源码到当前仓库。
- 不在缺少 QNN SDK / QNN runtime / 真机出图证据时声明 NPU 已可用。
- 不把 README 写成参考仓库说明。

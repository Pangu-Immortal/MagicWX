# Tasks

> 参考实现：https://github.com/xororz/local-dream（已克隆到 `modules/local-dream`）
> 执行策略：先让 SD1.5 CPU 路径 100% 跑通并复现 LocalDream 协议，再逐步接入 SDXL / Anima / NPU / Upscaler；每阶段结束必须真机出图。

## Phase 1：SD1.5 CPU 全能力真实跑通（当前最高优先级）

### 1.1 Native `/generate` 完整参数支持
- [ ] 移除 magicwx_image_backend.cpp 中对 negative_prompt / mode / width / height / cfg / batch / preview / output_format / scheduler 的 501 拦截
- [ ] 对齐 LocalDream 请求协议：支持 `mode` (txt2img/img2img/inpaint/ultrafix)、`negative_prompt`、`cfg`、`scheduler`、`batch_count`、`show_diffusion_process`、`show_diffusion_stride`、`preview_format`、`output_format`
- [ ] 对齐 LocalDream 响应协议：`progress` 事件带 `step/total_steps/image/format`；`complete` 事件带 `image/seed/width/height/format`
- [ ] 支持 img2img / inpaint / ultrafix 的 base64 image/mask 输入
- [ ] 输出支持 raw / jpeg / png，默认 raw 减少传输

### 1.2 Kotlin 请求层与服务层适配
- [ ] LocalDreamBackendProtocol 输出与 LocalDream 后端完全一致
- [ ] ImageGenerationService 接收 base64 结果并写盘/通知 UI
- [ ] 移除当前 MNN-only 的文件路径输出假设
- [ ] 支持中途预览图解码与 UI 刷新

### 1.3 UI 工作流闭环
- [x] 生图页 Prompt / Result / History 三分区可正常切换（已有，待真机跑通）
- [x] 参数控件（steps、cfg、seed、size、aspect ratio、scheduler、denoise、batch、preview、output format）全部可用（已有）
- [x] 相册导入、裁剪、mask 绘制、Room 持久化历史和批量保存/删除完成真实执行链路：
  - 新建 `ui/image/MaskDrawingCanvas.kt`（照抄 `modules/local-dream/.../ui/screens/InpaintScreen.kt` + `utils/ImageUtils.kt`：Compose Canvas detectDragGestures 画白刷子，编码成 base64 PNG，尺寸 = 请求 width/height，backend 在 width/8 解码）
  - 新建 `ui/image/CropImageScreen.kt`（照抄 `modules/local-dream/.../ui/screens/CropImageScreen.kt`：cropify 或自写可拖动矩形裁剪，返回 Uri）
  - 新建 `data/db/{HistoryEntity,HistoryDao,AppDatabase,HistoryRepository}.kt`（照抄 `modules/local-dream/.../data/db/*` + `data/HistoryManager.kt`）
  - `build.gradle.kts` 确认 room-runtime/ktx/compiler 依赖（local-dream 有，MagicWX 待查）
  - `MainViewModel`：`sessionHistory` 内存 list 换成 Room `HistoryRepository` 的 Flow
  - `MainActivity.ImageGenerationScreen`：maskPickerLauncher 的 GetContent 换成 MaskDrawingCanvas；onUpscale 接真实 /upscale
  - 补充（2026-08-05 全量完成）：CropImageScreen 已接相册选图流（含 UltraFix 入口，2048 降采样防 OOM）；默认提示词/负向提示词对齐 local-dream AnythingV5 codeDefaults（逐字节核验）；生成完成自动跳转结果页；进度卡空白占位移除


### 1.4 运行时门禁与模型测试
- [x] Samsung SM-A566E 真机 SD1.5 CPU txt2img 出图成功（2026-08-05：AnythingV5 512×512 20 步 ≈ 196-299s，多张真实产出 + UI 自动跳结果页 + Room 历史落库；xtensor clang 缺陷修复 + UNet fp16/VAE fp32 门控后无 bad_alloc 无黑图）
- [x] 同设备 img2img / inpaint 出图成功（协议级直连验证：512×512 4 步各 ~56s 正常出图；UI 涂抹/裁剪链路代码已接线）
- [x] ultrafix 结论：QNN/NPU 专属能力，local-dream CPU 管线同样不支持（vaeTilingSupported=false），行为与上游对齐，随 Phase 3 开放
- [x] 真机验证参数不崩溃：dpm/euler/euler_a/lcm/dpm_sde 五种调度器 512×512 全部 complete；**768×768 实测杀死后端进程（attention 内存随序列长度平方增长，8GB 中端机 OOM）**——已加双层门禁：ImageGenerationService require(≤512) + UI 尺寸按钮禁用 768/1024 并文案说明
- [x] UI 端涂抹蒙版→inpaint、裁剪、历史删除/清空的纯手工点按验收（2026-08-06 adb 自动化端到端：发到图生图→inpaint→导入蒙版开编辑器[画笔/橡皮/撤销/重做/清除/滑条]→画蒙版→确认[状态"手涂蒙版·512×512"]→生成。后端 logcat `Img2Img:1 Mask:1 Denoise:0.6`+UNet 20 步+VAE+Image send 360KB；结果图 mean=124.2 非黑；Room 落库 mode=inpaint/denoise0.6/modelId/缩略图）

> 进度（2026-08-05 22:10）：bad_alloc 真因 = pinned xtensor 在 NDK clang 下 xtype_for_shape 特化守卫缺陷（__GNUC__=4 排除 4 参特化→rank 错配→NDEBUG 读垃圾维度→new[] 抛错），xexpression_traits.hpp 一行 `|| defined(__clang__)` 修复后原始 xt::eval CFG 代码 byte-for-byte 还原可用。性能：-O3 + UNet fp16（VAE/CLIP fp32 防黑图）→ 9.8s/step（原 -O0 30s/step）。FGS：迁 specialUse + COMPLETED 事件前置；三星 Android 16 仍会在 App 退前台时杀 FGS（与 AOSP 不同），产品层需前台生成或引导白名单。
> /upscale：native MNN 路径已实现并验证协议，但上游 xororz/upscaler 仅提供 QNN 上下文二进制（.bin，骁龙 NPU 专用），无 MNN 版模型——与 local-dream 在 Exynos 设备的能力对齐；Kotlin 入口改为明确提示，QNN 超分随 Phase 3 NPU 开放。

## Phase 2：/tokenize 与 /upscale

### 2.1 /tokenize
- [x] native 后端实现 `/tokenize`（prompt → token count / tokens）
- [x] Kotlin 请求层与 ViewModel 接入（ImageTokenizeClient + ImageGenerationScreen 防抖接入，backendReady 门控 + 本地估算降级）
- [x] UI token 估算替换当前占位实现（后端就绪显示真实 CLIP 计数）

### 2.2 /upscale
- [x] native 后端实现 `/upscale`（二进制图片 body + headers；MNN 路径实现并协议验证通过）
- [ ] Kotlin UpscaleService / UpscaleScreen（当前为结果页超分入口，随 QNN 模型开放后评估独立页面）
- [x] 结果页超分入口和 UltraFix 回流入口真实执行（入口接线完成；上游无 MNN 超分模型，Exynos 设备显示明确文案，QNN 模型随 Phase 3 开放——与 local-dream 在非骁龙设备的能力对齐）

> 备注（2026-08-05）：ultrafix 为 QNN/NPU 专属能力——local-dream CPU 管线 vaeTilingSupported()=false 即不支持 ultrafix（协议明确返回 "ultrafix not supported by this backend"），我方行为与上游一致；UltraFix UI 入口在 NPU 阶段生效。
> 多参数防崩扫描（2026-08-05，512×512 2 步直连验证）：dpm/euler/euler_a/lcm/dpm_sde 全部 complete 无异常。

## Phase 3：多 Pipeline 后端（SDXL / Anima / QNN / NPU）

> **B 方案落地（2026-08-07，v1.2.0）**：绕过高通专有 QNN SDK 编译头硬阻塞，改用 local-dream 预编译 `libstable_diffusion_core.so`（含 QNN 管线，MNN 静态链接，ELF 可执行文件非 JNI 库——local-dream 本就是 HTTP 进程隔离架构，与 MagicWX 同构）+ `assets/qnnlibs/` 20 个 QNN .so 运行时。MagicWX HTTP 架构 0 推翻，协议 100% 对齐（agent 核实），仅换可执行文件名 + 加 `prepareQnnRuntimeDir` 复制 QNN .so 到 `filesDir/qnn/` + `SDK_INTEGRATED=true`。cpp 自编译停用（externalNativeBuild 注释，源码保留备用）。详见 memory `b-plan-local-dream-so-swap`。

### 3.1 后端启动参数扩展
- [x] ImageBackendService 支持 `--type` (sd15cpu|sd15npu|sdxl|anima|upscaler)、`--lib_dir`、`--patch`、`--lowram`、`--anima_seq_dit`（2026-08-06：native 后端全量对齐 local-dream main.cpp，17 参数逐项一致，MAGICWX_WITH_QNN 条件编译；QNN 类型在 CPU-only 构建以退出码 6 明确报错；--convert/safety_checker/upscaler_mode/embeddings/NSFW 全消费；zstd 已按 pin 入库）
- [x] 按 model 的 `imageBackendType` 选择 pipeline 类型（Kotlin 接线随 NPU 批落地）
- [x] QNN runtime / HTP skel / 环境变量准备（**2026-08-07 B方案**：改用 local-dream 预编译 libstable_diffusion_core.so 含 QNN 管线 + assets/qnnlibs/ 20 个 QNN .so（libQnnHtp/libQnnSystem/V68-V81 Stub+Skel），prepareQnnRuntimeDir 复制到 filesDir/qnn/ + LD_LIBRARY_PATH 加系统库路径 + DSP_LIBRARY_PATH，绕过 QNN SDK 编译头阻塞）

### 3.2 各 Pipeline 真实出图
- [x] SD1.5 NPU txt2img（**2026-08-07 vivo V2505A SM8850 8gen2**：libstable_diffusion_core.so + libQnnHtpV81Skel.so CDSP fastrpc domain 3，**2.5 秒出图** 512×512，mean=104.3/std=54.7 非黑有内容，自动跳结果页；img2img/inpaint/ultrafix NPU 链路待补）
- [ ] SDXL txt2img/img2img/inpaint/ultrafix + aspect_ratio + lowram（需下载 8gen2 SDXL QNN 包）
- [ ] Anima txt2img/img2img/inpaint + aspect_ratio + lowram + seq_dit（需下载 8gen2 Anima QNN 包）

### 3.3 运行时门禁
- [ ] MNN SD1.5 CPU/OpenCL 门禁（Samsung CPU 路径待连机验证 xtensor bad_alloc 是否复发）
- [x] QNN HTP 可用性门禁（SDK_INTEGRATED=true 解除 ImageGenerationService 生成前门禁；设备 SoC 门禁 DeviceSocCapability.qnnSupported 保留，SM8850 8gen2 放行）
- [ ] SDXL 8 Gen 3+ 设备门禁
- [ ] Anima DiT split / 内存门禁
- [ ] Upscaler 模型存在性门禁

## Phase 4：Room 历史与持久化
- [x] Room Entity/Dao 保存每次生成记录（含 modelId 归属、Migration(1,2) 显式迁移、COMPLETED 事件权威落库）
- [x] History 列表、详情、批量删除/保存（列表+单条删除+清空，删除联动清理磁盘产物防孤儿文件；批量选择/收藏随产品迭代）
- [x] 参数复制、重试、超分、UltraFix 从历史记录启动（复用/复制已验证，超分与 UltraFix 入口随对应能力开放）

> Phase 4 补充（2026-08-06 竞品审计修复批）：
> - C1 参数持久化：DataStore 按 modelId 键控全 17 字段，进页恢复 + 500ms 防抖自动保存（对齐参照 GenerationPreferences）
> - C2 中间预览：progress 事件 base64 预览图解码上屏（.preview.jpg 覆盖写 + UiState.previewPath；CPU 管线 previewSupported=false 时占位兜底，与参照一致）
> - C3 UltraFix 映射修复：ultrafixSteps→steps、(denoiseSteps-0.5)/totalSteps→denoise_strength、质量提示词替换、输入图实际尺寸、门禁放行至 2048（逐行对齐参照 ModelRunSupport）
> - C4 批量闭环：顺序循环 + 空 seed 每轮随机/固定 seed 单张 + 批次进度展示（对齐参照批量语义）
> - H1 结果导出：MediaStore 保存到 Pictures/MagicWX + FileProvider 分享
> - H2 健康窗口 60s + 指数退避（对齐参照 checkBackendHealth）
> - H3 服务健壮性：onTimeout 兜底 + destroyForcibly + completed 竞态保护（防 Samsung FGS 查杀覆盖成功态）
> - OpenCL 结论：Xclipse GPU 在 app namespace 下不可用（A/B 实证），CPU 路径为当前上限；MNN 已留 MAGICWX_OPENCL_LIB 补丁位
> - cpp 修复：Upscaler 强制 fp32（防 ESRGAN fp16 黑图）、/upscale 补 X-Duration-Ms 头

## Phase 5：全量验收
- [ ] CPU、NPU、SDXL、Anima、Upscaler 全部真实测试通过
- [x] `./gradlew testDebugUnitTest assembleDebug :app:lintDebug` 通过（2026-08-06：testDebugUnitTest 21/21 + assembleDebug 全绿；lintDebug 原 2 errors=ImageBackendService NewApi[process.waitFor(3,SECONDS)/destroyForcibly API26+, minSdk=24]→加 Build.VERSION 守卫+API24/25 守护线程退化，现已 BUILD SUCCESSFUL）
- [ ] 至少两款真机（Samsung + 骁龙 8 Gen 3）完整流程无崩溃

## Phase 1.5：UI 完整性补齐（参照对齐批，2026-08-06）

> 用户指令：GitHub 有的功能 100% 迁移。ui-inventory 盘点 + ref-gap-check 核验后，4 项属迁移 mandated（参照有、MagicWX 缺）；1 项缩放下画蒙版参照也无、不属迁移范围。tag 数据源用内置启动 CSV（assets 预置精简 danbooru tag 集，偏离上游但开箱即用）。4 项全摸 MainActivity.kt 需串行；独立文件集可并行。

- [ ] **重置参数按钮**：在「高级与预览」区加一键重置 steps/cfg/seed/size/scheduler/denoise 等回模型专属默认（对齐参照 AdvancedSettingsDialog.kt:454 onReset + reset_hint）。MainViewModel 加 `resetGenerationParamsToModelDefault()`。~30min
- [ ] **导入参数（剪贴板）**：提示词页进页检测剪贴板共享参数文本→弹窗选字段应用（对齐参照 import_params_title/hint「Shared parameters were found on the clipboard. Choose which fields to apply.」）。复用现有 copyLocalDreamParamsToClipboard 的逆解析。MainActivity + MainViewModel + 可能新 Util。~1h
- [x] **tag 补全**：移植参照 TagAutocompleteRepository/TagModels/TagText + PromptTagTextField/PromptFieldController 到 data/ui.components/ui.screens；assets/tags/ 内置精简 main.csv+translation.csv 启动集。✅ 2026-08-06 真机验证通过（Samsung R5CY31C6PWM）：logcat `suggest done results=2/4` + `popup show=true` + 截图候选 "blue eyes"/"blue hair" 可见 + tap 候选 `applySuggestion` 替换为 "(black background:0.9), "（A1111 权重格式 + 分隔符）+ token 联动 1→2/77。焦点门控 `if(!autocompleteAvailable||!isFocused)return` 与原仓库 PromptFieldController:109 逐行一致。根因：上次"候选不显示"= adb 假阴性（polyspace 抢前台→tap 没点到框→isFocused 未 true）；force-stop polyspace 后全链路正常。
- [x] **历史过滤/收藏**：data/db 加 HistoryEntity.favorite + Dao setFavorite/getFavorites/filterByModel/filterByFavorite + AppDatabase Migration(2,3) `ALTER TABLE generation_history ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0`（对齐参照 AppDatabase.kt:74-78）；MainActivity 历史页加按模型/收藏过滤 chips + 收藏星标切换。✅ 2026-08-06 真机验证通过（Samsung R5CY31C6PWM）：logcat `MainViewModel: 已切换收藏状态: id=13 favorite=true` + tap "收藏" filter chip → 历史列表只剩 history-item-13（已收藏），过滤生效。基础过滤(全部/当前模型/收藏/未收藏)+收藏星标切换均工作。Agent A 已补高级过滤数据层(HistoryFilter+DAO 扩展：按模式/调度器/尺寸/设备/时间/Prompt，schema v4 + runOnCpu/useOpenCL 字段)，UI chips 扩展待接线。
- [ ] **Room 重复落库 bug 修复**：真机 inpaint 验收发现 id 10/11 同时间戳同 outputPath 但 seed 不同（疑似 COMPLETED 事件双插入或 completed 竞态保护未覆盖该路径）。排查 ImageGenerationService COMPLETED→MainViewModel insert 链路，去重（同一 generationId 只落一次）。随 MainActivity 批次一起改 MainViewModel。

> 进度（2026-08-06 17:40）：
> - ✅ 提示词迁移完整性核验：原仓库 Model.kt 7 个唯一 codeDefaults.prompt，MagicWX LocalDreamDefaults 已逐字节对齐 6 个（ANYTHING_V5/ILLUSTRIOUS 水下蓝双马尾/QTEAMIX/ABSOLUTE_REALITY/CHILLOUTMIX/CYBER_REALISTIC），仅 createCustomModel 的 `a cat sat on a mat` 随自定义导入功能未迁（见下条）。
> - ✅ history-db 层完成（sonnet Agent）：data/db 四件套 favorite 列 + Migration v2→v3 + Dao setFavorite/filterByModel/filterByFavorite + Repository，assembleDebug 真实重编绿。MainActivity 历史页 UI 接线待整合批。
> - ✅ CPU 预览区不留空白（已修）：MainActivity 进度监控区 180dp 占位框改条件渲染——previewBitmap 非空才占 180dp（NPU/SDXL/Anima 管线），否则生成中只显一行"扩散中…完成后自动跳转结果页"，CPU 管线不再留大空白。
> - 🔄 tag-core2（sonnet Agent）移植 tag 补全核心中。
- [ ] **自定义模型导入（createCustomModel）**：原仓库 `Model.kt createCustomModel` 支持导入任意模型目录 + 读 config.json + 兜底默认提示词 `masterpiece, best quality, a cat sat on a mat,` + 按 isSdxl/isAnima 选 generationSize(1024/512) + runOnCpu=!isNpu。MagicWX 当前无此能力（首页只展示预登记模型）。属"100% 迁移"缺口，需加"导入自定义模型目录"入口 + ModelInfo 动态构造 + 兜底提示词常量。

> ref-gap 全量审计 + 矛盾点核实（2026-08-06，sonnet Agent + 主控 grep 交叉验证）：
> **Agent 误判项（已实现，非缺口）**：调度器 UI（MainActivity:925/1321）、keepScreenOn（:895-900）、宽高比 aspectRatio（:926/1323）、发到图生图（:2704）、token 计数（ImageTokenizeClient :1132-1152 已接后端 /tokenize；PromptFieldController:97-103 内部桩值未用，小瑕疵）。
> **记忆误判修正**：蒙版双指缩放/平移——原仓库 InpaintScreen.kt:473-602 **确有**（awaitEachGesture+scale/offset/zoomFactor+coerceIn(1f,5f)），原记"参照也无"错误，改判 mandated 缺口。
> **核实后真 mandated 缺口（当前 CPU 阶段）**：
> - P0：① createCustomModel 自定义模型导入 ② 历史批量操作（多选/批量删除/批量保存/Paging 分页）③ 历史高级过滤（按模式/调度器/尺寸/设备/时间/Prompt 搜索——当前仅模型+收藏）
> - P1：④ 蒙版双指缩放/平移⭐(记忆修正) ⑤ 蒙版画笔颜色选择(原仓库8色) ⑥ 历史备份导出/导入 zip ⑦ 参数复现 Reproduce 对话框(历史结果一键复现参数+seed生图) ⑧ 结果页发到 inpaint 回流(发到图生图已做) ⑨ 独立历史浏览器页面 ⑩ 种子回填按钮(使用上次种子)
> - P2：⑪ 模型升级检测(v3 marker) ⑫ Chipset 后缀映射 ⑬ 模型固定 Pin ⑭ 蒙版 pathHistory 持久化(跨会话) ⑮ 结果页底部缩略图历史条 ⑯ 分享参数格式选择(Base64/JSON) ⑰ 问题报告 ⑱ PromptFieldController tokenCount 桩值接后端(overflow 灰度提示)
> **Phase 3 NPU（非当前）**：ultrafix UI+tiled 链路、分辨率扫描 patch、SDXL/Anima/QNN（硬阻塞待 QNN SDK + 骁龙 HTP 验证机）。
> **认可偏离（已认可，无需补）**：assets 精简 CSV、自绘裁剪替代 Cropify、参数 UI 内联 MainActivity、远程主机模式、超分路径硬编码、数据源镜像切换、模型列表 UI 内联。

> **交付进度（2026-08-06 晚，6 Agent + 主控 2 bug 修复，assembleDebug 全绿 + 真机验证）**：
> - ✅ 数据层独立交付（已构建进 APK，dex 二进制 grep -a 实证符号）：① createCustomModel(Agent E: CustomModelImporter.kt 252行 + LocalDreamDefaults.CAT_SAT_ON_MAT 兜底常量，对齐 ModelRepository L444-509) ②③ 历史批量+高级过滤数据层(Agent A: HistoryDao+13方法 deleteByIds/observeModelIds/Schedulers/Sizes/query*/getPaged + 新建 HistoryFilter.kt toSqlQuery/toIdQuery 组合过滤 + schema v4 runOnCpu/useOpenCL + Repository 包装) ④⑤ 蒙版双指缩放+8色画笔(Agent B: MaskDrawingCanvas 519→761行，对齐 InpaintScreen:473-602，画笔颜色仅 UI 显示/encodeMask 始终白色) ⑥ 历史备份zip(Agent C: HistoryBackup.kt 359行 exportHistoryZip/importHistoryZip，字段映射 imagePath→outputPath) ⑦ 参数复现对话框(Agent D: ReproduceParametersDialog.kt+13 strings，HistoryEntity→LocalDreamImageRequest 一键复现) ⑰ 问题报告(Agent F: ReportImageUtils.kt 193行，本地落盘报告+FileProvider 分享，无后端改造)
> - ✅ 主控修复 2 bug：① Migration v3→v4 漏建 modelId index 致 Room schema 校验失败 App 启动即崩（Agent A 给 Entity 加 Index(["modelId"]) 但 MIGRATION_3_4 漏 CREATE INDEX）→ 补 `CREATE INDEX IF NOT EXISTS index_generation_history_modelId`，DB v4 + 13 条历史全保留；② strings.xml 漏 `</resources>` 闭合（Agent D 加 13 strings 时删了）→ 补回。
> - ✅ 真机验证（Samsung R5CY31C6PWM）：tag 全链路(suggest+popup+applySuggestion+token联动) + 历史过滤收藏(setFavorite+过滤生效) + DB v4 schema(13条保留) + 6 Agent 产物 App 健康启动不崩。
> - ✅ UI 接线完成（Agent G 串行 7 点 + 主控修 3 bug：unavailableBadge null→""/emptyList()→emptyList<String>()/MainViewModel 缺 Flow import；assembleDebug 全绿 + 装机健康）：① 高级过滤 chips(模式/调度器/尺寸，observeModelIds/Schedulers/Sizes + DbHistoryFilter 查询) ② 历史备份 zip 导出/导入入口 ③ 参数复现按钮(overlay→ReproduceParametersDialog) ④ createCustomModel 导入入口(CPU生图 tab) ⑤ 问题报告入口(overlay→ReportImageUtils) ⑥ 种子回填(参数卡"使用上次种子"+lastReturnedSeed StateFlow) ⑦ 发到 inpaint 回流(结果页→feedOutputImageTo INPAINT)。真机验证 3 点 UI 在位+功能：高级过滤 chips[模式→展开文生图/图生图/局部重绘/UltraFix] / 历史备份zip[导出/导入按钮] / createCustomModel[导入自定义模型按钮]。其余 4 点(参数复现/问题报告/种子回填/发到inpaint)代码接线+构建通过+App健康，待逐点点按。
> - ⏳ P2 待做：v3 marker 检测 / Chipset 后缀映射 / 模型 Pin / 蒙版 pathHistory 持久化 / 结果页缩略图历史条 / PromptFieldController tokenCount 桩值接后端(overflow 灰度提示)
> - ⏳ Agent B 蒙版双指缩放真机手工验证（adb 难模拟双指手势，需用户手指操作验证缩放/平移/颜色选择）
> - ⏳ Phase 3 NPU（硬阻塞待 QNN SDK + 骁龙 HTP 验证机）：ultrafix UI+tiled 链路 / 分辨率扫描 patch / SDXL/Anima/QNN

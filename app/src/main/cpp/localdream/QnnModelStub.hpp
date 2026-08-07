// QnnModelStub - 真实 QNN SDK 不可用时 QnnModel 的最小占位实现。
//
// 功能与函数简介：
// - 定义与真实 QNN SDK 等价的 StatusCode 枚举（SUCCESS=0 / FAILURE=1）。
// - 提供 QnnModel 占位类，其 executeUpscalerGraphs 为空操作并恒返回 FAILURE。
//
// 背景：MagicWX 的 SD1.5 CPU 路径只走 MNN（见 Upscaler::upscaleWithMnn），
// 不依赖任何 QNN runtime；但 Upscaler.hpp 的 upscaleWithQnn 仍引用 QnnModel
// 与 StatusCode。为了让该函数编译通过、又绝不真正可用，本桩提供一个永远
// 返回 FAILURE 的 executeUpscalerGraphs：一旦误调 upscaleWithQnn，会立刻
// 抛出 "Upscaler execution failed for tile"。仅 upscaleWithMnn 被实际使用。
// Phase 3 接入真实 QNN SDK 后删除本文件，改回 #include "QnnModel.hpp" 即可。
#ifndef QNNMODELSTUB_HPP
#define QNNMODELSTUB_HPP

// 与真实 QNN SDK 的 qnn::tools::sample_app::StatusCode 等价的最小状态枚举。
enum class StatusCode {
  SUCCESS = 0,  // 成功
  FAILURE = 1,  // 失败（桩默认返回值，确保 QNN 路径不可用）
};

// QnnModel 占位类：仅暴露 Upscaler.hpp 编译所需的最小接口，不持有任何状态。
class QnnModel {
 public:
  // 图执行空操作，恒返回 FAILURE；调用方据此抛错，保证 QNN 路径不可用。
  StatusCode executeUpscalerGraphs(float * /*input_image*/, float * /*output_image*/) {
    return StatusCode::FAILURE;
  }
};

#endif  // QNNMODELSTUB_HPP

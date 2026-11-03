# 电路与 FPGA 流程测试

测试重点是：用实际组件连成的电路，软件仿真结果是否与导出的逻辑、综合后的逻辑一致，端口是否落到正确的实验箱引脚，以及是否真的能生成位流。

## 运行完整测试

在 Windows 仓库根目录执行，需要 JDK 11、开发工具目录及 A7 原生运行时：

```powershell
.\toolchain\test-windows-fpga.ps1
```

可指定要验证的精简运行时。Icarus 仅用于开发验证，不需要重新放回安装包：

```powershell
.\toolchain\test-windows-fpga.ps1 `
  -RuntimeDirectory 'dist/LoongSim-1.1.0-flow-pruned/LoongSim/app/fpga/runtime/windows-x64' `
  -SimulationToolsDirectory 'fpga/runtime/windows-x64/oss-cad-suite' `
  -ResultsDirectory 'local/fpga-regression-new'
```

结果目录必须是新目录，防止覆盖旧证据。脚本归档 JUnit XML、HTML 报告、工具日志、测试电路、Verilog、XDC 和位流，并生成 `summary.json`。完整模式遇到测试跳过也会失败；脚本不会连接 JTAG 或向实验箱下载。

只运行不需要原生综合和实现工具的测试：

```powershell
.\toolchain\test-windows-fpga.ps1 -UnitOnly
```

这个模式会明确跳过原生流程测试，不能据此声称 FPGA 全流程通过。

## 检查的层次

| 层次 | 检查内容 |
|---|---|
| 组件逻辑 | 独立数学及布尔参考值，与生产组件的输出比较，包含多种位宽和随机向量 |
| 实际电路 | 真实引脚、连线、组件与仿真传播器；保存 `.circ` 后重新打开并导出 |
| 导出一致性 | 用实际电路仿真结果逐向量检查导出的 Verilog |
| 综合一致性 | 再检查 Yosys 综合后的 Xilinx 原语网表，避免只验证源 HDL |
| 端口映射 | 与独立的 `verified-pins.csv` 核对每一位的物理引脚；检查重复、方向、缺失、范围、资源冲突与重映射后的旧位流失效 |
| 原生实现 | 调用实际 Yosys、nextpnr-xilinx、帧和位流工具，验证完整实现产物 |
| 板卡适配 | LED 有效电平、数码管段选与十六进制显示、矩阵键盘接触和去抖模型 |
| 不支持的配置 | RAM、SimpleCounter，以及当前 A7 后端不能实现的锁存模式，在导出前提示组件和位置 |

测试保存在 `logisim/src/test/java/com/cburch/logisim/`。复合电路样例由 `WorkflowCircuit` 构造，每次完整测试都保存为可打开的 `.circ` 文件。随机输入使用固定种子，便于复现失败。

## 范围与限制

- 当前验证目标为 Windows x64、龙芯 A7 实验箱 XC7A200T / FBG676 / -1；不代表其他板卡和工具版本已验证。
- 软件能放置的组件不一定都有 FPGA 实现。RAM 和 SimpleCounter 仍未实现当前流程所需的 Verilog 导出；新增提示并不等于实现了这些功能。
- 寄存器高、低电平锁存模式受当前布局布线工具限制；上升沿、下降沿模式单独测试。
- ALU 控制码 `011` 和 S-R 的 `S=R=1` 是未定义或非法输入，不作为正常电路等价性向量；ALU 的非法控制行为另有组件测试。
- 仿真在时钟边沿之前建立输入，不把零建立时间的竞态当作确定逻辑。机械按键直接作时钟时的抖动仍需电路处理。
- 网表仿真不是布局布线后的时序仿真。位流生成成功也不能代替板上电平、接线、时钟质量和实际运行的验证。
- 不穷举所有拓扑、位宽和属性组合，不能保证任意电路没有问题。
- 本轮侧重电路与 FPGA 后台流程，未覆盖所有窗口的人工交互和安装升级路径。

最近的实测结果、发现的问题及修复见 [2026-11-03 测试记录](2026-11-03.md)。

[返回文档中心](../README.md)

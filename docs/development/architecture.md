# LoongSim 流程与实现状态

以下为从项目首页迁入的详细技术资料；目录结构总览见[项目结构](project-structure.md)。

## 项目组成

- `logisim/`：Java/Swing 电路编辑器、仿真器、HDL 生成器和 FPGA 界面。
- `fpga/yosys-runtime/`：已编译的 LoongArch Yosys、Berkeley ABC 和 Yosys 技术库。
- `fpga/runtime/windows-x64/`：由脚本安装的 Windows 私有工具与数据库（不提交二进制到 Git）。
- `fpga/boards/`：板卡约束模板和资源说明。
- Windows 的 nextpnr、Project X-Ray、Python 与 openFPGALoader 均纳入上述私有工具目录。
- `toolchain/`：工具安装、下载器构建、打包脚本和版本锁定；详细说明集中在 `docs/`。

## 当前状态

电路编辑、仿真和 Verilog 导出可以使用。Windows FPGA 工作台已贯通至真实实验箱下载：
生成 Verilog 工程后进入板卡端口映射窗口；用户确认兼容的板卡资源后，LoongSim 才生成当前电路专属 `constraints.xdc`。
生成时会先选择一个工程目录；Verilog、板卡模板、XDC、映射清单和后续 Yosys 输出都会保存在这个目录中。
端口映射由方向、位宽和资源占用规则约束，不能把输入端口映射到 LED 等输出资源。
Windows 工作台依次提供“端口映射”“生成位流”“下载到 FPGA”三个操作；生成位流时自动调用
内置综合、布局布线和位流生成工具，无需分别操作这些阶段。
2026-10-29 已通过“开关 → 非门 → LED”实机验证：从 LoongSim 界面生成位流、自动识别
Xilinx USB 下载器、下载到 XC7A200T，并确认板上 SW0 控制 LED1 亮灭。
LoongArch 保留原有 Yosys 0.8 + Berkeley ABC 综合；其后续实现工具尚未接入。

Windows 的构建、完整应用打包、工具版本及验证说明见 [Windows FPGA 集成](windows-fpga.md)。

当前 FPGA 导出会让 `sources.list`、`top-module.txt` 和 `constraints.xdc` 严格对应同一个实现顶层。
所有工程都有独立的板卡接口顶层，自动适配单色 LED 的低电平点亮，使灯光与仿真一致。
使用 Logisim `Clock` 时，LoongSim 还会自动生成时钟分频器、
时钟总线和一个顶层封装，学生只需要把封装的 `FPGA_GlobalClock` 映射到板卡专用 `clk` 资源。

## 从源码运行桌面应用

```bash
cd logisim
./gradlew run
```

## 目标流程

```text
LoongSim 电路图
  → 生成 Verilog
  → 根据顶层端口生成本电路 XDC
  → Yosys 综合
  → Xilinx 布局布线
  → 生成位流
  → JTAG 烧录
```

说明：XDC 在流程上应在实现前准备好，时钟和 I/O 约束通常会被后续布局布线工具使用。
LoongSim 先生成 Verilog，是为了知道当前电路实际有哪些顶层端口，再从板卡完整模板筛选出本电路的 XDC。
当前内置的 Yosys 阶段只负责读取 Verilog 并生成综合网表；XDC 会在后续 Xilinx 实现阶段使用。

[返回开发文档](README.md)

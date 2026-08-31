# LoongSim

LoongSim 是一套统一的数字电路设计、仿真与 FPGA 实现环境。面向用户时，电路设计、
综合、布局布线、位流生成和烧录都统一呈现为 LoongSim 功能；各开源项目只是内部引擎。

## 项目组成

- `logisim/`：Java/Swing 电路编辑器、仿真器、HDL 生成器和 FPGA 界面。
- `fpga/yosys-runtime/`：已编译的 LoongArch Yosys、Berkeley ABC 和 Yosys 技术库。
- `fpga/boards/`：板卡约束模板和资源说明。
- 后续 `nextpnr`、Project X-Ray、openFPGALoader 也以各自的 `*-runtime/` 编译产物形式加入。
- `toolchain/`：LoongSim 自己的工具链编排、板卡配置和说明文档。

## 当前状态

电路编辑、仿真和 Verilog 导出可以使用。FPGA 工作台已经接通三个阶段：
生成 Verilog 工程后进入板卡端口映射窗口；用户确认兼容的板卡资源后，LoongSim 才生成当前电路专属 `constraints.xdc`。
生成时会先选择一个工程目录；Verilog、板卡模板、XDC、映射清单和后续 Yosys 输出都会保存在这个目录中。
随后调用项目内置的 LoongArch Yosys 0.8 + Berkeley ABC。端口映射由方向、位宽和资源占用规则约束，不能把输入端口映射到 LED 等输出资源。
nextpnr、芯片数据库、位流和烧录阶段仍在后续接入。

当前 FPGA 导出会让 `sources.list`、`top-module.txt` 和 `constraints.xdc` 严格对应同一个实现顶层。
没有派生时钟时，电路模块本身就是实现顶层；使用 Logisim `Clock` 时，LoongSim 会自动生成时钟分频器、
时钟总线和一个顶层封装，学生只需要把封装的 `FPGA_GlobalClock` 映射到板卡专用 `clk` 资源。

## 运行桌面应用

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

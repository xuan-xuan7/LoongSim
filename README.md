# LoongSim

LoongSim 是一套统一的数字电路设计、仿真与 FPGA 实现环境。面向用户时，电路设计、
综合、布局布线、位流生成和烧录都统一呈现为 LoongSim 功能；各开源项目只是内部引擎。

## 分支关系

- `master`：完整版本，Logisim 编辑、仿真部分与 `logisim-only` 同步，并保留 FPGA 按钮、菜单、工作台、板卡资源和工具链。
- `logisim-only`：相同的 Logisim 编辑、仿真核心，不包含 LoongSim 自定义 FPGA 界面、流程、板卡资源和工具链。
- 两个分支共用的组件、界面调整、中文兼容修复和测试应保持一致；专用于 `logisim-only` 的安装包发布脚本仍在该分支维护。

## 版本更新记录

每次功能、修复、兼容性或安装包更新都记录用户可感知的变化。未发布修改先记入“未发布”，
正式发布时补充版本、日期、Release 链接和升级注意事项。源码同步不代表安装包已更新，
同版本重新打包也应记录日期和对应源码提交。

### 未发布（2026-10-19）

- 将 `logisim-only` 的 Logisim 改动同步到 `master`，同步来源为 `5b47d457`；应用核心版本同步为 1.0.1。
- 同步中文文件名和中文目录支持：“保存”“另存为”不依赖界面语言，支持空格、短横线和自动补全 `.circ` 后缀，继续检查系统非法字符与保留设备名。
- 同步中文电路名、标签、库路径、组合逻辑分析和真值表支持；相关文本读写使用 UTF-8，真值表与测试向量支持 UTF-8 BOM。
- 同步教学工具箱分组、属性面板简化、组件外观和默认设置、五端口简化计数器及新版应用图标；保留旧电路文件的兼容读取。
- 保留 `master` 的“FPGA 实现”按钮、FPGA 菜单、工作台、端口映射、时钟封装、Verilog 导出、板卡约束和 LoongArch Yosys 集成。
- 保留原 FPGA 测试，并增加同步后的 Logisim 核心到 FPGA Verilog 导出、端口映射和时钟封装的兼容性测试。
- 修复验证中发现的 FPGA 端口识别问题：跳过生成的 Verilog 注释，避免将注释中的 `input`/`output` 误识别为端口声明，造成重复端口和板卡资源重复占用错误。
- 验证：macOS / JDK 11 下 `clean test shadowJar` 通过，共 38 项测试；默认 ASCII 编码下额外运行的 19 项中文保存和 FPGA 测试通过。未在本机运行 LoongArch 原生 Yosys，也未进行硬件综合与烧录验证。
- 本次仅同步源码，不创建或替换 Release 安装包；`logisim-only` 分支不变。

### 1.0.1（2026-10-16，Logisim-only 发布）

[下载 Release](https://github.com/xuan-xuan7/LoongSim/releases/tag/v1.0.1-logisim-only)

- 修复中文文件名和目录的保存、另存为问题，不依赖英文或中文界面设置。
- 完善中文分析、TXT/CSV 导入导出、中文库路径与 UTF-8 编码处理。
- 修复大写 `.CIRC` 后缀识别及启动时不存在的 classpath 项处理。
- 增加英文、简体中文、土耳其语下的另存为回归测试，发布前 Logisim-only 共 32 项自动化测试。
- 更新内置 Java 的 Windows EXE，应用和安装包版本统一为 1.0.1；安装包源码为 `77003f81`。

升级时先保存电路并关闭旧版，卸载 1.0.0 后安装 1.0.1，保留自己的 `.circ` 文件。
该 Release 仅含 Logisim 功能，不包含 `master` 的 FPGA 集成；未可信签名，尚未完成真实 Windows 安装和启动验证。

### 1.0.0（首次发布于 2026-10-09，Logisim-only 发布）

[历史 Release](https://github.com/xuan-xuan7/LoongSim/releases/tag/v1.0.0-logisim-only)

- 提供 LoongSim 电路编辑、仿真、可配置位宽 ALU，以及 Windows 运行包和内置 Java 安装包。
- `logisim-only` 从完整版本移除 LoongSim 自定义 FPGA 界面、板卡资源和工具链。
- 2026-10-12 同版本更新（源码 `22f9d86c`）：更新图标、教学工具箱、属性与组件外观，新增 Max/CLK/RST 输入、Value/Zero 输出的简化计数器。
- 旧版 1.0.0 安装包不包含中文文件名修复，切换显示语言不能解决，应升级到 1.0.1。

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

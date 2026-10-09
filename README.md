# LoongSim Logisim-only

这个分支只保留 LoongSim 的 Logisim 电路编辑与仿真工程，适合先使用基础数字逻辑功能。

LoongSim 的 `logisim/` 工程基于 [Logisim-evolution](https://github.com/logisim-evolution/logisim-evolution)
的代码基线（当前内置版本号为 `3.3.6`）。下面列出 LoongSim 相对于上游项目的主要改动；
“logisim-only”表示当前分支进一步去除了 LoongSim 自定义 FPGA 集成部分。

## 与 Logisim-evolution 的改动

### 1. LoongSim 品牌和应用身份

- 应用名称、Gradle 工程名称和发布版本改为 `LoongSim` / `1.0.0`。
- 主窗口标题、关于窗口、版本命令和部分中英文资源改为显示 LoongSim 品牌。
- 增加龙芯教学平台品牌栏和 `loong-edu.png`、`LoongSim.icns` 等品牌资源。
- 增加面向中文教学场景的“设计”和“仿真”工作区标签、工具栏区域和界面配色调整。

主要位置：`logisim/src/main/java/com/cburch/logisim/Main.java`、
`logisim/src/main/java/com/cburch/logisim/gui/main/Frame.java`、
`logisim/src/main/java/com/cburch/logisim/gui/main/LoongsonBrandBar.java`。

### 2. 新增可配置位宽 ALU 组件

LoongSim 在算术库中增加了 `ALU` 组件，并注册到算术工具箱。该组件提供：

- 可配置的数据位宽，默认 8 位；
- A、B 两个数据输入，3 位控制输入，结果和进位输出；
- 与、或、加法、反相后与/或、减法以及有符号小于比较；
- 未定义值和错误值在仿真中的传播；
- 对应的 Verilog HDL 生成器。

主要位置：`logisim/src/main/java/com/cburch/logisim/std/arith/ALU.java`、
`ALUHDLGeneratorFactory.java`、`Arithmetic.java`，以及各语言的 `std.properties`。

### 3. 教学环境相关的 I/O 调整

I/O 工具箱保留了按钮、拨码开关、LED、RGB LED、数码管、点阵和端口等数字逻辑实验常用组件，
并对不适用于当前龙芯教学板卡的外设入口进行调整。当前 `logisim-only` 分支不依赖任何板卡文件，
这些组件仍可用于普通电路编辑和仿真。

### 4. Java 11 和跨架构运行适配

- Gradle 编译目标统一为 Java 11 字节码。
- 增加 Java 11 / Loongnix 运行说明和 macOS 兼容层。
- 使用 Shadow JAR 生成包含 Java 依赖的可分发包。
- JAR 本身不包含 CPU 原生代码，因此在 x86、ARM 和 LoongArch 上可以使用对应架构的 Java 运行时启动。

主要位置：`logisim/build.gradle.kts`、`logisim/gradle.properties`、
`logisim/src/main/java/com/cburch/logisim/gui/start/MacOsAdapter.java`、
`logisim/JAVA11-LOONGNIX.md`。

### 5. Logisim-only 分支移除的 LoongSim FPGA 集成

这些内容属于 LoongSim 自己的 FPGA 集成，不是当前 Logisim-only 版本的功能：

- 移除顶栏“FPGA 实现”按钮；
- 移除菜单栏 FPGA 入口；
- 移除 `FpgaWorkbench`、`FpgaMappingDialog` 和 `com.cburch.logisim.fpga.loongsim` 自定义流程类；
- 移除仓库顶层 `fpga/` 和 `toolchain/`，包括 Yosys 运行库、板卡约束、板卡图片和工具链脚本；
- 移除 LoongSim 自定义 FPGA 流程测试和板卡资源。

上游 Logisim-evolution 原有的 HDL、VHDL 和 FPGA 相关源码仍可能存在于 `logisim/` 工程中，
这是为了保留原工程的源码结构和构建依赖；但本分支不提供 LoongSim FPGA 工作台、工具链、板卡数据库、
位流生成或烧录能力。

## 未改变的上游能力

以下能力主要来自 Logisim-evolution，本分支继续保留：

- `.circ` 电路文件的创建、打开、保存和兼容处理；
- 数字逻辑组件、层次化电路、时钟和事件驱动仿真；
- 波形观察、真值表和组合逻辑分析；
- 原有 HDL/VHDL 编辑与导出能力；
- 原有多语言资源、帮助文档和 GPLv3 许可。

## 运行桌面应用

```bash
cd logisim
./gradlew run
```

## 构建

```bash
cd logisim
./gradlew shadowJar
```

构建结果位于 `logisim/build/libs/`。运行时需要 Java 11 或更高版本。

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

Windows 本地预览：构建 `shadowJar` 后，双击仓库根目录的 `run-loongsim.cmd`。
启动脚本会优先使用 `local/tools/temurin11` 中的便携 Java，启动最新构建的 JAR；
也支持 `JAVA_HOME` 或 PATH 中的 Java 11 及以上版本，无需生成安装包。

## 构建

```bash
cd logisim
./gradlew shadowJar
```

构建结果位于 `logisim/build/libs/`。运行时需要 Java 11 或更高版本。

## 工具箱显示

原“连线”和“输入/输出”在工具箱中按用途显示为“输入”“输出”“其它”：

- 输入：输入引脚、时钟、常量、电源、接地、按钮、拨码开关。
- 输出：输出引脚、LED、RGB LED、7 段数码管、16 进制数码显示。
- 其它：分线器、信号观察器（原探针）、同名连接点（原隧道）、位扩展器。

输入引脚、输出引脚是两个独立的工具预设，选择后即可放置对应方向的引脚。
分类只影响工具箱显示，电路文件继续使用原有 Wiring、I/O 库和 Pin 组件格式。
分组规则位于 `logisim/src/main/java/com/cburch/logisim/gui/generic/ToolboxGroup.java`。

工具箱继续隐藏上/下拉电阻、上电复位、不连接、晶体管和传输门。
组件源码和注册信息仍然保留，已有 `.circ` 文件中的这些组件可以继续加载。
隐藏名单位于 `logisim/src/main/java/com/cburch/logisim/std/wiring/Wiring.java` 的 `HIDDEN_TOOLS`。
LED 矩阵和端口 I/O 也暂时隐藏，源码和注册信息保留。
这两项的隐藏名单位于 `logisim/src/main/java/com/cburch/logisim/std/io/Io.java` 的 `HIDDEN_TOOLS`。

TTL、TCL、BFH mega functions 和片上系统也暂时隐藏，源码和库注册信息继续保留。
隐藏库名单位于 `logisim/src/main/java/com/cburch/logisim/std/Builtin.java` 的 `HIDDEN_LIBRARIES`。
“存储器”分类的中文显示名称改为“时序组件”，原有组件仍然保留。

## 属性面板

属性表隐藏 HDL 支持提示，使用“属性名称”“属性值”两列表头。
普通标签和电路共享标签统一使用 SansSerif、粗体、16 号，隐藏字体选择项；
打开旧电路时，其标签字体也统一为这个默认值。
输入、输出引脚均固定为箭头外观，属性面板隐藏“输出方向”“三态输出”“上拉/下拉电阻”和外观选择。
两种引脚保留各自的输入、输出功能，默认朝向分别为东、西；输出引脚的尖头也朝向连接端。
两种引脚都支持标签位置选择，默认放在连接端的相反侧；修改后会随电路保存。
所有组件的空标签均显示为空白，不再显示 HDL Required 提示。
常量支持标签，属性表和画布按数据位宽显示二进制，数值编辑和快捷键也使用二进制；
旧电路文件的数值格式继续兼容，新增标签可随电路保存，标签位置默认在西侧。
按钮、拨码开关标签固定为蓝色、始终可见，隐藏标签颜色和标签可见选项，按钮本体颜色仍可设置。
拨码开关的标签位置也默认在西侧。
LED、RGB LED 固定高电位点亮，标签固定蓝色且可见，隐藏这三项设置。
7 段数码管固定高电位点亮、保留默认小数点，标签固定可见，隐藏这些设置。
16 进制数码显示固定不带小数点、标签可见，隐藏这两项设置。
分线器固定左手型外观；属性表按分支显示位范围，点击分支设置开始位、结束位。
范围弹窗只保留分支名称、开始位、结束位及从 0 编号的说明。
范围包含起止位，编号从 0 开始；所选位会移到此分支，其原有范围之外的位取消分配。
范围修改可一次撤销；旧文件中的非连续分配继续保留并如实显示，文件仍使用原有逐位格式。
信号观察器固定经典 Logisim 外观，隐藏外观选项。
同名连接点采用无尖头的圆角标签框，端口与同名连接功能保留。
位扩展器采用宽 80、高 40 的外框，类型与 extend 在中间上下排列，输入、输出位宽显示在框内两侧。
新放置的位扩展器使用宽版；旧文件保留原端口间距，避免已连接的导线断开。
位扩展器的类型与 extend 收紧为两行，位宽数字字号略小，向框内左右两侧对齐。
新放置的宽版 MUX 增加 20 像素宽度，所有输入显示各自编号，MUX 文字靠输出一侧，去掉内部灰色选择提示圆点。
旧电路中的 MUX 保留端口位置；窄版保留原有尺寸。
画布上的可连线端点统一使用直径 7 像素的深绿色实心圆点，带细白边；异常信号仍标红，导线保留信号状态颜色。
端点坐标和连线判定不变，时钟端口也显示圆点并保留时钟符号，打印仍使用原有黑色端点。
多路分配器、译码器、优先级编码器、位选择器采用与 MUX 一致的留白、字号和深绿色端点。
新放置的组件增加 20 像素宽度；分配器和译码器的输出、优先级编码器的输入显示完整编号，功能文字与编号分开。
框内缩写分别为 DMX、DEC、PRI、SEL；位选择器仍表示一个总线输入，不添加多路输入编号。旧文件保留原端口布局。
所有逻辑门的输出模式固定为 0/1，隐藏输出值选择；逐个输入的取反选项隐藏并固定为不取反。
异或门和同或门固定使用奇数个 1 的行为，隐藏多输入行为选择；同或门仍对异或结果取反。
扩展类型只提供全 0、全 1、符号扩展；旧文件中的输入控制扩展仍可读取。
门组件工具箱隐藏缓冲器、奇校验门、偶校验门、受控缓冲器、受控反相器及 PLA，源码和旧文件组件保留。
电路和子电路隐藏共享标签、共享标签朝向、外观、VHDL 路径；子电路标签固定可见，隐藏可见性设置。
新电路默认采用 Logisim-HolyCross 外观；已有文件保存的外观继续保留。
电路属性隐藏“使用固定框大小”；此设置只用于 Logisim-Evolution 子电路符号的固定宽度，HolyCross 外观不使用它，旧文件中保存的值继续保留。
“时序组件”新增参考 CircuitVerse 文档的简化计数器（文件名 SimpleCounter），原 Counter 继续隐藏并保留。
简化计数器有最大值 Max、时钟 CLK、复位 RST 三个输入，以及当前数值 Value、零标志 Zero 两个输出；上升沿加 1，超过最大值后循环到 0，复位为高电平时立即清零。
最大值未连接时使用位宽能表示的最大值；Zero 在数值为 0 时为 1；组件内部按十进制显示当前数值，输出仍为二进制总线。
默认数据位宽为 8，支持标签和标签位置；改变位宽时宽总线和符号尺寸同步调整，64 位计数按无符号数处理。
多路选择器、多路分配器的选择位数和译码器的输入位数提供 1～7 位（最多 128 路）。
这三个组件隐藏禁用时输出、启用使能，并固定不使用使能端、禁用时输出 0；多路分配器和译码器也固定不使用三态。
译码器隐藏选择端朝向，位数名称改为“输入位数”；新放置的译码器输入在左侧中央，输出在右侧按编号排列，并可随组件朝向旋转。
旧译码器保留已保存的输入、输出位置；新布局随电路保存和恢复。
优先级编码器、乘法器、除法器、位计数器和位查找器从工具箱隐藏，组件注册和旧电路读取继续保留。
新放置的 ALU 使用左侧凹口、右侧斜边的轮廓，A/B 在左、CTR 在上、Ans 在右、Carry 在下；运算功能不变。
新放置的加法器、减法器改为宽 80、高 60 的外框，中央显示较大的 ＋/−，输入 A/B 和进位 Cin/Cout、借位 Bin/Bout 分开排布。
旧 ALU、加法器、减法器保留原有端点位置；新布局随电路保存。
求负器、计数器、移位寄存器和随机数发生器也暂时从工具箱隐藏，源码和旧文件中的组件继续保留。
加法器和减法器的右侧结果端分别标为 Sum（和）与 Diff（差），与中间运算符保持间距。
加法器和减法器的 ＋/− 位于外框正中心，Sum/Diff 使用略小的字号，在右侧端点内侧显示。
D、T、J-K、S-R 触发器及寄存器固定为 Logisim-Evolution 外观，RAM 和 ROM 固定为经典 Logisim 外观，隐藏这些组件的外观选择。
寄存器的“在寄存器选项卡中显示”用于左侧下方“状态”页：设为“是”并填写非空标签后，显示所属电路、寄存器名称和十六进制数值，随仿真刷新，不影响运算。
该状态页显示选项现固定为开启并隐藏；寄存器填写非空标签后自动显示。
RAM 隐藏仿真重置行为、使用清除引脚和标签可见，固定为重置时清空内容、带高电位有效的清除引脚、标签可见。
RAM 固定为字节使能模式但不启用额外字节使能端、读写分离、异步读取；读取行为固定为先写后读（异步读取时不使用），隐藏这些选项。
触发方式继续可选，新放置的 RAM 默认高电平写入；旧文件中未填写触发方式的 RAM 继续使用上升沿，显式填写的触发方式保留。
ROM 固定行大小为 1、不允许未对齐，隐藏这两项选择；内容编辑和地址、数据位宽继续可用。

## 应用图标

应用图标的原图为 `logisim/src/main/resources/resources/logisim/img/LoongSim.png`，
采用红色芯片和白色龙芯标识。窗口使用同目录下多种尺寸的 `logisim-icon-*.png`；
Windows 打包脚本 `package-windows.ps1` 使用多尺寸 `LoongSim.ico`，
将图标嵌入程序和安装包，安装时创建的快捷方式也使用该图标。

更换原图后，可以用 Python 3 和 Pillow 重新生成 PNG、ICO 和 ICNS 资源：

```bash
python scripts/update-app-icons.py
```

图标资源和配置的修改会在下次构建、打包后生效。已经发布或安装的程序需要使用新的安装包更新。

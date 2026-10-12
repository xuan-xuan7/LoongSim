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

### 通用显示与电路属性

- 属性表使用“属性名称”“属性值”两列表头，隐藏 HDL 支持提示。
- 普通标签和共享标签统一为 SansSerif、粗体、16 号，隐藏字体选择；旧文件的标签字体也统一为这个默认值。
- 空标签显示为空白，不再出现 HDL Required 提示。
- 电路和子电路隐藏共享标签、共享标签朝向、外观、VHDL 路径；子电路标签固定可见。
- 新电路默认采用 Logisim-HolyCross 外观；已有文件保存的外观继续保留。
- 隐藏“使用固定框大小”。它用于 Logisim-Evolution 子电路符号的固定宽度，HolyCross 外观不使用；旧文件中的值保留。
- 可连线端点使用直径 7 像素的深绿色圆点和细白边，异常信号仍标红；端点坐标及导线状态颜色保留。时钟端保留时钟符号，打印使用黑色端点。

### 输入与输出组件

- **输入、输出引脚**：固定箭头外观；隐藏输出方向、三态输出、上拉/下拉电阻和外观选择。输入默认朝东，输出默认朝西，尖头朝连接端；标签位置可选，默认在连接端相反侧。
- **常量**：支持标签，默认标签在西侧；画布、属性表、编辑和快捷键使用二进制。旧文件数值格式继续兼容，标签随电路保存。
- **按钮**：标签固定蓝色且可见，隐藏标签颜色、可见性；按钮本体颜色仍可设置。
- **拨码开关**：标签默认在西侧，固定蓝色且可见，隐藏颜色、可见性选项。
- **LED、RGB LED**：固定高电位点亮，标签固定蓝色且可见，隐藏对应选项。
- **7 段数码管**：固定高电位点亮、保留默认小数点、标签可见，隐藏对应选项。
- **16 进制数码显示**：固定无小数点、标签可见，隐藏对应选项。

### 连接与位宽组件

- **分线器**：固定左手型外观；按分支设置开始位、结束位，包含起止位，位编号从 0 开始。弹窗只保留分支名、位范围和编号说明。
- **位范围修改**：所选位移到该分支，其原有范围外的位取消分配；可一次撤销。旧文件的非连续分配如实显示，保存仍使用逐位格式。
- **信号观察器**：固定经典 Logisim 外观，隐藏外观选择。
- **同名连接点**：采用无尖头的圆角标签框，保留端口及同名连接功能。
- **位扩展器**：新组件使用宽 80、高 40 的外框；类型与 extend 收紧为上下两行，位宽在框内左右两侧。类型提供全 0、全 1、符号扩展。
- **旧位扩展器**：保留原端口间距；旧文件的输入控制扩展仍可读取。

### 逻辑门

- 所有逻辑门固定输出 0/1，隐藏输出值选择。
- 各输入固定不取反，隐藏逐个输入的取反选项。
- 异或门、同或门固定为奇数个 1 的行为，隐藏多输入行为；同或门对异或结果取反。
- 工具箱隐藏缓冲器、奇/偶校验门、受控缓冲器、受控反相器、PLA，源码及旧文件组件保留。

### 多路选择组件

- **MUX**：新宽版增加 20 像素宽度，显示所有输入编号；文字靠输出侧，移除内部灰色选择提示点。窄版及旧文件保留原端口位置。
- **DMX、DEC、SEL**：采用与 MUX 一致的留白、字号和端点，新组件增加 20 像素宽度；分配器、译码器显示所有输出编号。位选择器仍表示单个总线输入。
- **MUX、DMX、DEC 的位数**：可选 1～7 位，最多 128 路；固定无使能端、禁用时输出 0。DMX、DEC 同时固定无三态，隐藏这些选项。
- **译码器**：位数名称为“输入位数”，隐藏选择端朝向；新组件输入在左侧中央、输出在右侧，随朝向旋转，布局随电路保存。
- **旧布局**：旧文件保留多路选择组件的原端点；原优先级编码器 PRI 保留绘图和读取，但从工具箱隐藏。

### 算术组件

- **ALU**：新组件使用左侧凹口、右侧斜边；A/B 在左、CTR 在上、Ans 在右、Carry 在下，运算功能保留。
- **加法器、减法器**：新外框宽 80、高 60，＋/− 居中；A/B、Cin/Cout 或 Bin/Bout 分开排布，右侧输出标为 Sum 或 Diff。
- **旧布局**：旧 ALU、加法器、减法器保留原端点位置，新布局随电路保存。
- 工具箱隐藏乘法器、除法器、位计数器、位查找器、求负器，源码及旧文件组件保留。

### 触发器与寄存器

- D、T、J-K、S-R 触发器及寄存器固定为 Logisim-Evolution 外观，隐藏外观选择。
- 寄存器固定开启状态页显示，隐藏该选项；填写非空标签后，左下方“状态”页自动显示所属电路、名称、十六进制数值，随仿真刷新。
- 原计数器 Counter、移位寄存器、随机数发生器从工具箱隐藏，源码和旧文件组件保留。

### 简化计数器

- “时序组件”中的新计数器使用文件标识 `SimpleCounter`，参考 CircuitVerse 文档的五端口接口。
- 输入为 Max（最大值）、CLK（时钟）、RST（复位）；输出为 Value（当前数值）、Zero（零标志）。
- 上升沿加 1，超过最大值后回到 0；复位高电平时立即清零；值为 0 时 Zero 输出 1。
- Max 未连接时使用位宽能表示的最大值；框内按十进制显示，输出仍为二进制总线。
- 默认 8 位，支持标签、标签位置；位宽修改同时调整总线及符号尺寸，64 位计数按无符号数处理。

### RAM 与 ROM

- RAM、ROM 固定经典 Logisim 外观，隐藏外观选择。
- **RAM**：固定重置时清空内容、带高电位有效的清除引脚、标签可见；隐藏这三项选项。
- **RAM 读写**：固定字节使能模式但不启用额外字节使能端、读写分离、异步读取；读取行为固定先写后读（异步时不使用），隐藏这些选项。
- **RAM 触发方式**：继续可选，新组件默认高电平写入。旧文件缺省的触发方式保持上升沿，显式设置继续保留。
- **ROM**：固定行大小 1、不允许未对齐，隐藏这两项；内容编辑、地址位宽和数据位宽仍可设置。

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

## Windows 与 Mac 安装包

- Windows 提供 EXE 和 MSI，Mac 分别提供 Apple Silicon、Intel 的 DMG；均内置 Java 运行时。
- 构建、Windows 代码签名、Apple Developer ID 签名及公证的流程见 [安装包说明](packaging/README.md)。
- 未签名产物标为 `unsigned`；可明确选择发布未签名版本，Release 会注明签名状态和首次打开时的系统提示。

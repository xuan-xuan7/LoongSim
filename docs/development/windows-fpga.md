# Windows 版 LoongSim FPGA 集成

## 用户操作

用户从 LoongSim 顶部的“FPGA 实现”进入工作台，依次执行三个固定步骤：

1. **端口映射**：选择工程目录，由学生将电路端口连接到板卡资源，点击“完成端口映射”。
2. **生成位流**：自动执行综合、布局布线和 `.bit` 生成，完成后可以下载。
3. **下载到 FPGA**：自动检查下载器和目标芯片，再下载生成的位流。

端口映射后不会立即开始编译。综合和布局布线是第二步的内部阶段，界面显示状态与日志，
不再提供重复的独立按钮。引脚约束（XDC）自动生成，其说明位于“流程说明”中。
点击「检测设备」或「下载到 FPGA」时，LoongSim 会自动识别连接的下载器。Xilinx Platform Cable USB
使用 WinUSB，Digilent USB/JTAG 可直接使用 Windows 中已有的 FTDI 驱动。两款下载器使用相同的 A7 引脚映射
和位流。界面显示器件型号 XC7A200TFBG676，工具链仍以 -1 速度等级进行时序分析。

每次下载前，LoongSim 会读取 JTAG 链并确认只有一颗 XC7A200T。实验时连接一台下载器。
位流写入 FPGA 的 SRAM，实验箱断电后清除。

## 运行时组成

Windows 10/11 x64，不依赖 WSL、Vivado 或系统 Python：

| 内部引擎 | 固定发行版本 | 用途 |
|---|---|---|
| Yosys、ABC | OSS CAD Suite 2026-10-21 | 电路综合，生成 Xilinx 网表 |
| nextpnr-xilinx | openXC7 工具包 2026-10-27 | 布局布线，输出 FASM 和时序报告 |
| Project X-Ray、FASM、器件数据库 | 同一 openXC7 工具包 | FASM → frames → `.bit` |
| Python | 同一 OSS CAD Suite 的 Python 3.11 | 内部运行 X-Ray 脚本 |
| openFPGALoader | 同一 OSS CAD Suite | USB/JTAG 检测和 FPGA 下载 |
| USB/JTAG 下载工具 | openFPGALoader v1.2.0 + 项目补丁 | 支持 Xilinx 03fd:0007/0008 和 Digilent Windows FTDI 驱动 |

版本按 openXC7 的配套要求固定，不能只替换其中一个工具。下载地址、SHA256、版本和
器件在 `windows-tools.lock.json` 中记录。构建机保留原始工具目录、动态库、技术库、许可证和构建信息；
二进制放在忽略目录 `fpga/runtime/windows-x64/`，不提交到 Git。
Windows 应用打包时由 `copy-windows-a7-runtime.ps1` 排除其他 FPGA 的专用工具和数据库：
保留 XC7A200T 布局布线数据库、FBG676/-1 封装数据、Artix 共用位流数据及映射表，
移除其他 Xilinx 器件数据和 iCE40、ECP5、Gowin 等工具、数据库、综合技术库。
打包只保留流程直接调用的 Yosys、ABC、nextpnr-xilinx、xc7frames2bit、fasm2frames、Python
及两个 openFPGALoader 下载工具，排除未调用的独立仿真器、形式验证/求解工具、波形查看器、
其他下载工具、位流检查/修改工具及其专用后端、资源、示例和未启用的 HDL 前端插件。
Java、Python 运行时、位流转换所需模块、共用动态库、许可证及原始开发工具目录保持原样。
应用中 `fpga/runtime/windows-x64/loongsim/A7-PACKAGING.json` 记录规则摘要、排除文件及体积；
应用和安装包构建清单也记录该摘要，方便核对采用的打包配置。

## 开发与打包

以下脚本供开发、构建安装包使用，学生不需要运行：

先准备现代 MinGW-w64 和 CMake。此次使用 w64devkit 2.11.0（GCC 16.2.0）及 CMake 3.31.6。
便携编译器放在 `local/tools/w64devkit/bin`，CMake 放在
`local/tools/native-build/cmake/data/bin/cmake.exe`；也可以通过安装脚本的
`-CompilerDirectory`、`-CMakeExe` 参数指定位置。编译器只在构建机使用，不进入学生操作流程。

```powershell
# 下载、校验并解压 LoongSim 私有工具目录，不修改系统 PATH 或 USB 驱动
.\toolchain\install-windows-tools.ps1

# 已安装原始运行时后，单独重建下载器兼容助手
.\toolchain\build-windows-xpcu.ps1

# 使用 JDK 11 构建 LoongSim
.\logisim\gradlew.bat -p logisim --no-daemon test shadowJar

# 使用 JDK 17 生成内置 Java、A7 工具及板卡资料的 Windows 应用目录
.\toolchain\package-windows-fpga.ps1 -Java17Home 'JDK17路径'

# 生成 Windows EXE 安装向导与 MSI（需要 WiX 3.14）
.\toolchain\package-windows-fpga-installer.ps1 -Java17Home 'JDK17路径' -WixHome 'WiX目录'
```

同版本重新打包时可加 `-InstallerRevision 1`：公开版本号不变，EXE 的内部版本增加第四段，并在 MSI 中启用同版本替换。后续同版本修订须递增该数字；正常发布更高版本时可恢复默认值0。

应用目录中可以双击运行 LoongSim.exe。Windows EXE 安装向导会预装 FTDI 的原版签名驱动，
由 Windows 选择匹配的驱动。裸 MSI 只安装应用，供已经部署好驱动的环境使用。

面向两种下载器的安装包需要在构建时提供有权部署的 Xilinx 签名驱动和配套固件。向安装包脚本传入
XilinxDriverDirectory（目录内包含 xpcwinusb.inf 和 xpcwinusb.cat）和 XilinxFirmwareDirectory
（目录内包含 xusbdfwu.hex、xusb_emb.hex 和 xusb_xp2.hex）。使用预生成的应用目录时，先在构建
应用目录时传入 XilinxFirmwareDirectory，再在生成安装包时传入 XilinxDriverDirectory。

FTDI 原版驱动可提前下载到 local/toolchain-cache/CDM-v2.12.36.20-WHQL-Certified.zip，也可以通过
FtdiDriverDirectory 指定已解压的原版签名包目录。安装向导需要 Windows 管理员授权来准备下载器驱动。
默认发布目录是 `dist/LoongSim-版本号/`，文件名带 `master` 与 `unsigned`，并附 SHA256 和构建清单。
工具链按多个嵌入式 CAB 打包，避免单个 CAB 的体积限制；与旧 LoongSim 安装包保持升级标识兼容。
已有应用目录可以通过 `-AppImagePath` 传入，避免重新复制工具链。应用目录必须包含配套 FPGA 工具链，不能用仅含编辑器的目录代替。
WiX 3 对源文件仍有路径长度限制，安装包构建默认使用系统盘 `LS/随机目录` 作为临时目录；
可用 `-WorkDirectory` 指定另一个尚不存在的短路径。构建失败时保留该目录供查看日志。

运行时优先查找应用目录，源码运行时查找项目私有工具目录。开发时可用
`LOONGSIM_FPGA_RUNTIME` 环境变量或 `-Dloongsim.fpga.runtime=...` 指定另一个完整运行时。
子进程环境由 LoongSim 设置，不调用系统 PATH 中的其他版本。
Windows 下会通过系统短路径机制适配上游工具的安装目录限制；本机已验证中文、带空格的
完整应用目录。若目标磁盘禁用 8.3 短文件名且安装目录含中文，界面会提示改用英文安装目录。

## 工程输出与失败处理

- `constraints.xdc` 保留板卡映射的完整原始约束。
- `implementation.xdc` 用于 openXC7：仅移除 Vivado 专用的 `CLOCK_DEDICATED_ROUTE BACKBONE`
  属性，保留封装引脚、电平和时钟约束。布局布线以 100 MHz 为默认目标，不允许放宽时序失败。
- `yosys-netlist.json`、`routed.json`、`timing.json`、`design.fasm`、`design.frames`、`design.bit`
  分别记录各阶段输出。
- `logs/` 保存每个工具的日志；失败时停止后续阶段。
- 成功记录将输出的 SHA256 与源文件、顶层和约束对应。重新导出、重新综合会清除旧位流；
  修改源文件或约束后，旧结果不能用于下载。

## 验证与硬件条件

真实工具测试从 Logisim 电路出发，覆盖开关直连输出与 Logisim Clock 顶层封装，使用中文和
空格目录，直到生成 XC7A200T 位流。可显式运行：

```powershell
$env:LOONGSIM_RUN_FPGA_INTEGRATION='1'
# Icarus 仅供开发测试，不随精简安装包分发。
$env:LOONGSIM_FPGA_TEST_SIMULATOR=(Resolve-Path 'fpga/runtime/windows-x64/oss-cad-suite').Path
# 验证已打包的工具时，将此变量设为应用内 app/fpga/runtime/windows-x64 的绝对路径。
# $env:LOONGSIM_FPGA_RUNTIME='精简应用目录/LoongSim/app/fpga/runtime/windows-x64'
.\logisim\gradlew.bat -p logisim --no-daemon cleanTest test --tests '*WindowsFpgaIntegrationTest'
```

自动化测试涵盖下载器识别，但设备下载需要连接实机。Xilinx 下载器的记录见下文。Digilent FT232H
已使用 Windows 原有驱动读回 XC7A200T 的 IDCODE，并完成 SRAM 下载。
板卡事实及 55 个资源引脚的资料核对见
[板卡说明](../../fpga/boards/loongson-a7/HARDWARE_NOTES.md)。

## 下载器初始化

此次下载器在 Windows 中显示为 Xilinx Platform Cable USB Firmware Loader，USB ID 为
`03fd:0007`，已有 WinUSB 驱动。它在加载固件后仍保留相同 USB 编号。项目补丁会先验证
Xilinx 固件协议，已就绪时直接使用；刚插入 USB 时将官方固件加载到下载器 RAM，再验证协议并
重新打开同一编号。只影响本次运行，不写下载器 EEPROM，也不修改驱动。

LoongSim 自动查找应用私有 `loongsim/firmware` 目录、`XILINX_VIVADO` 对应的数据目录，以及
系统盘和 Program Files 中的 Xilinx/AMD Vivado 安装目录。此次使用本机现有的
`C:\Xilinx\Vivado\2019.2\data\xicom\xusbdfwu.hex`。不会启动 Vivado。
学生界面无需指定固件。构建或部署 A7 工具组件时准备配套的官方 HEX 文件；开发时可用
`LOONGSIM_XPCU_FIRMWARE` 或
`-Dloongsim.fpga.xpcuFirmware=...` 指定。Xilinx 的驱动和固件由打包方按可部署的授权提供。

兼容助手与原始 openFPGALoader 分开保存，其他下载器仍使用原始工具。补丁、源码提交、编译器版本、
二进制 SHA256、许可证保存在运行时的 `loongsim/` 中。正常自动化测试不会执行硬件下载。

## 实机验证记录（2026-10-29）

- 当时使用“开关 → 非门 → 输出引脚”的示例控制低电平点亮的 LED；后续电平适配修复见下文。
- 在 LoongSim 的“FPGA 实现”界面完成导出、映射、综合、布局布线和位流生成；位流大小 9,730,757 字节。
- `switchIn` 映射 `switch[7]` / AC21，即板上 SW0；`ledOut` 映射 `led[15]` / H7，即板上 LED1。
- USB 自动识别 `03fd:0007`，6 MHz JTAG，读回 IDCODE `0x03636093`，单器件 XC7A200T。
- 从工作台下载到 SRAM，返回 `isc_done=1、isc_ena=0、init=1、done=1`。
- 用户实际拨动 SW0，确认 LED1 随之亮灭。
- 用户再次拔插 USB 后，使用最新编排代码和应用内工具验证自动加载固件、重新检测和下载。

下载成功会同时检查工具退出码和 FPGA 返回的 INIT/DONE 状态，避免仅凭进度条达到 100% 就报告成功。

## 仿真与实体 LED 一致

之前的集成直接把电路模块输出连接到板卡引脚，遗漏了原 Logisim FPGA 顶层中的电平适配步骤。
逻辑 1 在仿真中点亮 LED，却会使 A7 的低电平有效单色 LED 熄灭。现在保留原电路 HDL，
在独立的 `loongsim-board-interface.v` 中对映射到 `led` / `led[n]` 的输出反相。
其他输出、输入和双向端口不因这次修复被反相。单个输出和整条 LED 总线均适用。

实现顶层为电路/时钟封装模块外的 `*_LoongSimBoardTop`；顶层端口名称及旧资源 ID 保持兼容。
重新映射会重写板卡接口并清除旧综合和位流结果，不会叠加反相。旧工程需要从电路重新生成。
`mapping.json` 记录实体资源名称和自动适配的 LED 端口。映射界面显示 SW7、SW6、LED16 等
板上丝印编号，避免把内部 `switch[0]` 误认为板上 SW0。

门窗报警示例的 8 种输入组合在导出 HDL 和综合后网表中均通过 Icarus Verilog 仿真，检验
`warning = (door | window) & ~guard`，以及实体 LED 的驱动引脚始终等于 `~warning`。
原出错工程的 FASM LUT 内容也已核对，与门电路一致；问题位于板卡电平适配。

修复后的原始 guard 电路已重新下载，用户确认全部组合符合：SW7/SW6/SW5 都向上时灯灭；
SW5 向上且 SW7/SW6 至少一个向下时灯亮；SW5 向下时灯始终灭。
该实验箱这组开关的下端对应逻辑 1，上端对应逻辑 0，不应把机械方向直接当作逻辑值。

上游来源：

- [OSS CAD Suite 2026-10-21](https://github.com/YosysHQ/oss-cad-suite-build/releases/tag/2026-10-21)
- [openXC7 工具包 2026-10-27](https://github.com/cavearr/toolchain-openxc7-releases/releases/tag/2026-10-27)
- [openFPGALoader](https://github.com/trabucayre/openFPGALoader)

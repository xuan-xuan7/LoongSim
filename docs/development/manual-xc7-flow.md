# LoongSim 到 Xilinx 7 系列 FPGA 手工流程

本文以 `xc7a35tcsg324-1` 为示例。实际操作时，必须把器件、封装、速度等级、引脚、
时钟、数据库和下载器参数替换为真实实验箱的数据。

## 一、准备工具

在构建机上确认下列命令可用。LoongSim 发布目录不要求安装这些开发工具，运行时由
`fpga/*-runtime/` 提供：

```bash
java -version
yosys -V
cmake --version
python3 --version
openFPGALoader --version
```

还需要完成 Project X-Ray 编译，并准备与 FPGA 型号匹配的数据库。

## 二、从 LoongSim 导出可综合 Verilog

在 LoongSim 中：

1. 选择顶层电路。
2. 打开 FPGA/HDL 功能。
3. 选择已有开发板，或者新建实验箱板卡定义。
4. 把所有顶层输入、输出和时钟映射到开发板资源。
5. 把 Verilog 生成到独立的构建目录。

顶层端口使用 `clock`、`reset`、`button`、`led` 等简单英文名称。不要使用仿真专用
时钟、悬空输入或 FPGA 不支持的交互组件。

## 三、编写 XDC 引脚约束

以下仅为示例：

```tcl
set_property PACKAGE_PIN E3 [get_ports clock]
set_property IOSTANDARD LVCMOS33 [get_ports clock]
create_clock -period 10.000 [get_ports clock]

set_property PACKAGE_PIN A14 [get_ports button]
set_property IOSTANDARD LVCMOS33 [get_ports button]

set_property PACKAGE_PIN H5 [get_ports led]
set_property IOSTANDARD LVCMOS33 [get_ports led]
```

必须根据实验箱原理图填写真实引脚和 I/O 电压。错误电压或把两个输出连接到一起可能损坏硬件。

## 四、使用 Yosys 综合

进入生成 Verilog 的目录：

```bash
/path/to/LoongSim/fpga/yosys-runtime/usr/bin/yosys -p \
  "read_verilog top.v; synth_xilinx -flatten -top top; write_json top.json"
```

如果 LoongSim 生成多个 Verilog 文件，需要把所有源文件加入 `read_verilog`。

输出为：

```text
top.json
```

## 五、生成 Yosys 运行时

```bash
在独立的构建目录编译 Yosys，并将 `yosys`、`berkeley-abc`、`yosys-abc` 和
`share/yosys/` 复制到：

```text
LoongSim/fpga/yosys-runtime/usr/bin/
LoongSim/fpga/yosys-runtime/usr/share/yosys/
```

其中 `yosys-abc` 应是指向同目录 `berkeley-abc` 的符号链接。
```

依赖包名称因操作系统和发行版而异。LoongArch Linux 应使用系统原生 GCC/Clang 和开发库编译。

## 六、生成 nextpnr 运行时

```bash
在仓外的 nextpnr-xilinx 源码目录完成构建。将可执行文件、chipdb 和所需运行数据复制到：

```text
LoongSim/fpga/nextpnr-runtime/
```
```

可执行文件通常为：

```text
nextpnr-runtime/bin/nextpnr-xilinx
```

## 七、生成器件 chipdb

以 Artix-7 `xc7a35tcsg324-1` 为例：

```bash
python3 xilinx/python/bbaexport.py \
  --device xc7a35tcsg324-1 \
  --bba xilinx/xc7a35t.bba
```

查找 `bbasm`：

```bash
find build -type f -name bbasm
```

根据实际路径执行：

```bash
./build/bba/bbasm --l xilinx/xc7a35t.bba xilinx/xc7a35t.bin
```

生成：

```text
xilinx/xc7a35t.bin
```

## 八、布局布线

```bash
/path/to/LoongSim/fpga/nextpnr-runtime/bin/nextpnr-xilinx \
  --chipdb /path/to/LoongSim/fpga/nextpnr-runtime/chipdb/xc7a35t.bin \
  --xdc board.xdc \
  --json top.json \
  --write top-routed.json \
  --fasm top.fasm
```

如果出现以下错误，不要继续生成或烧录：

- 存在未布通网络；
- 时序不满足要求；
- 包含不支持的逻辑单元；
- XDC 约束错误；
- 引脚或 I/O 电压冲突。

## 九、生成 Project X-Ray 运行时

```bash
在仓外的 Project X-Ray 源码目录完成构建。将 `fasm2frames.py`、`xc7frames2bit`、
器件数据库和依赖的运行数据复制到：

```text
LoongSim/fpga/prjxray-runtime/
```
```

Project X-Ray 的上游环境较旧，实际依赖可能随 Linux 发行版不同而变化。优先按照仓库 README
准备 Python 环境和 CMake 依赖。

## 十、生成 Xilinx 位流

```bash
export XRAY_DIR=/path/to/LoongSim/fpga/prjxray-runtime
export XRAY_DATABASE_DIR=/path/to/LoongSim/fpga/nextpnr-runtime/prjxray-db
```

FASM 转 frames：

```bash
python3 "$XRAY_DIR/utils/fasm2frames.py" \
  --db-root "$XRAY_DATABASE_DIR/artix7" \
  --part xc7a35tcsg324-1 \
  top.fasm > top.frames
```

Frames 转 `.bit`：

```bash
"$XRAY_DIR/build/tools/xc7frames2bit" \
  --part_file "$XRAY_DATABASE_DIR/artix7/xc7a35tcsg324-1/part.yaml" \
  --part_name xc7a35tcsg324-1 \
  --frm_file top.frames \
  --output_file top.bit
```

## 十一、生成 openFPGALoader 运行时

```bash
在仓外的 openFPGALoader 源码目录完成构建，并将可执行文件和必要的 udev 规则复制到：

```text
LoongSim/fpga/openFPGALoader-runtime/
```
```

Linux 上还需要安装 libusb、libftdi 或与实际下载器匹配的开发库，并安装项目提供的 udev 规则。

## 十二、检测并烧录开发板

连接实验箱电源和 JTAG 后检测：

```bash
/path/to/LoongSim/fpga/openFPGALoader-runtime/bin/openFPGALoader --detect
```

Digilent 兼容下载器：

```bash
openFPGALoader --detect --cable digilent
openFPGALoader --cable digilent top.bit
```

如果 openFPGALoader 内置该开发板定义，优先使用：

```bash
openFPGALoader --board 开发板名称 top.bit
```

上述命令通常下载到 FPGA SRAM，断电后配置消失。写入 SPI Flash 与开发板和 Flash 型号强相关，
只有确认板卡定义、启动模式和 Flash 芯片后才能执行。

## 十三、上板验证

检查：

- DONE/配置指示灯是否显示成功；
- 时钟源和复位有效电平是否正确；
- 按键输入是否悬空；
- LED 是高电平点亮还是低电平点亮；
- 人眼可见信号是否经过足够分频；
- I/O Bank 电压是否与实验箱一致。

第一项实验建议采用“按键控制 LED”或“时钟分频驱动 LED”。在最小流程成功之前，
不要立即加入 RAM、PLL、SERDES 或厂商专用 IP。

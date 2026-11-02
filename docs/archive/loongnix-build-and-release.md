# 历史归档：旧世界 Loongnix 构建与 Release

> 本文保留历史方案与当时的环境假设。当前 master 不包含文中所有构建脚本，且当前 Java 核心要求 Java 11；本文的 Java 8、器件和脚本示例不能直接当作当前操作步骤。Windows 当前流程请看[开发文档](../development/README.md)。

本仓库是源码仓库，不包含 CMake、Bison、Flex、Python 或任何已经编译的二进制文件。
在一台旧世界 Loongnix/LoongArch64 构建机上完成一次构建，再将生成的 Release 分发给
ABI 兼容的目标机器。

## 构建机依赖与版本基线

下表是当前源码和脚本的**建议起始版本**，用于旧世界 Loongnix 的首次编译。它不是声称
所有版本都已经在你的具体机器上验证；实际编译时应记录 `apt-cache policy` 与报错，再按
机器的软件源、glibc 和 GCC 情况调整。

| 组件 | 建议基线 | 要求/用途 |
|---|---:|---|
| 系统与 ABI | 旧世界 Loongnix / LoongArch64 | 构建机应与目标机同 ABI，最好使用同一系统镜像或更旧的 glibc。 |
| GCC/G++ | 7.5 或更高 | nextpnr-xilinx 与 openFPGALoader 需要 C++17。 |
| GNU Make | 4.0 或更高 | 编译 Yosys、ABC 和其他原生工具。 |
| CMake | 3.13.5 或更高 | prjxray、nextpnr、openFPGALoader；仓库已为 CMake 3.13 兼容修补 prjxray 内嵌的 cctz 与 Abseil。 |
| Bison | 3.8.2 | Yosys 的 Verilog/RTLIL 解析器生成。 |
| Flex | 2.6.4 | Yosys 词法分析器生成。 |
| Python | **CPython 3.7.3** | prjxray 与芯片数据库脚本；当前脚本要求 Python 3.7.x。 |
| pip / setuptools / wheel | 23.1.2 / 67.8.0 / 0.41.3 | Python 构建与依赖安装。 |
| Java JDK/JRE | OpenJDK 8 | LoongSim 前端以 Java 8 编译与运行。 |
| Gradle | 6.3 | 由仓库的 Gradle Wrapper 自动下载，不要求系统安装。 |
| Boost | 1.70.0 | nextpnr 核心需要 filesystem、thread、program_options、iostreams。 |
| libusb | 1.0.22 或更高 | openFPGALoader 的 USB 下载支持。 |
| libftdi1（可选） | 1.5 | 仅 FTDI 型下载线、USB Blaster I、XVC 服务端需要。 |
| pkg-config | 0.29 或更高 | 查找 libusb、libftdi1 等开发库。 |

Python 库的精确锁定版本见 `toolchain/requirements-python37-runtime.txt`：

```text
Cython 0.29.36       numpy 1.21.6        PyYAML 6.0.1
intervaltree 3.1.0    sortedcontainers 2.4.0  pyjson5 1.6.2
simplejson 3.19.2     textX 3.0.0        Arpeggio 2.0.2
```

## apt 安装示例

```sh
sudo apt update
sudo apt install gcc g++ make cmake bison flex git pkg-config \
  libboost-all-dev libusb-1.0-0-dev python3 python3-pip openjdk-8-jdk
```

如使用 FTDI 型下载线，额外安装 `libftdi1-dev`。系统软件源包名可能因 Loongnix 版本而不同，
以 `apt search` 的实际结果为准。

安装后先记录实际版本，后续排错时一并提供：

```sh
gcc --version | head -1
g++ --version | head -1
make --version | head -1
cmake --version | head -1
bison --version | head -1
flex --version
python3 --version
java -version
pkg-config --modversion libusb-1.0
apt-cache policy cmake bison flex libboost-all-dev libusb-1.0-0-dev
```

检查构建机：

```sh
./toolchain/build-loongnix.sh check-deps
```

## 构建

启动或构建 Java UI 使用：

```sh
cd logisim
./run-loongsim.sh
```

构建 FPGA 后端：

```sh
cd ..
./toolchain/build-loongnix.sh all
```

`all` 不构建 Java JAR；`run-loongsim.sh` 会按需构建它。构建结果均在 `local/`、各
`build-oldworld/` 目录和 `logisim/build/`，这些文件均被 Git 忽略。

### 现有 Python 环境无法安装 requirements 时

`all` 是完整流程，因此会执行 `python-deps` 并安装 Project X-Ray 的 Python 库。若当前
机器不能安装或升级这些库，可先构建不依赖它们的原生核心：

```sh
./toolchain/build-loongnix.sh native-core
```

它依次构建 Yosys、nextpnr-xilinx、`xc7a35t` 芯片数据库和 openFPGALoader，不会调用
`pip install`。这可用于 UI、HDL、综合、布局布线和下载器验证；但不包含 Project X-Ray 的
Python/FASM/bitstream 阶段，因此不能作为完整 FPGA 上板 Release。

## 发布

在同一台构建机上生成运行包后，将 `dist/*.tar.gz` 上传为 GitHub Release 资产。运行包必须
内含 Java JRE、LoongSim JAR、原生 FPGA 工具、芯片数据库及其非系统运行库。目标机无需
安装 CMake、Bison、Flex、Gradle 或编译器。

Release 打包脚本在 JAR 缺失时会自动调用 Gradle Wrapper 构建，无需先打开 UI。

```sh
JRE_SOURCE=/实际路径/java-8-jre \
  ./toolchain/package-release-oldworld.sh
```

建议为每次可交付版本创建 Git 标签，并将生成的压缩包作为同名 GitHub Release 的附件上传：

```sh
git tag v0.1.0-loongnix-oldworld
git push origin v0.1.0-loongnix-oldworld
```

Release 用户只下载 `LoongSim-oldworld-loongnix-loongarch64.tar.gz`，解压后运行
`./LoongSim`；不需要 clone 源码或执行编译命令。

旧世界与新世界 LoongArch、不同 glibc 版本并不保证 ABI 兼容。应在与目标机同系统镜像或
更旧兼容环境上构建，并分别发布对应的 Release。

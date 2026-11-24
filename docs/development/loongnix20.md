# Loongnix 20 移植与打包

LoongSim 编辑器和仿真器使用同一个 Java 11 JAR。Loongnix 20 的 FPGA 工作台使用 LoongArch64 版 Yosys/ABC、openXC7 和 openFPGALoader，目标器件为龙芯 A7 实验箱的 `xc7a200tfbg676-1`。在 QEMU 中可验证编辑、仿真和位流生成，在龙芯 3A6000 上插入实验箱后验证 USB 下载。

## 构建原生 FPGA 工具

在 Loongnix 20 LoongArch64 上安装构建依赖。综合使用 Loongnix 软件仓库的 Yosys 0.8 和 ABC。脚本应用了与系统 CMake 3.13 配套的构建补丁，其他原生工具按指定提交编译。

```sh
sudo apt install build-essential git cmake ninja-build pkg-config yosys berkeley-abc \
  libboost-program-options-dev libboost-iostreams-dev libboost-thread-dev libeigen3-dev zlib1g-dev \
  libusb-1.0-0-dev libftdi1-dev libhidapi-dev libudev-dev \
  python3 python3-dev python3-pip dpkg-dev
JOBS=2 bash toolchain/build-loongnix20-tools.sh /path/to/openxc7-release.tgz
```

使用 `toolchain/windows-tools.lock.json` 中 `openxc7` 条目指定的归档。构建脚本通过 SHA-256 校验后，从归档提取器件数据库和与 nextpnr 匹配的 `chipdb`，可执行程序由 Loongnix 构建。输出在 `fpga/runtime/loongnix20-loongarch64/`，也可以通过 `LOONGSIM_FPGA_RUNTIME` 指定位置。

使用 JDK 11 生成 `logisim/build/libs/LoongSim-1.1.0-java11-all.jar`。可以在 Windows 等已有 Java 构建环境运行 Gradle，然后把 JAR 复制到 Loongnix。

```sh
cd logisim
sh gradlew --no-daemon test shadowJar
```

如需从源码环境执行原生工具回归，另外安装 `iverilog`，然后在 `logisim/` 目录运行下列命令。

```sh
LOONGSIM_RUN_FPGA_INTEGRATION=1 sh gradlew --no-daemon cleanTest test \
  --tests com.cburch.logisim.fpga.loongsim.WindowsFpgaIntegrationTest
```

## 生成和安装 `.deb`

在 Loongnix 20 上从仓库根目录执行。

```sh
bash toolchain/package-loongnix20.sh logisim/build/libs/LoongSim-1.1.0-java11-all.jar
```

将 `dist/loongsim_1.1.0~loongnix20.1_loongarch64.deb` 复制到运行 Loongnix 20 的机器上，在该文件所在目录执行安装。

```sh
sudo apt install ./loongsim_1.1.0~loongnix20.1_loongarch64.deb
```

安装后从应用菜单打开 LoongSim，或运行 `loongsim`。使用 FPGA 时先生成位流，再打开下载器检测。软件包为活动桌面用户设置了 A7 下载器的 USB 访问规则，安装后重新插入下载器。Xilinx USB 下载线如需初始化固件，可将从厂商工具取得的 HEX 文件放在 `/opt/loongsim/fpga/runtime/loongnix20-loongarch64/loongsim/firmware/` 下，或用 `LOONGSIM_XPCU_FIRMWARE` 指定具体文件。

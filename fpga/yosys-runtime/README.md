# LoongArch Yosys 运行时

此目录来自 LoongArch/3A5000 系统中提取的 `yosys` 和 `berkeley-abc` Debian 软件包，
不是源码树。当前运行时版本：

```text
Yosys 0.8 (git sha1 5706e90)
```

关键文件：

- `usr/bin/yosys`：Yosys 综合程序；
- `usr/bin/berkeley-abc`：ABC 逻辑优化和 LUT 技术映射程序；
- `usr/bin/yosys-abc`：指向同目录 `berkeley-abc` 的符号链接；
- `usr/share/yosys/`：Yosys 的 Xilinx 技术库和综合脚本。

这些文件是 LoongArch ELF，只能在兼容的 LoongArch Linux 用户空间运行。替换运行时
时必须同时替换 `yosys`、`berkeley-abc`、`yosys-abc` 和 `usr/share/yosys/`，不能只替换
单个可执行文件。

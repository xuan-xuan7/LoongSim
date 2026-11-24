# LoongSim FPGA 工具链

完整的 Xilinx 7 系列全开源流程包含以下组件：

1. **LoongSim**：绘制电路、仿真、生成 HDL、映射开发板资源。
2. **Yosys**：Verilog 综合和 Xilinx 技术映射。
3. **nextpnr-xilinx**：布局布线和时序分析。
4. **Project X-Ray 工具与数据库**：FASM 转 frames，再生成 `.bit` 位流。
5. **openFPGALoader**：通过 USB/JTAG 下载位流。

第三方工具运行时在构建机上准备，源码获取与分发义务按各自许可证处理。可执行文件、动态库和
运行数据放在相应的平台运行时目录，再由 LoongSim 调用。

## Windows 集成

Windows x64 已接入项目私有运行时 `fpga/runtime/windows-x64/`。用户从 LoongSim 的
“FPGA 实现”进入工作台，先完成端口映射，再生成位流，最后下载到 FPGA；综合和布局布线自动完成。
固定版本、安装脚本、完整应用打包和验证方式见 [Windows FPGA 集成](windows-fpga.md)。
第三方二进制通过有 SHA256 锁定的发行包安装，不放入 Git；开发者安装工具与用户操作流程分开。

## Loongnix 20 集成

`loongnix20` 分支使用 `fpga/runtime/loongnix20-loongarch64/`，支持龙芯 A7 实验箱的综合、布局布线和位流生成。从源码构建和生成 `.deb` 的步骤见 [Loongnix 20 移植与打包](loongnix20.md)。

## 重要限制

- 第一阶段建议只支持 Xilinx 7 系列。
- `nextpnr-xilinx` 是实验性项目，并非所有器件、原语和 IP 都完整支持。
- UltraScale+ 在该仓库中采用 RapidWright/Vivado 辅助流程，应作为单独目标处理。
- 运行时必须针对目标平台编译。`fpga/yosys-runtime/` 是早期的 LoongArch64 综合运行时。
- FPGA 完整型号、封装、速度等级、引脚约束和 I/O 电压必须与真实实验箱一致。

手工流程见 [手工流程](manual-xc7-flow.md)。

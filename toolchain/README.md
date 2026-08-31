# LoongSim FPGA 工具链

完整的 Xilinx 7 系列全开源流程包含以下组件：

1. **LoongSim**：绘制电路、仿真、生成 HDL、映射开发板资源。
2. **Yosys**：Verilog 综合和 Xilinx 技术映射。
3. **nextpnr-xilinx**：布局布线和时序分析。
4. **Project X-Ray 工具与数据库**：FASM 转 frames，再生成 `.bit` 位流。
5. **openFPGALoader**：通过 USB/JTAG 下载位流。

第三方工具源码不放入 LoongSim 发布目录。每个平台在构建机上编译完成后，只将可执行文件、
动态库和运行数据放入对应的 `fpga/*-runtime/` 目录，再由 LoongSim 调用。

## 重要限制

- 第一阶段建议只支持 Xilinx 7 系列。
- `nextpnr-xilinx` 是实验性项目，并非所有器件、原语和 IP 都完整支持。
- UltraScale+ 在该仓库中采用 RapidWright/Vivado 辅助流程，应作为单独目标处理。
- 运行时必须针对目标平台编译。当前 `fpga/yosys-runtime/` 是 LoongArch64 版本，不能在
  macOS 或 Windows 上执行；其它平台需要各自的 runtime 包。
- FPGA 完整型号、封装、速度等级、引脚约束和 I/O 电压必须与真实实验箱一致。

手工流程见 `MANUAL_XC7_FLOW.md`。

# LoongSim Logisim-only

这个分支只保留 LoongSim 的 Logisim 电路编辑与仿真工程，适合先使用基础数字逻辑功能。

支持：

- 创建、编辑、保存和打开 `.circ` 电路文件
- 数字逻辑电路仿真、时钟和波形观察
- 真值表、组合逻辑分析和电路层次编辑
- Verilog/VHDL 相关的 Logisim 原生编辑与导出能力

本分支不包含 LoongSim 自定义 FPGA 工作台、板卡工具链、Yosys/nextpnr、Project X-Ray 或烧录工具，
启动界面也不提供 FPGA 按钮或 FPGA 菜单。后续 FPGA 功能会在独立版本中维护。

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

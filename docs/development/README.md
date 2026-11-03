# 开发文档

## 从源码运行

Java 源码使用 JDK 11 编译；需要 JDK 11 或更高版本运行。
在仓库根目录执行：

```powershell
.\logisim\gradlew.bat -p logisim --no-daemon run
```

macOS / Linux 可在 `logisim/` 中执行 `./gradlew run`。电路编辑器可跨平台运行，当前集成的 FPGA 全流程及发布的安装包面向 Windows x64。

## 构建与测试

```powershell
.\logisim\gradlew.bat -p logisim --no-daemon test shadowJar
```

生成的 JAR 位于 `logisim/build/libs/`。Windows 应用和安装包通过 JDK 17 的 jpackage 生成，MSI / EXE 安装包还需 WiX 3.14；具体步骤见 [Windows FPGA 集成](windows-fpga.md)。

普通 `test` 默认跳过原生 FPGA 工具测试。要验证实际电路、映射、综合前后逻辑与位流生成，运行 `toolchain/test-windows-fpga.ps1`，具体依赖、报告及范围见[测试说明](../testing/README.md)。

## 技术资料

- [项目结构](project-structure.md)：各目录的职责。
- [FPGA 后端与板卡适配](fpga-backend.md)：端口映射、板卡接口和工程输出。
- [工具链概览](toolchain.md)：内部工具和平台限制。
- [手动检查更新](updates.md)：软件内更新入口、下载校验及 Release 发布要求。
- [流程与实现状态](architecture.md)：HDL、XDC、综合和实现顶层的关系。
- [手工 Xilinx 7 系列流程](manual-xc7-flow.md)：手动排查和构建参考。
- [板卡引脚核对](../../fpga/boards/loongson-a7/HARDWARE_NOTES.md)：实验箱资料和已确认的引脚。

## 分支和发布文档

所有功能开发、测试、文档与正式发布均基于 `master`。原纯电路分支已完成合并并停止维护，其专用打包入口已移除。
退役经过见[2026-11-03维护记录](../changelog/2026-11-03-master-only.md)，旧版本仅保留历史说明与源码标签。

发布前把用户可感知的改动写入[未发布记录](../changelog/unreleased.md)，发布时整理为独立版本文档。
README 保持简短；Release 正文只放版本用途、主要更新、安装提示和详细记录链接。
历史版本记录不可把尚未发布的本地安装包写成线上包，安装包与源码应有明确的版本对应关系。

[返回文档中心](../README.md)

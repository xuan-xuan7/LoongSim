LoongSim VERSION - Windows x64 Logisim-only
============================================

本安装包对应 LoongSim 仓库的 logisim-only 分支。

安装方法
--------

1. 双击 LoongSim-VERSION-logisim-only-windows-x64-setup.exe。
2. 按照安装向导选择安装目录并完成安装。
3. 双击桌面的 LoongSim 快捷方式，或从开始菜单启动 LoongSim。

默认安装位置
------------

%LOCALAPPDATA%\Programs\LoongSim

安装不需要管理员权限。安装包内含 Windows x64 Java 11 运行时，无需另外安装 Java。
可以在 Windows 的“设置 -> 应用”中卸载，也可以使用开始菜单中的“卸载 LoongSim”。

此版本包含
----------

- Logisim 数字电路编辑和仿真
- .circ 工程的打开与保存
- 时钟、探针、存储器、分析器和层次化电路
- LoongSim 新增的可配置位宽 ALU 组件

此版本不包含
------------

- LoongSim FPGA 工作台、FPGA 按钮或 FPGA 菜单
- Yosys、nextpnr-xilinx、Project X-Ray 或 openFPGALoader
- FPGA 板卡数据库、位流生成或烧录功能

说明
----

该安装包尚未使用商业代码签名证书签名。Windows SmartScreen 可能显示“未知发布者”；
请核对发布页提供的 SHA-256 校验值后再运行。

Source commit: COMMIT
Bundled runtime: Eclipse Temurin JRE 11.0.32.1+1 (Windows x64)

# LoongSim

面向青少年的数字电路设计、仿真与 FPGA 实验平台。
在同一个软件中完成画电路、验证逻辑、映射实验箱资源、生成位流和下载到 FPGA。

**[下载 Windows 安装包](https://github.com/xuan-xuan7/LoongSim/releases/tag/v1.1.0)** · [开始使用](docs/getting-started.md) · [文档中心](docs/README.md)

## 主要功能

- 中文电路编辑与逻辑仿真，提供适合教学的组件分组和属性设置。
- 在“FPGA 实现”中依次完成端口映射、生成位流、下载到实验箱。
- 映射界面显示中文标签与实体资源位置，支持多位端口拆分、数码管及矩阵键盘适配。
- Windows 安装包内置 Java 和 FPGA 工具，无需分别操作底层工具。

## 下载与运行

项目统一基于 `master` 开发和发布。当前版本1.1.0支持 Windows 10/11 x64，包含电路编辑、仿真及 FPGA 全流程。
FPGA 功能针对龙芯 A7 实验箱（XC7A200T / FBG676 / -1）。
安装后从桌面或开始菜单启动。实验箱下载所需的驱动和厂商固件条件见[使用指南](docs/getting-started.md)。

## 文档

- [开始使用](docs/getting-started.md)：安装、启动和 FPGA 操作步骤。
- [版本记录](docs/changelog/README.md)：每个版本的改动、升级说明和验证范围。
- [开发文档](docs/development/README.md)：源码运行、构建、打包与技术资料。
- [未发布改动](docs/changelog/unreleased.md)：本地验证完成、尚未进入正式安装包的更新。

## 开源说明

LoongSim 基于 Logisim-evolution 开发，相关源码遵循 [GPLv3](logisim/LICENSE.md)。
所集成的独立工具及依赖分别保留其许可证，具体声明随相应组件分发。

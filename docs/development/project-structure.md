# LoongSim 项目结构

```text
LoongSim/
├── README.md                   项目介绍、下载及文档入口
├── docs/                       使用指南、版本记录、开发资料与历史归档
├── logisim/                    Java/Swing 电路编辑、仿真与 FPGA 界面
├── fpga/
│   ├── boards/loongson-a7/      板卡图片、约束模板与引脚核对
│   ├── examples/               示例电路
│   ├── runtime/windows-x64/    Windows 工具与数据库（忽略二进制）
│   └── yosys-runtime/          原有 LoongArch Yosys / ABC 运行时
├── toolchain/                  工具安装、下载器构建、打包脚本及许可材料
├── local/                      本地工具、构建缓存和验证记录（忽略目录）
└── dist/                       应用目录与安装包输出（忽略目录）
```

## FPGA 工程输出

导出工程的位置由用户在“FPGA 实现”工作台选择，目录中保存 Verilog、约束、映射和工具输出。

| 文件 | 用途 |
|---|---|
| `board-template.xdc` | 板卡完整约束模板 |
| `constraints.xdc` | 端口映射确认后生成的当前电路约束 |
| `implementation.xdc` | 适配 openXC7 的实现约束 |
| `mapping.json`、`port-labels.json` | 资源映射及原始中文标签与 HDL 名称的对应 |
| `top-module.txt`、`sources.list` | 实现顶层和 HDL 源文件清单 |
| `loongsim-project.json` | 器件、顶层、源文件及约束等工程信息 |
| `yosys-netlist.json`、`routed.json`、`timing.json` | 综合、布局布线与时序结果 |
| `design.fasm`、`design.frames`、`design.bit` | 位流生成阶段输出 |
| `logs/` | 各工具日志 |

实现工程使用独立板卡接口顶层适配单色 LED 等资源；使用 Logisim `Clock` 时还包含时钟封装。
源文件、实现顶层和约束必须对应同一工程，修改设计后不能沿用旧位流。

[FPGA 后端与映射细节](fpga-backend.md) · [返回开发文档](README.md)

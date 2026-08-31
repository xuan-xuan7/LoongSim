# LoongSim 项目结构

```text
LoongSim/
├── README.md
├── PROJECT_STRUCTURE.md
├── logisim/
│   ├── src/
│   ├── boards_model/
│   ├── build.gradle.kts
│   └── gradlew
├── fpga/
│   ├── yosys-runtime/
│   └── boards/
└── toolchain/
    ├── README.md
    └── MANUAL_XC7_FLOW.md
```

## 目录职责

- `logisim/`：LoongSim 桌面界面、电路模型、仿真、HDL 生成和板卡资源映射。
- `fpga/yosys-runtime/`：固定的 LoongArch Yosys、Berkeley ABC 和 Yosys 技术库。
- `fpga/boards/`：Loongson A7 等板卡的 XDC 和资源说明。
- 每个导出的电路工程在端口映射确认后包含 `board-template.xdc`（板卡完整模板）、`constraints.xdc`（本电路实际使用的约束）和
  `mapping.json`（板卡资源、已匹配端口和待映射端口清单）。
- 同一工程还会生成 `top-module.txt`（综合顶层）、`sources.list`（唯一的 Verilog 输入清单）和
  `loongsim-project.json`（器件、顶层、源文件、约束和综合输出的机器可读清单）。
- 没有派生时钟时，生成的电路模块就是 FPGA 实现顶层；使用 Logisim `Clock` 时会额外生成
  `*_LoongSimTop` 顶层封装和 `loongsim-clock-support.v`，XDC 始终针对实现顶层生成。
- 导出工程目录由用户在 FPGA 工作台中选择；该目录同时保存 `verilog/`、XDC、映射文件和 Yosys 输出。
- 后续工具以 `nextpnr-runtime/`、`prjxray-runtime/`、`openFPGALoader-runtime/` 等编译产物目录加入。
- `toolchain/`：存放 LoongSim 自己的流程控制、工具发现、板卡清单、脚本和文档。

后续由 LoongSim Java 服务层统一调用这些运行时工具。第三方工具是可替换的内部实现组件，
用户看到的产品名称和操作入口始终是 LoongSim。

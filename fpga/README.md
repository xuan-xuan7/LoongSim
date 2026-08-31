# LoongSim FPGA 后端

本目录保存 LoongSim 使用的 FPGA 编译运行时和板卡资源，不保存第三方源码。

| 目录 | 作用 |
|---|---|
| `yosys-runtime/` | 固定的 LoongArch Yosys 0.8、Berkeley ABC 和 Yosys 技术库 |
| `boards/loongson-a7/` | xc7a200tfbgb676-1 的 XDC 模板 |

第三方工具在构建机上单独编译，完成后只把对应运行时目录复制到这里。LoongSim 自己的流程
编排代码、板卡清单和中文文档放在根目录的 `toolchain/`。

LoongSim 界面当前已经接通三个阶段：生成 Verilog 工程、端口映射并生成 XDC、调用 `yosys-runtime` 的
`synth_xilinx` 综合。运行时同时包含 `berkeley-abc`，`yosys-abc` 是指向它的符号链接。
nextpnr、芯片数据库、位流和烧录阶段将按相同的运行时目录方式加入。

流程顺序为：生成 Verilog → 端口映射确认 → 生成本电路 XDC → Yosys 综合 → 后续布局布线和位流生成。
当前 Yosys 只读取 Verilog；XDC 会由后续 Xilinx 实现工具读取。先生成 Verilog 再生成 XDC，
是为了准确筛选当前电路真正存在的端口，并不表示 XDC 可以延迟到布局布线之后。

`yosys-runtime/usr/bin/yosys` 是 LoongArch ELF，不能在 macOS 上执行；macOS 只用于编译和查看界面，
实际综合应在 3A5000/LoongArch Linux 上运行。

导出电路时，LoongSim 会把本目录的板卡 XDC 作为完整模板复制到电路工程，并按照顶层 Verilog
端口映射确认后，LoongSim 才会生成独立的 `constraints.xdc`。`mapping.json` 的 `portMappings` 使用“电路端口 -> 板卡资源”方向，
会记录板卡全部资源、已经匹配的端口和未匹配端口。资源选择受输入/输出方向、位宽和重复占用规则限制。

导出工程还会生成 `top-module.txt`、`sources.list` 和 `loongsim-project.json`。后续综合必须使用
`sources.list` 中的源文件和 `top-module.txt` 中的顶层，不能把工程目录里的临时 Verilog 文件混入。
`constraints.xdc` 中的每一个端口引用都会在代码层校验为该顶层真实存在的端口；总线通配符会按学生选择的
整条总线或单个 bit 展开，避免约束仍指向板卡模板中的原始名称。

使用 Logisim `Clock` 时，LoongSim 会额外生成 `loongsim-clock-support.v` 和 `*_LoongSimTop` 顶层封装。
封装把板卡 `FPGA_GlobalClock` 输入转换为 Logisim 的 5 位时钟树；XDC 针对封装顶层生成，内部的
`LOGISIM_CLOCK_TREE_*` 不会出现在学生的资源选择列表中。映射窗口不会允许时钟输入连接到拨码开关或普通按键。

Yosys 阶段会严格读取 `sources.list`，并确认 `top-module.txt` 对应的 Verilog 文件就在清单中；不能通过
手动修改清单来综合一个缺少顶层的工程。

工程目录由用户在 FPGA 工作台点击“生成 Verilog 工程”时选择，不再自动创建到默认工作区的项目名子目录。

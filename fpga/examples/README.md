# A7 实机示例

打开 `a7-switch-led.circ`，点击“FPGA 实现”→“1. 端口映射”，选择一个工程目录。
映射 `switchIn` 到“拨码开关 SW0”，`ledOut` 到“单色 LED1”，然后确认。
点击“完成端口映射”后，再点击“2. 生成位流”。LoongSim 自动完成综合、布局布线和位流生成，
完成后点击“3. 下载到 FPGA”。

对应的实体器件是板上 SW0（AC21）与 LED1（H7）。来回拨动 SW0，LED1 应随之亮灭。
实验箱 LED 是低电平点亮，由 LoongSim 的板卡接口自动适配，电路中不需要为此添加非门。
下载到 SRAM，实验箱断电后清除。

`a7-guard.circ` 是门窗报警电路：`warning = (door | window) & ~guard`。
可映射 door/window/guard 到 SW7/SW6/SW5，warning 对应的输出端口到 LED16。
电路中 warning 为 1 时实体灯亮，为 0 时实体灯灭。

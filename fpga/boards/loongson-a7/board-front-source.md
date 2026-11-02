# 板卡底图处理记录

底图来自用户提供的 A7 实验箱照片（附件 `codex-clipboard-7c0cff63-16f9-411d-b328-330585e93404.png`）。
使用内置 imagegen 清除红圈、红色引线、A–K 字母标记和外围细框，并裁去外侧标注留白。
原有绿色矩形遮挡保留。此图用于界面位置参考；电气连接以 `verified-pins.csv` 中核对过的引脚为准。

生成提示词：

> Use case: precise-object-edit. Edit target: supplied photo of a Loongson A7 FPGA development board with red annotations. Remove ALL added bright-red circles/ellipses, red leader lines, and circled red letters A through K. Remove the thin light-green outer annotation frame too. Preserve the actual red LED components! Keep the exact same physical board, same component positions, same counts of LEDs, switches, 8-digit seven-segment displays and 4x4 keypad, same chips, ports, LCD, copper traces, colors, low-resolution photographic texture, lighting and perspective. Preserve the solid green rectangular occlusion over the upper-left-middle PCB; do not invent details behind it. Only inpaint the thin overlay strokes using neighboring texture, do not redesign or beautify the electronics. Crop away the external white annotation margins so the board fills the output with only a very small even white border, landscape rectangle in the board's original proportions (about 1.30:1). No new annotations, no text labels, no numbering, no added arrows, no new watermark. Output a clean reference photo for resource-location mapping in desktop software.

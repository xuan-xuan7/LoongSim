"""Read the supplied A7 pin workbook and check LoongSim's established resource aliases."""
import argparse
import csv
from pathlib import Path
import re

import openpyxl


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('workbook', type=Path)
    parser.add_argument('--write-reference', type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    book = openpyxl.load_workbook(args.workbook, read_only=True, data_only=True)
    signals = {}
    # Only the three populated columns; source sheets have formatting extending to XFD.
    for sheet in book:
        if sheet.title == '总表':
            continue
        for index, (name, pin, _) in enumerate(sheet.iter_rows(min_col=1, max_col=3, values_only=True), 1):
            if isinstance(name, str) and isinstance(pin, str) and re.fullmatch(r'[A-Z]+[0-9]+', pin):
                signals[name] = (pin, sheet.title, f'B{index}')
    aliases = {'clk': 'FPGA_CLK_IN', 'resetn': 'FPGA_RSTn_in'}
    aliases.update({f'switch[{bit}]': f'FPGA_SW{7-bit}' for bit in range(8)})
    aliases.update({f'led[{bit}]': f'FPGA_LED{16-bit}' for bit in range(16)})
    aliases.update({f'num_csn[{bit}]': f'FPGA_NUM_CSN{7-bit}' for bit in range(8)})
    aliases.update({f'num_a_g[{bit}]': f'FPGA_NUM{6-bit}_{"GFEDCBA"[bit]}' for bit in range(7)})
    aliases.update({f'led_rg{group}[{bit}]': f'FPGA_LED{group}{"RG"[bit]}' for group in range(2) for bit in range(2)})
    aliases.update({f'btn_key_{axis}[{bit}]': f'FPGA_KEY_{axis.upper()}{bit+1}' for axis in ('col', 'row') for bit in range(4)})
    aliases.update({f'btn_step[{bit}]': f'FPGA_KEY_STEP{bit}' for bit in range(2)})
    xdc = (repo / 'fpga/boards/loongson-a7/loongson-a7-fbg676-1.xdc').read_text(encoding='utf-8')
    pins = {}
    for pin, braced, plain in re.findall(r'^set_property\s+PACKAGE_PIN\s+(\w+)\s+\[get_ports\s+(?:\{([^}]+)\}|([^\]\s]+))\]', xdc, re.M):
        pins[braced or plain] = pin
    rows = []
    for resource, signal in aliases.items():
        expected, sheet, cell = signals[signal]
        if pins.get(resource) != expected:
            raise SystemExit(f'Pin mismatch: {resource}, XDC={pins.get(resource)}, workbook={expected}')
        rows.append((resource, signal, expected, sheet, cell))
    if args.write_reference:
        with args.write_reference.open('w', newline='', encoding='utf-8') as output:
            writer = csv.writer(output)
            writer.writerow(('resource', 'board_signal', 'package_pin', 'source_sheet', 'source_cell'))
            writer.writerows(rows)
    print(f'Verified {len(rows)} resource pins against the supplied workbook.')
    print('num_data[31:0] has no matching LCD bus definition in this workbook; it is not verified.')


if __name__ == '__main__':
    main()

package com.cburch.logisim.fpga.loongsim;

import com.cburch.logisim.std.io.HexDigit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Board-side display refresh and keypad scanning, leaving the student's circuit HDL intact. */
final class FpgaDisplayMapping {
  final List<FpgaPort> physicalPorts = new ArrayList<>();
  final Map<String, String> physicalMappings = new LinkedHashMap<>();
  private final List<FpgaPort> logicalPorts;
  private final Map<String, FpgaPort> logicalByName = new HashMap<>();
  private final List<String> assignments = new ArrayList<>();
  private final Map<Integer, String[]> patterns = new java.util.TreeMap<>();
  private final Map<Integer, String> hexValues = new java.util.TreeMap<>();
  private final Set<String> names = new HashSet<>();
  private String segments, selects, clock;
  private String keypadColumns, keypadRows, keypadValues;

  FpgaDisplayMapping(List<FpgaPort> logicalPorts, List<FpgaPort> targets,
      Map<String, String> mappings) throws IOException {
    this.logicalPorts = logicalPorts;
    for (FpgaPort port : logicalPorts) {
      names.add(port.getName());
      logicalByName.put(port.getName(), port);
    }
    if (mappings.values().stream().anyMatch(LoongSimBoardCatalog::isKeypadKey)) {
      keypadColumns = addPhysical("loongsim_keypad_columns", FpgaResource.Direction.INOUT, 4, "btn_key_col");
      keypadRows = addPhysical("loongsim_keypad_rows", FpgaResource.Direction.INPUT, 4, "btn_key_row");
      keypadValues = unique("loongsim_keypad_pressed");
    }
    for (FpgaPort target : targets) {
      String resource = mappings.get(target.getName());
      if (LoongSimBoardCatalog.isKeypadKey(resource)) {
        assignments.add("  assign " + reference(target.getBaseName(), target.getLowBit()) + " = "
            + keypadValues + "[" + LoongSimBoardCatalog.keypadIndex(resource) + "];");
        continue;
      }
      if (LoongSimBoardCatalog.isDisplay(resource)) {
        int position = LoongSimBoardCatalog.displayPosition(resource);
        if (LoongSimBoardCatalog.isHexDisplay(resource) && !target.isDecodedHexDisplay()) {
          List<String> bits = new ArrayList<>();
          for (int bit = 3; bit >= 0; bit--) bits.add(reference(target.getBaseName(), target.getLowBit() + bit));
          hexValues.put(position, "{" + String.join(", ", bits) + "}");
        } else {
          String[] bits = patterns.computeIfAbsent(position,
              ignored -> new String[] {"1'b0", "1'b0", "1'b0", "1'b0", "1'b0", "1'b0", "1'b0"});
          int segment = LoongSimBoardCatalog.displaySegment(resource);
          if (segment >= 0) {
            bits[segment] = reference(target.getBaseName(), target.getLowBit());
          } else {
            for (int bit = 0; bit < target.getWidth(); bit++) {
              int named = LoongSimBoardCatalog.segmentForBit(target, bit);
              if (named >= 0 && named < 7) bits[named] = reference(target.getBaseName(), target.getLowBit() + bit);
            }
          }
        }
        continue;
      }
      if (target.getDirection() == FpgaResource.Direction.INOUT) {
        throw new IOException("板卡自动适配暂不支持与电路中的双向端口一起使用");
      }
      for (int bit = 0; bit < target.getWidth(); bit++) {
        String physical = unique("loongsim_io_" + physicalPorts.size());
        String board = target.getWidth() == 1 ? resource : resource + "[" + bit + "]";
        physicalPorts.add(new FpgaPort(physical, target.getDirection(), 1));
        physicalMappings.put(physical, board);
        String logical = reference(target.getBaseName(), target.getLowBit() + bit);
        if (target.getDirection() == FpgaResource.Direction.INPUT) {
          assignments.add("  assign " + logical + " = " + physical + ";");
          if (board.equals("clk")) clock = physical;
        } else {
          assignments.add("  assign " + physical + " = "
              + (LoongSimBoardCatalog.isActiveLowLed(board) ? "~" : "") + logical + ";");
        }
      }
    }
    if (!patterns.isEmpty() || !hexValues.isEmpty()) {
      segments = addPhysical("loongsim_display_segments", FpgaResource.Direction.OUTPUT, 7, "num_a_g");
      selects = addPhysical("loongsim_display_select", FpgaResource.Direction.OUTPUT, 8, "num_csn");
    }
    if (clock == null) {
      clock = addPhysical("loongsim_display_clock", FpgaResource.Direction.INPUT, 1, "clk");
    }
  }

  private String addPhysical(String base, FpgaResource.Direction direction, int width, String resource) {
    String name = unique(base);
    physicalPorts.add(new FpgaPort(name, direction, width));
    for (int bit = 0; bit < width; bit++) {
      physicalMappings.put(width == 1 ? name : name + "[" + bit + "]",
          width == 1 ? resource : resource + "[" + bit + "]");
    }
    return name;
  }

  private String unique(String base) {
    String name = base;
    int suffix = 1;
    while (!names.add(name)) name = base + "_" + suffix++;
    return name;
  }

  private String reference(String name, int bit) {
    return logicalByName.get(name).getWidth() == 1 ? name : name + "[" + bit + "]";
  }

  private String selectPattern(int digit) {
    // Template num_csn[7] is physical CSN0; a low cathode selects that digit.
    return "8'h" + Integer.toHexString(255 ^ (1 << (7 - digit)));
  }

  void write(Path directory, String top, String logicalModule, String header) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add(header + logicalModule);
    lines.add("// Display segments are active high; keypad columns use low/high-impedance scanning.");
    lines.add("module " + top + "(" + physicalPorts.stream().map(FpgaPort::getName)
        .collect(Collectors.joining(", ")) + ");");
    for (FpgaPort port : physicalPorts) {
      lines.add("  " + (port.getDirection() == FpgaResource.Direction.INPUT ? "input "
          : port.getDirection() == FpgaResource.Direction.INOUT ? "inout " : "output ")
          + range(port.getWidth()) + port.getName() + ";");
    }
    for (FpgaPort port : logicalPorts) {
      lines.add("  wire " + range(port.getWidth()) + port.getName() + ";");
    }
    if (keypadValues != null) lines.add("  wire [15:0] " + keypadValues + ";");
    lines.addAll(assignments);
    lines.add("  " + logicalModule + " circuit_instance (");
    for (int index = 0; index < logicalPorts.size(); index++) {
      String name = logicalPorts.get(index).getName();
      lines.add("    ." + name + "(" + name + ")" + (index + 1 < logicalPorts.size() ? "," : ""));
    }
    lines.add("  );");
    Map<Integer, String> displayPatterns = new java.util.TreeMap<>();
    patterns.forEach((position, bits) -> displayPatterns.put(position, "{" + String.join(", ", bits) + "}"));
    if (!hexValues.isEmpty()) {
      // The board orders its segment bits from A (MSB) to G (LSB).
      String decoder = unique("loongsim_decode_hex");
      lines.add("  function [6:0] " + decoder + ";");
      lines.add("    input [3:0] digit;");
      lines.add("    begin");
      lines.add("      case (digit)");
      for (int value = 0; value < 16; value++) {
        int pattern = Integer.reverse(HexDigit.getSegmentPattern(value)) >>> (Integer.SIZE - 7);
        lines.add("        4'h" + Integer.toHexString(value) + ": " + decoder
            + " = 7'h" + Integer.toHexString(pattern) + ";");
      }
      lines.add("        default: " + decoder + " = 7'b0;");
      lines.add("      endcase");
      lines.add("    end");
      lines.add("  endfunction");
      hexValues.forEach((position, input) -> displayPatterns.put(position, decoder + "(" + input + ")"));
    }
    if (segments != null) {
      String divider = unique("loongsim_scan_divider");
      String index = unique("loongsim_scan_index");
      lines.add("  reg [15:0] " + divider + " = 16'd0;");
      lines.add("  reg [2:0] " + index + " = 3'd0;");
      lines.add("  always @(posedge " + clock + ") begin");
      lines.add("    " + divider + " <= " + divider + " + 1'b1;");
      lines.add("    if (&" + divider + ") " + index + " <= " + index + " + 1'b1;");
      lines.add("  end");
      String data = "7'b0";
      String select = "8'hff";
      for (Map.Entry<Integer, String> entry : displayPatterns.entrySet()) {
        data = "(" + index + " == 3'd" + entry.getKey() + ") ? " + entry.getValue() + " : " + data;
        select = "(" + index + " == 3'd" + entry.getKey() + ") ? " + selectPattern(entry.getKey()) + " : " + select;
      }
      lines.add("  assign " + segments + " = " + data + ";");
      lines.add("  assign " + selects + " = " + select + ";");
    }
    String keypadModule = top + "_LoongSimKeypadScanner";
    if (keypadValues != null) {
      lines.add("  " + keypadModule + " " + unique("loongsim_keypad_scanner") + " (");
      lines.add("    .clk(" + clock + "), .columns(" + keypadColumns + "),");
      lines.add("    .rows(" + keypadRows + "), .pressed(" + keypadValues + "));");
    }
    lines.add("endmodule");
    if (keypadValues != null) {
      try (java.io.InputStream source = FpgaDisplayMapping.class.getResourceAsStream("/resources/loongsim/keypad-scanner.v")) {
        if (source == null) throw new IOException("缺少矩阵键盘扫描模块，请检查 LoongSim 安装文件");
        lines.add(new String(source.readAllBytes(), StandardCharsets.UTF_8).replace("LOONGSIM_KEYPAD_MODULE", keypadModule));
      }
    }
    Files.write(directory.resolve("verilog/loongsim-board-interface.v"), lines, StandardCharsets.UTF_8);
  }

  private static String range(int width) { return width > 1 ? "[" + (width - 1) + ":0] " : ""; }
}

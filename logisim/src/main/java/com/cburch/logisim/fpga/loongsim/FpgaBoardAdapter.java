package com.cburch.logisim.fpga.loongsim;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Adapts board electrical levels without changing the student's circuit HDL. */
final class FpgaBoardAdapter {
  private static final String FILE = "verilog/loongsim-board-interface.v";
  private static final String PREFIX = "// LoongSim board interface; circuit module: ";

  private FpgaBoardAdapter() {}

  static void write(Path project, String top, String circuit, List<FpgaPort> ports,
      Map<String, String> mappings) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add(PREFIX + circuit);
    lines.add("// Circuit values stay logical; board LEDs are active low.");
    lines.add("module " + top + "(" + ports.stream().map(FpgaPort::getName)
        .collect(Collectors.joining(", ")) + ");");
    for (FpgaPort port : ports) {
      String direction = port.getDirection() == FpgaResource.Direction.INPUT ? "input"
          : port.getDirection() == FpgaResource.Direction.OUTPUT ? "output" : "inout";
      lines.add("  " + direction + " " + range(port) + port.getName() + ";");
      if (inverted(port, mappings)) {
        lines.add("  wire " + range(port) + "s_board_value_" + port.getName() + ";");
        BigInteger mask = inversionMask(port, mappings);
        String value = "s_board_value_" + port.getName();
        String adapted = mask.equals(BigInteger.ONE.shiftLeft(port.getWidth()).subtract(BigInteger.ONE))
            ? "~" + value : value + " ^ " + port.getWidth() + "'h" + mask.toString(16);
        lines.add("  assign " + port.getName() + " = " + adapted + ";");
      }
    }
    lines.add("  " + circuit + " circuit_instance (");
    for (int i = 0; i < ports.size(); i++) {
      FpgaPort port = ports.get(i);
      String name = port.getName();
      lines.add("    ." + name + "(" + (inverted(port, mappings) ? "s_board_value_" : "") + name
          + ")" + (i + 1 < ports.size() ? "," : ""));
    }
    lines.add("  );");
    lines.add("endmodule");
    Files.write(project.resolve(FILE), lines, StandardCharsets.UTF_8);
  }

  static final class MappingResult {
    final List<FpgaPort> ports;
    final Map<String, String> mappings;
    MappingResult(List<FpgaPort> ports, Map<String, String> mappings) {
      this.ports = ports;
      this.mappings = mappings;
    }
  }

  static MappingResult applyMapping(Path project, String top, List<FpgaPort> ports,
      Map<String, String> mappings, List<FpgaPort> targets) throws IOException {
    String logical = logicalModule(project);
    if (mappings.values().stream().anyMatch(LoongSimBoardCatalog::isVirtualResource)) {
      FpgaDisplayMapping display = new FpgaDisplayMapping(ports, targets, mappings);
      display.write(project, top, logical, PREFIX);
      return new MappingResult(display.physicalPorts, display.physicalMappings);
    }
    Map<String, String> physical = new java.util.LinkedHashMap<>();
    for (FpgaPort target : targets) {
      String resource = mappings.get(target.getName());
      if (target.getName().contains(":")) {
        for (int bit = 0; bit < target.getWidth(); bit++) {
          physical.put(target.bit(bit).getName(), resource + "[" + bit + "]");
        }
      } else physical.put(target.getName(), resource);
    }
    write(project, top, logical, ports, physical);
    return new MappingResult(ports, physical);
  }

  static String logicalModule(Path project) throws IOException {
    Path file = project.resolve(FILE);
    if (!Files.isRegularFile(file)) {
      throw new IOException("此工程缺少板卡电平适配，请重新从电路生成工程后映射");
    }
    String source = Files.readString(file, StandardCharsets.UTF_8);
    Matcher header = Pattern.compile("\\A" + Pattern.quote(PREFIX) + "([A-Za-z_][A-Za-z0-9_]*)\\R")
        .matcher(source);
    if (!header.find()) throw new IOException("板卡适配记录无效，请重新生成工程");
    return header.group(1);
  }

  static boolean inverted(FpgaPort port, Map<String, String> mappings) {
    return inversionMask(port, mappings).signum() != 0;
  }

  private static BigInteger inversionMask(FpgaPort port, Map<String, String> mappings) {
    if (port.getDirection() != FpgaResource.Direction.OUTPUT) return BigInteger.ZERO;
    if (LoongSimBoardCatalog.isActiveLowLed(mappings.get(port.getName()))) {
      return BigInteger.ONE.shiftLeft(port.getWidth()).subtract(BigInteger.ONE);
    }
    BigInteger mask = BigInteger.ZERO;
    if (port.getWidth() > 1) {
      for (int bit = 0; bit < port.getWidth(); bit++) {
        if (LoongSimBoardCatalog.isActiveLowLed(mappings.get(port.getName() + "[" + bit + "]"))) {
          mask = mask.setBit(bit);
        }
      }
    }
    return mask;
  }

  private static String range(FpgaPort port) {
    return port.getWidth() > 1 ? "[" + (port.getWidth() - 1) + ":0] " : "";
  }
}

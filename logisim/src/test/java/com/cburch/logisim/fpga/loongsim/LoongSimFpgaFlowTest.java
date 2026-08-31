package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class LoongSimFpgaFlowTest {
  private static final Path TEMPLATE = Path.of(
      "../fpga/boards/loongson-a7/loongson-a7-fbg676-1.xdc");

  @Test
  public void expandsWildcardConstraintsForSingleBitMappings() throws Exception {
    Path output = Files.createTempFile("loongsim-single-bit-", ".xdc");
    try {
      Set<String> ports = new LinkedHashSet<>(List.of("input0", "output0"));
      Map<String, String> mappings = new LinkedHashMap<>();
      mappings.put("input0", "switch[0]");
      mappings.put("output0", "led[3]");

      Set<String> emitted = LoongSimFpgaFlow.generateCircuitXdc(
          TEMPLATE, output, ports, mappings, "test");
      String xdc = Files.readString(output, StandardCharsets.UTF_8);

      assertTrue(emitted.contains("switch[0]"));
      assertTrue(emitted.contains("led[3]"));
      assertTrue(xdc.contains("[get_ports {input0}]")
          || xdc.contains("[get_ports input0]"));
      assertTrue(xdc.contains("[get_ports {output0}]")
          || xdc.contains("[get_ports output0]"));
      assertFalse(xdc.contains("[get_ports {switch[*]}]"));
      assertFalse(xdc.contains("[get_ports {led[*]}]"));
    } finally {
      Files.deleteIfExists(output);
    }
  }

  @Test
  public void keepsAggregateBusConstraintsOnAggregateMapping() throws Exception {
    Path output = Files.createTempFile("loongsim-bus-", ".xdc");
    try {
      Set<String> ports = new LinkedHashSet<>(List.of("switches", "leds"));
      Map<String, String> mappings = new LinkedHashMap<>();
      mappings.put("switches", "switch");
      mappings.put("leds", "led");

      Set<String> emitted = LoongSimFpgaFlow.generateCircuitXdc(
          TEMPLATE, output, ports, mappings, "test");
      String xdc = Files.readString(output, StandardCharsets.UTF_8);

      assertTrue(emitted.contains("switch"));
      assertTrue(emitted.contains("led"));
      assertTrue(xdc.contains("[get_ports {switches}]")
          || xdc.contains("[get_ports switches]"));
      assertTrue(xdc.contains("[get_ports {leds}]")
          || xdc.contains("[get_ports leds]"));
      assertFalse(xdc.contains("[get_ports {switch[*]}]"));
      assertFalse(xdc.contains("[get_ports {led[*]}]"));
    } finally {
      Files.deleteIfExists(output);
    }
  }

  @Test
  public void allowsDistinctBitsButRejectsAggregateBusReuse() {
    FpgaResource bit0 = LoongSimBoardCatalog.resources().stream()
        .filter(resource -> "led[0]".equals(resource.getId())).findFirst().orElseThrow();
    FpgaResource bit1 = LoongSimBoardCatalog.resources().stream()
        .filter(resource -> "led[1]".equals(resource.getId())).findFirst().orElseThrow();
    FpgaResource bus = LoongSimBoardCatalog.resources().stream()
        .filter(resource -> "led".equals(resource.getId())).findFirst().orElseThrow();

    assertFalse(bit0.conflictsWith("led[1]"));
    assertTrue(bit0.conflictsWith("led[0]"));
    assertTrue(bit1.conflictsWith("led"));
    assertTrue(bus.conflictsWith("led[7]"));
  }

  @Test
  public void onlyDedicatedClockResourceAcceptsClockPort() {
    FpgaPort clock = new FpgaPort("FPGA_GlobalClock", FpgaResource.Direction.INPUT, 1);
    FpgaResource boardClock = LoongSimBoardCatalog.resources().stream()
        .filter(resource -> "clk".equals(resource.getId())).findFirst().orElseThrow();
    FpgaResource switchBit = LoongSimBoardCatalog.resources().stream()
        .filter(resource -> "switch[0]".equals(resource.getId())).findFirst().orElseThrow();

    assertTrue(boardClock.accepts(clock));
    assertFalse(switchBit.accepts(clock));
  }
}

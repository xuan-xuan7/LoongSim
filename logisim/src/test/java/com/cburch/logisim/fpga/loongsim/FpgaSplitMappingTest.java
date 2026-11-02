package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.*;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.LocaleManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FpgaSplitMappingTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  @BeforeClass public static void locale() { LocaleManager.setLocale(Locale.ENGLISH); }

  @Test public void mapsTwoFourBitInputsToEightSwitchesAndOutputsToEightLeds() throws Exception {
    withExport((flow, result, ports, mapping) -> {
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mapping, null);
      String xdc = Files.readString(result.getProjectDirectory().resolve("constraints.xdc"));
      String[] inputPins = {"AC21", "AD24", "AC22", "AC23", "AB6", "W6", "AA7", "Y6"};
      String[] outputPins = {"H7", "D5", "A3", "A5", "A4", "F7", "G8", "H8"};
      for (int bit = 0; bit < 4; bit++) {
        assertPin(xdc, ports.get("输入A").bit(bit).getName(), inputPins[bit]);
        assertPin(xdc, ports.get("输入B").bit(bit).getName(), inputPins[bit + 4]);
        assertPin(xdc, ports.get("结果A").bit(bit).getName(), outputPins[bit]);
        assertPin(xdc, ports.get("结果B").bit(bit).getName(), outputPins[bit + 4]);
        assertTrue(FpgaPortMapping.isComplete(ports.get("输入A"), mapping));
        assertEquals("输入A[" + bit + "]", ports.get("输入A").bit(bit).getDisplayName());
      }
      String adapter = Files.readString(result.getProjectDirectory().resolve("verilog/loongsim-board-interface.v"));
      assertTrue(adapter.contains("assign " + ports.get("结果A").getName() + " = ~s_board_value_"));
      String json = Files.readString(result.getProjectDirectory().resolve("mapping.json"));
      assertTrue(Pattern.compile("\"unmappedCircuitPorts\"\\s*:\\s*\\[\\s*\\]").matcher(json).find());
      assertTrue(json.contains(ports.get("输入A").getName() + "[0]"));
    });
  }

  @Test public void rejectsMissingBitsInvalidIndicesAndMixedMappingModes() throws Exception {
    withExport((flow, result, ports, mapping) -> {
      Map<String, String> missing = new LinkedHashMap<>(mapping);
      missing.remove(ports.get("输入A").bit(3).getName());
      assertFalse(FpgaPortMapping.isComplete(ports.get("输入A"), missing));
      rejects(flow, result, missing, "尚未映射");
      Map<String, String> invalid = new LinkedHashMap<>(mapping);
      invalid.put(ports.get("输入A").getName() + "[4]", "resetn");
      rejects(flow, result, invalid, "无效位号");
      Map<String, String> mixed = new LinkedHashMap<>(mapping);
      mixed.put(ports.get("输入A").getName(), "btn_key_col");
      rejects(flow, result, mixed, "整组/逐位");
      assertFalse(Files.exists(result.getProjectDirectory().resolve("constraints.xdc")));
    });
  }

  @Test public void rejectsResourceReuseWidthAndDirectionMismatch() throws Exception {
    withExport((flow, result, ports, mapping) -> {
      Map<String, String> reused = new LinkedHashMap<>(mapping);
      reused.put(ports.get("输入B").bit(0).getName(), mapping.get(ports.get("输入A").bit(0).getName()));
      rejects(flow, result, reused, "重复使用");
      Map<String, String> wrongWidth = new LinkedHashMap<>(mapping);
      wrongWidth.put(ports.get("输入A").bit(0).getName(), "switch");
      rejects(flow, result, wrongWidth, "有效映射");
      Map<String, String> wrongDirection = new LinkedHashMap<>(mapping);
      wrongDirection.put(ports.get("输入A").bit(0).getName(), "led[0]");
      rejects(flow, result, wrongDirection, "有效映射");
    });
  }

  @Test public void adaptsOnlyTheOutputBitsMappedToActiveLowLeds() throws Exception {
    Path directory = temporary.newFolder().toPath();
    Files.createDirectories(directory.resolve("verilog"));
    FpgaBoardAdapter.write(directory, "board_top", "circuit_top",
        List.of(new FpgaPort("result", FpgaResource.Direction.OUTPUT, 4)), Map.of(
            "result[0]", "led[0]", "result[1]", "num_a_g[0]",
            "result[2]", "led[2]", "result[3]", "num_a_g[1]"));
    String source = Files.readString(directory.resolve("verilog/loongsim-board-interface.v"));
    assertTrue(source.contains("assign result = s_board_value_result ^ 4'h5;"));
    assertTrue(source.contains(".result(s_board_value_result)"));
  }

  @Test public void aSplitClockStillRequiresTheDedicatedClockResource() {
    FpgaPort clock = new FpgaPort("clock", FpgaResource.Direction.INPUT, 4).bit(0);
    FpgaResource resource = LoongSimBoardCatalog.resources().stream()
        .filter(candidate -> candidate.getId().equals("switch[0]")).findFirst().orElseThrow();
    assertFalse(resource.accepts(clock));
  }

  private static void assertPin(String xdc, String port, String pin) {
    assertTrue(port + " -> " + pin, Pattern.compile("set_property\\s+PACKAGE_PIN\\s+"
        + pin + "\\s+\\[get_ports\\s+\\{" + Pattern.quote(port) + "\\}\\]").matcher(xdc).find());
    assertTrue(xdc.contains("IOSTANDARD LVCMOS33 [get_ports {" + port + "}]"));
  }

  private static void rejects(LoongSimFpgaFlow flow, LoongSimFpgaFlow.ExportResult result,
      Map<String, String> mappings, String message) throws Exception {
    try {
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mappings, null);
      fail("Expected invalid mapping rejection");
    } catch (IOException error) { assertTrue(error.getMessage(), error.getMessage().contains(message)); }
  }

  private void withExport(Check check) throws Exception {
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<project source=\"3.3.6\" version=\"1.0\"><lib desc=\"#Wiring\" name=\"0\"/>"
        + "<main name=\"main\"/><circuit name=\"main\">");
    for (int row = 0; row < 2; row++) {
      String suffix = row == 0 ? "A" : "B";
      int y = 100 + 100 * row;
      xml.append("<comp lib=\"0\" name=\"Pin\" loc=\"(100,").append(y).append(")\">"
          + "<a name=\"width\" val=\"4\"/><a name=\"label\" val=\"输入").append(suffix).append("\"/></comp>");
      xml.append("<comp lib=\"0\" name=\"Pin\" loc=\"(200,").append(y).append(")\">"
          + "<a name=\"width\" val=\"4\"/><a name=\"output\" val=\"true\"/>"
          + "<a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"结果").append(suffix).append("\"/></comp>");
      xml.append("<wire from=\"(100,").append(y).append(")\" to=\"(200,").append(y).append(")\"/>");
    }
    xml.append("</circuit></project>");
    LogisimFile file = new Loader(null).openLogisimFile(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)));
    Project project = new Project(file);
    file.getCircuits().forEach(circuit -> circuit.SetProject(project));
    try {
      LoongSimFpgaFlow flow = new LoongSimFpgaFlow();
      LoongSimFpgaFlow.ExportResult result = flow.generateVerilog(project, file.getMainCircuit(), temporary.newFolder().toPath(), null);
      Map<String, FpgaPort> ports = result.getPorts().stream().collect(Collectors.toMap(FpgaPort::getDisplayName, port -> port));
      Map<String, String> mapping = new LinkedHashMap<>();
      for (int bit = 0; bit < 4; bit++) {
        mapping.put(ports.get("输入A").bit(bit).getName(), "switch[" + (7 - bit) + "]");
        mapping.put(ports.get("输入B").bit(bit).getName(), "switch[" + (3 - bit) + "]");
        mapping.put(ports.get("结果A").bit(bit).getName(), "led[" + (15 - bit) + "]");
        mapping.put(ports.get("结果B").bit(bit).getName(), "led[" + (11 - bit) + "]");
      }
      check.run(flow, result, ports, mapping);
    } finally { project.getSimulator().shutDown(); }
  }

  private interface Check {
    void run(LoongSimFpgaFlow flow, LoongSimFpgaFlow.ExportResult result,
        Map<String, FpgaPort> ports, Map<String, String> mappings) throws Exception;
  }
}

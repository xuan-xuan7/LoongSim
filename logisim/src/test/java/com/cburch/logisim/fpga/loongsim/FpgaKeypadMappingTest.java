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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FpgaKeypadMappingTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  @BeforeClass public static void locale() { LocaleManager.setLocale(Locale.ENGLISH); }

  @Test public void offersSixteenKeysInRowMajorOrderInsteadOfRawRowAndColumnWires() {
    List<FpgaResource> keys = LoongSimBoardCatalog.mappingResources().stream()
        .filter(resource -> LoongSimBoardCatalog.areaFor(resource.getId()) == LoongSimBoardCatalog.Area.KEYPAD)
        .collect(java.util.stream.Collectors.toList());
    assertEquals(16, keys.size());
    for (int key = 0; key < 16; key++) {
      assertEquals("keypad[" + key + "]", keys.get(key).getId());
      assertTrue(keys.get(key).getLabel().contains("第" + (key / 4 + 1) + "行第" + (key % 4 + 1) + "列"));
      assertTrue(keys.get(key).accepts(new FpgaPort("start", FpgaResource.Direction.INPUT, 1)));
      assertFalse(keys.get(key).accepts(new FpgaPort("out", FpgaResource.Direction.OUTPUT, 1)));
      assertFalse(keys.get(key).accepts(new FpgaPort("value", FpgaResource.Direction.INPUT, 4)));
      assertFalse(keys.get(key).accepts(new FpgaPort("FPGA_GlobalClock", FpgaResource.Direction.INPUT, 1)));
    }
    assertFalse(LoongSimBoardCatalog.isKeypadKey("keypad[16]"));
    assertFalse(LoongSimBoardCatalog.isKeypadKey("keypad[-1]"));
  }

  @Test public void scannerOwnsTheEightMatrixPinsButDifferentKeysCanBeMappedIndependently() {
    FpgaResource key = resource("keypad[0]");
    assertTrue(key.conflictsWith("keypad[0]"));
    assertFalse(key.conflictsWith("keypad[1]"));
    for (String raw : List.of("btn_key_col", "btn_key_row", "btn_key_col[3]", "btn_key_row[2]")) {
      assertTrue(key.conflictsWith(raw));
      assertTrue(resource(raw).conflictsWith(key.getId()));
    }
    assertFalse(key.conflictsWith("switch[0]"));
    assertFalse(key.conflictsWith("digit[0].a"));
  }

  @Test public void mapsAllSixteenInputBitsToKeysAndUsesOnlyOneMatrixAndClock() throws Exception {
    withExport(wire("keys", "lights", 16, 100), (flow, result) -> {
      Map<String, String> mapping = new LinkedHashMap<>();
      for (int key = 0; key < 16; key++) {
        mapping.put("keys[" + key + "]", "keypad[" + key + "]");
        mapping.put("lights[" + key + "]", "led[" + key + "]");
      }
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mapping, null);
      String xdc = Files.readString(result.getProjectDirectory().resolve("constraints.xdc"));
      assertEquals(25, xdc.lines().filter(line -> line.startsWith("set_property PACKAGE_PIN")).count());
      for (String pin : List.of("V8", "V9", "Y8", "V7", "U7", "W8", "Y7", "AA8", "AC19")) {
        assertTrue(java.util.regex.Pattern.compile("PACKAGE_PIN\\s+" + pin + "\\s").matcher(xdc).find());
      }
      assertFalse(LoongSimFpgaFlow.validateProject(result.getProjectDirectory(), result.getTopModule()).isEmpty());
      mapping.put("keys[15]", "keypad[0]");
      try {
        flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mapping, null);
        fail("A key cannot be assigned twice");
      } catch (IOException expected) { assertTrue(expected.getMessage().contains("重复使用")); }
    });
  }

  @Test public void keypadAndHexDisplayShareOneClockAndCanBeRemappedBackToASwitch() throws Exception {
    String display = "<comp lib=\"0\" name=\"Constant\" loc=\"(100,200)\"><a name=\"width\" val=\"4\"/></comp>"
        + "<comp lib=\"0\" name=\"Pin\" loc=\"(200,200)\"><a name=\"width\" val=\"4\"/><a name=\"output\" val=\"true\"/>"
        + "<a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"number\"/></comp><wire from=\"(100,200)\" to=\"(200,200)\"/>";
    withExport(wire("start", "light", 1, 100) + display, (flow, result) -> {
      Map<String,String> mapping = new LinkedHashMap<>(Map.of("start", "keypad[5]", "light", "led[0]", "number", "digit[0].hex"));
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mapping, null);
      String xdc = Files.readString(result.getProjectDirectory().resolve("constraints.xdc"));
      assertEquals(25, xdc.lines().filter(line -> line.startsWith("set_property PACKAGE_PIN")).count());
      assertEquals(1, xdc.lines().filter(line -> line.startsWith("create_clock")).count());
      assertFalse(LoongSimFpgaFlow.validateProject(result.getProjectDirectory(), result.getTopModule()).isEmpty());
      mapping.put("start", "switch[7]");
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mapping, null);
      xdc = Files.readString(result.getProjectDirectory().resolve("constraints.xdc"));
      assertEquals(18, xdc.lines().filter(line -> line.startsWith("set_property PACKAGE_PIN")).count());
      assertFalse(LoongSimFpgaFlow.validateProject(result.getProjectDirectory(), result.getTopModule()).isEmpty());
    });
  }

  private static FpgaResource resource(String id) {
    return LoongSimBoardCatalog.resources().stream().filter(resource -> resource.getId().equals(id)).findFirst().orElseThrow();
  }

  private static String wire(String input, String output, int width, int y) {
    return "<comp lib=\"0\" name=\"Pin\" loc=\"(100," + y + ")\"><a name=\"width\" val=\"" + width
        + "\"/><a name=\"label\" val=\"" + input + "\"/></comp><comp lib=\"0\" name=\"Pin\" loc=\"(200," + y
        + ")\"><a name=\"width\" val=\"" + width + "\"/><a name=\"output\" val=\"true\"/><a name=\"facing\" val=\"west\"/>"
        + "<a name=\"label\" val=\"" + output + "\"/></comp><wire from=\"(100," + y + ")\" to=\"(200," + y + ")\"/>";
  }

  private void withExport(String body, Check check) throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><project source=\"3.3.6\" version=\"1.0\">"
        + "<lib desc=\"#Wiring\" name=\"0\"/><main name=\"main\"/><circuit name=\"main\">" + body + "</circuit></project>";
    LogisimFile file = new Loader(null).openLogisimFile(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Project project = new Project(file);
    file.getCircuits().forEach(circuit -> circuit.SetProject(project));
    try {
      LoongSimFpgaFlow flow = new LoongSimFpgaFlow();
      check.run(flow, flow.generateVerilog(project, file.getMainCircuit(), temporary.newFolder().toPath(), null));
    } finally { project.getSimulator().shutDown(); }
  }

  private interface Check { void run(LoongSimFpgaFlow flow, LoongSimFpgaFlow.ExportResult result) throws Exception; }
}

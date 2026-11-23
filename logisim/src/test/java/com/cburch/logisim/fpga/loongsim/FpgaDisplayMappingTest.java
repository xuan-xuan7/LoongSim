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
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FpgaDisplayMappingTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  @BeforeClass public static void locale() { LocaleManager.setLocale(Locale.ENGLISH); }

  @Test public void visibleResourcesContainNoAggregateBusesOrManualDigitSelects() {
    List<FpgaResource> choices = LoongSimBoardCatalog.mappingResources();
    assertFalse(choices.stream().anyMatch(resource -> resource.getLabel().contains("总线")));
    assertFalse(choices.stream().anyMatch(resource -> resource.getId().startsWith("num_")));
    assertEquals(8, choices.stream().filter(resource -> LoongSimBoardCatalog.isDisplay(resource.getId()) && resource.getWidth() == 7).count());
    assertEquals(56, choices.stream().filter(resource -> LoongSimBoardCatalog.displaySegment(resource.getId()) >= 0).count());
    assertEquals(8, choices.stream().filter(resource -> LoongSimBoardCatalog.isHexDisplay(resource.getId())).count());
    List<FpgaResource> switches = choices.stream().filter(resource -> resource.getId().startsWith("switch[")).collect(java.util.stream.Collectors.toList());
    assertEquals("拨码开关 SW0", switches.get(0).getLabel());
    assertEquals("拨码开关 SW7", switches.get(7).getLabel());
  }

  @Test public void aSingleSegmentPortNeedsNoClockOrSelectPortInTheStudentsCircuit() throws Exception {
    String body = "<comp lib=\"0\" name=\"Constant\" loc=\"(100,100)\"><a name=\"width\" val=\"7\"/></comp>"
        + "<comp lib=\"0\" name=\"Pin\" loc=\"(200,100)\"><a name=\"width\" val=\"7\"/>"
        + "<a name=\"output\" val=\"true\"/><a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"段选\"/></comp>"
        + "<wire from=\"(100,100)\" to=\"(200,100)\"/>";
    withExport(body, (flow, result) -> {
      FpgaPort port = result.getPorts().get(0);
      assertEquals(7, port.getWidth());
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), Map.of(port.getName(), "digit[2]"), null);
      String xdc = Files.readString(result.getProjectDirectory().resolve("constraints.xdc"));
      assertEquals(16, xdc.lines().filter(line -> line.startsWith("set_property PACKAGE_PIN")).count());
      assertEquals(16, xdc.lines().filter(line -> line.startsWith("set_property IOSTANDARD")).count());
      assertTrue(xdc.contains("PACKAGE_PIN AC19"));
      assertFalse(LoongSimFpgaFlow.validateProject(result.getProjectDirectory(), result.getTopModule()).isEmpty());
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), Map.of(port.getName(), "digit[5]"), null);
      String json = Files.readString(result.getProjectDirectory().resolve("mapping.json"));
      assertTrue(json.contains("\"" + port.getName() + "\": \"digit[5]\""));
      assertFalse(json.contains("\"" + port.getName() + "\": \"digit[2]\""));
    });
  }

  @Test public void twoHexDisplaysUseComponentLabelsAndShareOnePhysicalDisplayInterface() throws Exception {
    withExport(hex("显示甲", 100) + hex("显示乙", 200), (flow, result) -> {
      assertEquals(1, result.getPorts().size());
      List<FpgaPort> parts = result.getPorts().get(0).componentParts();
      assertEquals(2, parts.size());
      assertTrue(parts.stream().allMatch(FpgaPort::isDecodedHexDisplay));
      assertTrue(parts.stream().allMatch(resource("digit[0].hex")::accepts));
      Map<String,String> mappings = new LinkedHashMap<>();
      for (FpgaPort part : parts) {
        mappings.put(part.getName(), part.getDisplayName().equals("显示甲") ? "digit[0].hex" : "digit[7]");
      }
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mappings, null);
      String xdc = Files.readString(result.getProjectDirectory().resolve("constraints.xdc"));
      assertEquals(16, xdc.lines().filter(line -> line.startsWith("set_property PACKAGE_PIN")).count());
      assertTrue(xdc.contains("PACKAGE_PIN AC19"));
      assertTrue(FpgaPortMapping.isComplete(result.getPorts().get(0), mappings));
      String adapter = Files.readString(result.getProjectDirectory().resolve("verilog/loongsim-board-interface.v"));
      assertFalse("The circuit has already decoded both displays", adapter.contains("loongsim_decode_hex"));
      assertTrue(adapter.contains("8'h7f"));
      assertTrue(adapter.contains("8'hfe"));
      assertFalse(LoongSimFpgaFlow.validateProject(result.getProjectDirectory(), result.getTopModule()).isEmpty());
      Map<String,String> duplicate = new LinkedHashMap<>();
      parts.forEach(part -> duplicate.put(part.getName(), "digit[0]"));
      try {
        flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), duplicate, null);
        fail("A physical digit cannot be assigned twice");
      } catch (IOException expected) { assertTrue(expected.getMessage().contains("重复使用")); }
    });
  }

  @Test public void displayAndLedWithinOneBubbleBusCanBeMappedIndependently() throws Exception {
    String led = "<comp lib=\"0\" name=\"Constant\" loc=\"(100,200)\"/>"
        + "<comp lib=\"1\" name=\"LED\" loc=\"(200,200)\"><a name=\"label\" val=\"状态灯\"/></comp>"
        + "<wire from=\"(100,200)\" to=\"(200,200)\"/>";
    withExport(hex("显示结果", 100) + led, (flow, result) -> {
      List<FpgaPort> parts = result.getPorts().get(0).componentParts();
      assertEquals(2, parts.size());
      Map<String,String> mappings = new LinkedHashMap<>();
      for (FpgaPort part : parts) mappings.put(part.getName(), part.hasNamedSegments() ? "digit[3]" : "led[0]");
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mappings, null);
      String xdc = Files.readString(result.getProjectDirectory().resolve("constraints.xdc"));
      assertEquals(17, xdc.lines().filter(line -> line.startsWith("set_property PACKAGE_PIN")).count());
      assertTrue(xdc.contains("PACKAGE_PIN K23"));
      assertFalse(LoongSimFpgaFlow.validateProject(result.getProjectDirectory(), result.getTopModule()).isEmpty());
    });
  }

  @Test public void displayPositionsConflictWithRawSegmentAndSelectWiring() {
    FpgaResource digit = LoongSimBoardCatalog.resources().stream().filter(resource -> resource.getId().equals("digit[0]")).findFirst().orElseThrow();
    assertTrue(digit.conflictsWith("num_a_g[0]"));
    assertTrue(digit.conflictsWith("num_csn"));
    assertFalse(digit.conflictsWith("digit[1]"));
    assertFalse(digit.accepts(new FpgaPort("number", FpgaResource.Direction.OUTPUT, 4)));
    FpgaResource a = resource("digit[0].a"), b = resource("digit[0].b"), hex = resource("digit[0].hex");
    assertTrue(a.accepts(new FpgaPort("a", FpgaResource.Direction.OUTPUT, 1)));
    assertFalse(a.accepts(new FpgaPort("switch", FpgaResource.Direction.INPUT, 1)));
    assertFalse(a.conflictsWith(b.getId()));
    assertTrue(a.conflictsWith(digit.getId()));
    assertTrue(digit.conflictsWith(a.getId()));
    assertTrue(hex.conflictsWith(a.getId()));
    assertTrue(a.conflictsWith(hex.getId()));
    assertFalse(a.conflictsWith("digit[1].a"));
    assertTrue(hex.accepts(new FpgaPort("number", FpgaResource.Direction.OUTPUT, 4)));
    assertFalse(hex.accepts(new FpgaPort("segments", FpgaResource.Direction.OUTPUT, 7)));
  }

  @Test public void sevenIndependentSwitchesCanMapToSevenNamedSegmentsAlongsideHexValues() throws Exception {
    StringBuilder body = new StringBuilder();
    for (int segment = 0; segment < 7; segment++) {
      int y = 100 + segment * 100;
      body.append("<comp lib=\"0\" name=\"Pin\" loc=\"(100,").append(y).append(")\"><a name=\"label\" val=\"in")
          .append(segment).append("\"/></comp><comp lib=\"0\" name=\"Pin\" loc=\"(200,").append(y)
          .append(")\"><a name=\"output\" val=\"true\"/><a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"seg")
          .append(segment).append("\"/></comp><wire from=\"(100,").append(y).append(")\" to=\"(200,").append(y).append(")\"/>");
    }
    body.append("<comp lib=\"0\" name=\"Constant\" loc=\"(100,800)\"><a name=\"width\" val=\"4\"/></comp>")
        .append("<comp lib=\"0\" name=\"Pin\" loc=\"(200,800)\"><a name=\"width\" val=\"4\"/>")
        .append("<a name=\"output\" val=\"true\"/><a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"hexValue\"/></comp>")
        .append("<wire from=\"(100,800)\" to=\"(200,800)\"/>");
    withExport(body.toString(), (flow, result) -> {
      Map<String, String> mapping = new LinkedHashMap<>();
      for (int segment = 0; segment < 7; segment++) {
        mapping.put("in" + segment, "switch[" + (7 - segment) + "]");
        mapping.put("seg" + segment, "digit[2]." + (char) ('a' + segment));
      }
      mapping.put("hexValue", "digit[5].hex");
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mapping, null);
      String xdc = Files.readString(result.getProjectDirectory().resolve("constraints.xdc"));
      assertEquals(23, xdc.lines().filter(line -> line.startsWith("set_property PACKAGE_PIN")).count());
      assertEquals(23, xdc.lines().filter(line -> line.startsWith("set_property IOSTANDARD")).count());
      assertFalse(LoongSimFpgaFlow.validateProject(result.getProjectDirectory(), result.getTopModule()).isEmpty());
      mapping.put("seg1", "digit[2].a");
      try {
        flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mapping, null);
        fail("The same segment cannot be driven by two ports");
      } catch (IOException expected) { assertTrue(expected.getMessage().contains("重复使用")); }
    });
  }

  @Test public void aSevenBitOutputCanSplitIntoSegmentsAndIncompletePhysicalDigitsAreAllowed() throws Exception {
    String body = "<comp lib=\"0\" name=\"Constant\" loc=\"(100,100)\"><a name=\"width\" val=\"7\"/></comp>"
        + "<comp lib=\"0\" name=\"Pin\" loc=\"(200,100)\"><a name=\"width\" val=\"7\"/>"
        + "<a name=\"output\" val=\"true\"/><a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"segments\"/></comp>"
        + "<wire from=\"(100,100)\" to=\"(200,100)\"/>";
    withExport(body, (flow, result) -> {
      Map<String, String> mapping = new LinkedHashMap<>();
      for (int bit = 0; bit < 7; bit++) mapping.put("segments[" + bit + "]", "digit[3]." + (char) ('g' - bit));
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mapping, null);
      assertFalse(LoongSimFpgaFlow.validateProject(result.getProjectDirectory(), result.getTopModule()).isEmpty());
      mapping.put("segments[0]", "digit[4].g");
      flow.completeMapping(result.getProjectDirectory(), result.getTopModule(), result.getPorts(), mapping, null);
      assertFalse(LoongSimFpgaFlow.validateProject(result.getProjectDirectory(), result.getTopModule()).isEmpty());
    });
  }

  private static FpgaResource resource(String id) {
    return LoongSimBoardCatalog.resources().stream().filter(resource -> resource.getId().equals(id)).findFirst().orElseThrow();
  }

  private static String hex(String label, int y) {
    return "<comp lib=\"0\" name=\"Constant\" loc=\"(100," + y + ")\"><a name=\"width\" val=\"4\"/></comp>"
        + "<comp lib=\"1\" name=\"Hex Digit Display\" loc=\"(200," + y + ")\"><a name=\"label\" val=\"" + label + "\"/></comp>"
        + "<wire from=\"(100," + y + ")\" to=\"(200," + y + ")\"/>";
  }

  private void withExport(String body, Check check) throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><project source=\"3.3.6\" version=\"1.0\">"
        + "<lib desc=\"#Wiring\" name=\"0\"/><lib desc=\"#I/O\" name=\"1\"/><main name=\"main\"/>"
        + "<circuit name=\"main\">" + body + "</circuit></project>";
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

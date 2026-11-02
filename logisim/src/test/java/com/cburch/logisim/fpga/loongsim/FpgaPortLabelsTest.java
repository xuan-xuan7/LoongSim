package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.*;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.LocaleManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FpgaPortLabelsTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  @BeforeClass public static void locale() { LocaleManager.setLocale(Locale.ENGLISH); }

  @Test public void chinesePinsKeepDisplayLabelsWhileConstraintsUseHdlNames() throws Exception {
    String circuit = "<comp lib=\"0\" name=\"Pin\" loc=\"(100,100)\">"
        + "<a name=\"label\" val=\"门磁状态\"/></comp>"
        + "<comp lib=\"0\" name=\"Pin\" loc=\"(200,100)\">"
        + "<a name=\"output\" val=\"true\"/><a name=\"facing\" val=\"west\"/>"
        + "<a name=\"label\" val=\"报警输出\"/></comp>"
        + "<wire from=\"(100,100)\" to=\"(200,100)\"/>";
    withExport("<circuit name=\"main\">" + circuit + "</circuit>", (flow, exported, logs) -> {
      Map<String, FpgaPort> byDisplay = exported.getPorts().stream()
          .collect(Collectors.toMap(FpgaPort::getDisplayName, port -> port));
      assertEquals(Set.of("门磁状态", "报警输出"), byDisplay.keySet());
      for (FpgaPort port : exported.getPorts()) {
        assertEquals(CorrectLabel.getCorrectLabel(port.getDisplayName()), port.getName());
        assertTrue(port.getName().matches("[A-Za-z_][A-Za-z0-9_]*"));
        assertFalse(port.toString().contains("_u"));
      }
      assertTrue(logs.stream().anyMatch(line -> line.contains("已识别顶层端口") && line.contains("门磁状态")));
      flow.completeMapping(exported.getProjectDirectory(), exported.getTopModule(), exported.getPorts(), Map.of(
          byDisplay.get("门磁状态").getName(), "switch[0]",
          byDisplay.get("报警输出").getName(), "led[0]"), logs::add);
      String xdc = Files.readString(exported.getProjectDirectory().resolve("constraints.xdc"));
      assertTrue(xdc.contains(byDisplay.get("门磁状态").getName()));
      assertFalse(xdc.contains("门磁状态"));
      String labels = Files.readString(exported.getProjectDirectory().resolve("port-labels.json"));
      assertTrue(labels.contains("\"displayName\": \"门磁状态\""));
      assertTrue(labels.contains("\"hdlName\": \"" + byDisplay.get("门磁状态").getName() + "\""));
      assertTrue(Files.readString(exported.getProjectDirectory().resolve("mapping.json"))
          .contains("\"portLabelFile\": \"port-labels.json\""));
    });
  }

  @Test public void mergedLedBusRetainsEachComponentsChineseLabel() throws Exception {
    String circuit = "<circuit name=\"main\">"
        + "<comp lib=\"0\" name=\"Pin\" loc=\"(100,100)\"><a name=\"label\" val=\"控制开关\"/></comp>"
        + "<comp lib=\"1\" name=\"LED\" loc=\"(200,100)\"><a name=\"label\" val=\"警报灯\"/></comp>"
        + "<comp lib=\"1\" name=\"LED\" loc=\"(200,140)\"><a name=\"label\" val=\"状态灯\"/></comp>"
        + "<wire from=\"(100,100)\" to=\"(200,100)\"/>"
        + "<wire from=\"(100,100)\" to=\"(100,140)\"/>"
        + "<wire from=\"(100,140)\" to=\"(200,140)\"/></circuit>";
    withExport(circuit, (flow, exported, logs) -> {
      FpgaPort led = exported.getPorts().stream()
          .filter(port -> port.getDirection() == FpgaResource.Direction.OUTPUT).findFirst().orElseThrow();
      assertEquals("LOGISIM_OUTPUT_BUBBLES", led.getName());
      assertEquals(2, led.getWidth());
      assertEquals(Set.of("警报灯", "状态灯"), Set.copyOf(led.getBitLabels().values()));
      assertTrue(led.getDisplayName().contains("警报灯"));
      assertTrue(led.getDisplayName().contains("状态灯"));
      assertFalse(led.toString().contains("LOGISIM"));
      assertTrue(Files.readString(exported.getProjectDirectory().resolve("port-labels.json")).contains("警报灯"));
    });
  }

  @Test public void nestedLedUsesOriginalChineseInstanceAndComponentPath() throws Exception {
    String circuits = "<circuit name=\"main\"><comp name=\"child\" loc=\"(200,200)\">"
        + "<a name=\"label\" val=\"报警模块\"/></comp></circuit>"
        + "<circuit name=\"child\"><comp lib=\"0\" name=\"Constant\" loc=\"(100,100)\"/>"
        + "<comp lib=\"1\" name=\"LED\" loc=\"(200,100)\"><a name=\"label\" val=\"状态灯\"/></comp>"
        + "<wire from=\"(100,100)\" to=\"(200,100)\"/></circuit>";
    withExport(circuits, (flow, exported, logs) -> {
      FpgaPort led = exported.getPorts().get(0);
      assertEquals("报警模块 / 状态灯", led.getDisplayName());
      assertEquals(Map.of(0, "报警模块 / 状态灯"), led.getBitLabels());
    });
  }

  @Test public void punctuationInDisplayLabelsIsEscapedInMetadata() throws Exception {
    String circuits = "<circuit name=\"main\"><comp lib=\"0\" name=\"Constant\" loc=\"(100,100)\"/>"
        + "<comp lib=\"1\" name=\"LED\" loc=\"(200,100)\">"
        + "<a name=\"label\" val=\"灯&amp;&lt;&quot;甲&quot;&gt;&#10;状态\"/></comp>"
        + "<wire from=\"(100,100)\" to=\"(200,100)\"/></circuit>";
    withExport(circuits, (flow, exported, logs) -> {
      assertEquals("灯&<\"甲\">\n状态", exported.getPorts().get(0).getDisplayName());
      String json = Files.readString(exported.getProjectDirectory().resolve("port-labels.json"));
      assertTrue(json.contains("灯&<\\\"甲\\\">\\u000a状态"));
    });
  }

  private void withExport(String circuits, Check check) throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><project source=\"3.3.6\" version=\"1.0\">"
        + "<lib desc=\"#Wiring\" name=\"0\"/><lib desc=\"#I/O\" name=\"1\"/><main name=\"main\"/>"
        + circuits + "</project>";
    LogisimFile file = new Loader(null).openLogisimFile(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Project project = new Project(file);
    file.getCircuits().forEach(circuit -> circuit.SetProject(project));
    try {
      Path directory = temporary.newFolder().toPath();
      List<String> logs = new ArrayList<>();
      LoongSimFpgaFlow flow = new LoongSimFpgaFlow();
      LoongSimFpgaFlow.ExportResult exported = flow.generateVerilog(project, file.getMainCircuit(), directory, logs::add);
      check.run(flow, exported, logs);
    } finally { project.getSimulator().shutDown(); }
  }

  private interface Check {
    void run(LoongSimFpgaFlow flow, LoongSimFpgaFlow.ExportResult exported, List<String> logs) throws Exception;
  }
}

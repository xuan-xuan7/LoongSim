package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.LocaleManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FpgaLogisimCompatibilityTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @BeforeClass
  public static void initializeLocale() {
    LocaleManager.setLocale(Locale.ENGLISH);
  }

  @Test
  public void exportsUpdatedPinsAndMapsBoardResourcesInChineseDirectory() throws Exception {
    exportAndMap(false);
  }

  @Test
  public void exportsClockWrapperAndMapsDedicatedClockResource() throws Exception {
    exportAndMap(true);
  }

  private void exportAndMap(boolean clock) throws Exception {
    String input = clock
        ? "<comp lib=\"0\" name=\"Clock\" loc=\"(100,100)\"/>"
        : "<comp lib=\"0\" name=\"Pin\" loc=\"(100,100)\">"
            + "<a name=\"label\" val=\"switchIn\"/></comp>";
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<project source=\"3.3.6\" version=\"1.0\">"
        + "<lib desc=\"#Wiring\" name=\"0\"/><main name=\"main\"/>"
        + "<circuit name=\"main\">" + input
        + "<comp lib=\"0\" name=\"Pin\" loc=\"(200,100)\">"
        + "<a name=\"output\" val=\"true\"/><a name=\"facing\" val=\"west\"/>"
        + "<a name=\"label\" val=\"ledOut\"/></comp>"
        + "<wire from=\"(100,100)\" to=\"(200,100)\"/></circuit></project>";
    LogisimFile file = new Loader(null).openLogisimFile(
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Project project = new Project(file);
    try {
      Path directory = temporary.newFolder("\u4e2d\u6587 FPGA \u5de5\u7a0b").toPath();
      LoongSimFpgaFlow flow = new LoongSimFpgaFlow();
      LoongSimFpgaFlow.ExportResult result = flow.generateVerilog(
          project, file.getMainCircuit(), directory, message -> {});
      assertEquals(clock ? "main_LoongSimTop_LoongSimBoardTop" : "main_LoongSimBoardTop", result.getTopModule());
      assertEquals(result.getPorts().toString(), 2, result.getPorts().size());
      assertFalse(Files.exists(directory.resolve("constraints.xdc")));

      Map<String, String> mappings = new LinkedHashMap<>();
      mappings.put(clock ? "FPGA_GlobalClock" : "switchIn", clock ? "clk" : "switch[0]");
      mappings.put("ledOut", "led[0]");
      for (FpgaPort port : result.getPorts()) {
        assertTrue("Unexpected port: " + port, mappings.containsKey(port.getName()));
        assertEquals(1, port.getWidth());
        assertEquals("ledOut".equals(port.getName())
            ? FpgaResource.Direction.OUTPUT : FpgaResource.Direction.INPUT, port.getDirection());
      }
      flow.completeMapping(directory, result.getTopModule(), result.getPorts(), mappings, message -> {});
      assertEquals(result.getTopModule(), Files.readString(directory.resolve("top-module.txt")).trim());
      String xdc = Files.readString(directory.resolve("constraints.xdc"), StandardCharsets.UTF_8);
      for (String port : mappings.keySet()) assertTrue(xdc.contains(port));
      assertTrue(Files.isRegularFile(directory.resolve("mapping.json")));
      assertFalse(Files.readAllLines(directory.resolve("sources.list")).isEmpty());
      assertEquals("xc7a200tfbg676-1", LoongSimBoardCatalog.DEVICE);
      assertTrue(getClass().getResource("/resources/loongsim/board-front.png") != null);
    } finally {
      project.getSimulator().shutDown();
    }
  }
}

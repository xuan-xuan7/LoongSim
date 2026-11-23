package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.std.memory.RamAppearance;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RamTeachingRegressionTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test
  public void separateBusReadsWithoutOeAndClearOverridesWrites() throws Exception {
    var expected = List.of(0L, 0L, 0L, 2L, 0L, 5L, 0L, 8L, 0L, 11L, 11L, 8L, 5L, 2L,
        8L, 8L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 13L, 0L, 13L);
    for (String name : List.of("ram4", "ram4-falling")) {
      try (WorkflowCircuit fixture = new WorkflowCircuit(name)) {
        Component ram = ramIn(fixture.project.getLogisimFile());
        assertEquals(-1, RamAppearance.getOEIndex(0, ram.getAttributeSet()));
        assertEquals(6, ram.getEnds().size());
        fixture.simulate();
        assertEquals(expected, fixture.expected.stream()
            .map(output -> output.values().iterator().next()).collect(Collectors.toList()));
      }
    }
  }

  @Test
  public void newLayoutSurvivesSaveAndReopen() throws Exception {
    try (WorkflowCircuit fixture = new WorkflowCircuit("ram4")) {
      Component ram = ramIn(fixture.project.getLogisimFile());
      assertEquals(List.of(Location.create(600, 620), Location.create(840, 660),
          Location.create(600, 660), Location.create(600, 700), Location.create(600, 730),
          Location.create(640, 600)), portLocations(ram));
      Component reopened = ramIn(new Loader(null)
          .openLogisimFile(new ByteArrayInputStream(fixture.save())));
      assertEquals(portLocations(ram), portLocations(reopened));
    }
  }

  @Test
  public void existingRamKeepsItsWiredPortsWhenOutputEnableIsRemoved() throws Exception {
    String xml = "<project source=\"3.3.6\" version=\"1.0\">"
        + "<lib desc=\"#Memory\" name=\"0\"/><main name=\"main\"/><circuit name=\"main\">"
        + "<comp lib=\"0\" name=\"RAM\" loc=\"(300,300)\"/>"
        + "<wire from=\"(250,350)\" to=\"(300,350)\"/>"
        + "<wire from=\"(250,360)\" to=\"(300,360)\"/>"
        + "<wire from=\"(250,370)\" to=\"(300,370)\"/></circuit></project>";
    Loader loader = new Loader(null);
    LogisimFile file = loader.openLogisimFile(
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Component ram = ramIn(file);
    assertEquals(List.of(Location.create(300, 310), Location.create(540, 390),
        Location.create(300, 390), Location.create(300, 350), Location.create(300, 370),
        Location.create(340, 300)), portLocations(ram));
    var saved = temporary.newFile("legacy-ram.circ");
    assertTrue(loader.save(file, saved));
    LogisimFile reopened = new Loader(null).openLogisimFile(saved);
    assertEquals(portLocations(ram), portLocations(ramIn(reopened)));
    assertEquals(file.getMainCircuit().getWires(), reopened.getMainCircuit().getWires());
  }

  private static Component ramIn(LogisimFile file) {
    return file.getMainCircuit().getNonWires().stream()
        .filter(component -> component.getFactory() instanceof Ram).findFirst().orElseThrow();
  }

  private static List<Location> portLocations(Component ram) {
    return ram.getEnds().stream().map(end -> end.getLocation()).collect(Collectors.toList());
  }
}

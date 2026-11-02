package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class A7VerifiedPinsTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void emitsAll55ResourcePinsAsVerifiedAgainstTheBoardWorkbook() throws Exception {
    Path board = Path.of("../fpga/boards/loongson-a7");
    List<String> rows = Files.readAllLines(board.resolve("verified-pins.csv"), StandardCharsets.UTF_8);
    assertEquals(56, rows.size());
    Set<String> verifiedResources = rows.stream().skip(1)
        .map(row -> row.split(",")[0]).collect(Collectors.toSet());
    Set<String> offeredResources = LoongSimBoardCatalog.resources().stream()
        .filter(resource -> resource.getWidth() == 1 && !LoongSimBoardCatalog.isVirtualResource(resource.getId()))
        .map(FpgaResource::getId)
        .collect(Collectors.toSet());
    assertEquals("The mapper must offer the verified board resources", verifiedResources, offeredResources);
    Path output = temporary.newFile("constraints.xdc").toPath();
    for (String row : rows.subList(1, rows.size())) {
      String[] cells = row.split(",");
      LoongSimFpgaFlow.generateCircuitXdc(board.resolve("loongson-a7-fbg676-1.xdc"),
          output, Set.of("port0"), Map.of("port0", cells[0]), "main");
      String generated = Files.readString(output, StandardCharsets.UTF_8);
      assertTrue(cells[0] + " -> " + cells[2], java.util.regex.Pattern.compile(
          "set_property\\s+PACKAGE_PIN\\s+" + cells[2] + "\\s+\\[get_ports\\s+\\{?port0\\}?\\]")
          .matcher(generated).find());
    }
  }
}

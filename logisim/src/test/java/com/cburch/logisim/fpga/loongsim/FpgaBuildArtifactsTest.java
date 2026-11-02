package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FpgaBuildArtifactsTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  private Path project() throws Exception {
    Path directory = temporary.newFolder("中文 FPGA 工程").toPath().toAbsolutePath();
    Files.createDirectory(directory.resolve("verilog"));
    Files.writeString(directory.resolve("verilog/main.v"), "module main(input a, output b); assign b=a; endmodule");
    Files.writeString(directory.resolve("sources.list"), "verilog/main.v\n");
    Files.writeString(directory.resolve("top-module.txt"), "main\n");
    Files.writeString(directory.resolve("constraints.xdc"), "set_property PACKAGE_PIN AC21 [get_ports a]\n");
    Files.writeString(directory.resolve("yosys-netlist.json"), "{\"modules\": {\"main\": {}}}");
    FpgaBuildArtifacts.recordSynthesis(directory);
    Files.writeString(directory.resolve("design.bit"), "example bitstream");
    FpgaBuildArtifacts.recordImplementation(directory);
    return directory;
  }

  @Test public void acceptsUnchangedBuildAndRejectsEditedSource() throws Exception {
    Path directory = project();
    FpgaBuildArtifacts.verifyImplementation(directory);
    Files.writeString(directory.resolve("verilog/main.v"), "module main; endmodule");
    rejects(directory);
  }

  @Test public void rejectsChangedPinsAndTamperedBitstream() throws Exception {
    Path directory = project();
    String xdc = Files.readString(directory.resolve("constraints.xdc"));
    Files.writeString(directory.resolve("constraints.xdc"), xdc.replace("AC21", "H7"));
    rejects(directory);
    Files.writeString(directory.resolve("constraints.xdc"), xdc);
    Files.writeString(directory.resolve("design.bit"), "another bitstream");
    rejects(directory);
  }

  @Test public void failedOrRepeatedSynthesisCannotLeaveProgrammableOldOutput() throws Exception {
    Path directory = project();
    FpgaBuildArtifacts.clearSynthesis(directory);
    assertFalse(Files.exists(directory.resolve("design.bit")));
    assertFalse(Files.exists(directory.resolve("yosys-netlist.json")));
    rejects(directory);
  }

  @Test public void rejectsMissingFilesAndSourceListOutsideProject() throws Exception {
    Path directory = project();
    Files.writeString(directory.resolve("sources.list"), "../external.v\n", StandardCharsets.UTF_8);
    rejects(directory);
  }

  @Test public void exportDoesNotDeleteAnExistingUnownedVerilogProject() throws Exception {
    Path directory = temporary.newFolder("existing project").toPath();
    Files.createDirectory(directory.resolve("verilog"));
    Path original = directory.resolve("verilog/student.v");
    Files.writeString(original, "student original source");
    Files.writeString(directory.resolve("README.txt"), "student notes");
    try { LoongSimFpgaFlow.clearGeneratedOutputs(directory); fail("Unowned project was overwritten"); }
    catch (IOException expected) { assertTrue(expected.getMessage().contains("新的工程目录")); }
    assertTrue(Files.readString(original).equals("student original source"));
    assertTrue(Files.readString(directory.resolve("README.txt")).equals("student notes"));
  }

  private static void rejects(Path directory) throws Exception {
    try { FpgaBuildArtifacts.verifyImplementation(directory); fail("Changed build was accepted"); }
    catch (IOException expected) { assertTrue(expected.getMessage().length() > 0); }
  }
}

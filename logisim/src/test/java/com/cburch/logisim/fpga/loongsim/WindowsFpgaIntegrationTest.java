package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

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
import org.junit.Test;

/** Opt-in real tools test: no JTAG access and no device programming. */
public class WindowsFpgaIntegrationTest {
  @Test public void compilesLogisimSwitchToLedIntoA7Bitstream() throws Exception { build(false); }
  @Test public void compilesLogisimClockWrapperIntoA7Bitstream() throws Exception { build(true); }

  @Test public void guardCircuitMatchesAllEightLedStatesBeforeAndAfterSynthesis() throws Exception {
    assumeTrue(FpgaToolchain.isWindows() && "1".equals(System.getenv("LOONGSIM_RUN_FPGA_INTEGRATION")));
    LocaleManager.setLocale(Locale.ENGLISH);
    Path output = Path.of(System.getenv().getOrDefault("LOONGSIM_FPGA_SMOKE_DIRECTORY",
        "../local/windows-fpga-smoke")).toAbsolutePath().normalize().resolve("门窗报警真值表");
    Files.createDirectories(output);
    String xml = Files.readString(Path.of("../fpga/examples/a7-guard.circ"));
    LogisimFile file = new Loader(null).openLogisimFile(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Project project = new Project(file);
    try {
      LoongSimFpgaFlow flow = new LoongSimFpgaFlow();
      LoongSimFpgaFlow.ExportResult exported = flow.generateVerilog(project, file.getMainCircuit(), output, null);
      flow.completeMapping(output, exported.getTopModule(), exported.getPorts(), Map.of(
          "door", "switch[0]", "window", "switch[1]", "guard", "switch[2]", "LOGISIM_OUTPUT_BUBBLES", "led[0]"), null);
      FpgaToolchain tools = FpgaToolchain.discover();
      Files.writeString(output.resolve("truth-table.v"), "module tb;\n"
          + "reg door, window, guard; wire pin; reg expected; integer i;\n"
          + exported.getTopModule() + " dut(.door(door), .window(window), .guard(guard), .LOGISIM_OUTPUT_BUBBLES(pin));\n"
          + "initial begin for (i=0;i<8;i=i+1) begin\n"
          + "{door,window,guard}=i; expected=(door | window) & ~guard; #1;\n"
          + "$display(\"%b%b%b warning=%b pin=%b\",door,window,guard,expected,pin);\n"
          + "if ((~pin)!==expected) $fatal(1,\"Board LED disagrees with circuit\"); end\n"
          + "$display(\"GUARD_TRUTH_TABLE_PASSED\"); $finish; end endmodule\n");
      simulateGuard(tools, output, LoongSimFpgaFlow.readSourceList(output), "export");
      flow.runYosys(output, exported.getTopModule(), null);
      Files.writeString(output.resolve("netlist-sim.ys"), "read_json yosys-netlist.json; write_verilog -noattr netlist-sim.v\n");
      FpgaToolProcess.run(tools, output, java.util.List.of(tools.yosys().toString(), "-s", "netlist-sim.ys"),
          "netlist-sim.log", null);
      simulateGuard(tools, output, java.util.List.of(output.resolve("netlist-sim.v"), tools.yosysData().resolve("xilinx/cells_sim.v")), "synthesized");
      new FpgaImplementation().buildBitstream(output, exported.getTopModule(), null);
      FpgaBuildArtifacts.verifyImplementation(output);
    } finally { project.getSimulator().shutDown(); }
  }

  private static void simulateGuard(FpgaToolchain tools, Path output, java.util.List<Path> sources, String stage)
      throws Exception {
    // Icarus checks the exported logic in development; it is not an application dependency.
    // Synthesis, place/route and bitstream generation still use the runtime under test.
    String simulator = System.getenv("LOONGSIM_FPGA_TEST_SIMULATOR");
    Path bin = simulator == null || simulator.isBlank() ? tools.yosys().getParent()
        : WindowsNativePath.resolve(Path.of(simulator).toAbsolutePath().normalize()).resolve("bin");
    for (String executable : java.util.List.of("iverilog.exe", "vvp.exe")) {
      if (!Files.isRegularFile(bin.resolve(executable))) {
        throw new java.io.IOException("Icarus test tools are missing; set LOONGSIM_FPGA_TEST_SIMULATOR "
            + "to the development oss-cad-suite directory: " + bin.resolve(executable));
      }
    }
    java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
        bin.resolve("iverilog.exe").toString(), "-B", bin.resolve("../lib/ivl").normalize().toString(),
        "-g2012", "-s", "tb", "-o", "guard-" + stage + ".vvp", "truth-table.v"));
    for (Path source : sources) command.add(source.startsWith(output) ? output.relativize(source).toString() : source.toString());
    FpgaToolProcess.run(tools, output, command, "iverilog-" + stage + ".log", null);
    FpgaToolProcess.run(tools, output, java.util.List.of(bin.resolve("vvp.exe").toString(), "guard-" + stage + ".vvp"),
        "truth-table-" + stage + ".log", null);
    assertTrue(Files.readString(output.resolve("logs/truth-table-" + stage + ".log")).contains("GUARD_TRUTH_TABLE_PASSED"));
  }

  private static void build(boolean clock) throws Exception {
    assumeTrue(FpgaToolchain.isWindows() && "1".equals(System.getenv("LOONGSIM_RUN_FPGA_INTEGRATION")));
    LocaleManager.setLocale(Locale.ENGLISH);
    Path base = Path.of(System.getenv().getOrDefault("LOONGSIM_FPGA_SMOKE_DIRECTORY",
        "../local/windows-fpga-smoke")).toAbsolutePath().normalize();
    Path output = base.resolve(clock ? "时钟驱动 LED" : "开关控制 LED");
    Files.createDirectories(output);
    String input = clock ? "<comp lib=\"0\" name=\"Clock\" loc=\"(100,100)\"/>"
        : "<comp lib=\"0\" name=\"Pin\" loc=\"(100,100)\"><a name=\"label\" val=\"switchIn\"/></comp>";
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<project source=\"3.3.6\" version=\"1.0\"><lib desc=\"#Wiring\" name=\"0\"/>"
        + "<main name=\"main\"/><circuit name=\"main\">" + input
        + "<comp lib=\"0\" name=\"Pin\" loc=\"(200,100)\"><a name=\"output\" val=\"true\"/>"
        + "<a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"ledOut\"/></comp>"
        + "<wire from=\"(100,100)\" to=\"(200,100)\"/></circuit></project>";
    Files.writeString(output.resolve("示例.circ"), xml, StandardCharsets.UTF_8);
    LogisimFile file = new Loader(null).openLogisimFile(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Project project = new Project(file);
    try {
      LoongSimFpgaFlow flow = new LoongSimFpgaFlow();
      LoongSimFpgaFlow.ExportResult exported = flow.generateVerilog(project, file.getMainCircuit(), output, null);
      Map<String, String> mapping = new LinkedHashMap<>();
      mapping.put(clock ? "FPGA_GlobalClock" : "switchIn", clock ? "clk" : "switch[0]");
      mapping.put("ledOut", "led[0]");
      flow.completeMapping(output, exported.getTopModule(), exported.getPorts(), mapping, null);
      flow.runYosys(output, exported.getTopModule(), null);
      new FpgaImplementation().buildBitstream(output, exported.getTopModule(), null);
      FpgaBuildArtifacts.verifyImplementation(output);
      assertTrue(Files.size(output.resolve("design.bit")) > 1024);
      assertTrue(Files.size(output.resolve("timing.json")) > 0);
      System.out.println("Native Windows FPGA build passed: " + output.resolve("design.bit"));
    } finally {
      project.getSimulator().shutDown();
    }
  }
}

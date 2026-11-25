package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertTrue;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.std.wiring.Clock;
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
    assumeTrue(FpgaToolchain.supportsNativeImplementation()
        && "1".equals(System.getenv("LOONGSIM_RUN_FPGA_INTEGRATION")));
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
      simulateTruthTable(tools, output, LoongSimFpgaFlow.readSourceList(output), "export", "GUARD_TRUTH_TABLE_PASSED");
      flow.runYosys(output, exported.getTopModule(), null);
      Files.writeString(output.resolve("netlist-sim.ys"), "read_json yosys-netlist.json; write_verilog -noattr netlist-sim.v\n");
      FpgaToolProcess.run(tools, output, java.util.List.of(tools.yosys().toString(), "-s", "netlist-sim.ys"),
          "netlist-sim.log", null);
      simulateTruthTable(tools, output, java.util.List.of(output.resolve("netlist-sim.v"), tools.yosysData().resolve("xilinx/cells_sim.v")), "synthesized", "GUARD_TRUTH_TABLE_PASSED");
      new FpgaImplementation().buildBitstream(output, exported.getTopModule(), null);
      FpgaBuildArtifacts.verifyImplementation(output);
    } finally { project.getSimulator().shutDown(); }
  }

  @Test public void builtInClockAdvancesTeachingCounterAndResetsAsynchronously() throws Exception {
    assumeTrue(FpgaToolchain.supportsNativeImplementation() && "1".equals(System.getenv("LOONGSIM_RUN_FPGA_INTEGRATION")));
    Path output = Path.of(System.getenv().getOrDefault("LOONGSIM_FPGA_SMOKE_DIRECTORY",
        "../local/windows-fpga-smoke")).toAbsolutePath().normalize().resolve("内部时钟驱动教学计数器");
    LocaleManager.setLocale(Locale.ENGLISH);
    try (WorkflowCircuit fixture = new WorkflowCircuit("counter4")) {
      var clockPin = fixture.inputs.get("clk");
      var clock = Clock.FACTORY.createComponent(clockPin.getLocation(), Clock.FACTORY.createAttributeSet());
      var mutation = new CircuitMutation(fixture.circuit);
      mutation.remove(clockPin);
      mutation.add(clock);
      mutation.execute();
      fixture.project.getSimulator().setTickFrequency(25_000_000);
      Files.createDirectories(output);
      fixture.writeExample(output.resolve("内部时钟计数器.circ"));

      LoongSimFpgaFlow flow = new LoongSimFpgaFlow();
      var result = flow.generateVerilog(fixture.project, fixture.circuit, output, null);
      String reset = portNamed(result, "输入2");
      String count = portNamed(result, "输出3");
      String zero = portNamed(result, "输出4");
      Map<String, String> mapping = new LinkedHashMap<>();
      mapping.put("FPGA_GlobalClock", "clk");
      mapping.put(reset, "switch[7]");
      mapping.put(zero, "led[11]");
      for (int bit = 0; bit < 4; bit++) mapping.put(count + "[" + bit + "]", "led[" + (15 - bit) + "]");
      flow.completeMapping(output, result.getTopModule(), result.getPorts(), mapping, null);

      Files.writeString(output.resolve("truth-table.v"), "module tb;\n"
          + "reg clk=0, reset=0; wire [3:0] leds; wire zero; integer i, count, last, transitions=0;\n"
          + result.getTopModule() + " dut(.FPGA_GlobalClock(clk), ." + reset + "(reset), ."
          + count + "(leds), ." + zero + "(zero));\n"
          + "initial begin #5; last=0;\n"
          + "for(i=0; i<96; i=i+1) begin clk=1; #5; count=(~leds) & 15;\n"
          + "if(count != last) begin\n"
          + "  if(count != ((last==5) ? 0 : last+1)) $fatal(1,\"clock tick lost or duplicated\");\n"
          + "  last=count; transitions=transitions+1; end\n"
          + "if(zero !== (count!=0)) $fatal(1,\"incorrect Zero signal\"); clk=0; #5; end\n"
          + "if(transitions < 10 || transitions > 13) $fatal(1,\"unexpected tick rate: %d\",transitions);\n"
          + "reset=1; #5; if(leds !== 4'hf || zero !== 1'b0) $fatal(1,\"async reset did not clear\");\n"
          + "$display(\"COUNTER_BUILT_IN_CLOCK_PASSED\"); $finish; end endmodule\n");
      FpgaToolchain tools = FpgaToolchain.discover();
      String passed = "COUNTER_BUILT_IN_CLOCK_PASSED";
      simulateTruthTable(tools, output, LoongSimFpgaFlow.readSourceList(output), "export", passed);
      flow.runYosys(output, result.getTopModule(), null);
      Files.writeString(output.resolve("netlist-sim.ys"), "read_json yosys-netlist.json; write_verilog -noattr netlist-sim.v\n");
      FpgaToolProcess.run(tools, output, java.util.List.of(tools.yosys().toString(), "-s", "netlist-sim.ys"),
          "netlist-sim.log", null);
      simulateTruthTable(tools, output, java.util.List.of(output.resolve("netlist-sim.v"),
          tools.yosysData().resolve("xilinx/cells_sim.v")), "synthesized", passed);
      new FpgaImplementation().buildBitstream(output, result.getTopModule(), null);
      FpgaBuildArtifacts.verifyImplementation(output);
    }
  }

  private static String portNamed(LoongSimFpgaFlow.ExportResult result, String displayName) {
    return result.getPorts().stream().filter(port -> displayName.equals(port.getDisplayName()))
        .findFirst().orElseThrow().getName();
  }

  private static void simulateTruthTable(FpgaToolchain tools, Path output, java.util.List<Path> sources,
      String stage, String successMarker) throws Exception {
    // Icarus checks the exported logic in development; it is not an application dependency.
    // Synthesis, place/route and bitstream generation still use the runtime under test.
    String simulator = System.getenv("LOONGSIM_FPGA_TEST_SIMULATOR");
    boolean windows = FpgaToolchain.isWindows();
    Path bin = simulator == null || simulator.isBlank() ? (windows ? tools.yosys().getParent() : Path.of("/usr/bin"))
        : WindowsNativePath.resolve(Path.of(simulator).toAbsolutePath().normalize()).resolve("bin");
    String extension = windows ? ".exe" : "";
    for (String executable : java.util.List.of("iverilog" + extension, "vvp" + extension)) {
      if (!Files.isRegularFile(bin.resolve(executable))) {
        throw new java.io.IOException("Icarus test tools are missing; set LOONGSIM_FPGA_TEST_SIMULATOR "
            + "to the development oss-cad-suite directory: " + bin.resolve(executable));
      }
    }
    java.util.List<String> command = new java.util.ArrayList<>();
    command.add(bin.resolve("iverilog" + extension).toString());
    if (windows) command.addAll(java.util.List.of("-B", bin.resolve("../lib/ivl").normalize().toString()));
    command.addAll(java.util.List.of("-g2012", "-s", "tb", "-o", "truth-table-" + stage + ".vvp", "truth-table.v"));
    for (Path source : sources) command.add(source.startsWith(output) ? output.relativize(source).toString() : source.toString());
    FpgaToolProcess.run(tools, output, command, "iverilog-" + stage + ".log", null);
    FpgaToolProcess.run(tools, output, java.util.List.of(bin.resolve("vvp" + extension).toString(), "truth-table-" + stage + ".vvp"),
        "truth-table-" + stage + ".log", null);
    assertTrue(Files.readString(output.resolve("logs/truth-table-" + stage + ".log")).contains(successMarker));
  }

  private static void build(boolean clock) throws Exception {
    assumeTrue(FpgaToolchain.supportsNativeImplementation()
        && "1".equals(System.getenv("LOONGSIM_RUN_FPGA_INTEGRATION")));
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
      System.out.println("Native FPGA build passed: " + output.resolve("design.bit"));
    } finally {
      project.getSimulator().shutDown();
    }
  }
}

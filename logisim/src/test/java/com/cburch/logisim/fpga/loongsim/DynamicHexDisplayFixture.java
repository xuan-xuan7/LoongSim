package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A common physical input drives both hex-display workflows. */
public final class DynamicHexDisplayFixture {
  private static final String CIRCUIT = String.join("\n",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
      "<project source=\"3.3.6\" version=\"1.0\">",
      "  <lib desc=\"#Wiring\" name=\"0\"/><lib desc=\"#I/O\" name=\"1\"/>",
      "  <main name=\"main\"/><circuit name=\"main\">",
      "    <comp lib=\"0\" name=\"Pin\" loc=\"(100,100)\">",
      "      <a name=\"width\" val=\"4\"/><a name=\"label\" val=\"拨码输入\"/>",
      "    </comp>",
      "    <comp lib=\"0\" name=\"Pin\" loc=\"(300,100)\">",
      "      <a name=\"width\" val=\"4\"/><a name=\"output\" val=\"true\"/>",
      "      <a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"原始数值\"/>",
      "    </comp>",
      "    <comp lib=\"1\" name=\"Hex Digit Display\" loc=\"(300,240)\">",
      "      <a name=\"label\" val=\"十六进制组件\"/>",
      "    </comp>",
      "    <wire from=\"(100,100)\" to=\"(180,100)\"/>",
      "    <wire from=\"(180,100)\" to=\"(300,100)\"/>",
      "    <wire from=\"(180,100)\" to=\"(180,240)\"/>",
      "    <wire from=\"(180,240)\" to=\"(300,240)\"/>",
      "  </circuit>",
      "</project>");

  public static void main(String[] args) throws Exception {
    Path output = Path.of(args[0]).toAbsolutePath().normalize();
    Files.createDirectories(output);
    Files.writeString(output.resolve("双路十六进制.circ"), CIRCUIT);
    LogisimFile file = new Loader(null).openLogisimFile(
        new ByteArrayInputStream(CIRCUIT.getBytes(StandardCharsets.UTF_8)));
    Project project = new Project(file);
    file.getCircuits().forEach(circuit -> circuit.SetProject(project));
    try {
      LoongSimFpgaFlow flow = new LoongSimFpgaFlow();
      var exported = flow.generateVerilog(project, file.getMainCircuit(), output, null);
      List<FpgaPort> ports = new ArrayList<>();
      for (FpgaPort port : exported.getPorts()) ports.addAll(port.componentParts());
      FpgaPort switches = port(ports, "拨码输入");
      FpgaPort number = port(ports, "原始数值");
      FpgaPort display = port(ports, "十六进制组件");
      assertEquals(4, switches.getWidth());
      assertEquals(4, number.getWidth());
      assertEquals("4 位数值（已译码为段信号）", display.getWidthLabel());
      assertTrue(display.isDecodedHexDisplay());

      Map<String, String> mappings = new LinkedHashMap<>();
      for (int bit = 0; bit < 4; bit++) {
        mappings.put(switches.bit(bit).getName(), "switch[" + (7 - bit) + "]");
      }
      mappings.put(number.getName(), "digit[0].hex");
      mappings.put(display.getName(), "digit[7].hex");
      flow.completeMapping(output, exported.getTopModule(), exported.getPorts(), mappings, null);

      String xdc = Files.readString(output.resolve("constraints.xdc"));
      String[] pins = {"AC21", "AD24", "AC22", "AC23"};
      List<String> connections = new ArrayList<>();
      connections.add(connection(xdc, "AC19", "clock"));
      connections.add(connection(xdc, "C3", "segments"));
      connections.add(connection(xdc, "D3", "selects"));
      for (int bit = 0; bit < pins.length; bit++) {
        connections.add(connection(xdc, pins[bit], "switches[" + bit + "]"));
      }
      Files.writeString(output.resolve("hex-display-tb.v"),
          bench(exported.getTopModule(), connections));

      FpgaToolchain tools = FpgaToolchain.discover();
      simulate(tools, output, LoongSimFpgaFlow.readSourceList(output), "export");
      flow.runYosys(output, exported.getTopModule(), null);
      Files.writeString(output.resolve("hex-netlist.ys"),
          "read_json yosys-netlist.json; write_verilog -noattr hex-netlist.v\n");
      FpgaToolProcess.run(tools, output, List.of(tools.yosys().toString(), "-s", "hex-netlist.ys"),
          "hex-netlist.log", null);
      simulate(tools, output, List.of(output.resolve("hex-netlist.v"),
          tools.yosysData().resolve("xilinx/cells_sim.v")), "synthesized");
      new FpgaImplementation().buildBitstream(output, exported.getTopModule(), null);
      FpgaBuildArtifacts.verifyImplementation(output);
    } finally {
      project.getSimulator().shutDown();
    }
  }

  private static FpgaPort port(List<FpgaPort> ports, String label) {
    return ports.stream().filter(candidate -> candidate.getDisplayName().equals(label)).findFirst()
        .orElseThrow(() -> new AssertionError("Mapping lost the circuit label " + label));
  }

  private static String connection(String xdc, String pin, String signal) {
    return "." + SegmentHardwareFixture.pinPort(xdc, pin) + "(" + signal + ")";
  }

  private static String bench(String module, List<String> connections) {
    return "module tb;\n"
        + "reg clock=0; reg [3:0] switches=0;\n"
        + "wire [6:0] segments; wire [7:0] selects;\n"
        + "reg [6:0] expected [0:15]; integer value, slot, left, right;\n"
        + module + " dut(" + String.join(",", connections) + ");\n"
        // A is bit 6 and G is bit 0 in the board's segment bus.
        + "initial begin\n"
        + "expected[0]=7'h7e; expected[1]=7'h30; expected[2]=7'h6d; expected[3]=7'h79;\n"
        + "expected[4]=7'h33; expected[5]=7'h5b; expected[6]=7'h5f; expected[7]=7'h70;\n"
        + "expected[8]=7'h7f; expected[9]=7'h7b; expected[10]=7'h77; expected[11]=7'h1f;\n"
        + "expected[12]=7'h4e; expected[13]=7'h3d; expected[14]=7'h4f; expected[15]=7'h47;\n"
        + "force dut.loongsim_scan_divider=16'hffff;\n"
        + "for(value=0;value<16;value=value+1) begin\n"
        + "  switches=value; left=0; right=0;\n"
        + "  for(slot=0;slot<8;slot=slot+1) begin\n"
        + "    #10; clock=1; #10;\n"
        + "    case(selects)\n"
        + "      8'h7f: begin left=left+1; if(segments !== expected[value])\n"
        + "        $fatal(1,\"raw digit value=%h got=%h expected=%h\",switches,segments,expected[value]); end\n"
        + "      8'hfe: begin right=right+1; if(segments !== expected[value])\n"
        + "        $fatal(1,\"display component value=%h got=%h expected=%h\",switches,segments,expected[value]); end\n"
        + "      8'hff: if(segments !== 7'b0) $fatal(1,\"unused digits must be blank\");\n"
        + "      default: $fatal(1,\"unexpected digit select %h\",selects);\n"
        + "    endcase\n"
        + "    clock=0;\n"
        + "  end\n"
        + "  if(left != 1 || right != 1) $fatal(1,\"each digit must be scanned once, %d, %d\",left,right);\n"
        + "end\n"
        + "$display(\"ALL_16_VALUES_ON_BOTH_MAPPED_DISPLAYS_PASSED\"); $finish; end\n"
        + "endmodule\n";
  }

  private static void simulate(FpgaToolchain tools, Path output, List<Path> sources, String stage)
      throws Exception {
    Path bin = NativeTestTools.simulatorBin(tools);
    String image = "hex-" + stage + ".vvp";
    List<String> command = NativeTestTools.iverilogCommand(bin, image, "hex-display-tb.v");
    for (Path source : sources) {
      command.add(source.startsWith(output) ? output.relativize(source).toString() : source.toString());
    }
    FpgaToolProcess.run(tools, output, command, "iverilog-hex-" + stage + ".log", null);
    String log = "hex-" + stage + ".log";
    FpgaToolProcess.run(tools, output, NativeTestTools.vvpCommand(bin, image), log, null);
    assertTrue(Files.readString(output.resolve("logs").resolve(log))
        .contains("ALL_16_VALUES_ON_BOTH_MAPPED_DISPLAYS_PASSED"));
  }
}

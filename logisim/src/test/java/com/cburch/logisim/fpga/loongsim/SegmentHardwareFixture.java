package com.cburch.logisim.fpga.loongsim;

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
import java.util.regex.Pattern;

/** Real Logisim export, native Verilog truth tables and XC7A200T implementation; never programs JTAG. */
final class SegmentHardwareFixture {
  static String pinPort(String xdc, String pin) {
    var matcher=Pattern.compile("set_property\\s+PACKAGE_PIN\\s+"+pin+"\\s+\\[get_ports\\s+\\{?([A-Za-z_][A-Za-z0-9_]*)(?:\\[\\d+\\])?\\}?\\]").matcher(xdc);
    if(!matcher.find()) throw new IllegalStateException("No pin "+pin);
    return matcher.group(1);
  }
  public static void main(String[] args) throws Exception {
    Path directory=Path.of(args[0]).toAbsolutePath().normalize(); Files.createDirectories(directory);
    StringBuilder body=new StringBuilder();
    for(int segment=0;segment<7;segment++) {
      int y=70+segment*40;
      body.append("<comp lib=\"0\" name=\"Pin\" loc=\"(100,").append(y).append(")\"><a name=\"label\" val=\"in")
          .append(segment).append("\"/></comp><comp lib=\"0\" name=\"Pin\" loc=\"(300,").append(y)
          .append(")\"><a name=\"output\" val=\"true\"/><a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"seg")
          .append(segment).append("\"/></comp><wire from=\"(100,").append(y).append(")\" to=\"(300,").append(y).append(")\"/>");
    }
    body.append("<comp lib=\"0\" name=\"Constant\" loc=\"(100,350)\"><a name=\"width\" val=\"4\"/><a name=\"value\" val=\"0xa\"/></comp>")
        .append("<comp lib=\"0\" name=\"Pin\" loc=\"(300,350)\"><a name=\"width\" val=\"4\"/><a name=\"output\" val=\"true\"/>")
        .append("<a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"hexValue\"/></comp><wire from=\"(100,350)\" to=\"(300,350)\"/>")
        .append("<comp lib=\"0\" name=\"Constant\" loc=\"(100,420)\"><a name=\"width\" val=\"4\"/><a name=\"value\" val=\"0x2\"/></comp>")
        .append("<comp lib=\"1\" name=\"Hex Digit Display\" loc=\"(300,420)\"><a name=\"label\" val=\"十六进制组件\"/></comp>")
        .append("<wire from=\"(100,420)\" to=\"(300,420)\"/>");
    String xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?><project source=\"3.3.6\" version=\"1.0\"><lib desc=\"#Wiring\" name=\"0\"/>"
        +"<lib desc=\"#I/O\" name=\"1\"/><main name=\"main\"/><circuit name=\"main\">"+body+"</circuit></project>";
    Files.writeString(directory.resolve("逐段映射与十六进制.circ"),xml);
    LogisimFile file=new Loader(null).openLogisimFile(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Project project=new Project(file); file.getCircuits().forEach(circuit->circuit.SetProject(project));
    try {
      LoongSimFpgaFlow flow=new LoongSimFpgaFlow();
      Path output=directory.resolve("export");
      var exported=flow.generateVerilog(project,file.getMainCircuit(),output,null);
      Map<String,String> mapping=new LinkedHashMap<>();
      for(int segment=0;segment<7;segment++) {
        mapping.put("in"+segment,"switch["+(7-segment)+"]");
        mapping.put("seg"+segment,"digit[2]."+(char)('a'+segment));
      }
      mapping.put("hexValue","digit[5].hex");
      for (FpgaPort port : exported.getPorts()) {
        if (port.isDecodedHexDisplay()) mapping.put(port.getName(), "digit[7].hex");
      }
      flow.completeMapping(output,exported.getTopModule(),exported.getPorts(),mapping,null);
      String xdc=Files.readString(output.resolve("constraints.xdc"));
      String[] swPins={"AC21","AD24","AC22","AC23","AB6","W6","AA7"};
      List<String> connections=new ArrayList<>();
      connections.add("."+pinPort(xdc,"AC19")+"(clk)");
      connections.add("."+pinPort(xdc,"C3")+"(segments)");
      connections.add("."+pinPort(xdc,"D3")+"(selects)");
      for(int segment=0;segment<7;segment++) connections.add("."+pinPort(xdc,swPins[segment])+"(switches["+(6-segment)+"])");
      String tb="module tb; reg clk=0; reg[6:0] switches=0; wire[6:0] segments; wire[7:0] selects; integer v,slot; reg[6:0] expected; "
          +exported.getTopModule()+" dut("+String.join(",",connections)+"); "
          +"function[6:0] glyph(input[3:0] value); begin case(value) "
          +"0:glyph=7'h7e; 1:glyph=7'h30; 2:glyph=7'h6d; 3:glyph=7'h79; 4:glyph=7'h33; 5:glyph=7'h5b; 6:glyph=7'h5f; 7:glyph=7'h70; "
          +"8:glyph=7'h7f; 9:glyph=7'h7b; 10:glyph=7'h77; 11:glyph=7'h1f; 12:glyph=7'h4e; 13:glyph=7'h3d; 14:glyph=7'h4f; 15:glyph=7'h47; "
          +"endcase end endfunction "
          +"initial begin force dut.loongsim_scan_divider=16'hffff; "
          +"for(v=0;v<128;v=v+1) begin switches=v; force dut.hexValue=v; "
          +"for(slot=0;slot<8;slot=slot+1) begin #1; clk=1; #1; case(selects) "
          +"8'hdf: if(segments !== switches) $fatal(1,\"segment mismatch switches=%h segments=%h\",switches,segments); "
          +"8'hfb: if(segments !== glyph(v[3:0])) $fatal(1,\"hex mismatch value=%h segments=%h\",v[3:0],segments); "
          +"8'hfe: if(segments !== 7'h6d) $fatal(1,\"existing hex component changed\"); "
          +"8'hff: if(segments !== 7'b0) $fatal(1,\"unused display not blank\"); "
          +"default: $fatal(1,\"invalid display selection %h\",selects); endcase clk=0; end end "
          +"release dut.loongsim_scan_divider; release dut.hexValue; $display(\"ALL_128_SEGMENT_PATTERNS_AND_16_HEX_DIGITS_PASSED\"); $finish; end endmodule";
      Files.writeString(output.resolve("modes-tb.v"),tb);
      FpgaToolchain tools=FpgaToolchain.discover(); Path bin=NativeTestTools.simulatorBin(tools);
      List<String> command=NativeTestTools.iverilogCommand(bin,"modes.vvp","modes-tb.v");
      command.addAll(Files.readAllLines(output.resolve("sources.list")));
      FpgaToolProcess.run(tools,output,command,"iverilog-modes.log",System.out::println);
      FpgaToolProcess.run(tools,output,NativeTestTools.vvpCommand(bin,"modes.vvp"),"modes-truth.log",System.out::println);
      // A second mapping leaves g unconnected in digit 2, and uses only g in digit 3.
      mapping.put("seg6", "digit[3].g");
      flow.completeMapping(output,exported.getTopModule(),exported.getPorts(),mapping,null);
      tb=tb.replace("segments !== switches", "segments !== (switches & 7'h7e)")
          .replace("8'hfb:", "8'hef: if(segments !== {6'b0,switches[0]}) $fatal(1,\"unconnected segments must stay off\"); 8'hfb:")
          .replace("ALL_128_SEGMENT_PATTERNS_AND_16_HEX_DIGITS_PASSED", "PARTIAL_DIGITS_KEEP_UNCONNECTED_SEGMENTS_OFF_PASSED");
      Files.writeString(output.resolve("modes-tb.v"),tb);
      FpgaToolProcess.run(tools,output,command,"iverilog-partial.log",System.out::println);
      FpgaToolProcess.run(tools,output,NativeTestTools.vvpCommand(bin,"modes.vvp"),"partial-truth.log",System.out::println);
      flow.runYosys(output,exported.getTopModule(),null); System.out.println("SEGMENT_MODES_SYNTHESIS_PASSED");
      new FpgaImplementation().buildBitstream(output,exported.getTopModule(),null);
      FpgaBuildArtifacts.verifyImplementation(output); System.out.println("SEGMENT_MODES_BITSTREAM_PASSED "+Files.size(output.resolve("design.bit")));
    } finally { project.getSimulator().shutDown(); }
  }
}

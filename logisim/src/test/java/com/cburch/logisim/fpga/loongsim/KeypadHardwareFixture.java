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

/** Real Logisim export and a resistor-strength/transistor-switch matrix model; no JTAG use. */
final class KeypadHardwareFixture {
  private static String pinPort(String xdc, String pin) {
    var matcher=Pattern.compile("set_property\\s+PACKAGE_PIN\\s+"+pin+"\\s+\\[get_ports\\s+\\{?([A-Za-z_][A-Za-z0-9_]*)(?:\\[\\d+\\])?\\}?\\]").matcher(xdc);
    if(!matcher.find()) throw new IllegalStateException("No pin "+pin);
    return matcher.group(1);
  }
  public static void main(String[] args) throws Exception {
    Path directory=Path.of(args[0]).toAbsolutePath().normalize(); Files.createDirectories(directory);
    StringBuilder body=new StringBuilder();
    for(int key=0;key<16;key++) {
      int y=70+key*40;
      body.append("<comp lib=\"0\" name=\"Pin\" loc=\"(100,").append(y).append(")\"><a name=\"label\" val=\"按键")
          .append(key+1).append("\"/></comp><comp lib=\"0\" name=\"Pin\" loc=\"(300,").append(y)
          .append(")\"><a name=\"output\" val=\"true\"/><a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"指示灯")
          .append(key+1).append("\"/></comp><wire from=\"(100,").append(y).append(")\" to=\"(300,").append(y).append(")\"/>");
    }
    body.append("<comp lib=\"0\" name=\"Constant\" loc=\"(450,200)\"><a name=\"width\" val=\"4\"/><a name=\"value\" val=\"0xa\"/></comp>")
        .append("<comp lib=\"0\" name=\"Pin\" loc=\"(600,200)\"><a name=\"width\" val=\"4\"/><a name=\"output\" val=\"true\"/>")
        .append("<a name=\"facing\" val=\"west\"/><a name=\"label\" val=\"数字\"/></comp><wire from=\"(450,200)\" to=\"(600,200)\"/>");
    String xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?><project source=\"3.3.6\" version=\"1.0\"><lib desc=\"#Wiring\" name=\"0\"/>"
        +"<main name=\"main\"/><circuit name=\"main\">"+body+"</circuit></project>";
    Files.writeString(directory.resolve("矩阵键盘按键映射示例.circ"),xml);
    LogisimFile file=new Loader(null).openLogisimFile(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Project project=new Project(file); file.getCircuits().forEach(circuit->circuit.SetProject(project));
    try {
      LoongSimFpgaFlow flow=new LoongSimFpgaFlow(); Path output=directory.resolve("export");
      var exported=flow.generateVerilog(project,file.getMainCircuit(),output,null);
      Map<String,String> mapping=new LinkedHashMap<>();
      for(FpgaPort port:exported.getPorts()) {
        String label=port.getDisplayName();
        if(label.startsWith("按键")) mapping.put(port.getName(),"keypad["+(Integer.parseInt(label.substring(2))-1)+"]");
        else if(label.startsWith("指示灯")) mapping.put(port.getName(),"led["+(Integer.parseInt(label.substring(3))-1)+"]");
        else if(label.equals("数字")) mapping.put(port.getName(),"digit[4].hex");
        else throw new IllegalStateException("Unexpected port "+label);
      }
      flow.completeMapping(output,exported.getTopModule(),exported.getPorts(),mapping,null);
      String xdc=Files.readString(output.resolve("constraints.xdc"));
      Map<String,String> pins=new LinkedHashMap<>();
      for(String line:Files.readAllLines(Path.of("../fpga/boards/loongson-a7/verified-pins.csv"))) {
        String[] cells=line.split(","); if(cells.length>2) pins.put(cells[0],cells[2]);
      }
      List<String> connections=new ArrayList<>();
      connections.add("."+pinPort(xdc,"AC19")+"(clk)");
      connections.add("."+pinPort(xdc,"C3")+"(segments)");
      connections.add("."+pinPort(xdc,"D3")+"(selects)");
      connections.add("."+pinPort(xdc,"V8")+"(columns)");
      connections.add("."+pinPort(xdc,"U7")+"(rows)");
      for(int key=0;key<16;key++) connections.add("."+pinPort(xdc,pins.get("led["+key+"]"))+"(leds["+key+"])");
      String tb="module tb; reg clk=0; always #5 clk=~clk; reg[15:0] contacts=0; wire[15:0] leds; tri[3:0] columns,rows; wire[6:0] segments; wire[7:0] selects; integer k,j; "
          +exported.getTopModule()+" dut("+String.join(",",connections)+"); "
          +"defparam dut.loongsim_keypad_scanner.COLUMN_TICKS=16; defparam dut.loongsim_keypad_scanner.DEBOUNCE_FRAMES=3; "
          +"assign (weak1,weak0) columns=4'b0000; genvar r,c; generate for(r=0;r<4;r=r+1) begin: physical_row pullup(rows[r]); "
          +"for(c=0;c<4;c=c+1) begin: physical_column tranif1 contact(columns[c],rows[r],contacts[r*4+c]); end end endgenerate "
          +"task settle; begin repeat(384) @(negedge clk); #1; end endtask "
          +"task check(input[15:0] expected); begin if(leds !== ~expected) $fatal(1,\"keys expected=%h got=%h contacts=%h\",expected,~leds,contacts); end endtask "
          +"always @(negedge clk) begin if(selects===8'hf7 && segments!==7'h77) $fatal(1,\"display scan changed\"); end "
          +"initial begin force dut.loongsim_scan_divider=16'hffff; settle; check(0); "
          +"for(k=0;k<16;k=k+1) begin contacts=16'b1<<k; settle; check(16'b1<<k); repeat(256) @(negedge clk); check(16'b1<<k); "
          +"contacts=0; settle; check(0); end $display(\"ALL_16_KEYS_PRESS_HOLD_RELEASE_PASSED\"); "
          +"for(j=0;j<10;j=j+1) begin contacts=1; repeat(2) @(negedge clk); contacts=0; repeat(2) @(negedge clk); end "
          +"settle; check(0); contacts=1; repeat(72) @(negedge clk); contacts=0; settle; check(0); "
          +"contacts=1; settle; check(1); for(j=0;j<10;j=j+1) begin contacts=0; repeat(2) @(negedge clk); contacts=1; repeat(2) @(negedge clk); end "
          +"check(1); contacts=0; settle; check(0); $display(\"PRESS_AND_RELEASE_BOUNCE_FILTER_PASSED\"); "
          +"for(k=0;k<16;k=k+1) for(j=k+1;j<16;j=j+1) begin contacts=(16'b1<<k)|(16'b1<<j); settle; check(contacts); end "
          +"$display(\"ALL_120_TWO_KEY_COMBINATIONS_PASSED\"); contacts=16'h0013; settle; check(0); "
          +"contacts=16'h0033; settle; check(0); contacts=16'h0020; settle; check(16'h0020); "
          +"$display(\"GHOST_RECTANGLE_SUPPRESSION_AND_RECOVERY_PASSED\"); contacts=0; settle; check(0); $finish; end endmodule";
      Files.writeString(output.resolve("keypad-tb.v"),tb);
      FpgaToolchain tools=FpgaToolchain.discover(); Path bin=NativeTestTools.simulatorBin(tools);
      List<String> command=new ArrayList<>(List.of(bin.resolve("iverilog.exe").toString(),"-B",bin.resolve("../lib/ivl").normalize().toString(),
          "-g2012","-s","tb","-o","keypad.vvp","keypad-tb.v"));
      command.addAll(Files.readAllLines(output.resolve("sources.list")));
      FpgaToolProcess.run(tools,output,command,"iverilog-keypad.log",System.out::println);
      FpgaToolProcess.run(tools,output,List.of(bin.resolve("vvp.exe").toString(),"keypad.vvp"),"keypad-truth.log",System.out::println);
      flow.runYosys(output,exported.getTopModule(),null); System.out.println("KEYPAD_AND_DISPLAY_SYNTHESIS_PASSED");
      new FpgaImplementation().buildBitstream(output,exported.getTopModule(),null);
      FpgaBuildArtifacts.verifyImplementation(output); System.out.println("KEYPAD_AND_DISPLAY_BITSTREAM_PASSED "+Files.size(output.resolve("design.bit")));
    } finally { project.getSimulator().shutDown(); }
  }
}

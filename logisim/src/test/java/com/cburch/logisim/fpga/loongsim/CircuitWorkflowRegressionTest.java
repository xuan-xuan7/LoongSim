package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.LocaleManager;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Circuit -> simulator -> .circ round-trip -> HDL -> pins -> synthesis -> bitstream. */
@RunWith(Parameterized.class)
public class CircuitWorkflowRegressionTest {
  @Parameterized.Parameters(name="{0}") public static Object[][] circuits(){
    return new Object[][] {{"adder4"},{"subtractor4"},{"alu2"},{"comparator4"},{"shifter4"},
      {"mux4"},{"demux2"},{"decoder3"},{"extender4to8"},{"register4"},{"dff"},{"tff"},{"jkff"},{"srff"},{"rom4"},
      {"hierarchy"},{"accumulator4"},{"dff-falling"},{"tff-falling"},{"jkff-falling"},{"srff-falling"},
      {"register4-falling"}};
  }
  @BeforeClass public static void language(){LocaleManager.setLocale(Locale.ENGLISH);}
  @Rule public TemporaryFolder temporary=new TemporaryFolder();
  private final String scenario;
  public CircuitWorkflowRegressionTest(String scenario){this.scenario=scenario;}

  @Test public void actualCircuitSimulatesSurvivesSaveAndMapsEveryBit() throws Exception {
    try(WorkflowCircuit fixture=new WorkflowCircuit(scenario)) {
      fixture.simulate();
      byte[] saved=fixture.save();
      LogisimFile reopened=new Loader(null).openLogisimFile(new ByteArrayInputStream(saved));
      Project copy=new Project(reopened);
      try {
        reopened.getCircuits().forEach(c -> c.SetProject(copy));
        assertEquals(fixture.circuit.getNonWires().size(),copy.getCurrentCircuit().getNonWires().size());
        Path directory=temporary.newFolder("保存重开 "+scenario).toPath();
        var result=new LoongSimFpgaFlow().generateVerilog(copy,copy.getCurrentCircuit(),directory,System.out::println);
        mapAndCheck(result,fixture);
      } finally {copy.getSimulator().shutDown();}
    }
  }
  @Test public void exportedAndSynthesizedLogicMatchesTheCircuitAndBuildsBitstream() throws Exception {
    assumeTrue(FpgaToolchain.isWindows()&&"1".equals(System.getenv("LOONGSIM_RUN_FPGA_INTEGRATION")));
    Path base=Path.of(System.getenv().getOrDefault("LOONGSIM_FPGA_SMOKE_DIRECTORY","../local/windows-fpga-smoke"));
    Path directory=base.toAbsolutePath().resolve("组合电路回归").resolve(scenario);Files.createDirectories(directory);
    try(WorkflowCircuit fixture=new WorkflowCircuit(scenario)) {
      fixture.simulate();fixture.writeExample(directory.resolve(scenario+".circ"));
      LoongSimFpgaFlow flow=new LoongSimFpgaFlow();
      var result=flow.generateVerilog(fixture.project,fixture.circuit,directory,System.out::println);
      fixture.inputs.forEach((label,pin) -> assertEquals("Export changed the student's input label",label,pin.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL)));
      fixture.outputs.forEach((label,pin) -> assertEquals("Export changed the student's output label",label,pin.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL)));
      mapAndCheck(result,fixture);writeTestbench(directory,result,fixture);
      simulateHdl(directory,LoongSimFpgaFlow.readSourceList(directory),"export");
      flow.runYosys(directory,result.getTopModule(),null);
      FpgaToolchain tools=FpgaToolchain.discover();
      Files.writeString(directory.resolve("netlist-sim.ys"),"read_json yosys-netlist.json; write_verilog -noattr netlist-sim.v\n");
      FpgaToolProcess.run(tools,directory,List.of(tools.yosys().toString(),"-s","netlist-sim.ys"),"netlist-sim.log",null);
      simulateHdl(directory,List.of(directory.resolve("netlist-sim.v"),tools.yosysData().resolve("xilinx/cells_sim.v")),"synthesized");
      new FpgaImplementation().buildBitstream(directory,result.getTopModule(),null);
      FpgaBuildArtifacts.verifyImplementation(directory);
      System.out.println("WORKFLOW_PASSED="+scenario+";VECTORS="+fixture.vectors.size()+";BITSTREAM_BYTES="+Files.size(directory.resolve("design.bit")));
    }
  }
  private void mapAndCheck(LoongSimFpgaFlow.ExportResult result,WorkflowCircuit fixture) throws Exception {
    Map<String,String> mapping=new LinkedHashMap<>(); int input=0,output=0;
    for(FpgaPort port:result.getPorts()) {
      boolean isInput=port.getDirection()==FpgaResource.Direction.INPUT;
      assertTrue("Original label lost: "+port.getDisplayName(),(isInput?fixture.inputs:fixture.outputs).containsKey(port.getDisplayName()));
      if(port.getDisplayName().equals("clk")){mapping.put(port.getName(),"clk");continue;}
      for(int bit=0;bit<port.getWidth();bit++) {
        String key=port.getWidth()==1?port.getName():port.getName()+"["+bit+"]";
        String resource;
        if(isInput){assertTrue("Test exceeds A7 switch/button capacity",input<10);resource=input<8?"switch["+(7-input)+"]":"btn_step["+(input-8)+"]";input++;}
        else {assertTrue("Test exceeds A7 LED capacity",output<16);resource="led["+(15-output++)+"]";}
        mapping.put(key,resource);
      }
    }
    new LoongSimFpgaFlow().completeMapping(result.getProjectDirectory(),result.getTopModule(),result.getPorts(),mapping,null);
    verifyXdc(result.getProjectDirectory(),mapping);
  }
  static void verifyXdc(Path directory,Map<String,String> mapping) throws Exception {
    Map<String,String> verified=new LinkedHashMap<>();
    for(String row:Files.readAllLines(Path.of("../fpga/boards/loongson-a7/verified-pins.csv"))) {
      String[] cells=row.split(",");if(cells.length>=3&&!cells[0].equals("resource"))verified.put(cells[0],cells[2]);
    }
    Map<String,String> actual=new LinkedHashMap<>();
    for(String line:Files.readAllLines(directory.resolve("constraints.xdc"))) {
      if(!line.startsWith("set_property PACKAGE_PIN "))continue;
      String pin=line.split("\\s+")[2];
      String reference=line.substring(line.indexOf("get_ports ")+10,line.lastIndexOf(']')).replace("{","").replace("}","").trim();
      assertNull("A top bit is assigned more than once",actual.put(reference,pin));
    }
    assertEquals("Missing/extra physical pins",mapping.size(),actual.size());
    for(var entry:mapping.entrySet())assertEquals("XDC mismatch: "+entry.getKey()+" -> "+entry.getValue(),verified.get(entry.getValue()),actual.get(entry.getKey()));
    assertEquals("Physical pin reused",actual.size(),new HashSet<>(actual.values()).size());
  }
  private void writeTestbench(Path directory,LoongSimFpgaFlow.ExportResult result,WorkflowCircuit fixture) throws Exception {
    StringBuilder bench=new StringBuilder("module tb;\n");List<String> connections=new ArrayList<>();
    Map<String,String> variables=new LinkedHashMap<>();int index=0;
    for(FpgaPort port:result.getPorts()) {
      String variable="p"+index++;variables.put(port.getName(),variable);
      bench.append(port.getDirection()==FpgaResource.Direction.INPUT?"reg ":"wire ");
      if(port.getWidth()>1)bench.append('[').append(port.getWidth()-1).append(":0] ");
      bench.append(variable).append(";\n");connections.add("."+port.getName()+"("+variable+")");
    }
    bench.append(result.getTopModule()).append(" dut(").append(String.join(",",connections)).append(");\ninitial begin\n");
    for(int vector=0;vector<fixture.vectors.size();vector++) {
      for(FpgaPort port:result.getPorts())if(port.getDirection()==FpgaResource.Direction.INPUT) {
        bench.append(variables.get(port.getName())).append('=').append(port.getWidth()).append("'h")
          .append(Long.toUnsignedString(fixture.vectors.get(vector).get(port.getDisplayName()),16)).append(";\n");
      }
      bench.append("#20;\n");
      for(FpgaPort port:result.getPorts())if(port.getDirection()==FpgaResource.Direction.OUTPUT) {
        long mask=com.cburch.logisim.data.BitWidth.create(port.getWidth()).getMask();
        long expected=(~fixture.expected.get(vector).get(port.getDisplayName()))&mask;
        bench.append("if (").append(variables.get(port.getName())).append(" !== ").append(port.getWidth()).append("'h")
            .append(Long.toUnsignedString(expected,16)).append(") $fatal(1,\"").append(scenario).append(" vector ").append(vector)
            .append(" output ").append(port.getName()).append(" disagrees with Logisim\");\n");
      }
    }
    bench.append("$display(\"WORKFLOW_VECTORS_PASSED\");$finish;end endmodule\n");
    Files.writeString(directory.resolve("workflow-tb.v"),bench);
  }
  private void simulateHdl(Path directory,List<Path> sources,String stage) throws Exception {
    FpgaToolchain tools=FpgaToolchain.discover();
    String override=System.getenv("LOONGSIM_FPGA_TEST_SIMULATOR");
    Path bin=override==null?tools.yosys().getParent():WindowsNativePath.resolve(Path.of(override)).resolve("bin");
    List<String> command=new ArrayList<>(List.of(bin.resolve("iverilog.exe").toString(),"-B",bin.resolve("../lib/ivl").normalize().toString(),
        "-g2012","-s","tb","-o","workflow-"+stage+".vvp","workflow-tb.v"));
    for(Path source:sources)command.add(source.startsWith(directory)?directory.relativize(source).toString():source.toString());
    FpgaToolProcess.run(tools,directory,command,"iverilog-"+stage+".log",null);
    FpgaToolProcess.run(tools,directory,List.of(bin.resolve("vvp.exe").toString(),"workflow-"+stage+".vvp"),"truth-table-"+stage+".log",null);
    assertTrue(Files.readString(directory.resolve("logs/truth-table-"+stage+".log")).contains("WORKFLOW_VECTORS_PASSED"));
  }
}

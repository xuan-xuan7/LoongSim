package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.*;
import com.cburch.logisim.fpga.hdlgenerator.HDLGeneratorFactory;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HdlCapabilityRegressionTest {
  @Rule public TemporaryFolder temporary=new TemporaryFolder();
  @Test public void levelTriggeredRamIsRejectedBeforeExport() throws Exception {reject("RAM-high", "锁存模式", false);}
  @Test public void largeRamReportsTheAddressWidthLimitBeforeExport() throws Exception {reject("RAM-large", "最多支持 8 位地址", false);}
  @Test public void dynamicCounterMaximumIsRejectedBeforeHdlOutput() throws Exception {reject("SimpleCounter", "Max 请接常量", true);}
  @Test public void dynamicRamReadEnableIsRejectedBeforeHdlOutput() throws Exception {reject("ram4-dynamic-oe", "OE 请接高电平或留空", true);}
  @Test public void highLevelRegisterIsRejectedBeforeImplementation() throws Exception {rejectLatch("register4-high");}
  @Test public void lowLevelRegisterIsRejectedBeforeImplementation() throws Exception {rejectLatch("register4-low");}
  private void rejectLatch(String mode) throws Exception {
    try(WorkflowCircuit fixture=new WorkflowCircuit(mode)) {
      Path directory=temporary.getRoot().toPath().resolve(mode);
      try {new LoongSimFpgaFlow().generateVerilog(fixture.project,fixture.circuit,directory,null);fail("Unsupported latch mode accepted");}
      catch(IOException expected){assertTrue(expected.getMessage().contains("锁存模式"));assertFalse(Files.exists(directory));}
    }
  }
  @Test public void unsupportedComponentsInsideChineseSubcircuitsAreIdentified() throws Exception {
    try(WorkflowCircuit fixture=new WorkflowCircuit("hierarchy")) {
      var child=fixture.project.getLogisimFile().getCircuits().stream().filter(c -> c.getName().equals("运算模块")).findFirst().orElseThrow();
      var factory=((AddTool)fixture.project.getLogisimFile().getLibrary("Memory").getTool("RAM")).getFactory();
      var mutation=new com.cburch.logisim.circuit.CircuitMutation(child);
      var attrs = factory.createAttributeSet();
      attrs.setValue(com.cburch.logisim.instance.StdAttr.TRIGGER, com.cburch.logisim.instance.StdAttr.TRIG_HIGH);
      mutation.add(factory.createComponent(com.cburch.logisim.data.Location.create(1200,1200),attrs));
      mutation.execute();
      try {new LoongSimFpgaFlow().generateVerilog(fixture.project,fixture.circuit,temporary.getRoot().toPath().resolve("nested"),null);fail("Nested unsupported RAM was accepted");}
      catch(IOException expected){assertTrue(expected.getMessage().contains("RAM"));assertTrue(expected.getMessage().contains("运算模块"));}
    }
  }
  private void reject(String component, String message, boolean afterNetlist) throws Exception {
    try(WorkflowCircuit fixture=new WorkflowCircuit(component)) {
      Path directory=temporary.getRoot().toPath().resolve("尚未导出");
      try {
        new LoongSimFpgaFlow().generateVerilog(fixture.project,fixture.circuit,directory,null);
        fail(component+" was accepted despite having no compatible Verilog generator");
      } catch(IOException failure) {
        assertTrue("Failure did not identify the unsupported component: "+failure.getMessage(),failure.getMessage().contains(message));
        assertFalse("Unsupported circuits must not produce HDL",
            Files.exists(afterNetlist ? directory.resolve("sources.list") : directory));
      }
    }
  }
  @Test public void recordsDefaultVerilogSupportForEveryVisibleFactory() throws Exception {
    try(WorkflowCircuit fixture=new WorkflowCircuit("adder4")) {
      List<String> rows=new ArrayList<>();rows.add("library\tcomponent\tdefaultVerilogSupport");
      for(String name:new String[]{"Wiring","Gates","Plexers","Arithmetic","Memory","I/O"}) {
        Library library=fixture.project.getLogisimFile().getLibrary(name);if(library==null)continue;
        for(Tool tool:library.getVisibleTools())if(tool instanceof AddTool) {
          var factory=((AddTool)tool).getFactory();
          rows.add(name+"\t"+factory.getName()+"\t"+factory.HDLSupportedComponent(HDLGeneratorFactory.VERILOG,factory.createAttributeSet()));
        }
      }
      assertTrue("No component inventory was produced",rows.size()>30);
      Path base=Path.of(System.getenv().getOrDefault("LOONGSIM_FPGA_SMOKE_DIRECTORY","../local/fpga-coverage"));
      Files.createDirectories(base);Files.write(base.resolve("component-hdl-support.tsv"),rows);
      System.out.println("VISIBLE_COMPONENTS_AUDITED="+(rows.size()-1));
    }
  }
}

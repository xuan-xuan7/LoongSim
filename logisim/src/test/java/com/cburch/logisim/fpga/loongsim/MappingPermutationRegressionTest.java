package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MappingPermutationRegressionTest {
  @Rule public TemporaryFolder temporary=new TemporaryFolder();
  private Map<String,String> assignment(List<FpgaPort> ports,Random random) {
    List<String> switches=new ArrayList<>(),leds=new ArrayList<>();
    for(int i=0;i<8;i++)switches.add("switch["+i+"]");for(int i=0;i<16;i++)leds.add("led["+i+"]");
    Collections.shuffle(switches,random);Collections.shuffle(leds,random);
    int input=0,output=0;Map<String,String> mapping=new LinkedHashMap<>();
    for(FpgaPort port:ports)for(int bit=0;bit<port.getWidth();bit++) {
      String key=port.getWidth()==1?port.getName():port.getName()+"["+bit+"]";
      mapping.put(key,port.getDirection()==FpgaResource.Direction.INPUT?switches.get(input++):leds.get(output++));
    }
    return mapping;
  }
  @Test public void fiftyReorderedBusMappingsUseExactPinsAndInvalidateOldBitstreams() throws Exception {
    try(WorkflowCircuit circuit=new WorkflowCircuit("adder4")) {
      Path directory=temporary.newFolder("重复映射").toPath();LoongSimFpgaFlow flow=new LoongSimFpgaFlow();
      var result=flow.generateVerilog(circuit.project,circuit.circuit,directory,null);Random random=new Random(0xA750L);
      for(int iteration=0;iteration<50;iteration++) {
        Map<String,String> mapping=assignment(result.getPorts(),random);
        Files.writeString(directory.resolve("design.bit"),"old configuration must not survive remapping");
        flow.completeMapping(directory,result.getTopModule(),result.getPorts(),mapping,null);
        CircuitWorkflowRegressionTest.verifyXdc(directory,mapping);
        assertFalse("Old bitstream survived remapping",Files.exists(directory.resolve("design.bit")));
      }
    }
  }
  @Test public void duplicateWrongDirectionMissingAndOutOfRangeMappingsAreRejected() throws Exception {
    try(WorkflowCircuit circuit=new WorkflowCircuit("adder4")) {
      Path directory=temporary.newFolder("无效映射").toPath();LoongSimFpgaFlow flow=new LoongSimFpgaFlow();
      var result=flow.generateVerilog(circuit.project,circuit.circuit,directory,null);
      Map<String,String> good=assignment(result.getPorts(),new Random(4));
      flow.completeMapping(directory,result.getTopModule(),result.getPorts(),good,null);
      List<String> keys=new ArrayList<>(good.keySet());
      List<Map<String,String>> bad=new ArrayList<>();
      Map<String,String> duplicate=new LinkedHashMap<>(good);duplicate.put(keys.get(1),good.get(keys.get(0)));bad.add(duplicate);
      Map<String,String> missing=new LinkedHashMap<>(good);missing.remove(keys.get(0));bad.add(missing);
      Map<String,String> range=new LinkedHashMap<>(good);String old=keys.stream().filter(k -> k.endsWith("[0]")).findFirst().orElseThrow();
      String resource=range.remove(old);range.put(old.substring(0,old.length()-3)+"[999999999999999999999]",resource);bad.add(range);
      Map<String,String> direction=new LinkedHashMap<>(good);String output=keys.stream().filter(k -> good.get(k).startsWith("led[")).findFirst().orElseThrow();
      direction.put(output,"switch[0]");bad.add(direction);
      for(Map<String,String> mapping:bad) {
        try{flow.completeMapping(directory,result.getTopModule(),result.getPorts(),mapping,null);fail("Invalid mapping was accepted: "+mapping);}
        catch(IOException expected){assertFalse(expected.getMessage().isBlank());}
      }
      flow.completeMapping(directory,result.getTopModule(),result.getPorts(),good,null);
      CircuitWorkflowRegressionTest.verifyXdc(directory,good);
    }
  }
  @Test public void allResourceConflictsAreSymmetricAndDirectionChecksHold() {
    List<FpgaResource> resources=LoongSimBoardCatalog.resources();
    for(FpgaResource a:resources) {
      assertTrue(a.conflictsWith(a.getId()));assertFalse(a.accepts(null));
      for(FpgaResource b:resources)assertEquals("Asymmetric conflict: "+a.getId()+" / "+b.getId(),a.conflictsWith(b.getId()),b.conflictsWith(a.getId()));
    }
  }
}

/** This file is part of LoongSim. */

package com.cburch.logisim.std.arith;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.NetlistComponent;
import com.cburch.logisim.fpga.gui.FPGAReport;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHDLGeneratorFactory;
import com.cburch.logisim.instance.StdAttr;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;

/** Emits the fixed ALU operation table as synthesizable Verilog. */
public class ALUHDLGeneratorFactory extends AbstractHDLGeneratorFactory {
  private static final String NR_OF_BITS = "NrOfBits";
  private static final int NR_OF_BITS_ID = -1;

  @Override
  public String getComponentStringIdentifier() {
    return "ALU";
  }

  @Override
  public SortedMap<String, Integer> GetInputList(Netlist netlist, AttributeSet attrs) {
    SortedMap<String, Integer> inputs = new TreeMap<String, Integer>();
    int bits = attrs.getValue(StdAttr.WIDTH).getWidth() == 1 ? 1 : NR_OF_BITS_ID;
    inputs.put("DataA", bits);
    inputs.put("DataB", bits);
    inputs.put("Control", 3);
    return inputs;
  }

  @Override
  public ArrayList<String> GetModuleFunctionality(
      Netlist netlist, AttributeSet attrs, FPGAReport reporter, String hdlType) {
    ArrayList<String> contents = new ArrayList<String>();
    contents.add("   assign Result = (Control == 3'b000) ? (DataA & DataB) :");
    contents.add("                   (Control == 3'b001) ? (DataA | DataB) :");
    contents.add("                   (Control == 3'b010) ? (DataA + DataB) :");
    contents.add("                   (Control == 3'b100) ? (DataA & ~DataB) :");
    contents.add("                   (Control == 3'b101) ? (DataA | ~DataB) :");
    contents.add("                   (Control == 3'b110) ? (DataA - DataB) :");
    contents.add("                   (Control == 3'b111) ? ($signed(DataA) < $signed(DataB)) : 0;");
    contents.add("   assign CarryOut = (Control == 3'b010) ? (DataA + DataB < DataA) :");
    contents.add("                     (Control == 3'b110) ? (DataA >= DataB) : 1'b0;");
    return contents;
  }

  @Override
  public SortedMap<String, Integer> GetOutputList(Netlist netlist, AttributeSet attrs) {
    SortedMap<String, Integer> outputs = new TreeMap<String, Integer>();
    int bits = attrs.getValue(StdAttr.WIDTH).getWidth() == 1 ? 1 : NR_OF_BITS_ID;
    outputs.put("Result", bits);
    outputs.put("CarryOut", 1);
    return outputs;
  }

  @Override
  public SortedMap<Integer, String> GetParameterList(AttributeSet attrs) {
    SortedMap<Integer, String> parameters = new TreeMap<Integer, String>();
    if (attrs.getValue(StdAttr.WIDTH).getWidth() > 1) parameters.put(NR_OF_BITS_ID, NR_OF_BITS);
    return parameters;
  }

  @Override
  public SortedMap<String, Integer> GetParameterMap(
      Netlist nets, NetlistComponent componentInfo, FPGAReport reporter) {
    SortedMap<String, Integer> parameters = new TreeMap<String, Integer>();
    int bits = componentInfo.GetComponent().getEnd(ALU.A).getWidth().getWidth();
    if (bits > 1) parameters.put(NR_OF_BITS, bits);
    return parameters;
  }

  @Override
  public SortedMap<String, String> GetPortMap(
      Netlist nets, Object mapInfo, FPGAReport reporter, String hdlType) {
    SortedMap<String, String> portMap = new TreeMap<String, String>();
    if (!(mapInfo instanceof NetlistComponent)) return portMap;
    NetlistComponent componentInfo = (NetlistComponent) mapInfo;
    portMap.putAll(GetNetMap("DataA", true, componentInfo, ALU.A, reporter, hdlType, nets));
    portMap.putAll(GetNetMap("DataB", true, componentInfo, ALU.B, reporter, hdlType, nets));
    portMap.putAll(GetNetMap("Control", true, componentInfo, ALU.CONTROL, reporter, hdlType, nets));
    portMap.putAll(GetNetMap("Result", true, componentInfo, ALU.ANSWER, reporter, hdlType, nets));
    portMap.putAll(GetNetMap("CarryOut", true, componentInfo, ALU.CARRY, reporter, hdlType, nets));
    return portMap;
  }

  @Override
  public String GetSubDir() {
    return "arithmetic";
  }

  @Override
  public boolean HDLTargetSupported(String hdlType, AttributeSet attrs) {
    return hdlType.equals(VERILOG);
  }
}

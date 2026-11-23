/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.memory;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.NetlistComponent;
import com.cburch.logisim.fpga.gui.FPGAReport;
import java.util.ArrayList;

final class SimpleCounterVerilogGenerator extends MemoryVerilogGenerator {
  @Override
  public String getComponentStringIdentifier() {
    return "loongsim_simple_counter";
  }

  @Override
  public boolean HDLTargetSupported(String language, AttributeSet attrs) {
    return VERILOG.equals(language);
  }

  @Override
  public ArrayList<String> GetInlinedCode(Netlist nets, Long id, NetlistComponent component,
      FPGAReport reporter, String circuit, String language) {
    var maxWidth = component.GetComponent().getEnd(SimpleCounter.MAX).getWidth();
    long max = component.EndIsConnected(SimpleCounter.MAX)
        ? nets.readConstantInput(component, SimpleCounter.MAX).orElseThrow(
            () -> new IllegalStateException("SimpleCounter Max must be constant for Verilog export"))
        : maxWidth.getMask();
    int bits = maxWidth.getWidth();
    String count = "s_loongsim_counter_" + id + "_count";
    String zero = "s_loongsim_counter_" + id + "_zero";
    Clock clock = clock(nets, component, SimpleCounter.CLOCK, true);
    boolean hasReset = component.EndIsConnected(SimpleCounter.RESET);
    String reset = GetNetName(component, SimpleCounter.RESET, true, language, nets);
    String limit = bits + "'h" + Long.toUnsignedString(max, 16);
    var code = new ArrayList<String>();
    code.add("   reg [" + (bits - 1) + ":0] " + count + " = 0;");
    code.add("   wire " + zero + " = (" + count + " == 0);");
    code.add("   always @(" + clock.edge + (hasReset ? " or posedge " + reset : "") + ") begin");
    if (hasReset) code.add("      if (" + reset + ") " + count + " <= 0;");
    code.add("      " + (hasReset ? "else " : "") + "if (" + clock.enable + ") begin");
    code.add("         if (" + count + " == " + limit + ") " + count + " <= 0;");
    code.add("         else " + count + " <= " + count + " + 1'b1;");
    code.add("      end");
    code.add("   end");
    driveOutput(code, nets, component, SimpleCounter.VALUE, count);
    driveOutput(code, nets, component, SimpleCounter.ZERO, zero);
    return code;
  }
}

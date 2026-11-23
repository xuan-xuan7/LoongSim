/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.memory;

import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.NetlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHDLGeneratorFactory;
import com.cburch.logisim.std.wiring.ClockHDLGeneratorFactory;
import java.util.List;

/** Shared Verilog wiring for the small teaching memories. */
abstract class MemoryVerilogGenerator extends AbstractHDLGeneratorFactory {
  @Override
  public final boolean IsOnlyInlined(String language) {
    return VERILOG.equals(language);
  }

  static final class Clock {
    final String edge;
    final String enable;

    Clock(String edge, String enable) {
      this.edge = edge;
      this.enable = enable;
    }
  }

  final Clock clock(Netlist nets, NetlistComponent component, int port, boolean rising) {
    String tree = GetClockNetName(component, port, nets);
    if (tree.isEmpty()) {
      return new Clock((rising ? "posedge " : "negedge ")
          + GetNetName(component, port, true, VERILOG, nets),
          component.EndIsConnected(port) ? "1'b1" : "1'b0");
    }
    int tick = rising ? ClockHDLGeneratorFactory.PositiveEdgeTickIndex
        : ClockHDLGeneratorFactory.NegativeEdgeTickIndex;
    return new Clock("posedge " + tree + "[" + ClockHDLGeneratorFactory.GlobalClockIndex + "]",
        nets.RequiresGlobalClockConnection() ? "1'b1" : tree + "[" + tick + "]");
  }

  final void driveOutput(List<String> code, Netlist nets, NetlistComponent component,
      int port, String signal) {
    var end = component.getEnd(port);
    if (end.NrOfBits() == 1) {
      if (component.EndIsConnected(port)) {
        code.add("   assign " + GetNetName(component, port, true, VERILOG, nets)
            + " = " + signal + ";");
      }
      return;
    }
    for (int bit = 0; bit < end.NrOfBits(); bit++) {
      if (end.GetConnection((byte) bit).GetParrentNet() != null) {
        code.add("   assign " + GetBusEntryName(component, port, true, bit, VERILOG, nets)
            + " = " + signal + "[" + bit + "];");
      }
    }
  }
}

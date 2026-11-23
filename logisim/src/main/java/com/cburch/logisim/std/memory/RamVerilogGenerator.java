/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.memory;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.NetlistComponent;
import com.cburch.logisim.fpga.gui.FPGAReport;
import com.cburch.logisim.instance.StdAttr;
import java.util.ArrayList;

final class RamVerilogGenerator extends MemoryVerilogGenerator {
  @Override
  public String getComponentStringIdentifier() {
    return "loongsim_ram";
  }

  @Override
  public boolean HDLTargetSupported(String language, AttributeSet attrs) {
    if (!VERILOG.equals(language) || attrs == null) return false;
    Object trigger = attrs.getValue(StdAttr.TRIGGER);
    return (trigger == StdAttr.TRIG_RISING || trigger == StdAttr.TRIG_FALLING)
        && RamAttributes.BUS_SEP.equals(attrs.getValue(RamAttributes.ATTR_DBUS))
        && Mem.USEBYTEENABLES.equals(attrs.getValue(Mem.ENABLES_ATTR))
        && Boolean.TRUE.equals(attrs.getValue(Mem.ASYNC_READ))
        && RamAppearance.getNrBEPorts(attrs) == 0
        && attrs.getValue(Mem.ADDR_ATTR).getWidth() <= Ram.FPGA_MAX_ADDRESS_BITS;
  }

  @Override
  public ArrayList<String> GetInlinedCode(Netlist nets, Long id, NetlistComponent component,
      FPGAReport reporter, String circuit, String language) {
    if (!Ram.hasAlwaysEnabledRead(nets, component)) {
      throw new IllegalStateException("RAM OE must be open or tied high for Verilog export");
    }
    AttributeSet attrs = component.GetComponent().getAttributeSet();
    int words = 1 << attrs.getValue(Mem.ADDR_ATTR).getWidth();
    int bits = attrs.getValue(Mem.DATA_ATTR).getWidth();
    String name = "s_loongsim_ram_" + id;
    String address = GetNetMap("address", true, component,
        RamAppearance.getAddrIndex(0, attrs), reporter, language, nets).get("address");
    String data = GetNetMap("data", true, component,
        RamAppearance.getDataInIndex(0, attrs), reporter, language, nets).get("data");
    String write = GetNetName(component, RamAppearance.getWEIndex(0, attrs), true, language, nets);
    int clearPort = RamAppearance.getClrIndex(0, attrs);
    boolean hasClear = component.EndIsConnected(clearPort);
    String clear = hasClear ? GetNetName(component, clearPort, true, language, nets) : "1'b0";
    Clock clock = clock(nets, component, RamAppearance.getClkIndex(0, attrs),
        attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_RISING);
    String writeEnabled = write + " && " + clock.enable;
    String word = "[" + address + "]";
    var code = new ArrayList<String>();
    code.add("   reg [" + (bits - 1) + ":0] " + name + "_data [0:" + (words - 1) + "];");
    code.add("   reg [" + (words - 1) + ":0] " + name + "_valid = 0;");
    code.add("   wire [" + (bits - 1) + ":0] " + name + "_read = (!" + clear + " && "
        + name + "_valid" + word + ") ? " + name + "_data" + word + " : " + bits + "'b0;");
    // Clear the valid bits, so every address reads zero immediately and the data array
    // can still be implemented as ordinary single-port memory.
    code.add("   always @(" + clock.edge + (hasClear ? " or posedge " + clear : "") + ") begin");
    if (hasClear) code.add("      if (" + clear + ") " + name + "_valid <= 0;");
    code.add("      " + (hasClear ? "else " : "") + "if (" + writeEnabled + ") "
        + name + "_valid" + word + " <= 1'b1;");
    code.add("   end");
    code.add("   always @(" + clock.edge + ") begin");
    code.add("      if (!" + clear + " && " + writeEnabled + ") " + name + "_data" + word
        + " <= " + data + ";");
    code.add("   end");
    driveOutput(code, nets, component, RamAppearance.getDataOutIndex(0, attrs), name + "_read");
    return code;
  }
}

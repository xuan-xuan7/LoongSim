/**
 * This file is part of logisim-evolution.
 *
 * Logisim-evolution is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Logisim-evolution is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with logisim-evolution. If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code by Carl Burch (http://www.cburch.com), 2011.
 * Subsequent modifications by:
 *   + College of the Holy Cross
 *     http://www.holycross.edu
 *   + Haute École Spécialisée Bernoise/Berner Fachhochschule
 *     http://www.bfh.ch
 *   + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *     http://hepia.hesge.ch/
 *   + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *     http://www.heig-vd.ch/
 */

package com.cburch.logisim.std.io;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.fpga.designrulecheck.NetlistComponent;
import com.cburch.logisim.fpga.gui.FPGAReport;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHDLGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.HDLGeneratorFactory;
import com.cburch.logisim.instance.StdAttr;
import java.util.ArrayList;

public class HexDigitHDLGeneratorFactory extends AbstractHDLGeneratorFactory {

  @Override
  public ArrayList<String> GetInlinedCode(
      Netlist Nets,
      Long ComponentId,
      NetlistComponent ComponentInfo,
      FPGAReport Reporter,
      String CircuitName,
      String HDLType) {
    ArrayList<String> Contents = new ArrayList<String>();
    String Label = ComponentInfo.GetComponent().getAttributeSet().getValue(StdAttr.LABEL);
    String BusName = GetBusName(ComponentInfo, HexDigit.HEX, HDLType, Nets);
    String DPName = GetNetName(ComponentInfo, HexDigit.DP, true, HDLType, Nets);
    if (HDLType.equals(HDLGeneratorFactory.VHDL)) {
      Contents.add(" ");
      if (ComponentInfo.EndIsConnected(HexDigit.HEX)) {
        Contents.add("   WITH ("+BusName+") SELECT "+HDLGeneratorFactory.LocalOutputBubbleBusname+"( "+
        		(ComponentInfo.GetLocalBubbleOutputStartId() + 6)+" DOWNTO "+
        		ComponentInfo.GetLocalBubbleOutputStartId()+") <= ");
        for (int digit = 0; digit < 16; digit++) {
          Contents.add("      \"" + binary(HexDigit.getSegmentPattern(digit), 7)
              + "\" WHEN \"" + binary(digit, 4) + "\",");
        }
        Contents.add("      \"0000000\" WHEN OTHERS;");
      } else {
        Contents.add("   "+HDLGeneratorFactory.LocalOutputBubbleBusname+"( "+
        		(ComponentInfo.GetLocalBubbleOutputStartId() + 6)+" DOWNTO "+
        		ComponentInfo.GetLocalBubbleOutputStartId()+") <= "+BusName+";");
      }
      if (ComponentInfo.GetComponent().getAttributeSet().getValue(SevenSegment.ATTR_DP)) {
        Contents.add( "   "
                + HDLGeneratorFactory.LocalOutputBubbleBusname
                + "("
                + Integer.toString(ComponentInfo.GetLocalBubbleOutputStartId() + 7)
                + ") <= "
                + DPName
                + ";");
      }
    } else {
      String Signame = HDLGeneratorFactory.LocalOutputBubbleBusname+"["+
                       (ComponentInfo.GetLocalBubbleOutputStartId() + 6)+":"+
                       ComponentInfo.GetLocalBubbleOutputStartId()+"]";
      if (ComponentInfo.EndIsConnected(HexDigit.HEX)) {
        String decoder = "s_" + CorrectLabel.getCorrectLabel(Label) + "_" + ComponentId + "_segments";
        Contents.add(" ");
        Contents.add("   function [6:0] " + decoder + ";");
        Contents.add("      input [3:0] digit;");
        Contents.add("      begin");
        Contents.add("         case (digit)");
        for (int digit = 0; digit < 16; digit++) {
          Contents.add("            4'h" + Integer.toHexString(digit) + ": " + decoder
              + " = 7'h" + Integer.toHexString(HexDigit.getSegmentPattern(digit)) + ";");
        }
        Contents.add("            default: " + decoder + " = 7'b0;");
        Contents.add("         endcase");
        Contents.add("      end");
        Contents.add("   endfunction");
        Contents.add("   assign " + Signame + " = " + decoder + "(" + BusName + ");");
      } else {
        Contents.add("   assign "+Signame+" = "+BusName+";");
      }
      if (ComponentInfo.GetComponent().getAttributeSet().getValue(SevenSegment.ATTR_DP)) {
        Contents.add("   assign "+HDLGeneratorFactory.LocalOutputBubbleBusname+"["+
                     Integer.toString(ComponentInfo.GetLocalBubbleOutputStartId() + 7)+
                     "] = "+DPName+";");
      }
    }
    return Contents;
  }

  private static String binary(int value, int width) {
    String digits = Integer.toBinaryString(value);
    return "0".repeat(width - digits.length()) + digits;
  }

  @Override
  public boolean HDLTargetSupported(String HDLType, AttributeSet attrs) {
    return true;
  }

  @Override
  public boolean IsOnlyInlined(String HDLType) {
    return true;
  }
}

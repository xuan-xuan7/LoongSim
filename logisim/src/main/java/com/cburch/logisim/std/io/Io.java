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

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.tools.FactoryDescription;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Io extends Library {
  private static final Set<String> HIDDEN_TOOLS = Set.of("DotMatrix", "PortIO");
  public static final Attribute<Color> ATTR_COLOR =
      Attributes.forColor("color", S.getter("ioColorAttr"));
  static final Attribute<Color> ATTR_ON_COLOR = Attributes.forColor("color", S.getter("ioOnColor"));
  static final Attribute<Color> ATTR_OFF_COLOR =
      Attributes.forColor("offcolor", S.getter("ioOffColor"));
  static final Attribute<Color> ATTR_BACKGROUND =
      Attributes.forColor("bg", S.getter("ioBackgroundColor"));
  public static final Attribute<Boolean> ATTR_ACTIVE =
      Attributes.forBoolean("active", S.getter("ioActiveAttr"));

  static final Color DEFAULT_BACKGROUND = new Color(255, 255, 255, 0);

  private static FactoryDescription[] DESCRIPTIONS = {
    new FactoryDescription("Button", S.getter("buttonComponent"), "button.gif", "Button"),
    new FactoryDescription("DipSwitch", S.getter("dipswitchComponent"), "dipswitch.gif", "DipSwitch"),
    // Unsupported board peripherals are intentionally hidden for Loongson A7.
    new FactoryDescription("LED", S.getter("ledComponent"), "led.gif", "Led"),
    new FactoryDescription("RGBLED", S.getter("RGBledComponent"), "rgbled.gif", "RGBLed"),
    new FactoryDescription("7-Segment Display", S.getter("sevenSegmentComponent"), "7seg.gif", "SevenSegment"),
    new FactoryDescription("Hex Digit Display", S.getter("hexDigitComponent"), "hexdig.gif", "HexDigit"),
    new FactoryDescription("DotMatrix", S.getter("dotMatrixComponent"), "dotmat.gif", "DotMatrix"),
    new FactoryDescription("PortIO", S.getter("pioComponent"), "pio.gif", "PortIO"),
  };

  private List<Tool> tools = null;

  public Io() {}

  @Override
  public String getDisplayName() {
    return S.get("ioLibrary");
  }

  @Override
  public String getName() {
    return "I/O";
  }

  @Override
  public List<Tool> getTools() {
    if (tools == null) {
      tools = new ArrayList<>();
      tools.addAll(FactoryDescription.getTools(Io.class, DESCRIPTIONS));
    }
    return tools;
  }

  @Override
  public List<Tool> getVisibleTools() {
    List<Tool> visibleTools = new ArrayList<>();
    for (Tool tool : getTools()) {
      if (!HIDDEN_TOOLS.contains(tool.getName())) visibleTools.add(tool);
    }
    return visibleTools;
  }

  public boolean removeLibrary(String Name) {
    return false;
  }
}

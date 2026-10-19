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

package com.cburch.logisim.std.wiring;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Graphics;
import java.awt.Font;
import java.awt.Window;

public class BitExtender extends InstanceFactory {
  private static final Attribute<Integer> ATTR_BODY_WIDTH = new Attribute<Integer>(
      "bodyWidth", S.getter("extenderComponent")) {
    @Override
    public Integer parse(String value) {
      int width = Integer.parseInt(value);
      if (width != 40 && width != 80) throw new NumberFormatException("Invalid extender width");
      return width;
    }

    @Override
    public boolean isHidden() { return true; }
  };
  private static final Attribute<BitWidth> ATTR_IN_WIDTH =
      Attributes.forBitWidth("in_width", S.getter("extenderInAttr"));
  private static final Attribute<BitWidth> ATTR_OUT_WIDTH =
      Attributes.forBitWidth("out_width", S.getter("extenderOutAttr"));
  private static final Attribute<AttributeOption> ALL_TYPES =
      Attributes.forOption(
          "type",
          S.getter("extenderTypeAttr"),
          new AttributeOption[] {
            new AttributeOption("zero", "zero", S.getter("extenderZeroType")),
            new AttributeOption("one", "one", S.getter("extenderOneType")),
            new AttributeOption("sign", "sign", S.getter("extenderSignType")),
            new AttributeOption("input", "input", S.getter("extenderInputType")),
          });

  private static final Attribute<AttributeOption> VISIBLE_TYPES = Attributes.forOption(
      "type", S.getter("extenderTypeAttr"), new AttributeOption[] {
        ALL_TYPES.parse("zero"), ALL_TYPES.parse("one"), ALL_TYPES.parse("sign")
      });

  // Keep parsing the fourth type in existing files, while offering only the teaching choices.
  static final Attribute<AttributeOption> ATTR_TYPE = new Attribute<AttributeOption>(
      "type", S.getter("extenderTypeAttr")) {
    @Override
    public AttributeOption parse(String value) { return ALL_TYPES.parse(value); }

    @Override
    public String toDisplayString(AttributeOption value) { return ALL_TYPES.toDisplayString(value); }

    @Override
    public java.awt.Component getCellEditor(Window source, AttributeOption value) {
      return VISIBLE_TYPES.getCellEditor(source,
          value != null && "input".equals(value.getValue()) ? null : value);
    }
  };

  public static final BitExtender FACTORY = new BitExtender();

  public BitExtender() {
    super("Bit Extender", S.getter("extenderComponent"));
    setIconName("extender.gif");
    setAttributes(
        new Attribute[] {ATTR_IN_WIDTH, ATTR_OUT_WIDTH, ATTR_TYPE, ATTR_BODY_WIDTH},
        new Object[] {BitWidth.create(8), BitWidth.create(16), ATTR_TYPE.parse("sign"), 80});
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(
        JoinedConfigurator.create(
            new BitWidthConfigurator(ATTR_OUT_WIDTH),
            new BitWidthConfigurator(ATTR_IN_WIDTH, 1, Value.MAX_WIDTH, 0)));
    setOffsetBounds(Bounds.create(-80, -20, 80, 40));
  }

  //
  // methods for instances
  //
  @Override
  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion version) {
    // Old files have wires attached at x = -40. Save the wider layout explicitly for new instances.
    if (attr == ATTR_BODY_WIDTH) return 40;
    return super.getDefaultAttributeValue(attr, version);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int width = attrs.getValue(ATTR_BODY_WIDTH);
    return Bounds.create(-width, -20, width, 40);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    configurePorts(instance);
    instance.addAttributeListener();
  }

  private void configurePorts(Instance instance) {
    Port p0 = new Port(0, 0, Port.OUTPUT, ATTR_OUT_WIDTH);
    Port p1 = new Port(-instance.getAttributeValue(ATTR_BODY_WIDTH), 0, Port.INPUT, ATTR_IN_WIDTH);
    String type = getType(instance.getAttributeSet());
    if (type.equals("input")) {
      instance.setPorts(new Port[] {p0, p1, new Port(-20, -20, Port.INPUT, 1)});
    } else {
      instance.setPorts(new Port[] {p0, p1});
    }
  }

  private String getType(AttributeSet attrs) {
    AttributeOption topt = attrs.getValue(ATTR_TYPE);
    return (String) topt.getValue();
  }

  @Override
  public boolean HDLSupportedComponent(String HDLIdentifier, AttributeSet attrs) {
    if (MyHDLGenerator == null) MyHDLGenerator = new BitExtenderHDLGeneratorFactory();
    return MyHDLGenerator.HDLTargetSupported(HDLIdentifier, attrs);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_BODY_WIDTH) {
      instance.recomputeBounds();
      configurePorts(instance);
      instance.fireInvalidated();
    } else if (attr == ATTR_TYPE) {
      configurePorts(instance);
      instance.fireInvalidated();
    } else {
      instance.fireInvalidated();
    }
  }

  //
  // graphics methods
  //
  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    painter.drawBounds();

    String s0;
    String type = getType(painter.getAttributeSet());
    if (type.equals("zero")) s0 = S.get("extenderZeroLabel");
    else if (type.equals("one")) s0 = S.get("extenderOneLabel");
    else if (type.equals("sign")) s0 = S.get("extenderSignLabel");
    else if (type.equals("input")) s0 = S.get("extenderInputLabel");
    else s0 = "???"; // should never happen
    String s1 = S.get("extenderMainLabel");
    Bounds bds = painter.getBounds();
    int x = bds.getX() + bds.getWidth() / 2;
    int y = painter.getLocation().getY();
    Font originalFont = g.getFont();
    g.setFont(originalFont.deriveFont(12f));
    GraphicsUtil.drawText(g, s0, x, y - 6,
        GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER_OVERALL);
    GraphicsUtil.drawText(g, s1, x, y + 6,
        GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER_OVERALL);

    BitWidth w0 = painter.getAttributeValue(ATTR_OUT_WIDTH);
    BitWidth w1 = painter.getAttributeValue(ATTR_IN_WIDTH);
    g.setFont(originalFont.deriveFont(11f));
    if (bds.getWidth() == 80) {
      GraphicsUtil.drawText(g, "" + w1.getWidth(), bds.getX() + 10, y,
          GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER_OVERALL);
      GraphicsUtil.drawText(g, "" + w0.getWidth(), bds.getX() + bds.getWidth() - 10, y,
          GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER_OVERALL);
    } else {
      GraphicsUtil.drawText(g, "" + w1.getWidth(), bds.getX() - 6, y,
          GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER);
      GraphicsUtil.drawText(g, "" + w0.getWidth(), bds.getX() + bds.getWidth() + 6, y,
          GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER);
    }
    g.setFont(originalFont);
    painter.drawPort(0);
    painter.drawPort(1);
    if (type.equals("input")) {
      painter.drawPort(2);
    }
  }

  @Override
  public void propagate(InstanceState state) {
    Value in = state.getPortValue(1);
    BitWidth wout = state.getAttributeValue(ATTR_OUT_WIDTH);
    String type = getType(state.getAttributeSet());
    Value extend;
    if (type.equals("one")) {
      extend = Value.TRUE;
    } else if (type.equals("sign")) {
      int win = in.getWidth();
      extend = win > 0 ? in.get(win - 1) : Value.ERROR;
    } else if (type.equals("input")) {
      extend = state.getPortValue(2);
      if (extend.getWidth() != 1) extend = Value.ERROR;
    } else {
      extend = Value.FALSE;
    }

    Value out = in.extendWidth(wout.getWidth(), extend);
    state.setPort(0, out, 1);
  }
}

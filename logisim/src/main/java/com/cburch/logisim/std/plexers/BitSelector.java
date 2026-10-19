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

package com.cburch.logisim.std.plexers;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.gui.icons.PlexerIcon;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Font;

public class BitSelector extends InstanceFactory {
  public static final Attribute<BitWidth> GROUP_ATTR =
      Attributes.forBitWidth("group", S.getter("bitSelectorGroupAttr"));

  public BitSelector() {
    super("BitSelector", S.getter("bitSelectorComponent"));
    setAttributes(
        new Attribute[] {StdAttr.FACING, StdAttr.WIDTH, GROUP_ATTR, PlexerDrawing.BODY_PADDING},
        new Object[] {Direction.EAST, BitWidth.create(8), BitWidth.ONE, 20});
    setKeyConfigurator(
        JoinedConfigurator.create(
            new BitWidthConfigurator(GROUP_ATTR, 1, Value.MAX_WIDTH, 0),
            new BitWidthConfigurator(StdAttr.WIDTH)));

    setIcon(new PlexerIcon(false,true));
    setFacingAttribute(StdAttr.FACING);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
  }

  @Override
  public String getHDLName(AttributeSet attrs) {
    StringBuffer CompleteName = new StringBuffer();
    CompleteName.append(CorrectLabel.getCorrectLabel(this.getName()));
    if (attrs.getValue(GROUP_ATTR).getWidth() > 1) CompleteName.append("_bus");
    return CompleteName.toString();
  }

  @Override
  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
    if (attr == PlexerDrawing.BODY_PADDING) return 0;
    return super.getDefaultAttributeValue(attr, ver);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    Direction facing = attrs.getValue(StdAttr.FACING);
    int width = 30 + attrs.getValue(PlexerDrawing.BODY_PADDING);
    Bounds base = Bounds.create(-width, -15, width, 30);
    return base.rotate(Direction.EAST, facing, 0, 0);
  }

  @Override
  public boolean HDLSupportedComponent(String HDLIdentifier, AttributeSet attrs) {
    if (MyHDLGenerator == null) MyHDLGenerator = new BitSelectorHDLGeneratorFactory();
    return MyHDLGenerator.HDLTargetSupported(HDLIdentifier, attrs);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING || attr == PlexerDrawing.BODY_PADDING) {
      instance.recomputeBounds();
      updatePorts(instance);
    } else if (attr == StdAttr.WIDTH || attr == GROUP_ATTR) {
      updatePorts(instance);
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    Plexers.drawTrapezoid(
        painter.getGraphics(), painter.getBounds(), painter.getAttributeValue(StdAttr.FACING), 9);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    Direction facing = painter.getAttributeValue(StdAttr.FACING);

    Plexers.drawTrapezoid(g, painter.getBounds(), facing, 9);
    Bounds bds = painter.getBounds();
    g.setColor(Color.BLACK);
    Font originalFont = g.getFont();
    g.setFont(originalFont.deriveFont(facing == Direction.EAST || facing == Direction.WEST ? 12f : 10f));
    int labelY = bds.getCenterY();
    int offset = painter.getAttributeValue(PlexerDrawing.BODY_PADDING) == 0 ? 4 : 7;
    if (facing == Direction.NORTH) labelY += offset;
    else if (facing == Direction.SOUTH) labelY -= offset;
    GraphicsUtil.drawText(g, "SEL", bds.getCenterX(), labelY,
        GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER_OVERALL);
    g.setFont(originalFont);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    Value data = state.getPortValue(1);
    Value select = state.getPortValue(2);
    BitWidth groupBits = state.getAttributeValue(GROUP_ATTR);
    Value group;
    if (!select.isFullyDefined()) {
      group = Value.createUnknown(groupBits);
    } else {
      int shift = (int)select.toLongValue() * groupBits.getWidth();
      if (shift >= data.getWidth()) {
        group = Value.createKnown(groupBits, 0);
      } else if (groupBits.getWidth() == 1) {
        group = data.get(shift);
      } else {
        Value[] bits = new Value[groupBits.getWidth()];
        for (int i = 0; i < bits.length; i++) {
          if (shift + i >= data.getWidth()) {
            bits[i] = Value.FALSE;
          } else {
            bits[i] = data.get(shift + i);
          }
        }
        group = Value.create(bits);
      }
    }
    state.setPort(0, group, Plexers.DELAY);
  }

  private void updatePorts(Instance instance) {
    Direction facing = instance.getAttributeValue(StdAttr.FACING);
    BitWidth data = instance.getAttributeValue(StdAttr.WIDTH);
    BitWidth group = instance.getAttributeValue(GROUP_ATTR);
    int groups = (data.getWidth() + group.getWidth() - 1) / group.getWidth() - 1;
    int selectBits = 1;
    if (groups > 0) {
      while (groups != 1) {
        groups >>= 1;
        selectBits++;
      }
    }
    BitWidth select = BitWidth.create(selectBits);

    Location inPt;
    Location selPt;
    int padding = instance.getAttributeValue(PlexerDrawing.BODY_PADDING);
    int width = 30 + padding;
    int selector = 10 + padding / 2;
    if (facing == Direction.WEST) {
      inPt = Location.create(width, 0);
      selPt = Location.create(selector, 10);
    } else if (facing == Direction.NORTH) {
      inPt = Location.create(0, width);
      selPt = Location.create(-10, selector);
    } else if (facing == Direction.SOUTH) {
      inPt = Location.create(0, -width);
      selPt = Location.create(-10, -selector);
    } else {
      inPt = Location.create(-width, 0);
      selPt = Location.create(-selector, 10);
    }

    Port[] ps = new Port[3];
    ps[0] = new Port(0, 0, Port.OUTPUT, group.getWidth());
    ps[1] = new Port(inPt.getX(), inPt.getY(), Port.INPUT, data.getWidth());
    ps[2] = new Port(selPt.getX(), selPt.getY(), Port.INPUT, select.getWidth());
    ps[0].setToolTip(S.getter("bitSelectorOutputTip"));
    ps[1].setToolTip(S.getter("bitSelectorDataTip"));
    ps[2].setToolTip(S.getter("bitSelectorSelectTip"));
    instance.setPorts(ps);
  }
}

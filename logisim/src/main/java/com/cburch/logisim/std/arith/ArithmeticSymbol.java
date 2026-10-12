/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.arith;

import static com.cburch.logisim.std.Strings.S;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Polygon;

/** Readable symbols for newly placed components; saved legacy layouts keep their ports. */
final class ArithmeticSymbol {
  static final Attribute<Boolean> SPACIOUS = new Attribute<Boolean>(
      "teachingSymbol", S.getter("stdAppearanceAttr")) {
    @Override
    public Boolean parse(String value) { return Boolean.valueOf(value); }
    @Override
    public boolean isHidden() { return true; }
  };

  private ArithmeticSymbol() {}

  static Polygon aluOutline(int x, int y) {
    int[] xs = {-50, 15, 50, 50, 15, -50, -50, -30, -30, -50};
    int[] ys = {-70, -70, -20, 20, 70, 70, 35, 15, -15, -35};
    for (int i = 0; i < xs.length; i++) { xs[i] += x; ys[i] += y; }
    return new Polygon(xs, ys, xs.length);
  }

  static void drawAluOutline(InstancePainter painter) {
    Location loc = painter.getLocation();
    Graphics g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);
    g.drawPolygon(aluOutline(loc.getX(), loc.getY()));
    GraphicsUtil.switchToWidth(g, 1);
  }

  static void text(Graphics g, String label, int x, int y, int align) {
    GraphicsUtil.drawText(g, label, x, y, align, GraphicsUtil.V_CENTER_OVERALL);
  }

  static void drawAlu(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    Font font = g.getFont();
    Color color = g.getColor();
    Location loc = painter.getLocation();
    int x = loc.getX(), y = loc.getY();
    boolean spacious = painter.getAttributeValue(SPACIOUS);
    g.setColor(Color.BLACK);
    if (spacious) drawAluOutline(painter);
    else painter.drawBounds();
    g.setFont(font.deriveFont(spacious ? 10f : 9f));
    if (spacious) {
      text(g, "A", x - 42, y - 50, GraphicsUtil.H_LEFT);
      text(g, "B", x - 42, y + 50, GraphicsUtil.H_LEFT);
      text(g, "CTR", x - 20, y - 56, GraphicsUtil.H_CENTER);
      text(g, "Ans", x + 43, y, GraphicsUtil.H_RIGHT);
      text(g, "Carry", x - 20, y + 56, GraphicsUtil.H_CENTER);
    } else {
      text(g, "A", x - 52, y - 10, GraphicsUtil.H_LEFT);
      text(g, "B", x - 52, y + 10, GraphicsUtil.H_LEFT);
      text(g, "CTR", x, y - 19, GraphicsUtil.H_CENTER);
      text(g, "Ans", x + 52, y - 10, GraphicsUtil.H_RIGHT);
      text(g, "Carry", x + 52, y + 10, GraphicsUtil.H_RIGHT);
    }
    g.setFont(font.deriveFont(spacious ? 20f : 14f));
    g.setColor(new Color(0, 120, 48));
    text(g, "ALU", x, y, GraphicsUtil.H_CENTER);
    g.setFont(font);
    g.setColor(color);
    painter.drawPorts();
  }

  static void drawAdder(InstancePainter painter, boolean subtract) {
    Graphics g = painter.getGraphics();
    Font font = g.getFont();
    Color color = g.getColor();
    Bounds b = painter.getBounds();
    int x = b.getCenterX(), y = b.getCenterY();
    boolean spacious = painter.getAttributeValue(SPACIOUS);
    g.setColor(Color.BLACK);
    painter.drawBounds();
    g.setFont(font.deriveFont(spacious ? 10f : 8f));
    if (spacious) {
      text(g, "A", b.getX() + 8, y - 20, GraphicsUtil.H_LEFT);
      text(g, "B", b.getX() + 8, y + 20, GraphicsUtil.H_LEFT);
    }
    int labelY = spacious ? 18 : 13;
    text(g, subtract ? "Bin" : "Cin", x, y - labelY, GraphicsUtil.H_CENTER);
    text(g, subtract ? "Bout" : "Cout", x, y + labelY, GraphicsUtil.H_CENTER);
    g.setFont(font.deriveFont(spacious ? 9f : 8f));
    if (spacious) {
      text(g, subtract ? "Diff" : "Sum", b.getX() + b.getWidth() - 8, y, GraphicsUtil.H_RIGHT);
    } else {
      // Narrow legacy symbols retain their connected ports; put the output name outside.
      text(g, subtract ? "Diff" : "Sum", b.getX() + b.getWidth() + 8, y - 8, GraphicsUtil.H_LEFT);
    }
    g.setFont(font.deriveFont(spacious ? 22f : 16f));
    text(g, subtract ? "−" : "+", x, y, GraphicsUtil.H_CENTER);
    g.setFont(font);
    g.setColor(color);
    painter.drawPorts();
  }
}

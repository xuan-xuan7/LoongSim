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

package com.cburch.logisim.comp;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;

public class ComponentDrawContext {
  private static final int PIN_RADIUS = 3;
  private static final int PIN_DIAMETER = 2 * PIN_RADIUS + 1;
  private static final Color PIN_COLOR = new Color(0, 120, 48);

  private java.awt.Component dest;
  private Circuit circuit;
  private CircuitState circuitState;
  private Graphics base;
  private Graphics g;
  private boolean showState;
  private boolean showColor;
  private boolean printView;
  private WireSet highlightedWires;
  private InstancePainter instancePainter;

  public ComponentDrawContext(
      java.awt.Component dest,
      Circuit circuit,
      CircuitState circuitState,
      Graphics base,
      Graphics g) {
    this(dest, circuit, circuitState, base, g, false);
  }

  public ComponentDrawContext(
      java.awt.Component dest,
      Circuit circuit,
      CircuitState circuitState,
      Graphics base,
      Graphics g,
      boolean printView) {
    this.dest = dest;
    this.circuit = circuit;
    this.circuitState = circuitState;
    this.base = base;
    this.g = g;
    this.showState = true;
    this.showColor = true;
    this.printView = printView;
    this.highlightedWires = WireSet.EMPTY;
    this.instancePainter = new InstancePainter(this, null);
  }

  //
  // helper methods
  //
  public void drawBounds(Component comp) {
    GraphicsUtil.switchToWidth(g, 2);
    g.setColor(Color.BLACK);
    Bounds bds = comp.getBounds();
    g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    GraphicsUtil.switchToWidth(g, 1);
  }

  public void drawClock(Component comp, int i, Direction dir) {
    Color curColor = g.getColor();
    g.setColor(Color.BLACK);
    GraphicsUtil.switchToWidth(g, 2);

    EndData e = comp.getEnd(i);
    Location pt = e.getLocation();
    drawConnectionPoint(pt);
    int x = pt.getX();
    int y = pt.getY();
    final int CLK_BASE = PIN_RADIUS + 1;
    final int CLK_TIP = CLK_BASE + 3;
    final int CLK_HALF_HEIGHT = 3;
    if (dir == Direction.NORTH) {
      g.drawLine(x - CLK_HALF_HEIGHT, y - CLK_BASE, x, y - CLK_TIP);
      g.drawLine(x + CLK_HALF_HEIGHT, y - CLK_BASE, x, y - CLK_TIP);
    } else if (dir == Direction.SOUTH) {
      g.drawLine(x - CLK_HALF_HEIGHT, y + CLK_BASE, x, y + CLK_TIP);
      g.drawLine(x + CLK_HALF_HEIGHT, y + CLK_BASE, x, y + CLK_TIP);
    } else if (dir == Direction.EAST) {
      g.drawLine(x + CLK_BASE, y - CLK_HALF_HEIGHT, x + CLK_TIP, y);
      g.drawLine(x + CLK_BASE, y + CLK_HALF_HEIGHT, x + CLK_TIP, y);
    } else if (dir == Direction.WEST) {
      g.drawLine(x - CLK_BASE, y - CLK_HALF_HEIGHT, x - CLK_TIP, y);
      g.drawLine(x - CLK_BASE, y + CLK_HALF_HEIGHT, x - CLK_TIP, y);
    }

    g.setColor(curColor);
    GraphicsUtil.switchToWidth(g, 1);
  }

  public void drawClockSymbol(Component comp, int xpos, int ypos) {
    GraphicsUtil.switchToWidth(g, 2);
    g.drawLine(xpos, ypos - 4, xpos + 8, ypos);
    g.drawLine(xpos, ypos + 4, xpos + 8, ypos);
    GraphicsUtil.switchToWidth(g, 1);
  }

  public void drawDongle(int x, int y) {
    GraphicsUtil.switchToWidth(g, 2);
    g.drawOval(x - 4, y - 4, 9, 9);
  }

  public void drawHandle(int x, int y) {
    g.setColor(Color.white);
    g.fillRect(x - 3, y - 3, 7, 7);
    g.setColor(Color.black);
    g.drawRect(x - 3, y - 3, 7, 7);
  }

  public void drawHandle(Location loc) {
    drawHandle(loc.getX(), loc.getY());
  }

  public void drawHandles(Component comp) {
    Bounds b = comp.getBounds(g);
    int left = b.getX();
    int right = left + b.getWidth();
    int top = b.getY();
    int bot = top + b.getHeight();
    drawHandle(right, top);
    drawHandle(left, bot);
    drawHandle(right, bot);
    drawHandle(left, top);
  }

  private void drawConnectionPoint(Location pt) {
    Color curColor = g.getColor();
    if (isPrintView()) {
      g.setColor(Color.BLACK);
      g.fillOval(pt.getX() - 2, pt.getY() - 2, 4, 4);
    } else {
      // A fixed marker identifies the connection target; wires still show their signal colors.
      g.setColor(Color.WHITE);
      g.fillOval(pt.getX() - PIN_RADIUS - 1, pt.getY() - PIN_RADIUS - 1,
          PIN_DIAMETER + 2, PIN_DIAMETER + 2);
      Color color = shouldDrawColor() ? PIN_COLOR : Color.BLACK;
      if (shouldDrawColor() && getShowState() && circuitState != null
          && circuitState.getValue(pt).isErrorValue()) {
        color = circuitState.getValue(pt).getColor();
      }
      g.setColor(color);
      g.fillOval(pt.getX() - PIN_RADIUS, pt.getY() - PIN_RADIUS, PIN_DIAMETER, PIN_DIAMETER);
    }
    g.setColor(curColor);
  }

  public void drawPin(Component comp, int i) {
    drawConnectionPoint(comp.getEnd(i).getLocation());
  }

  public void drawPin(Component comp, int i, String label, Direction dir) {
    if (i < 0 || i >= comp.getEnds().size()) return;
    EndData e = comp.getEnd(i);
    Location pt = e.getLocation();
    int x = pt.getX();
    int y = pt.getY();
    drawConnectionPoint(pt);
    int labelOffset = isPrintView() ? 3 : PIN_RADIUS + 3;
    if (dir == Direction.EAST) {
      GraphicsUtil.drawText(g, label, x + labelOffset, y, GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER);
    } else if (dir == Direction.WEST) {
      GraphicsUtil.drawText(g, label, x - labelOffset, y, GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER);
    } else if (dir == Direction.SOUTH) {
      GraphicsUtil.drawText(g, label, x, y - labelOffset, GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE);
    } else if (dir == Direction.NORTH) {
      GraphicsUtil.drawText(g, label, x, y + labelOffset, GraphicsUtil.H_CENTER, GraphicsUtil.V_TOP);
    }
  }

  public void drawPins(Component comp) {
    for (EndData e : comp.getEnds()) {
      drawConnectionPoint(e.getLocation());
    }
  }

  public void drawRectangle(Component comp) {
    drawRectangle(comp, "");
  }

  public void drawRectangle(Component comp, String label) {
    Bounds bds = comp.getBounds(g);
    drawRectangle(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight(), label);
  }

  public void drawRectangle(
      ComponentFactory source, int x, int y, AttributeSet attrs, String label) {
    Bounds bds = source.getOffsetBounds(attrs);
    drawRectangle(source, x + bds.getX(), y + bds.getY(), bds.getWidth(), bds.getHeight(), label);
  }

  public void drawRectangle(
      ComponentFactory source, int x, int y, int width, int height, String label) {
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(x + 1, y + 1, width - 1, height - 1);
    if (label != null && !label.equals("")) {
      FontMetrics fm = base.getFontMetrics(g.getFont());
      int lwid = fm.stringWidth(label);
      if (height > 20) { // centered at top edge
        g.drawString(label, x + (width - lwid) / 2, y + 2 + fm.getAscent());
      } else { // centered overall
        g.drawString(label, x + (width - lwid) / 2, y + (height + fm.getAscent()) / 2 - 1);
      }
    }
  }

  public void drawRectangle(int x, int y, int width, int height, String label) {
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(x, y, width, height);
    if (label != null && !label.equals("")) {
      FontMetrics fm = base.getFontMetrics(g.getFont());
      int lwid = fm.stringWidth(label);
      if (height > 20) { // centered at top edge
        g.drawString(label, x + (width - lwid) / 2, y + 2 + fm.getAscent());
      } else { // centered overall
        g.drawString(label, x + (width - lwid) / 2, y + (height + fm.getAscent()) / 2 - 1);
      }
    }
  }

  public Circuit getCircuit() {
    return circuit;
  }

  public CircuitState getCircuitState() {
    return circuitState;
  }

  public java.awt.Component getDestination() {
    return dest;
  }

  public Object getGateShape() {
    return AppPreferences.GATE_SHAPE.get();
  }

  public Graphics getGraphics() {
    return g;
  }

  public WireSet getHighlightedWires() {
    return highlightedWires;
  }

  public InstancePainter getInstancePainter() {
    return instancePainter;
  }

  public boolean getShowState() {
    return !printView && showState;
  }

  public boolean isPrintView() {
    return printView;
  }

  public void setGraphics(Graphics g) {
    this.g = g;
  }

  public void setHighlightedWires(WireSet value) {
    this.highlightedWires = value == null ? WireSet.EMPTY : value;
  }

  public void setShowColor(boolean value) {
    showColor = value;
  }

  public void setShowState(boolean value) {
    showState = value;
  }

  public boolean shouldDrawColor() {
    return !printView && showColor;
  }

  public void drawRoundBounds(Component comp, Bounds bds, Color color) {
    GraphicsUtil.switchToWidth(g, 2);
    if (color != null && !color.equals(Color.WHITE)) {
      g.setColor(color);
      g.fillRoundRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight(), 10, 10);
    }
    g.setColor(Color.BLACK);
    g.drawRoundRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight(), 10, 10);
    GraphicsUtil.switchToWidth(g, 1);
  }

  public void drawRoundBounds(Component comp, Color color) {
    drawRoundBounds(comp, comp.getBounds(), color);
  }
}

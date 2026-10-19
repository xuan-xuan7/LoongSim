/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.plexers;

import static com.cburch.logisim.std.Strings.S;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

/** Shared spacing and text layout for the teaching toolbox's selection components. */
final class PlexerDrawing {
  static final Attribute<Integer> BODY_PADDING = new Attribute<Integer>(
      "bodyPadding", S.getter("gateSizeAttr")) {
    @Override
    public Integer parse(String value) {
      int padding = Integer.parseInt(value);
      if (padding != 0 && padding != 20) throw new NumberFormatException("Invalid plexer padding");
      return padding;
    }

    @Override
    public boolean isHidden() { return true; }
  };

  private PlexerDrawing() {}

  static void drawNumbers(InstancePainter painter, int count, Direction inside) {
    Graphics g = painter.getGraphics();
    Font originalFont = g.getFont();
    Color originalColor = g.getColor();
    g.setColor(Color.BLACK);
    g.setFont(originalFont.deriveFont(count == 2 ? 10f : 9f));
    for (int i = 0; i < count; i++) {
      Location pt = painter.getInstance().getPortLocation(i);
      if (inside == Direction.WEST) {
        GraphicsUtil.drawText(g, "" + i, pt.getX() - 5, pt.getY(),
            GraphicsUtil.H_RIGHT, GraphicsUtil.V_CENTER_OVERALL);
      } else if (inside == Direction.NORTH) {
        GraphicsUtil.drawText(g, "" + i, pt.getX(), pt.getY() - 5,
            GraphicsUtil.H_CENTER, GraphicsUtil.V_BOTTOM);
      } else if (inside == Direction.SOUTH) {
        GraphicsUtil.drawText(g, "" + i, pt.getX(), pt.getY() + 5,
            GraphicsUtil.H_CENTER, GraphicsUtil.V_TOP);
      } else {
        GraphicsUtil.drawText(g, "" + i, pt.getX() + 5, pt.getY(),
            GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER_OVERALL);
      }
    }
    g.setFont(originalFont);
    g.setColor(originalColor);
  }

  static void drawLabel(InstancePainter painter, String text, Direction awayFromNumbers) {
    Graphics g = painter.getGraphics();
    Bounds bds = painter.getBounds();
    boolean horizontal = awayFromNumbers == Direction.EAST || awayFromNumbers == Direction.WEST;
    int depth = horizontal ? bds.getWidth() : bds.getHeight();
    int offset = depth <= 40 ? 3 : 5;
    int x = bds.getCenterX(), y = bds.getCenterY();
    if (awayFromNumbers == Direction.EAST) x += offset;
    else if (awayFromNumbers == Direction.WEST) x -= offset;
    else if (awayFromNumbers == Direction.NORTH) y -= offset;
    else y += offset;
    Font originalFont = g.getFont();
    Color originalColor = g.getColor();
    g.setFont(originalFont.deriveFont(!horizontal ? 10f : depth <= 40 ? 9f : 12f));
    g.setColor(Color.BLACK);
    GraphicsUtil.drawText(g, text, x, y, GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER_OVERALL);
    g.setFont(originalFont);
    g.setColor(originalColor);
  }
}

package com.cburch.logisim.gui.main;

import com.cburch.logisim.fpga.loongsim.LoongSimBoardCatalog.Area;
import com.cburch.logisim.fpga.loongsim.LoongSimBoardCatalog;
import com.cburch.logisim.fpga.loongsim.FpgaResourcePosition;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** Read-only board reference. Resource selection happens in the resource list. */
final class FpgaBoardView extends JPanel {
  private static final long serialVersionUID = 1L;
  private static final Color BLUE = new Color(29, 78, 216);
  private static final Color SELECTED = new Color(180, 83, 9);
  private final Image image;
  private Area highlighted;
  private FpgaResourcePosition resourcePosition;

  FpgaBoardView(Image image) {
    this.image = image;
    setBackground(Color.WHITE);
    setPreferredSize(new Dimension(780, 350));
    getAccessibleContext().setAccessibleName("A7 板卡资源位置参考图");
  }

  void highlight(Area area) {
    highlighted = area;
    resourcePosition = null;
    repaint();
  }

  void highlightResource(String resourceId) {
    highlighted = resourceId == null ? null : LoongSimBoardCatalog.areaFor(resourceId);
    resourcePosition = FpgaResourcePosition.forResource(resourceId);
    repaint();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      int legendWidth = 226;
      double scale = Math.min((getWidth() - legendWidth - 16)
          / (double) LoongSimBoardCatalog.IMAGE_WIDTH,
          (getHeight() - 8) / (double) LoongSimBoardCatalog.IMAGE_HEIGHT);
      scale = Math.max(0.01, scale);
      int imageWidth = (int) (LoongSimBoardCatalog.IMAGE_WIDTH * scale);
      int imageHeight = (int) (LoongSimBoardCatalog.IMAGE_HEIGHT * scale);
      int originX = 4;
      int originY = (getHeight() - imageHeight) / 2;
      if (image != null) {
        g.drawImage(image, originX, originY, imageWidth, imageHeight, this);
        drawKeypadGuide(g, originX, originY, scale);
        for (Area area : Area.values()) {
          if (!area.hasImageLocation()) continue;
          int x = originX + (int) (area.getX() * scale);
          int y = originY + (int) (area.getY() * scale);
          int width = (int) (area.getWidth() * scale);
          int height = (int) (area.getHeight() * scale);
          Color color = area == highlighted ? SELECTED : BLUE;
          if (area == highlighted) {
            g.setColor(new Color(251, 191, 36, 90));
            g.fillRect(x, y, width, height);
          }
          g.setColor(Color.WHITE);
          g.setStroke(new BasicStroke(area == highlighted ? 5f : 4f));
          g.drawRect(x, y, width, height);
          g.setColor(color);
          g.setStroke(new BasicStroke(area == highlighted ? 3f : 2f));
          g.drawRect(x, y, width, height);
          int badgeX = area == Area.LED ? x + width - 26 : x;
          badge(g, area.getNumber(), badgeX, Math.max(1, y - (area == Area.KEYPAD ? 25 : 12)), color);
        }
        if (resourcePosition != null) drawResource(g, originX, originY, scale);
      } else {
        g.setColor(Color.DARK_GRAY);
        g.drawString("未找到板卡照片，请按右侧资源说明选择。", 12, getHeight() / 2);
      }
      int legendX = originX + imageWidth + 14;
      g.setFont(getFont().deriveFont(Font.BOLD, 13f));
      g.setColor(new Color(15, 23, 42));
      g.drawString("图中编号与资源列表对应", legendX, 24);
      int rowY = 50;
      for (Area area : Area.values()) {
        Color color = area == highlighted ? SELECTED : BLUE;
        if (area == highlighted) {
          g.setColor(new Color(254, 243, 199));
          g.fillRoundRect(legendX - 4, rowY - 13, legendWidth - 8, 28, 6, 6);
        }
        badge(g, area.getNumber(), legendX, rowY - 11, color);
        g.setFont(getFont().deriveFont(area == highlighted ? Font.BOLD : Font.PLAIN, 12f));
        g.setColor(area == highlighted ? SELECTED : new Color(30, 41, 59));
        g.drawString(area.getLabel(), legendX + 30, rowY + 5);
        rowY += 32;
      }
      g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
      if (resourcePosition != null) {
        g.setColor(new Color(185, 28, 28));
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g.drawString(resourcePosition.getLabel() + " · " + resourcePosition.getBrief(), legendX, rowY + 4);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        g.setColor(new Color(100, 116, 139));
        g.drawString("位置以图中板卡朝向为准。", legendX, rowY + 23);
      } else {
        g.setColor(new Color(100, 116, 139));
        g.drawString("移到具体资源上可查看单个位置。", legendX, rowY + 4);
        g.drawString("01 为板载时钟，照片中未标定位。", legendX, rowY + 23);
      }
    } finally {
      g.dispose();
    }
  }

  private void drawResource(Graphics2D g, int originX, int originY, double scale) {
    int x = originX + (int) Math.round(resourcePosition.getX() * scale);
    int y = originY + (int) Math.round(resourcePosition.getY() * scale);
    int width = Math.max(8, (int) Math.round(resourcePosition.getWidth() * scale));
    int height = Math.max(8, (int) Math.round(resourcePosition.getHeight() * scale));
    g.setColor(new Color(254, 240, 138, 150));
    g.fillRoundRect(x, y, width, height, 4, 4);
    g.setStroke(new BasicStroke(5f));
    g.setColor(Color.WHITE);
    g.drawRoundRect(x, y, width, height, 4, 4);
    g.setStroke(new BasicStroke(2.5f));
    g.setColor(new Color(220, 38, 38));
    g.drawRoundRect(x, y, width, height, 4, 4);
    String label = resourcePosition.getArea() == Area.KEYPAD
        ? resourcePosition.getBrief() : resourcePosition.getLabel();
    g.setFont(getFont().deriveFont(Font.BOLD, 12f));
    int labelWidth = g.getFontMetrics().stringWidth(label) + 12;
    int labelX = Math.max(originX, x + width / 2 - labelWidth / 2);
    int labelY = Math.max(2, y - 24);
    g.fillRoundRect(labelX, labelY, labelWidth, 21, 5, 5);
    g.setColor(Color.WHITE);
    g.drawString(label, labelX + 6, labelY + 15);
  }

  /** The cleaned reference photo is not a wiring diagram; show the verified 4x4 layout explicitly. */
  private void drawKeypadGuide(Graphics2D g, int originX, int originY, double scale) {
    Area area = Area.KEYPAD;
    int x = originX + (int) (area.getX() * scale), y = originY + (int) (area.getY() * scale);
    int width = (int) (area.getWidth() * scale), height = (int) (area.getHeight() * scale);
    g.setColor(new Color(226, 232, 240));
    g.fillRect(x, y, width, height);
    g.setFont(getFont().deriveFont(Font.BOLD, 9f));
    for (int row = 0; row < 4; row++) {
      for (int column = 0; column < 4; column++) {
        int left = x + column * width / 4, top = y + row * height / 4;
        int cellWidth = (column + 1) * width / 4 - column * width / 4;
        int cellHeight = (row + 1) * height / 4 - row * height / 4;
        g.setColor(new Color(248, 250, 252));
        g.fillRoundRect(left + 2, top + 2, cellWidth - 4, cellHeight - 4, 3, 3);
        g.setColor(new Color(71, 85, 105));
        String text = (row + 1) + "-" + (column + 1);
        g.drawString(text, left + (cellWidth - g.getFontMetrics().stringWidth(text)) / 2,
            top + (cellHeight + g.getFontMetrics().getAscent()) / 2 - 2);
      }
    }
  }

  private static void badge(Graphics2D g, String number, int x, int y, Color color) {
    g.setColor(color);
    g.fillRoundRect(x, y, 26, 23, 7, 7);
    g.setColor(Color.WHITE);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
    g.drawString(number, x + 5, y + 16);
  }
}

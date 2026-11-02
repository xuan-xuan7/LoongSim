package com.cburch.logisim.fpga.loongsim;

import com.cburch.logisim.fpga.loongsim.LoongSimBoardCatalog.Area;

/** Physical positions in the upright A7 photo, independent of legacy HDL bus ordering. */
public final class FpgaResourcePosition {
  private final Area area;
  private final String label, description, brief;
  private final int column, row, columns, rows;

  private FpgaResourcePosition(Area area, String label, String description, String brief,
      int column, int row, int columns, int rows) {
    this.area = area;
    this.label = label;
    this.description = description;
    this.brief = brief;
    this.column = column;
    this.row = row;
    this.columns = columns;
    this.rows = rows;
  }

  public Area getArea() { return area; }
  public String getLabel() { return label; }
  public String getDescription() { return description; }
  public String getBrief() { return brief; }
  public int getColumn() { return column; }
  public int getRow() { return row; }
  public int getColumns() { return columns; }
  public int getRows() { return rows; }
  public double getX() { return area.getX() + column * area.getWidth() / (double) columns; }
  public double getY() { return area.getY() + row * area.getHeight() / (double) rows; }
  public double getWidth() { return area.getWidth() / (double) columns; }
  public double getHeight() { return area.getHeight() / (double) rows; }

  public static FpgaResourcePosition forResource(String id) {
    if (id == null) return null;
    if (id.matches("switch\\[[0-7]\\]")) {
      int index = 7 - bit(id);
      return horizontal(Area.SWITCH, "SW" + index, "拨码开关这一排", index, 8);
    }
    if (id.matches("led\\[(?:[0-9]|1[0-5])\\]")) {
      int index = 15 - bit(id);
      return horizontal(Area.LED, "LED" + (index + 1), "单色 LED 这一排", index, 16);
    }
    if (LoongSimBoardCatalog.isDisplay(id)) {
      int index = LoongSimBoardCatalog.displayPosition(id);
      return horizontal(Area.DISPLAY, "数码管 " + index, "数码管这一排", index, 8);
    }
    if (id.matches("btn_step\\[[01]\\]")) {
      int index = bit(id), row = 1 - index;
      String side = index == 0 ? "下方" : "上方";
      return new FpgaResourcePosition(Area.STEP, "按钮 " + index,
          "拨码开关右侧，两个按钮中" + side + "的一个", "两个按钮中" + side + "的一个", 0, row, 1, 2);
    }
    if (id.equals("resetn")) {
      return new FpgaResourcePosition(Area.RESET, "复位键", "拨码开关左侧的独立按钮", "拨码开关左侧", 0, 0, 1, 1);
    }
    if (LoongSimBoardCatalog.isKeypadKey(id)) {
      int key = LoongSimBoardCatalog.keypadIndex(id), row = key / 4, column = key % 4;
      String position = "第" + (row + 1) + "行第" + (column + 1) + "列";
      return new FpgaResourcePosition(Area.KEYPAD, "矩阵按键 " + (key + 1),
          "矩阵键盘从上往下" + position + "（列从左往右数）", position, column, row, 4, 4);
    }
    // Clock has no verified photo coordinate. Color channels are not assigned an unverified left/right position.
    return null;
  }

  private static FpgaResourcePosition horizontal(Area area, String label, String group, int index, int count) {
    String position = "左起第" + (index + 1) + "个" + (index == 0 ? "（最左）" : index == count - 1 ? "（最右）" : "");
    return new FpgaResourcePosition(area, label, group + "，" + position, position, index, 0, count, 1);
  }

  private static int bit(String id) { return Integer.parseInt(id.substring(id.indexOf('[') + 1, id.indexOf(']'))); }
}

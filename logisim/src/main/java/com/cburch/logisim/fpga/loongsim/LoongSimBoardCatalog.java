package com.cburch.logisim.fpga.loongsim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed A7 board resources used by the first photo-based mapping workflow. */
public final class LoongSimBoardCatalog {
  public static final String BOARD_ID = "loongson-a7-fbg676-1";
  public static final String DEVICE = "xc7a200tfbgb676-1";
  public static final String IMAGE_RESOURCE = "fpga/boards/loongson-a7/board-front.png";

  private LoongSimBoardCatalog() {}

  public static List<FpgaResource> resources() {
    List<FpgaResource> resources = new ArrayList<>();
    add(resources, "clk", "系统时钟", "时钟", FpgaResource.Direction.INPUT, 1, 275, 321, 26, 26);
    add(resources, "resetn", "复位键", "按键", FpgaResource.Direction.INPUT, 1, 58, 398, 70, 32);
    addBus(resources, "switch", "拨码开关", "拨码", FpgaResource.Direction.INPUT, 8, 250, 388, 160, 35);
    addBus(resources, "btn_key_col", "矩阵键盘列", "矩阵键盘", FpgaResource.Direction.INPUT, 4, 440, 350, 90, 72);
    addBus(resources, "btn_key_row", "矩阵键盘行", "矩阵键盘", FpgaResource.Direction.OUTPUT, 4, 440, 350, 90, 72);
    addBus(resources, "btn_step", "步进按键", "按键", FpgaResource.Direction.INPUT, 2, 155, 397, 65, 30);
    addBus(resources, "led", "单色 LED", "LED", FpgaResource.Direction.OUTPUT, 16, 240, 78, 240, 28);
    addBus(resources, "led_rg0", "双色 LED 红绿组 0", "LED", FpgaResource.Direction.OUTPUT, 2, 114, 35, 38, 28);
    addBus(resources, "led_rg1", "双色 LED 红绿组 1", "LED", FpgaResource.Direction.OUTPUT, 2, 164, 35, 38, 28);
    addBus(resources, "num_a_g", "数码管段选", "数码管", FpgaResource.Direction.OUTPUT, 7, 181, 111, 75, 36);
    addBus(resources, "num_csn", "数码管位选", "数码管", FpgaResource.Direction.OUTPUT, 8, 267, 111, 100, 36);
    addBus(resources, "num_data", "TFT/视频数据", "显示接口", FpgaResource.Direction.OUTPUT, 32, 380, 200, 120, 100);
    return Collections.unmodifiableList(resources);
  }

  private static void add(List<FpgaResource> resources, String id, String label, String group,
      FpgaResource.Direction direction, int width, int x, int y, int imageWidth, int imageHeight) {
    resources.add(new FpgaResource(id, label, group, direction, width, x, y, imageWidth,
        imageHeight));
  }

  private static void addBus(List<FpgaResource> resources, String id, String label, String group,
      FpgaResource.Direction direction, int width, int x, int y, int imageWidth,
      int imageHeight) {
    add(resources, id, label + "（总线）", group, direction, width, x, y, imageWidth, imageHeight);
    for (int bit = 0; bit < width; bit++) {
      add(resources, id + "[" + bit + "]", label + "[" + bit + "]", group, direction, 1,
          x + (bit * Math.max(1, imageWidth / width)), y, Math.max(12, imageWidth / width),
          imageHeight);
    }
  }
}

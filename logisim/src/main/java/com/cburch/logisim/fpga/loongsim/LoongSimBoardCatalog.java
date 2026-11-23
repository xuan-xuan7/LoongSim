package com.cburch.logisim.fpga.loongsim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Verified A7 resources; the photo is a reference for locating these resources. */
public final class LoongSimBoardCatalog {
  public static final String BOARD_ID = "loongson-a7-fbg676-1";
  public static final String DEVICE = "xc7a200tfbg676-1";
  public static final String DEVICE_LABEL = "XC7A200TFBG676";
  public static final String IMAGE_RESOURCE = "fpga/boards/loongson-a7/board-front.png";
  public static final int IMAGE_WIDTH = 1445;
  public static final int IMAGE_HEIGHT = 1088;

  private LoongSimBoardCatalog() {}

  /** Coordinates refer to the cleaned reference photo, never to package pins. */
  public enum Area {
    CLOCK("01", "系统时钟 · 100 MHz", -1, -1, 0, 0),
    RESET("02", "复位键", 313, 948, 48, 67),
    SWITCH("03", "拨码开关 · SW0–SW7", 380, 945, 371, 68),
    STEP("04", "按钮 · 2 个", 767, 900, 66, 114),
    LED("05", "单色 LED · LED1–LED16", 293, 125, 500, 45),
    COLOR_LED("06", "双色 LED · 2 组", 292, 45, 94, 61),
    DISPLAY("07", "数码管 · 选择显示位置", 409, 174, 404, 106),
    KEYPAD("08", "矩阵键盘 · 16 个按键", 860, 793, 423, 219);

    private final String number;
    private final String label;
    private final int x, y, width, height;

    Area(String number, String label, int x, int y, int width, int height) {
      this.number = number;
      this.label = label;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    public String getNumber() { return number; }
    public String getLabel() { return label; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean hasImageLocation() { return x >= 0 && y >= 0; }
  }

  public static Area areaFor(String resourceId) {
    String base = resourceId.replaceFirst("\\[.*$", "");
    switch (base) {
      case "clk": return Area.CLOCK;
      case "resetn": return Area.RESET;
      case "switch": return Area.SWITCH;
      case "btn_step": return Area.STEP;
      case "led": return Area.LED;
      case "led_rg0":
      case "led_rg1": return Area.COLOR_LED;
      case "num_a_g":
      case "num_csn":
      case "digit": return Area.DISPLAY;
      case "btn_key_col":
      case "btn_key_row":
      case "keypad": return Area.KEYPAD;
      default: throw new IllegalArgumentException("Unknown A7 resource: " + resourceId);
    }
  }

  public static boolean isActiveLowLed(String resourceId) {
    return resourceId != null && (resourceId.equals("led") || resourceId.matches("led\\[\\d+\\]"));
  }

  public static boolean isKeypadKey(String resourceId) {
    return resourceId != null && resourceId.matches("keypad\\[(?:[0-9]|1[0-5])\\]");
  }

  public static int keypadIndex(String resourceId) {
    if (!isKeypadKey(resourceId)) throw new IllegalArgumentException("Invalid keypad resource: " + resourceId);
    return Integer.parseInt(resourceId.substring(7, resourceId.length() - 1));
  }

  public static boolean isVirtualResource(String resourceId) {
    return isDisplay(resourceId) || isKeypadKey(resourceId);
  }

  public static boolean isDisplay(String resourceId) {
    return resourceId != null && resourceId.matches("digit\\[[0-7]\\](?:\\.[a-g]|\\.hex)?");
  }

  public static int displayPosition(String resourceId) {
    if (!isDisplay(resourceId)) throw new IllegalArgumentException("Invalid display resource: " + resourceId);
    return resourceId.charAt(6) - '0';
  }

  /** 0 = a (top), ..., 6 = g (middle); -1 means a complete display. */
  public static int displaySegment(String resourceId) {
    return isDisplay(resourceId) && resourceId.matches(".*\\.[a-g]")
        ? resourceId.charAt(resourceId.length() - 1) - 'a' : -1;
  }

  public static boolean isHexDisplay(String resourceId) {
    return isDisplay(resourceId) && resourceId.endsWith(".hex");
  }

  /** Display components carry named segments; a plain 7-bit port uses bit 6=a, bit 0=g. */
  public static int segmentForBit(FpgaPort port, int offset) {
    String label = port.getBitLabels().get(port.getLowBit() + offset);
    if (label != null && label.matches(".*\\[Segment_[A-G]\\]$")) {
      return label.charAt(label.length() - 2) - 'A';
    }
    return 6 - offset;
  }

  /** Student-facing choices: individual resources and complete display positions. */
  public static List<FpgaResource> mappingResources() {
    List<FpgaResource> visible = new ArrayList<>();
    for (FpgaResource resource : resources()) {
      if (isVirtualResource(resource.getId()) || (resource.getWidth() == 1
          && !resource.getId().startsWith("num_") && !resource.getId().startsWith("btn_key_"))) visible.add(resource);
    }
    visible.sort(java.util.Comparator.comparingInt((FpgaResource resource) -> areaFor(resource.getId()).ordinal())
        .thenComparingInt(resource -> {
          String id = resource.getId();
          int bracket = id.indexOf('[');
          if (bracket < 0) return 0;
          int bit = Integer.parseInt(id.substring(bracket + 1, id.indexOf(']', bracket)));
          return id.startsWith("switch[") || id.startsWith("led[") ? -bit : bit;
        }));
    return Collections.unmodifiableList(visible);
  }

  public static List<FpgaResource> resources() {
    List<FpgaResource> resources = new ArrayList<>();
    add(resources, "clk", "系统时钟", "时钟", FpgaResource.Direction.INPUT, 1);
    add(resources, "resetn", "复位键", "按键", FpgaResource.Direction.INPUT, 1);
    addBus(resources, "switch", "拨码开关", "拨码", FpgaResource.Direction.INPUT, 8);
    addBus(resources, "btn_step", "按钮", "按键", FpgaResource.Direction.INPUT, 2);
    addBus(resources, "led", "单色 LED", "LED", FpgaResource.Direction.OUTPUT, 16);
    addBus(resources, "led_rg0", "双色 LED 红绿组 0", "LED", FpgaResource.Direction.OUTPUT, 2);
    addBus(resources, "led_rg1", "双色 LED 红绿组 1", "LED", FpgaResource.Direction.OUTPUT, 2);
    addBus(resources, "num_a_g", "数码管段选", "数码管", FpgaResource.Direction.OUTPUT, 7);
    addBus(resources, "num_csn", "数码管位选", "数码管", FpgaResource.Direction.OUTPUT, 8);
    addBus(resources, "btn_key_col", "矩阵键盘列", "矩阵键盘", FpgaResource.Direction.INPUT, 4);
    addBus(resources, "btn_key_row", "矩阵键盘行", "矩阵键盘", FpgaResource.Direction.OUTPUT, 4);
    for (int key = 0; key < 16; key++) {
      add(resources, "keypad[" + key + "]", "矩阵键盘 · 第" + (key / 4 + 1) + "行第" + (key % 4 + 1) + "列",
          "矩阵键盘", FpgaResource.Direction.INPUT, 1);
    }
    for (int digit = 0; digit < 8; digit++) {
      add(resources, "digit[" + digit + "]", "数码管 " + digit, "数码管",
          FpgaResource.Direction.OUTPUT, 7);
      for (char segment = 'a'; segment <= 'g'; segment++) {
        add(resources, "digit[" + digit + "]." + segment,
            "数码管 " + digit + " · 段 " + segment, "数码管", FpgaResource.Direction.OUTPUT, 1);
      }
      add(resources, "digit[" + digit + "].hex", "数码管 " + digit + " · 16进制数值",
          "数码管", FpgaResource.Direction.OUTPUT, 4);
    }
    // The legacy num_data bus is not the LCD interface in the supplied A7 schematics.
    // Keep its template constraints for compatibility, but do not offer an unverified mapping.
    return Collections.unmodifiableList(resources);
  }

  private static void add(List<FpgaResource> resources, String id, String label, String group,
      FpgaResource.Direction direction, int width) {
    Area area = areaFor(id);
    resources.add(new FpgaResource(id, label, group, direction, width,
        area.getX(), area.getY(), area.getWidth(), area.getHeight()));
  }

  private static void addBus(List<FpgaResource> resources, String id, String label, String group,
      FpgaResource.Direction direction, int width) {
    add(resources, id, label + "（总线）", group, direction, width);
    for (int bit = 0; bit < width; bit++) {
      String physicalLabel = "switch".equals(id) ? label + " SW" + (width - 1 - bit)
          : "led".equals(id) ? label + (width - bit)
          : "btn_step".equals(id) ? label + " " + bit : label + "[" + bit + "]";
      add(resources, id + "[" + bit + "]", physicalLabel, group, direction, 1);
    }
  }
}

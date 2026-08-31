package com.cburch.logisim.fpga.loongsim;

/** A logical board resource that can be selected by the FPGA port mapper. */
public final class FpgaResource {
  public enum Direction { INPUT, OUTPUT, INOUT }

  private final String id;
  private final String label;
  private final String group;
  private final Direction direction;
  private final int width;
  private final int x;
  private final int y;
  private final int imageWidth;
  private final int imageHeight;

  public FpgaResource(String id, String label, String group, Direction direction, int width,
      int x, int y, int imageWidth, int imageHeight) {
    this.id = id;
    this.label = label;
    this.group = group;
    this.direction = direction;
    this.width = width;
    this.x = x;
    this.y = y;
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;
  }

  public String getId() { return id; }
  public String getLabel() { return label; }
  public String getGroup() { return group; }
  public Direction getDirection() { return direction; }
  public int getWidth() { return width; }
  public int getX() { return x; }
  public int getY() { return y; }
  public int getImageWidth() { return imageWidth; }
  public int getImageHeight() { return imageHeight; }

  public boolean accepts(FpgaPort port) {
    if (port == null || port.getWidth() != width) return false;
    // A clock constraint is valid only for the board oscillator. Mapping a
    // clock-like top port to a switch would create a physically unreliable
    // design even though the direction and width appear compatible.
    if (port.getDirection() == Direction.INPUT && isClockPort(port) && !"clk".equals(id)) {
      return false;
    }
    if (port.getDirection() == Direction.INPUT) {
      return direction == Direction.INPUT || direction == Direction.INOUT;
    }
    if (port.getDirection() == Direction.OUTPUT) {
      return direction == Direction.OUTPUT || direction == Direction.INOUT;
    }
    return direction == Direction.INOUT;
  }

  private static boolean isClockPort(FpgaPort port) {
    String name = port.getName();
    String lower = name.toLowerCase();
    return "FPGA_GlobalClock".equals(name)
        || "clk".equalsIgnoreCase(name)
        || "clock".equalsIgnoreCase(name)
        || lower.endsWith("_clk")
        || lower.endsWith("_clock");
  }

  public boolean conflictsWith(String otherId) {
    if (otherId == null) return false;
    if (id.equals(otherId)) return true;
    boolean thisAggregate = id.indexOf('[') < 0;
    boolean otherAggregate = otherId.indexOf('[') < 0;
    if (!thisAggregate && !otherAggregate) return false;
    String thisBase = thisAggregate ? id : id.substring(0, id.indexOf('['));
    String otherBase = otherAggregate
        ? otherId : otherId.substring(0, otherId.indexOf('['));
    return thisBase.equals(otherBase);
  }

  @Override
  public String toString() {
    return label + " (" + direction + ", " + width + " bit)";
  }
}

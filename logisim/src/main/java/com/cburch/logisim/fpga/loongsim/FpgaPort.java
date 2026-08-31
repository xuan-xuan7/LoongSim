package com.cburch.logisim.fpga.loongsim;

/** A top-level Verilog port extracted from the generated circuit HDL. */
public final class FpgaPort {
  private final String name;
  private final FpgaResource.Direction direction;
  private final int width;

  public FpgaPort(String name, FpgaResource.Direction direction, int width) {
    this.name = name;
    this.direction = direction;
    this.width = width;
  }

  public String getName() { return name; }
  public FpgaResource.Direction getDirection() { return direction; }
  public int getWidth() { return width; }

  @Override
  public String toString() {
    return name + " (" + direction + ", " + width + " bit)";
  }
}

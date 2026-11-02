package com.cburch.logisim.fpga.loongsim;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A top-level Verilog port extracted from the generated circuit HDL. */
public final class FpgaPort {
  private final String name;
  private final FpgaResource.Direction direction;
  private final int width;
  private final String displayName;
  private final Map<Integer, String> bitLabels;

  public FpgaPort(String name, FpgaResource.Direction direction, int width) {
    this(name, direction, width, name, Map.of());
  }

  public FpgaPort(String name, FpgaResource.Direction direction, int width,
      String displayName, Map<Integer, String> bitLabels) {
    this.name = name;
    this.direction = direction;
    this.width = width;
    this.displayName = displayName == null || displayName.isEmpty() ? name : displayName;
    this.bitLabels = Collections.unmodifiableMap(new TreeMap<>(bitLabels));
  }

  public String getName() { return name; }
  public String getBaseName() {
    int bracket = name.indexOf('[');
    return bracket < 0 ? name : name.substring(0, bracket);
  }
  public FpgaResource.Direction getDirection() { return direction; }
  public int getWidth() { return width; }
  public String getDisplayName() { return displayName; }
  public Map<Integer, String> getBitLabels() { return bitLabels; }
  public int getLowBit() {
    Matcher match = Pattern.compile("\\[(\\d+)(?::(\\d+))?\\]$").matcher(name);
    return match.find() ? Integer.parseInt(match.group(2) == null ? match.group(1) : match.group(2)) : 0;
  }

  public boolean contains(FpgaPort other) {
    return getBaseName().equals(other.getBaseName()) && other.getLowBit() >= getLowBit()
        && other.getLowBit() + other.width <= getLowBit() + width;
  }

  public boolean containsReference(String reference) {
    if (name.equals(reference)) return true;
    Matcher match = Pattern.compile(Pattern.quote(getBaseName()) + "\\[(\\d+)(?::(\\d+))?\\]").matcher(reference);
    if (!match.matches()) return false;
    try {
      int high = Integer.parseInt(match.group(1));
      int low = match.group(2) == null ? high : Integer.parseInt(match.group(2));
      return low >= getLowBit() && high >= low && high < getLowBit() + width;
    } catch (NumberFormatException invalid) { return false; }
  }

  public FpgaPort slice(int offset, int count, String label) {
    if (count < 1 || offset < 0 || offset + count > width) throw new IllegalArgumentException("Invalid port slice");
    if (offset == 0 && count == width) return this;
    int low = getLowBit() + offset;
    Map<Integer, String> labels = new TreeMap<>();
    bitLabels.forEach((bit, value) -> { if (bit >= low && bit < low + count) labels.put(bit, value); });
    return new FpgaPort(getBaseName() + "[" + (count == 1 ? low : (low + count - 1) + ":" + low)
        + "]", direction, count, label, labels);
  }

  public boolean hasNamedSegments() {
    for (char segment = 'A'; segment <= 'G'; segment++) {
      final String suffix = "[Segment_" + segment + "]";
      if (bitLabels.values().stream().noneMatch(label -> label.endsWith(suffix))) return false;
    }
    return width == 7 || width == 8;
  }

  /** Separate display components from a shared Logisim output bubble port. */
  public List<FpgaPort> componentParts() {
    if (!name.startsWith("LOGISIM_OUTPUT_BUBBLE")) return List.of(this);
    List<FpgaPort> parts = new ArrayList<>();
    boolean displayFound = false;
    for (int bit = 0; bit < width;) {
      String first = bitLabels.get(bit);
      if (first != null && first.endsWith("[Segment_A]") && bit + 7 <= width) {
        String label = first.substring(0, first.length() - "[Segment_A]".length());
        boolean segments = true;
        for (int offset = 0; offset < 7; offset++) {
          segments &= (label + "[Segment_" + (char) ('A' + offset) + "]").equals(bitLabels.get(bit + offset));
        }
        if (segments) {
          int count = (label + "[DecimalPoint]").equals(bitLabels.get(bit + 7)) ? 8 : 7;
          parts.add(slice(bit, count, label));
          bit += count;
          displayFound = true;
          continue;
        }
      }
      parts.add(width == 1 ? this : bit(bit));
      bit++;
    }
    return displayFound ? parts : List.of(this);
  }
  public FpgaPort bit(int index) {
    if (width <= 1 || index < 0 || index >= width) throw new IllegalArgumentException("Invalid port bit");
    int absolute = getLowBit() + index;
    String label = bitLabels.get(absolute);
    if (label == null || label.isEmpty()) label = displayName + "[" + index + "]";
    else if (!label.endsWith("[" + index + "]")) label += "（位 " + index + "）";
    return new FpgaPort(getBaseName() + "[" + absolute + "]", direction, 1, label, Map.of());
  }
  public String getDirectionLabel() {
    return direction == FpgaResource.Direction.INPUT ? "输入"
        : direction == FpgaResource.Direction.OUTPUT ? "输出" : "双向";
  }

  @Override
  public String toString() {
    return displayName + "（" + getDirectionLabel() + "，" + width + " 位）";
  }
}

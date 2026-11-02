package com.cburch.logisim.fpga.loongsim;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A port is mapped either as a whole or as every individual bit. */
public final class FpgaPortMapping {
  private FpgaPortMapping() {}

  public static boolean isComplete(FpgaPort port, Map<String, String> mappings) {
    Map<String, String> own = new java.util.LinkedHashMap<>();
    mappings.forEach((name, resource) -> {
      if (name.equals(port.getName()) || name.startsWith(port.getName() + "[")) own.put(name, resource);
    });
    try {
      List<FpgaPort> targets = targets(List.of(port), own);
      return targets.stream().allMatch(target -> own.containsKey(target.getName()));
    } catch (IOException invalid) { return false; }
  }

  public static List<FpgaPort> targets(List<FpgaPort> ports, Map<String, String> mappings)
      throws IOException {
    List<FpgaPort> targets = new ArrayList<>();
    Set<String> expected = new LinkedHashSet<>();
    for (FpgaPort port : ports) {
      if (mappings.containsKey(port.getName()) || port.getWidth() == 1) {
        targets.add(port);
        expected.add(port.getName());
      } else {
        BitSet covered = new BitSet(port.getWidth());
        Pattern reference = Pattern.compile(Pattern.quote(port.getName()) + "\\[(\\d+)(?::(\\d+))?\\]");
        for (String name : mappings.keySet()) {
          Matcher match = reference.matcher(name);
          if (!match.matches()) continue;
          int high, low;
          try {
            high = Integer.parseInt(match.group(1));
            low = match.group(2) == null ? high : Integer.parseInt(match.group(2));
          } catch (NumberFormatException invalid) { throw new IOException("无效位号: " + name); }
          if (low < 0 || high < low || high >= port.getWidth()) throw new IOException("无效位号: " + name);
          for (int bit = low; bit <= high; bit++) {
            if (covered.get(bit)) throw new IOException("重复的整组/逐位分配: " + port.getDisplayName());
            covered.set(bit);
          }
          FpgaPort target = high == low ? port.bit(low) : port.slice(low, high - low + 1,
              port.getDisplayName() + "[" + high + ":" + low + "]");
          for (FpgaPort component : port.componentParts()) {
            if (component.getName().equals(name)) target = component;
          }
          targets.add(target);
          expected.add(name);
        }
        for (int bit = 0; bit < port.getWidth(); bit++) {
          if (!covered.get(bit)) throw new IOException("尚未映射: " + port.bit(bit).getDisplayName());
        }
      }
    }
    Set<String> extra = new LinkedHashSet<>(mappings.keySet());
    extra.removeAll(expected);
    if (!extra.isEmpty()) {
      throw new IOException("映射包含无效位号、未知端口或重复的整组/逐位分配: " + extra);
    }
    return targets;
  }
}

package com.cburch.logisim.fpga.loongsim;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.fpga.data.ComponentMapInformationContainer;
import com.cburch.logisim.fpga.designrulecheck.BubbleInformationContainer;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.NetlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.HDLGeneratorFactory;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.io.HexDigit;
import com.cburch.logisim.std.wiring.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Display labels follow component identity and netlist bubble IDs, never HDL-name decoding. */
final class FpgaPortLabels {
  private final Map<Component, String> originals = new IdentityHashMap<>();

  FpgaPortLabels(Circuit circuit) {
    capture(circuit, new HashSet<>());
  }

  private void capture(Circuit circuit, Set<Circuit> visited) {
    if (!visited.add(circuit)) return;
    Map<String, Integer> counts = new HashMap<>();
    for (Component component : circuit.getNonWires()) {
      String label = label(component);
      if (!label.isEmpty()) counts.merge(label, 1, Integer::sum);
    }
    for (Component component : circuit.getNonWires()) {
      String label = label(component);
      if (label.isEmpty()) label = component.getFactory().getDisplayName()
          + "（" + component.getLocation().getX() + "," + component.getLocation().getY() + "）";
      else if (counts.get(label) > 1) label += "（" + component.getLocation().getX()
          + "," + component.getLocation().getY() + "）";
      originals.put(component, label);
      if (component.getFactory() instanceof SubcircuitFactory) {
        capture(((SubcircuitFactory) component.getFactory()).getSubcircuit(), visited);
      }
    }
  }

  List<FpgaPort> apply(Circuit circuit, List<FpgaPort> ports) {
    Map<String, String> pinNames = new HashMap<>();
    Netlist netlist = circuit.getNetList();
    for (int i = 0; i < netlist.NumberOfInputPorts(); i++) addPin(pinNames, netlist.GetInputPin(i));
    for (int i = 0; i < netlist.NumberOfOutputPorts(); i++) addPin(pinNames, netlist.GetOutputPin(i));
    for (int i = 0; i < netlist.NumberOfInOutPorts(); i++) addPin(pinNames, netlist.GetInOutPin(i));
    Map<String, Map<Integer, String>> bits = new HashMap<>();
    Map<String, Map<Integer, String>> groups = new HashMap<>();
    Set<Integer> hexDisplayStarts = new HashSet<>();
    for (Map.Entry<ArrayList<String>, NetlistComponent> entry :
        netlist.GetMappableResources(new ArrayList<>(), false).entrySet()) {
      NetlistComponent component = entry.getValue();
      BubbleInformationContainer ids = component.GetGlobalBubbleId(entry.getKey());
      if (ids == null) continue;
      ComponentMapInformationContainer info = component.GetMapInformationContainer();
      String display = displayPath(circuit, entry.getKey());
      if (component.GetComponent().getFactory() instanceof HexDigit) {
        hexDisplayStarts.add(ids.GetOutputStartIndex());
      }
      addBits(bits, groups, HDLGeneratorFactory.LocalInputBubbleBusname, ids.GetInputStartIndex(),
          info.GetNrOfInports(), display, info::GetInportLabel);
      addBits(bits, groups, HDLGeneratorFactory.LocalOutputBubbleBusname, ids.GetOutputStartIndex(),
          info.GetNrOfOutports(), display, info::GetOutportLabel);
      addBits(bits, groups, HDLGeneratorFactory.LocalInOutBubbleBusname, ids.GetInOutStartIndex(),
          info.GetNrOfInOutports(), display, info::GetInOutportLabel);
    }
    List<FpgaPort> result = new ArrayList<>();
    for (FpgaPort port : ports) {
      String display = pinNames.get(port.getName());
      Map<Integer, String> bitLabels = new TreeMap<>();
      if (display != null) {
        if (port.getWidth() > 1) {
          for (int bit = 0; bit < port.getWidth(); bit++) bitLabels.put(bit, display + "[" + bit + "]");
        }
      } else if (bits.containsKey(port.getName())) {
        Map<Integer, String> known = bits.get(port.getName());
        for (int bit = 0; bit < port.getWidth(); bit++) {
          String value = known.get(bit);
          bitLabels.put(bit, value == null ? "未命名信号[" + bit + "]" : value);
        }
        Set<String> components = new LinkedHashSet<>(groups.get(port.getName()).values());
        display = String.join("、", components);
      } else if ("FPGA_GlobalClock".equals(port.getName())) {
        List<String> clocks = new ArrayList<>();
        for (Component component : circuit.getNonWires()) {
          if (component.getFactory() instanceof Clock && !label(component).isEmpty()) {
            clocks.add(originals.get(component));
          }
        }
        display = clocks.isEmpty() ? "系统时钟" : String.join("、", clocks) + "（系统时钟）";
      }
      Set<Integer> hexStarts = HDLGeneratorFactory.LocalOutputBubbleBusname.equals(port.getName())
          ? hexDisplayStarts : Set.of();
      result.add(new FpgaPort(port.getName(), port.getDirection(), port.getWidth(), display, bitLabels, hexStarts));
    }
    return result;
  }

  private void addPin(Map<String, String> names, NetlistComponent pin) {
    Component component = pin.GetComponent();
    names.put(CorrectLabel.getCorrectLabel(label(component)), originals.get(component));
  }

  private String displayPath(Circuit circuit, List<String> path) {
    List<String> parts = new ArrayList<>();
    for (String segment : path) {
      Component matched = null;
      for (Component candidate : circuit.getNonWires()) {
        if (segment.equals(CorrectLabel.getCorrectLabel(label(candidate)))) {
          matched = candidate;
          break;
        }
      }
      parts.add(matched == null ? segment : originals.get(matched));
      if (matched != null && matched.getFactory() instanceof SubcircuitFactory) {
        circuit = ((SubcircuitFactory) matched.getFactory()).getSubcircuit();
      }
    }
    return String.join(" / ", parts);
  }

  private static void addBits(Map<String, Map<Integer, String>> bits,
      Map<String, Map<Integer, String>> groups, String bus, int start, int count,
      String display, java.util.function.IntFunction<String> bitLabel) {
    for (int bit = 0; bit < count; bit++) {
      bits.computeIfAbsent(bus, key -> new TreeMap<>()).put(start + bit,
          count == 1 ? display : display + "[" + bitLabel.apply(bit) + "]");
      groups.computeIfAbsent(bus, key -> new TreeMap<>()).put(start + bit, display);
    }
  }

  private static String label(Component component) {
    return component.getAttributeSet().containsAttribute(StdAttr.LABEL)
        ? component.getAttributeSet().getValue(StdAttr.LABEL) : "";
  }
}

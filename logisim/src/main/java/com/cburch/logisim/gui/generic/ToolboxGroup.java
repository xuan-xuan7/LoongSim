/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.gui.generic;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.Direction;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.io.Io;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.Wiring;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Display-only categories. The original libraries remain responsible for file I/O. */
public final class ToolboxGroup extends Library {
  private static final Set<String> INPUT_TOOLS =
      Set.of("Button", "DipSwitch", "Clock", "Constant", "Power", "Ground");
  private static final Set<String> OUTPUT_TOOLS =
      Set.of("LED", "RGBLED", "7-Segment Display", "Hex Digit Display");

  private final String name;
  private final String displayKey;
  private final List<Tool> tools = new ArrayList<>();

  private ToolboxGroup(String name, String displayKey) {
    this.name = name;
    this.displayKey = displayKey;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDisplayName() {
    return S.get(displayKey);
  }

  @Override
  public List<Tool> getTools() {
    return Collections.unmodifiableList(tools);
  }

  @Override
  public boolean removeLibrary(String name) {
    return false;
  }

  private static boolean isSourceLibrary(Library library) {
    while (library instanceof LoadedLibrary) {
      library = ((LoadedLibrary) library).getBase();
    }
    return library instanceof Wiring || library instanceof Io;
  }

  static List<Library> groupLibraries(List<Library> libraries) {
    ToolboxGroup input = new ToolboxGroup("ToolboxInput", "toolboxInputGroup");
    ToolboxGroup output = new ToolboxGroup("ToolboxOutput", "toolboxOutputGroup");
    ToolboxGroup other = new ToolboxGroup("ToolboxOther", "toolboxOtherGroup");
    List<Library> result = new ArrayList<>();
    int insertionIndex = -1;
    for (Library library : libraries) {
      if (!isSourceLibrary(library) || library.isHidden()) {
        result.add(library);
        continue;
      }
      if (insertionIndex < 0) insertionIndex = result.size();
      for (Tool tool : library.getVisibleTools()) {
        String name = tool.getName();
        if (tool instanceof AddTool && ((AddTool) tool).getFactory() == Pin.FACTORY) {
          AddTool inputPin = new AddTool((AddTool) tool, S.getter("toolboxInputPin"));
          inputPin.getAttributeSet().setValue(Pin.ATTR_TYPE, false);
          inputPin.getAttributeSet().setValue(StdAttr.FACING, Direction.EAST);
          input.tools.add(0, inputPin);
          AddTool outputPin = new AddTool((AddTool) tool, S.getter("toolboxOutputPin"));
          outputPin.getAttributeSet().setValue(Pin.ATTR_TYPE, true);
          outputPin.getAttributeSet().setValue(StdAttr.FACING, Direction.WEST);
          output.tools.add(0, outputPin);
        } else if (INPUT_TOOLS.contains(name)) {
          input.tools.add(tool);
        } else if (OUTPUT_TOOLS.contains(name)) {
          output.tools.add(tool);
        } else {
          // Includes Probe, and preserves future, unclassified tools.
          other.tools.add(tool);
        }
      }
    }
    if (insertionIndex >= 0) {
      for (ToolboxGroup group : new ToolboxGroup[] {input, output, other}) {
        if (!group.tools.isEmpty()) result.add(insertionIndex++, group);
      }
    }
    return result;
  }
}

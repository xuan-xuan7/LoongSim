/**
 * This file is part of logisim-evolution.
 *
 * Logisim-evolution is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Logisim-evolution is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with logisim-evolution. If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code by Carl Burch (http://www.cburch.com), 2011.
 * Subsequent modifications by:
 *   + College of the Holy Cross
 *     http://www.holycross.edu
 *   + Haute École Spécialisée Bernoise/Berner Fachhochschule
 *     http://www.bfh.ch
 *   + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *     http://hepia.hesge.ch/
 *   + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *     http://www.heig-vd.ch/
 */

package com.cburch.logisim.gui.generic;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.SplitterAttributes;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.fpga.gui.HDLColorRenderer;
import com.cburch.logisim.fpga.hdlgenerator.HDLGeneratorFactory;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.ProbeAttributes;
import com.cburch.logisim.std.wiring.Constant;
import com.cburch.logisim.std.wiring.Probe;
import com.cburch.logisim.std.io.Button;
import com.cburch.logisim.std.io.DipSwitch;
import com.cburch.logisim.std.io.Led;
import com.cburch.logisim.std.io.RGBLed;
import com.cburch.logisim.std.io.SevenSegment;
import com.cburch.logisim.std.io.HexDigit;
import com.cburch.logisim.std.io.Io;
import com.cburch.logisim.std.plexers.Plexers;
import com.cburch.logisim.std.plexers.Decoder;
import com.cburch.logisim.std.memory.Memory;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.std.memory.Rom;
import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import javax.swing.JTextField;

public abstract class AttributeSetTableModel implements AttrTableModel, AttributeListener {
  private class AttrRow implements AttrTableModelRow {
    private Attribute<Object> attr;

    AttrRow(Attribute<?> attr) {
      @SuppressWarnings("unchecked")
      Attribute<Object> objAttr = (Attribute<Object>) attr;
      this.attr = objAttr;
    }

    public Component getEditor(Window parent) {
      Object value = attrs.getValue(attr);
      if (attr.equals(Constant.ATTR_VALUE)) return new JTextField(getValue());
      if (attr.equals(Plexers.ATTR_SELECT) && isTeachingPlexer())
        return Plexers.SELECT_BITS_EDITOR.getCellEditor(parent, (BitWidth) value);
      return attr.getCellEditor(parent, value);
    }

    public String getLabel() {
      if (attr.equals(Plexers.ATTR_SELECT) && CompInst instanceof Decoder)
        return com.cburch.logisim.std.Strings.S.get("decoderInputBitsAttr");
      return attr.getDisplayName();
    }

    public String getValue() {
      Object value = attrs.getValue(attr);
      if (value == null) {
        try {
          return attr.toDisplayString(value);
        } catch (NullPointerException e) {
          return "";
        }
      } else {
        try {
          String Str;
          if (attr.equals(Constant.ATTR_VALUE)) {
            BitWidth width = attrs.getValue(StdAttr.WIDTH);
            Str = width == null ? Long.toBinaryString((Long) value)
                : Constant.toBinaryString((Long) value, width.getWidth());
          } else {
            Str = attr.toDisplayString(value);
          }
          return Str;
        } catch (Exception e) {
          return "???";
        }
      }
    }

    public boolean isValueEditable() {
      return !attrs.isReadOnly(attr);
    }

    public boolean multiEditCompatible(AttrTableModelRow other) {
      if (other == null || !(other instanceof AttrRow)) return false;
      AttrRow o = (AttrRow) other;
      if (!(((Object) attr) instanceof SplitterAttributes.BitOutAttribute)) return false;
      if (!(((Object) o.attr) instanceof SplitterAttributes.BitOutAttribute)) return false;
      SplitterAttributes.BitOutAttribute a = (SplitterAttributes.BitOutAttribute) (Object) attr;
      SplitterAttributes.BitOutAttribute b = (SplitterAttributes.BitOutAttribute) (Object) o.attr;
      return a.sameOptions(b);
    }

    public void setValue(Window parent, Object value) throws AttrTableSetException {
      Attribute<Object> attr = this.attr;
      if (attr == null || value == null) return;

      try {
        if (value instanceof String) {
          if (attr.equals(Constant.ATTR_VALUE)) {
            String bits = ((String) value).trim();
            if (bits.startsWith("0b") || bits.startsWith("0B")) bits = bits.substring(2);
            if (!bits.matches("[01]+")) throw new NumberFormatException();
            long parsed = Long.parseUnsignedLong(bits, 2);
            BitWidth width = attrs.getValue(StdAttr.WIDTH);
            if (width != null && Long.toBinaryString(parsed).length() > width.getWidth())
              throw new NumberFormatException();
            value = parsed;
          } else {
            value = attr.parse((String) value);
          }
        }
        setValueRequested(attr, value);
      } catch (ClassCastException e) {
        String msg = S.get("attributeChangeInvalidError") + ": " + e;
        throw new AttrTableSetException(msg);
      } catch (NumberFormatException e) {
        String msg = S.get("attributeChangeInvalidError");
        String emsg = e.getMessage();
        if (emsg != null && emsg.length() > 0) msg += ": " + emsg;
        msg += ".";
        throw new AttrTableSetException(msg);
      }
    }
  }

  private class HDLrow extends AttrRow {

    HDLrow(Attribute<?> attr) {
      super(attr);
    }

    @Override
    public String getLabel() {
      if (CompInst == null) return HDLColorRenderer.UnKnownString;
      if (CompInst.HDLSupportedComponent(HDLGeneratorFactory.VHDL, attrs))
        return HDLColorRenderer.VHDLSupportString;
      return HDLColorRenderer.NoSupportString;
    }

    @Override
    public String getValue() {
      if (CompInst == null) return HDLColorRenderer.UnKnownString;
      if (CompInst.HDLSupportedComponent(HDLGeneratorFactory.VERILOG, attrs))
        return HDLColorRenderer.VHDLSupportString;
      return HDLColorRenderer.NoSupportString;
    }

    @Override
    public boolean isValueEditable() {
      return false;
    }

    @Override
    public void setValue(Window parent, Object value) {
      // Do Nothing
    }
  }

  private class SplitterBranchRow extends AttrRow {
    private final int branch;

    SplitterBranchRow(int branch) {
      super(SplitterAttributes.ATTR_MAPPING);
      this.branch = branch;
    }

    @Override
    public String getLabel() { return S.fmt("splitterBranchRange", branch); }

    @Override
    public String getValue() {
      byte[] mapping = attrs.getValue(SplitterAttributes.ATTR_MAPPING);
      if (mapping == null) return "";
      ArrayList<String> ranges = new ArrayList<>();
      for (int bit = 0; bit < mapping.length; bit++) {
        if (mapping[bit] != branch + 1) continue;
        int first = bit;
        while (bit + 1 < mapping.length && mapping[bit + 1] == branch + 1) bit++;
        ranges.add(first == bit ? "" + first : first + "–" + bit);
      }
      return ranges.isEmpty() ? S.get("splitterRangeUnassigned") : String.join(", ", ranges);
    }

    @Override
    public Component getEditor(Window parent) { return new SplitterRangeEditor(attrs, branch); }

    @Override
    public boolean isValueEditable() {
      return attrs.getValue(SplitterAttributes.ATTR_MAPPING) != null && super.isValueEditable();
    }
  }

  private ArrayList<AttrTableModelListener> listeners;
  private AttributeSet attrs;
  private HashMap<Attribute<?>, AttrRow> rowMap;
  private ArrayList<AttrRow> rows;
  private ComponentFactory CompInst = null;
  private boolean isTool;

  private boolean isTeachingPlexer() {
    return attrs != null && attrs.containsAttribute(Plexers.ATTR_SELECT)
        && attrs.containsAttribute(Plexers.ATTR_ENABLE);
  }

  private int splitterBranches() {
    if (attrs == null || !attrs.containsAttribute(SplitterAttributes.ATTR_MAPPING)) return 0;
    Integer fanout = attrs.getValue(SplitterAttributes.ATTR_FANOUT);
    return fanout == null ? 0 : fanout;
  }

  private void addSplitterRows(ArrayList<AttrRow> target) {
    for (int branch = 0; branch < splitterBranches(); branch++) target.add(new SplitterBranchRow(branch));
  }

  private boolean isVisibleAttribute(Attribute<?> attr) {
    if (attr.isHidden()
        || (isTool && attr == StdAttr.LABEL && !attrs.containsAttribute(Constant.ATTR_VALUE)))
      return false;
    if (attrs.containsAttribute(Pin.ATTR_TYPE)
        && (attr == Pin.ATTR_TYPE || attr == Pin.ATTR_TRISTATE || attr == Pin.ATTR_PULL
            || attr == ProbeAttributes.PROBEAPPEARANCE)) return false;
    if (attr == SplitterAttributes.ATTR_APPEARANCE || attr instanceof SplitterAttributes.BitOutAttribute)
      return false;
    if (CompInst instanceof Probe && attr == ProbeAttributes.PROBEAPPEARANCE) return false;
    if ((CompInst instanceof Button || CompInst instanceof DipSwitch)
        && (attr == StdAttr.LABEL_COLOR || attr == StdAttr.LABEL_VISIBILITY)) return false;
    boolean led = CompInst instanceof Led || CompInst instanceof RGBLed;
    boolean display = CompInst instanceof SevenSegment || CompInst instanceof HexDigit;
    if (led && (attr == Io.ATTR_ACTIVE || attr == StdAttr.LABEL_COLOR)) return false;
    if ((led || display) && attr == StdAttr.LABEL_VISIBILITY) return false;
    if (CompInst instanceof SevenSegment && attr == Io.ATTR_ACTIVE) return false;
    if (display && attr == SevenSegment.ATTR_DP) return false;
    if (attrs.containsAttribute(CircuitAttributes.NAME_ATTR)
        && (attr == StdAttr.LABEL_VISIBILITY || attr == CircuitAttributes.CIRCUIT_LABEL_ATTR
            || attr == CircuitAttributes.CIRCUIT_LABEL_FACING_ATTR
            || attr == CircuitAttributes.APPEARANCE_ATTR
            || attr == CircuitAttributes.NAMED_CIRCUIT_BOX_FIXED_SIZE
            || attr == CircuitAttributes.CIRCUIT_IS_VHDL_BOX
            || attr == CircuitAttributes.CIRCUIT_VHDL_PATH)) return false;
    if (isTeachingPlexer()
        && (attr == Plexers.ATTR_ENABLE || attr == Plexers.ATTR_DISABLED
            || attr == Plexers.ATTR_TRISTATE)) return false;
    if (CompInst instanceof Decoder && attr.getName().equals("selloc")) return false;
    if (attr == StdAttr.APPEARANCE && Memory.hasFixedAppearance(CompInst)) return false;
    if (CompInst instanceof Ram && (attr == StdAttr.LABEL_VISIBILITY
        || attr.getName().equals("type") || attr.getName().equals("clearpin")
        || attr.getName().equals("enables") || attr.getName().equals("databus")
        || attr.getName().equals("asyncread") || attr.getName().equals("readbehav")
        || attr.getName().equals("byteenables"))) return false;
    if (CompInst instanceof Rom && (attr.getName().equals("line")
        || attr.getName().equals("misaligned"))) return false;
    return true;
  }

  public AttributeSetTableModel(AttributeSet attrs) {
    this.attrs = attrs;
    this.listeners = new ArrayList<AttrTableModelListener>();
    this.rowMap = new HashMap<Attribute<?>, AttrRow>();
    this.rows = new ArrayList<AttrRow>();
    if (attrs != null) {
      for (Attribute<?> attr : attrs.getAttributes()) {
        if (isVisibleAttribute(attr)) {
          AttrRow row = new AttrRow(attr);
          rowMap.put(attr, row);
          rows.add(row);
        }
      }
      addSplitterRows(rows);
    }
  }

  public void SetInstance(ComponentFactory fact) {
    if (CompInst != fact) {
      CompInst = fact;
      attributeListChanged(null);
    }
  }

  public void SetIsTool() {
    isTool = true;
    attributeListChanged(null);
  }

  public void addAttrTableModelListener(AttrTableModelListener listener) {
    if (listeners.isEmpty() && attrs != null) {
      attrs.addAttributeListener(this);
    }
    listeners.add(listener);
  }

  //
  // AttributeListener methods
  //
  public void attributeListChanged(AttributeEvent e) {
    // if anything has changed, don't do anything
    int index = 0;
    boolean match = true;
    int rowsSize = rows.size();
    for (Attribute<?> attr : attrs.getAttributes()) {
      if (isVisibleAttribute(attr)) {
        if (index >= rowsSize || rows.get(index).attr != attr) {
          match = false;
          break;
        }
        index++;
      }
    }
    if (match && index + splitterBranches() == rows.size()) return;

    // compute the new list of rows, possible adding into hash map
    ArrayList<AttrRow> newRows = new ArrayList<AttrRow>();
    HashSet<Attribute<?>> missing = new HashSet<Attribute<?>>(rowMap.keySet());
    for (Attribute<?> attr : attrs.getAttributes()) {
      if (isVisibleAttribute(attr)) {
        AttrRow row = rowMap.get(attr);
        if (row == null) {
          row = new AttrRow(attr);
          rowMap.put(attr, row);
        } else {
          missing.remove(attr);
        }
        newRows.add(row);
      }
    }
    addSplitterRows(newRows);
    rows = newRows;
    for (Attribute<?> attr : missing) {
      rowMap.remove(attr);
    }
    fireStructureChanged();
  }

  public void attributeValueChanged(AttributeEvent e) {
    Attribute<?> attr = e.getAttribute();
    if (attr == Pin.ATTR_TYPE) attributeListChanged(null);
    if (attr == SplitterAttributes.ATTR_FANOUT) attributeListChanged(null);
    if (attr instanceof SplitterAttributes.BitOutAttribute || attr == SplitterAttributes.ATTR_MAPPING
        || attr == SplitterAttributes.ATTR_WIDTH || attr == SplitterAttributes.ATTR_FANOUT) {
      for (int i = 0; i < rows.size(); i++) {
        if (rows.get(i) instanceof SplitterBranchRow) fireValueChanged(i);
      }
    }
    if (attr == StdAttr.WIDTH && attrs.containsAttribute(Constant.ATTR_VALUE)) {
      AttrRow valueRow = rowMap.get(Constant.ATTR_VALUE);
      if (valueRow != null) fireValueChanged(rows.indexOf(valueRow));
    }
    AttrTableModelRow row = rowMap.get(attr);
    if (row != null) {
      int index = rows.indexOf(row);
      if (index >= 0) {
        fireValueChanged(index);
      }
    }
  }

  protected void fireStructureChanged() {
    AttrTableModelEvent event = new AttrTableModelEvent(this);
    for (AttrTableModelListener l : listeners) {
      l.attrStructureChanged(event);
    }
  }

  protected void fireTitleChanged() {
    AttrTableModelEvent event = new AttrTableModelEvent(this);
    for (AttrTableModelListener l : listeners) {
      l.attrTitleChanged(event);
    }
  }

  protected void fireValueChanged(int index) {
    AttrTableModelEvent event = new AttrTableModelEvent(this, index);
    for (AttrTableModelListener l : listeners) {
      l.attrValueChanged(event);
    }
  }

  public AttributeSet getAttributeSet() {
    return attrs;
  }

  public AttrTableModelRow getRow(int rowIndex) {
    return rows.get(rowIndex);
  }

  public int getRowCount() {
    return rows.size();
  }

  public abstract String getTitle();

  public void removeAttrTableModelListener(AttrTableModelListener listener) {
    listeners.remove(listener);
    if (listeners.isEmpty() && attrs != null) {
      attrs.removeAttributeListener(this);
    }
  }

  public void setAttributeSet(AttributeSet value) {
    if (attrs != value) {
      if (!listeners.isEmpty()) {
        attrs.removeAttributeListener(this);
      }
      attrs = value;
      if (!listeners.isEmpty()) {
        attrs.addAttributeListener(this);
      }
      attributeListChanged(null);
    }
  }

  protected abstract void setValueRequested(Attribute<Object> attr, Object value)
      throws AttrTableSetException;
}

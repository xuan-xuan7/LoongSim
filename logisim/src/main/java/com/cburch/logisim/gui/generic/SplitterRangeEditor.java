/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.gui.generic;

import static com.cburch.logisim.gui.Strings.S;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.circuit.SplitterAttributes;
import com.cburch.logisim.util.JInputComponent;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Edits both ends together; its result is one undoable mapping. */
final class SplitterRangeEditor extends JPanel implements JInputComponent {
  private final byte[] original;
  private final int branch;
  private final JSpinner start;
  private final JSpinner end;
  private boolean changed;
  private boolean updating;

  SplitterRangeEditor(AttributeSet attrs, int branch) {
    super(new BorderLayout(8, 8));
    this.original = attrs.getValue(SplitterAttributes.ATTR_MAPPING);
    this.branch = branch;
    int first = -1, last = -1;
    for (int bit = 0; bit < original.length; bit++) {
      if (original[bit] == branch + 1) {
        if (first < 0) first = bit;
        last = bit;
      }
    }
    changed = first < 0;
    start = new JSpinner(new SpinnerNumberModel(Math.max(0, first), 0, original.length - 1, 1));
    end = new JSpinner(new SpinnerNumberModel(Math.max(0, last), 0, original.length - 1, 1));
    JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
    add(new JLabel(S.fmt("splitterBranchRange", branch)), BorderLayout.NORTH);
    fields.add(new JLabel(S.get("splitterRangeStart"))); fields.add(start);
    fields.add(new JLabel(S.get("splitterRangeEnd"))); fields.add(end);
    add(fields, BorderLayout.CENTER);
    add(new JLabel(S.get("splitterRangeHint")), BorderLayout.SOUTH);
    start.addChangeListener(e -> synchronize(true));
    end.addChangeListener(e -> synchronize(false));
  }

  private void synchronize(boolean startChanged) {
    if (updating) return;
    changed = true; updating = true;
    if ((int) start.getValue() > (int) end.getValue()) {
      if (startChanged) end.setValue(start.getValue());
      else start.setValue(end.getValue());
    }
    updating = false;
  }

  @Override
  public Object getValue() {
    byte[] result = original.clone();
    if (!changed) return result;
    for (int bit = 0; bit < result.length; bit++) {
      if (result[bit] == branch + 1) result[bit] = 0;
    }
    for (int bit = (int) start.getValue(); bit <= (int) end.getValue(); bit++)
      result[bit] = (byte) (branch + 1);
    return result;
  }

  @Override
  public void setValue(Object value) {
    int[] range = (int[]) value;
    if (range[0] < 0 || range[1] < range[0] || range[1] >= original.length)
      throw new IllegalArgumentException("Invalid branch range");
    start.setValue(range[0]); end.setValue(range[1]);
    changed = true;
  }
}

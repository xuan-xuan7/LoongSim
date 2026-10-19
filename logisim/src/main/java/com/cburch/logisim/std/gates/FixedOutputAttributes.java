/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.gates;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import java.util.List;

/** Keeps the single-input gate's registered output attribute fixed to ordinary 0/1. */
final class FixedOutputAttributes extends AbstractAttributeSet {
  private AttributeSet values;

  FixedOutputAttributes(AttributeSet values) {
    this.values = values;
    values.setValue(GateAttributes.ATTR_OUTPUT, GateAttributes.OUTPUT_01);
  }

  @Override
  protected void copyInto(AbstractAttributeSet dest) {
    ((FixedOutputAttributes) dest).values = (AttributeSet) values.clone();
  }

  @Override
  public List<Attribute<?>> getAttributes() { return values.getAttributes(); }

  @Override
  public <V> V getValue(Attribute<V> attr) { return values.getValue(attr); }

  @Override
  public boolean isReadOnly(Attribute<?> attr) { return values.isReadOnly(attr); }

  @Override
  public void setReadOnly(Attribute<?> attr, boolean readOnly) { values.setReadOnly(attr, readOnly); }

  @Override
  @SuppressWarnings("unchecked")
  public <V> void setValue(Attribute<V> attr, V value) {
    if (attr == GateAttributes.ATTR_OUTPUT) value = (V) GateAttributes.OUTPUT_01;
    V oldValue = values.getValue(attr);
    values.setValue(attr, value);
    fireAttributeValueChanged(attr, value, oldValue);
  }
}

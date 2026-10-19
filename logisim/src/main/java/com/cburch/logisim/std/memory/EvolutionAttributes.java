/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.memory;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.instance.StdAttr;
import java.util.List;

/** Keeps flip-flops and registers in their fixed Logisim-Evolution appearance. */
final class EvolutionAttributes extends AbstractAttributeSet {
  private AttributeSet values;

  EvolutionAttributes(AttributeSet values) {
    this.values = values;
    values.setValue(StdAttr.APPEARANCE, StdAttr.APPEAR_EVOLUTION);
    if (values.containsAttribute(Register.ATTR_SHOW_IN_TAB))
      values.setValue(Register.ATTR_SHOW_IN_TAB, true);
  }

  @Override
  protected void copyInto(AbstractAttributeSet dest) {
    ((EvolutionAttributes) dest).values = (AttributeSet) values.clone();
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
    if (attr == StdAttr.APPEARANCE) value = (V) StdAttr.APPEAR_EVOLUTION;
    else if (attr == Register.ATTR_SHOW_IN_TAB) value = (V) Boolean.TRUE;
    V oldValue = values.getValue(attr);
    values.setValue(attr, value);
    fireAttributeValueChanged(attr, value, oldValue);
  }
}

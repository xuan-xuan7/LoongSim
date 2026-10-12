/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.io;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.instance.StdAttr;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Keeps simplified I/O settings fixed, including when loading old files. */
final class FixedLabelAttributes extends AbstractAttributeSet {
  private AttributeSet values;
  private final Map<Attribute<?>, Object> fixedValues;

  FixedLabelAttributes(AttributeSet values) {
    this(values, Map.of());
  }

  FixedLabelAttributes(AttributeSet values, Map<Attribute<?>, Object> extraFixedValues) {
    this.values = values;
    Map<Attribute<?>, Object> fixed = new HashMap<>(extraFixedValues);
    fixed.put(StdAttr.LABEL_COLOR, StdAttr.DEFAULT_LABEL_COLOR);
    fixed.put(StdAttr.LABEL_VISIBILITY, Boolean.TRUE);
    fixed.put(StdAttr.LABEL_FONT, StdAttr.DEFAULT_LABEL_FONT);
    fixedValues = Map.copyOf(fixed);
    for (Map.Entry<Attribute<?>, Object> entry : fixedValues.entrySet()) {
      if (values.containsAttribute(entry.getKey())) applyFixedValue(entry.getKey(), entry.getValue());
    }
  }

  @SuppressWarnings("unchecked")
  private void applyFixedValue(Attribute<?> attr, Object value) {
    values.setValue((Attribute<Object>) attr, value);
  }

  @Override
  protected void copyInto(AbstractAttributeSet dest) {
    ((FixedLabelAttributes) dest).values = (AttributeSet) values.clone();
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return values.getAttributes();
  }

  @Override
  public <V> V getValue(Attribute<V> attr) {
    return values.getValue(attr);
  }

  @Override
  public boolean isReadOnly(Attribute<?> attr) {
    return values.isReadOnly(attr);
  }

  @Override
  public void setReadOnly(Attribute<?> attr, boolean readOnly) {
    values.setReadOnly(attr, readOnly);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> void setValue(Attribute<V> attr, V value) {
    if (fixedValues.containsKey(attr)) value = (V) fixedValues.get(attr);
    V oldValue = values.getValue(attr);
    values.setValue(attr, value);
    fireAttributeValueChanged(attr, value, oldValue);
  }
}

/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.plexers;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import java.util.List;

/** Retains file attributes while fixing the simplified toolbox's operating mode. */
final class TeachingPlexerAttributes extends AbstractAttributeSet {
  private AttributeSet values;
  TeachingPlexerAttributes(AttributeSet values) {
    this.values = values;
    values.setValue(Plexers.ATTR_ENABLE, false);
    values.setValue(Plexers.ATTR_DISABLED, Plexers.DISABLED_ZERO);
    if (values.containsAttribute(Plexers.ATTR_TRISTATE))
      values.setValue(Plexers.ATTR_TRISTATE, false);
  }

  @Override
  protected void copyInto(AbstractAttributeSet dest) {
    ((TeachingPlexerAttributes) dest).values = (AttributeSet) values.clone();
  }

  @Override
  public List<Attribute<?>> getAttributes() { return values.getAttributes(); }

  @Override
  public <V> V getValue(Attribute<V> attr) { return values.getValue(attr); }

  @Override
  public boolean isReadOnly(Attribute<?> attr) { return values.isReadOnly(attr); }

  @Override
  public void setReadOnly(Attribute<?> attr, boolean readOnly) {
    values.setReadOnly(attr, readOnly);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V> void setValue(Attribute<V> attr, V value) {
    if (attr == Plexers.ATTR_ENABLE || attr == Plexers.ATTR_TRISTATE) value = (V) Boolean.FALSE;
    else if (attr == Plexers.ATTR_DISABLED) value = (V) Plexers.DISABLED_ZERO;
    V oldValue = values.getValue(attr);
    values.setValue(attr, value);
    fireAttributeValueChanged(attr, value, oldValue);
  }
}

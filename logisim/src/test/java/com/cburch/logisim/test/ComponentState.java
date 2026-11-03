package com.cburch.logisim.test;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.proj.Project;
import java.util.Arrays;

/** Exercises the production propagator with settled ports, without a GUI or timing thread. */
public final class ComponentState implements InstanceState {
  private final InstanceFactory factory;
  private final AttributeSet attributes;
  private final Instance instance;
  private final Value[] ports;
  private final boolean[] connected;
  private InstanceData data;
  private Project project;
  private long ticks;

  public ComponentState(InstanceFactory factory, AttributeSet attributes) {
    this.factory = factory;
    this.attributes = attributes;
    Component component = factory.createComponent(Location.create(0, 0), attributes);
    instance = Instance.getInstanceFor(component);
    ports = new Value[component.getEnds().size()];
    Arrays.fill(ports, Value.NIL);
    connected = new boolean[ports.length];
    Arrays.fill(connected, true);
  }

  public ComponentState drive(int port, Value value) { ports[port] = value; return this; }
  public ComponentState disconnect(int port) { connected[port] = false; return this; }
  public ComponentState withProject(Project project) { this.project=project; return this; }
  public void propagate() { ticks++; factory.propagate(this); }
  @Override public void fireInvalidated() {}
  @Override public AttributeSet getAttributeSet() { return attributes; }
  @Override public <E> E getAttributeValue(Attribute<E> attr) { return attributes.getValue(attr); }
  @Override public InstanceData getData() { return data; }
  @Override public InstanceFactory getFactory() { return factory; }
  @Override public Instance getInstance() { return instance; }
  @Override public int getPortIndex(Port port) { return instance.getPorts().indexOf(port); }
  @Override public Value getPortValue(int port) { return ports[port]; }
  @Override public Project getProject() { return project; }
  @Override public long getTickCount() { return ticks; }
  @Override public boolean isCircuitRoot() { return true; }
  @Override public boolean isPortConnected(int port) { return connected[port]; }
  @Override public void setData(InstanceData value) { data = value; }
  @Override public void setPort(int port, Value value, int delay) { ports[port] = value; }
}

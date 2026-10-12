/* This file is part of LoongSim, distributed under the GNU GPL version 3 or later. */
package com.cburch.logisim.std.memory;

import static com.cburch.logisim.std.Strings.S;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.icons.CounterIcon;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.math.BigInteger;

/** Five-port counter following the CircuitVerse documentation's teaching interface. */
public class SimpleCounter extends InstanceFactory {
  private static final int MAX = 0;
  private static final int CLOCK = 1;
  private static final int RESET = 2;
  private static final int VALUE = 3;
  private static final int ZERO = 4;
  private static final int DELAY = 8;

  private static class State extends ClockState implements InstanceData {
    long value;
  }

  public static class Logger extends InstanceLogger {
    @Override
    public String getLogName(InstanceState state, Object option) {
      String label = state.getAttributeValue(StdAttr.LABEL);
      return label.isEmpty() ? null : label;
    }

    @Override
    public Value getLogValue(InstanceState state, Object option) {
      return state.getPortValue(VALUE);
    }
  }

  public SimpleCounter() {
    super("SimpleCounter", S.getter("simpleCounterComponent"));
    setAttributes(new Attribute<?>[] {
      StdAttr.WIDTH, StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT
    }, new Object[] {BitWidth.create(8), "", Direction.NORTH, StdAttr.DEFAULT_LABEL_FONT});
    setIcon(new CounterIcon());
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setInstanceLogger(Logger.class);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    int bits = attrs.getValue(StdAttr.WIDTH).getWidth();
    int digits = unsigned(attrs.getValue(StdAttr.WIDTH).getMask()).toString().length();
    int width = Math.max(120, ((digits * 10 + 49) / 20) * 20);
    return Bounds.create(-width / 2, -40, width, 80);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    configurePorts(instance);
    instance.computeLabelTextField(Instance.AVOID_SIDES);
  }

  private void configurePorts(Instance instance) {
    int halfWidth = instance.getBounds().getWidth() / 2;
    Port[] ports = new Port[5];
    ports[MAX] = new Port(-halfWidth, -20, Port.INPUT, StdAttr.WIDTH);
    ports[CLOCK] = new Port(-halfWidth, 20, Port.INPUT, 1);
    ports[RESET] = new Port(0, 40, Port.INPUT, 1);
    ports[VALUE] = new Port(halfWidth, -20, Port.OUTPUT, StdAttr.WIDTH);
    ports[ZERO] = new Port(halfWidth, 20, Port.OUTPUT, 1);
    ports[MAX].setToolTip(S.getter("simpleCounterMaxTip"));
    ports[CLOCK].setToolTip(S.getter("simpleCounterClockTip"));
    ports[RESET].setToolTip(S.getter("simpleCounterResetTip"));
    ports[VALUE].setToolTip(S.getter("simpleCounterValueTip"));
    ports[ZERO].setToolTip(S.getter("simpleCounterZeroTip"));
    instance.setPorts(ports);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.WIDTH) {
      instance.recomputeBounds();
      configurePorts(instance);
      instance.computeLabelTextField(Instance.AVOID_SIDES);
    } else if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(Instance.AVOID_SIDES);
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) { painter.drawBounds(); }

  @Override
  public void paintInstance(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    Font oldFont = g.getFont();
    Color oldColor = g.getColor();
    Bounds b = painter.getBounds();
    int x = b.getCenterX(), y = b.getCenterY();
    g.setColor(Color.BLACK);
    painter.drawBounds();
    g.setFont(oldFont.deriveFont(10f));
    text(g, "Max", b.getX() + 8, y - 20, GraphicsUtil.H_LEFT);
    text(g, "CLK", b.getX() + 14, y + 20, GraphicsUtil.H_LEFT);
    text(g, "RST", x, y + 28, GraphicsUtil.H_CENTER);
    text(g, "Value", b.getX() + b.getWidth() - 8, y - 20, GraphicsUtil.H_RIGHT);
    text(g, "Zero", b.getX() + b.getWidth() - 8, y + 20, GraphicsUtil.H_RIGHT);
    g.setFont(oldFont.deriveFont(Font.BOLD, 12f));
    text(g, "CTR", x, y - 23, GraphicsUtil.H_CENTER);
    g.setFont(oldFont.deriveFont(16f));
    g.setColor(new Color(0, 120, 48));
    String value = "0";
    if (painter.getShowState()) value = painter.getPortValue(VALUE).toDecimalString(false);
    text(g, value, x, y + 5, GraphicsUtil.H_CENTER);
    g.setFont(oldFont);
    g.setColor(oldColor);
    painter.drawPorts();
    painter.drawClock(CLOCK, Direction.EAST);
    painter.drawLabel();
  }

  private static void text(Graphics g, String value, int x, int y, int align) {
    GraphicsUtil.drawText(g, value, x, y, align, GraphicsUtil.V_CENTER_OVERALL);
  }

  private static BigInteger unsigned(long value) {
    return new BigInteger(Long.toUnsignedString(value));
  }

  @Override
  public void propagate(InstanceState state) {
    State data = (State) state.getData();
    if (data == null) { data = new State(); state.setData(data); }
    BitWidth width = state.getAttributeValue(StdAttr.WIDTH);
    boolean edge = data.updateClock(state.getPortValue(CLOCK), StdAttr.TRIG_RISING);
    if (state.getPortValue(RESET) == Value.TRUE) {
      data.value = 0;
    } else {
      Value maximum = state.getPortValue(MAX);
      BigInteger limit;
      if (!state.isPortConnected(MAX)) {
        limit = unsigned(width.getMask());
      } else if (maximum.isFullyDefined()) {
        limit = unsigned(maximum.toLongValue());
      } else {
        state.setPort(VALUE, maximum.isErrorValue() ? Value.createError(width)
            : Value.createUnknown(width), DELAY);
        state.setPort(ZERO, maximum.isErrorValue() ? Value.ERROR : Value.UNKNOWN, DELAY);
        return;
      }
      BigInteger modulus = limit.add(BigInteger.ONE);
      BigInteger value = unsigned(data.value & width.getMask());
      if (edge) value = value.add(BigInteger.ONE);
      data.value = value.mod(modulus).longValue();
    }
    state.setPort(VALUE, Value.createKnown(width, data.value), DELAY);
    state.setPort(ZERO, data.value == 0 ? Value.TRUE : Value.FALSE, DELAY);
  }
}

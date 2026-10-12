/**
 * This file is part of LoongSim.
 *
 * LoongSim is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package com.cburch.logisim.std.arith;

import static com.cburch.logisim.std.Strings.S;
import com.cburch.logisim.LogisimVersion;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.gui.icons.ArithmeticIcon;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;

/** A compact, fixed-operation arithmetic logic unit for CPU datapaths. */
public class ALU extends InstanceFactory {
  static final int A = 0;
  static final int B = 1;
  static final int CONTROL = 2;
  static final int ANSWER = 3;
  static final int CARRY = 4;

  private static final int PER_DELAY = 1;

  public ALU() {
    super("ALU", S.getter("aluComponent"));
    setAttributes(new Attribute[] {StdAttr.WIDTH, ArithmeticSymbol.SPACIOUS},
        new Object[] {BitWidth.create(8), Boolean.TRUE});
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setIcon(new ArithmeticIcon("ALU", 3));
    setPorts(createPorts(true));
  }

  private Port[] createPorts(boolean spacious) {
    Port[] ports = new Port[5];
    ports[A] = new Port(spacious ? -50 : -60, spacious ? -50 : -10, Port.INPUT, StdAttr.WIDTH);
    ports[B] = new Port(spacious ? -50 : -60, spacious ? 50 : 10, Port.INPUT, StdAttr.WIDTH);
    ports[CONTROL] = new Port(spacious ? -20 : 0, spacious ? -70 : -30, Port.INPUT, 3);
    ports[ANSWER] = new Port(spacious ? 50 : 60, spacious ? 0 : -10, Port.OUTPUT, StdAttr.WIDTH);
    ports[CARRY] = new Port(spacious ? -20 : 60, spacious ? 70 : 10, Port.OUTPUT, 1);
    ports[A].setToolTip(S.getter("aluInputATip"));
    ports[B].setToolTip(S.getter("aluInputBTip"));
    ports[CONTROL].setToolTip(S.getter("aluControlTip"));
    ports[ANSWER].setToolTip(S.getter("aluAnswerTip"));
    ports[CARRY].setToolTip(S.getter("aluCarryTip"));
    return ports;
  }

  @Override
  public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
    if (attr == ArithmeticSymbol.SPACIOUS) return Boolean.FALSE;
    return super.getDefaultAttributeValue(attr, ver);
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    return attrs.getValue(ArithmeticSymbol.SPACIOUS) ? Bounds.create(-50, -70, 100, 140)
        : Bounds.create(-60, -30, 120, 60);
  }

  @Override
  public boolean contains(Location loc, AttributeSet attrs) {
    if (!attrs.getValue(ArithmeticSymbol.SPACIOUS)) return super.contains(loc, attrs);
    return ArithmeticSymbol.aluOutline(0, 0).intersects(loc.getX() - 1, loc.getY() - 1, 2, 2);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    instance.setPorts(createPorts(instance.getAttributeValue(ArithmeticSymbol.SPACIOUS)));
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ArithmeticSymbol.SPACIOUS) {
      instance.recomputeBounds();
      instance.setPorts(createPorts(instance.getAttributeValue(ArithmeticSymbol.SPACIOUS)));
    }
  }

  @Override
  public void paintGhost(InstancePainter painter) {
    if (painter.getAttributeValue(ArithmeticSymbol.SPACIOUS)) ArithmeticSymbol.drawAluOutline(painter);
    else painter.drawBounds();
  }

  @Override
  public String getHDLName(AttributeSet attrs) {
    return CorrectLabel.getCorrectLabel(this.getName());
  }

  @Override
  public boolean HDLSupportedComponent(String HDLIdentifier, AttributeSet attrs) {
    if (MyHDLGenerator == null) MyHDLGenerator = new ALUHDLGeneratorFactory();
    return MyHDLGenerator.HDLTargetSupported(HDLIdentifier, attrs);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    ArithmeticSymbol.drawAlu(painter);
  }

  @Override
  public void propagate(InstanceState state) {
    BitWidth width = state.getAttributeValue(StdAttr.WIDTH);
    Value a = state.getPortValue(A);
    Value b = state.getPortValue(B);
    Value control = state.getPortValue(CONTROL);
    Value answer;
    Value carry = Value.FALSE;

    if (!control.isFullyDefined()) {
      boolean error = control.isErrorValue();
      answer = error ? Value.createError(width) : Value.createUnknown(width);
      carry = error ? Value.ERROR : Value.UNKNOWN;
    } else {
      switch ((int) control.toLongValue()) {
        case 0: // A & B
          answer = a.and(b);
          break;
        case 1: // A | B
          answer = a.or(b);
          break;
        case 2: // A + B
          Value[] sum = Adder.computeSum(width, a, b, Value.FALSE);
          answer = sum[0];
          carry = sum[1];
          break;
        case 4: // A & ~B
          answer = a.and(b.not());
          break;
        case 5: // A | ~B
          answer = a.or(b.not());
          break;
        case 6: // A - B; carry is one when no unsigned borrow occurs.
          Value[] difference = Adder.computeSum(width, a, b.not(), Value.TRUE);
          answer = difference[0];
          carry = difference[1];
          break;
        case 7: // Signed set-less-than, returned in bit zero.
          answer = signedLessThan(width, a, b);
          break;
        default: // 011 is intentionally not assigned by the ALU specification.
          answer = Value.createError(width);
          carry = Value.ERROR;
          break;
      }
    }

    int delay = (width.getWidth() + 2) * PER_DELAY;
    state.setPort(ANSWER, answer, delay);
    state.setPort(CARRY, carry, delay);
  }

  private static Value signedLessThan(BitWidth width, Value a, Value b) {
    if (!a.isFullyDefined() || !b.isFullyDefined()) {
      return a.isErrorValue() || b.isErrorValue()
          ? Value.createError(width)
          : Value.createUnknown(width);
    }

    int bits = width.getWidth();
    long av = a.toLongValue();
    long bv = b.toLongValue();
    if (bits < 64) {
      long signBit = 1L << (bits - 1);
      long mask = (1L << bits) - 1;
      if ((av & signBit) != 0) av |= ~mask;
      if ((bv & signBit) != 0) bv |= ~mask;
    }
    return Value.createKnown(width, av < bv ? 1 : 0);
  }
}

/**
 * This file is part of LoongSim.
 *
 * LoongSim is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package com.cburch.logisim.std.arith;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.gui.icons.ArithmeticIcon;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Graphics;

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
    setAttributes(new Attribute[] {StdAttr.WIDTH}, new Object[] {BitWidth.create(8)});
    setKeyConfigurator(new BitWidthConfigurator(StdAttr.WIDTH));
    setOffsetBounds(Bounds.create(-60, -30, 120, 60));
    setIcon(new ArithmeticIcon("ALU", 3));

    Port[] ports = new Port[5];
    ports[A] = new Port(-60, -10, Port.INPUT, StdAttr.WIDTH);
    ports[B] = new Port(-60, 10, Port.INPUT, StdAttr.WIDTH);
    ports[CONTROL] = new Port(0, -30, Port.INPUT, 3);
    ports[ANSWER] = new Port(60, -10, Port.OUTPUT, StdAttr.WIDTH);
    ports[CARRY] = new Port(60, 10, Port.OUTPUT, 1);
    ports[A].setToolTip(S.getter("aluInputATip"));
    ports[B].setToolTip(S.getter("aluInputBTip"));
    ports[CONTROL].setToolTip(S.getter("aluControlTip"));
    ports[ANSWER].setToolTip(S.getter("aluAnswerTip"));
    ports[CARRY].setToolTip(S.getter("aluCarryTip"));
    setPorts(ports);
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
    Graphics g = painter.getGraphics();
    painter.drawBounds();
    Location loc = painter.getLocation();
    int x = loc.getX();
    int y = loc.getY();
    g.setColor(new Color(0, 128, 0));
    GraphicsUtil.drawCenteredText(g, "ALU", x, y);

    g.setColor(Color.GRAY);
    painter.drawPort(A, "A", Direction.EAST);
    painter.drawPort(B, "B", Direction.EAST);
    painter.drawPort(CONTROL, "CTR", Direction.NORTH);
    painter.drawPort(ANSWER, "Ans", Direction.WEST);
    painter.drawPort(CARRY, "Carry", Direction.WEST);
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

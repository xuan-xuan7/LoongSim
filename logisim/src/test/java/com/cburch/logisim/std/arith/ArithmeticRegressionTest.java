package com.cburch.logisim.std.arith;

import static org.junit.Assert.*;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.test.ComponentState;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Independent BigInteger oracle, including unsigned carry and signed comparisons. */
@RunWith(Parameterized.class)
public class ArithmeticRegressionTest {
  @Parameterized.Parameters(name="width={0}") public static Object[][] widths() {
    return new Object[][] {{1},{2},{4},{8},{16},{32},{63},{64}};
  }
  private final int bits;
  private final BitWidth width;
  private final BigInteger modulus;
  private final BigInteger mask;
  public ArithmeticRegressionTest(int bits) {
    this.bits=bits; width=BitWidth.create(bits);
    modulus=BigInteger.ONE.shiftLeft(bits); mask=modulus.subtract(BigInteger.ONE);
  }
  private BigInteger unsigned(long value) { return new BigInteger(Long.toUnsignedString(value)).and(mask); }
  private BigInteger signed(BigInteger value) { return value.testBit(bits-1) ? value.subtract(modulus) : value; }
  private Value known(long value) { return Value.createKnown(width,value); }
  private ComponentState component(InstanceFactory factory) {
    AttributeSet attributes=factory.createAttributeSet(); attributes.setValue(StdAttr.WIDTH,width);
    return new ComponentState(factory,attributes);
  }
  private List<long[]> operands() {
    List<long[]> pairs=new ArrayList<>();
    if(bits<=4) {
      for(int a=0;a<(1<<bits);a++) for(int b=0;b<(1<<bits);b++) pairs.add(new long[]{a,b});
    } else {
      long[] edges={0,1,2,3,width.getMask(),width.getMask()-1,Long.MIN_VALUE,Long.MAX_VALUE,1L<<(bits-1)};
      for(long a:edges) for(long b:edges) pairs.add(new long[]{a,b});
      Random random=new Random(0x10A6L+bits);
      for(int i=0;i<512;i++) pairs.add(new long[]{random.nextLong(),random.nextLong()});
    }
    return pairs;
  }
  private void expect(ComponentState state,int port,BigInteger result) {
    Value actual=state.getPortValue(port);
    assertTrue("undefined result at width "+bits+": "+actual,actual.isFullyDefined());
    assertEquals(result.and(mask).longValue(),actual.toLongValue());
  }
  @Test public void additionMatchesExactArithmeticIncludingCarry() {
    ComponentState state=component(new Adder()); int vectors=0;
    for(long[] pair:operands()) for(int carry=0;carry<2;carry++) {
      BigInteger result=unsigned(pair[0]).add(unsigned(pair[1])).add(BigInteger.valueOf(carry));
      state.drive(0,known(pair[0])).drive(1,known(pair[1])).drive(3,carry==1?Value.TRUE:Value.FALSE).propagate();
      expect(state,2,result); assertEquals(result.testBit(bits)?Value.TRUE:Value.FALSE,state.getPortValue(4)); vectors++;
    }
    System.out.println("ADDER_REFERENCE_VECTORS="+vectors);
  }
  @Test public void subtractionMatchesExactArithmeticIncludingBorrow() {
    ComponentState state=component(new Subtractor()); int vectors=0;
    for(long[] pair:operands()) for(int borrow=0;borrow<2;borrow++) {
      BigInteger result=unsigned(pair[0]).subtract(unsigned(pair[1])).subtract(BigInteger.valueOf(borrow));
      state.drive(0,known(pair[0])).drive(1,known(pair[1])).drive(3,borrow==1?Value.TRUE:Value.FALSE).propagate();
      expect(state,2,result); assertEquals(result.signum()<0?Value.TRUE:Value.FALSE,state.getPortValue(4)); vectors++;
    }
    System.out.println("SUBTRACTOR_REFERENCE_VECTORS="+vectors);
  }
  @Test public void allAluOperationsMatchIndependentReference() {
    ComponentState state=component(new ALU()); int vectors=0;
    for(long[] pair:operands()) for(int control:new int[]{0,1,2,4,5,6,7}) {
      BigInteger a=unsigned(pair[0]),b=unsigned(pair[1]),result; boolean carry=false;
      switch(control) {
        case 0: result=a.and(b); break;
        case 1: result=a.or(b); break;
        case 2: result=a.add(b); carry=result.testBit(bits); break;
        case 4: result=a.and(b.xor(mask)); break;
        case 5: result=a.or(b.xor(mask)); break;
        case 6: result=a.subtract(b); carry=a.compareTo(b)>=0; break;
        default: result=signed(a).compareTo(signed(b))<0?BigInteger.ONE:BigInteger.ZERO;
      }
      state.drive(ALU.A,known(pair[0])).drive(ALU.B,known(pair[1]))
          .drive(ALU.CONTROL,Value.createKnown(BitWidth.create(3),control)).propagate();
      expect(state,ALU.ANSWER,result); assertEquals(carry?Value.TRUE:Value.FALSE,state.getPortValue(ALU.CARRY)); vectors++;
    }
    System.out.println("ALU_REFERENCE_VECTORS="+vectors);
  }
  @Test public void invalidAndUnknownControlAreReportedInsteadOfInventingNumbers() {
    ComponentState state=component(new ALU());
    state.drive(ALU.A,known(0)).drive(ALU.B,known(1))
        .drive(ALU.CONTROL,Value.createKnown(BitWidth.create(3),3)).propagate();
    assertTrue(state.getPortValue(ALU.ANSWER).isErrorValue()); assertEquals(Value.ERROR,state.getPortValue(ALU.CARRY));
    state.drive(ALU.CONTROL,Value.createUnknown(BitWidth.create(3))).propagate();
    assertFalse(state.getPortValue(ALU.ANSWER).isFullyDefined()); assertFalse(state.getPortValue(ALU.ANSWER).isErrorValue());
    state.drive(ALU.CONTROL,Value.createError(BitWidth.create(3))).propagate();
    assertTrue(state.getPortValue(ALU.ANSWER).isErrorValue());
  }
}

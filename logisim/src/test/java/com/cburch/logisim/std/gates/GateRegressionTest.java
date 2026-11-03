package com.cburch.logisim.std.gates;

import static org.junit.Assert.*;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.test.ComponentState;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GateRegressionTest {
  @Parameterized.Parameters(name="width={0}, inputs={1}") public static List<Object[]> cases() {
    List<Object[]> cases=new ArrayList<>();
    for(int width:new int[]{1,4,8,32,64}) for(int inputs:new int[]{2,3,4,8}) cases.add(new Object[]{width,inputs});
    return cases;
  }
  private final int bits,inputs;
  public GateRegressionTest(int bits,int inputs){this.bits=bits;this.inputs=inputs;}
  @SuppressWarnings("unchecked")
  @Test public void completeBooleanTablesAndMixedBusPatterns() {
    BitWidth width=BitWidth.create(bits); BigInteger mask=BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
    Random random=new Random(bits*911L+inputs); int vectors=0;
    Project project=new Project(LogisimFile.createNew(new Loader(null),null));
    try {
    for(String name:new String[]{"NOT Gate","AND Gate","OR Gate","NAND Gate","NOR Gate","XOR Gate","XNOR Gate"}) {
      InstanceFactory factory=(InstanceFactory)((AddTool)new Gates().getTool(name)).getFactory();
      AttributeSet attributes=factory.createAttributeSet(); attributes.setValue(StdAttr.WIDTH,width);
      int count=name.equals("NOT Gate")?1:inputs;
      if(count>1) attributes.setValue((Attribute<Integer>)attributes.getAttribute("inputs"),count);
      ComponentState state=new ComponentState(factory,attributes).withProject(project);
      int booleanVectors=1<<count;
      for(int pattern=0;pattern<booleanVectors+128;pattern++) {
        BigInteger and=mask,or=BigInteger.ZERO,xor=BigInteger.ZERO;
        for(int input=0;input<count;input++) {
          long raw=pattern<booleanVectors?(((pattern>>input)&1)==1?width.getMask():0):random.nextLong();
          BigInteger value=new BigInteger(Long.toUnsignedString(raw)).and(mask);
          state.drive(input+1,Value.createKnown(width,raw)); and=and.and(value);or=or.or(value);xor=xor.xor(value);
        }
        BigInteger expected=name.contains("AND")?and:name.contains("OR")&&!name.contains("X")?or:xor;
        if(name.equals("NOT Gate")) expected=or.xor(mask);
        else if(name.equals("NAND Gate")||name.equals("NOR Gate")||name.equals("XNOR Gate")) expected=expected.xor(mask);
        state.propagate(); Value actual=state.getPortValue(0);
        assertTrue(name+" returned undefined data",actual.isFullyDefined());
        assertEquals(name+" width="+bits+" inputs="+count,expected.longValue(),actual.toLongValue());vectors++;
      }
    }
    System.out.println("GATE_REFERENCE_VECTORS="+vectors);
    } finally { project.getSimulator().shutDown(); }
  }
}

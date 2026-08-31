package com.cburch.logisim.fpga.designrulecheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cburch.logisim.fpga.hdlgenerator.HDLGeneratorFactory;

import org.junit.Test;

public class CorrectLabelTest {
  @Test
  public void encodesChineseDisplayLabelsAsAsciiHdlIdentifiers() {
    String hdlLabel = CorrectLabel.getCorrectLabel("输入信号");

    assertEquals("L__u8f93__u5165__u4fe1__u53f7_", hdlLabel);
    assertTrue(CorrectLabel.IsCorrectLabel(hdlLabel, HDLGeneratorFactory.VERILOG));
    assertTrue(CorrectLabel.IsCorrectLabel(hdlLabel, HDLGeneratorFactory.VHDL));
  }

  @Test
  public void retainsExistingAsciiLabelFormatting() {
    assertEquals("data_bus", CorrectLabel.getCorrectLabel("data bus"));
    assertEquals("L_8bit", CorrectLabel.getCorrectLabel("8bit"));
  }
}

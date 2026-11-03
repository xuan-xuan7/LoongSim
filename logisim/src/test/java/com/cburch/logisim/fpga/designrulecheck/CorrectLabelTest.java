package com.cburch.logisim.fpga.designrulecheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import com.cburch.logisim.fpga.hdlgenerator.HDLGeneratorFactory;

import org.junit.Test;
import com.cburch.logisim.util.LocaleManager;
import java.util.Locale;

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

  @Test public void invalidCharacterDiagnosticsDoNotCrashInSupportedLanguages() {
    Locale original=LocaleManager.getLocale();
    try {
      for(Locale locale:new Locale[]{Locale.ENGLISH,Locale.SIMPLIFIED_CHINESE,Locale.FRENCH,new Locale("ru")}) {
        LocaleManager.setLocale(locale);
        String diagnostic=CorrectLabel.NameErrors("中",HDLGeneratorFactory.VERILOG,"test label ");
        assertNotNull(diagnostic);assertTrue(diagnostic.contains("中"));
      }
    } finally {LocaleManager.setLocale(original);}
  }
}

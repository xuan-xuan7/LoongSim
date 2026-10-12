package com.cburch.logisim.analyze.model;

import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import org.junit.Test;

public class UnicodeParserTest {
  @org.junit.BeforeClass
  public static void initializeLocale() {
    com.cburch.logisim.util.LocaleManager.setLocale(java.util.Locale.ENGLISH);
  }

  @Test
  public void parsesChineseAndSupplementaryUnicodeSignals() throws Exception {
    AnalyzerModel model = new AnalyzerModel();
    model.setVariables(
        Arrays.asList(new Var("\u8f93\u5165", 1), new Var("\uD840\uDC00\u4fe1\u53f7", 2)),
        Arrays.asList(new Var("\u8f93\u51fa", 1)));
    assertNotNull(Parser.parse("\u8f93\u5165 & \uD840\uDC00\u4fe1\u53f7[1]", model));
    assertNotNull(Parser.parseMaybeAssignment("\u8f93\u51fa = ~\u8f93\u5165", model));
  }

  @Test
  public void parsesCombiningMarks() throws Exception {
    AnalyzerModel model = new AnalyzerModel();
    model.setVariables(Arrays.asList(new Var("e\u0301tat", 1)), Arrays.asList(new Var("result", 1)));
    assertNotNull(Parser.parse("~e\u0301tat", model));
  }
}

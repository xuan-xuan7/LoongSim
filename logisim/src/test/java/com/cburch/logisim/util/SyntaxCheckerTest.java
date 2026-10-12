package com.cburch.logisim.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SyntaxCheckerTest {
  @org.junit.BeforeClass
  public static void initializeLocale() {
    com.cburch.logisim.util.LocaleManager.setLocale(java.util.Locale.ENGLISH);
  }

  @Test
  public void acceptsUnicodeVariableAndCircuitNames() {
    for (String name : new String[] {
        "\u8f93\u5165", "\u6570\u636e_1", "ALU\u7ed3\u679c", "\uD840\uDC00\u4fe1\u53f7", "e\u0301tat", "A1"
    }) {
      assertNull(name, SyntaxChecker.getErrorMessage(name));
    }
  }

  @Test
  public void stillRejectsAmbiguousExpressionNames() {
    for (String name : new String[] {"1A", "A B", "A+B", "A[0]", "A__B", "A_", "\u8f93\u5165\n"}) {
      assertNotNull(name, SyntaxChecker.getErrorMessage(name));
    }
  }
}

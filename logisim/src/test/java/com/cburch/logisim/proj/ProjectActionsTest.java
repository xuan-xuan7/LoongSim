package com.cburch.logisim.proj;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import org.junit.Test;

public class ProjectActionsTest {
  @Test
  public void acceptsUnicodeAndOrdinaryFilesystemNames() {
    for (String name : new String[] {
        "\u8bfe\u7a0b\u5b9e\u9a8c.circ", "\u52a0\u6cd5\u5668 - \u7b2c 1 \u7248.circ",
        "\uD840\uDC00\u4fe1\u53f7.circ", "circuit.circ", "Pin", "main", "\u7535\u8def.CIRC"
    }) {
      assertTrue(name, ProjectActions.checkValidFilename(name, new HashMap<>()));
    }
  }

  @Test
  public void rejectsUnsafeAndReservedNames() {
    for (String name : new String[] {
        "", ".", "..", "bad/name", "bad\\name", "bad:name", "bad*name", "bad?name",
        "bad<name", "bad>name", "bad|name", "bad\"name", "bad\nname", "bad\u0000name",
        "name.", "name ", "CON", "con.circ", "NUL.circ", "COM1.circ", "lpt9.txt"
    }) {
      HashMap<String, String> errors = new HashMap<>();
      assertFalse(name, ProjectActions.checkValidFilename(name, errors));
      assertTrue(name, errors.containsValue("InvalidFileFormatError"));
    }
  }
}

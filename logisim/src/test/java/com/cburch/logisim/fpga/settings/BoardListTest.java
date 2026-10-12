package com.cburch.logisim.fpga.settings;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BoardListTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test
  public void scansChineseDirectoriesAndSkipsMissingClasspathEntries() throws Exception {
    File directory = temporary.newFolder("\u4e2d\u6587\u5b89\u88c5\u76ee\u5f55");
    Path boards = directory.toPath().resolve("resources/logisim/boards");
    Files.createDirectories(boards);
    Files.createFile(boards.resolve("\u6559\u5b66\u677f.xml"));
    String original = System.getProperty("java.class.path");
    try {
      System.setProperty("java.class.path", directory.getPath() + File.pathSeparator
          + new File(directory, "\u4e0d\u5b58\u5728\u7684\u76ee\u5f55").getPath());
      assertTrue(new BoardList().GetBoardNames().contains("\u6559\u5b66\u677f"));
    } finally {
      System.setProperty("java.class.path", original);
    }
  }
}

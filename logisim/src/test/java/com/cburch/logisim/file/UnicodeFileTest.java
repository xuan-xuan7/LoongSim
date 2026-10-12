package com.cburch.logisim.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cburch.logisim.data.TestVector;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.Template;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UnicodeFileTest {
  @org.junit.BeforeClass
  public static void initializeLocale() {
    com.cburch.logisim.util.LocaleManager.setLocale(java.util.Locale.ENGLISH);
  }

  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  private static final String CIRCUIT_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
      + "<project source=\"3.3.6\" version=\"1.0\">"
      + "<lib desc=\"#Wiring\" name=\"0\"/><main name=\"\u4e3b\u7535\u8def\"/>"
      + "<circuit name=\"\u4e3b\u7535\u8def\"><comp name=\"\u5b50\u7535\u8def\" loc=\"(200,100)\"/></circuit>"
      + "<circuit name=\"\u5b50\u7535\u8def\"><comp lib=\"0\" name=\"Pin\" loc=\"(100,100)\">"
      + "<a name=\"label\" val=\"\u8f93\u5165\u4fe1\u53f7\"/></comp></circuit></project>";

  @Test
  public void savesAndReopensChineseProjectCircuitAndSignalNames() throws Exception {
    Loader loader = new Loader(null);
    LogisimFile project = loader.openLogisimFile(new ByteArrayInputStream(CIRCUIT_XML.getBytes(StandardCharsets.UTF_8)));
    File file = new File(temporary.newFolder("\u4e2d\u6587\u76ee\u5f55"), "\u8bfe\u7a0b\u5b9e\u9a8c - 1.circ");
    assertTrue(loader.save(project, file));
    LogisimFile reopened = new Loader(null).openLogisimFile(file);
    assertEquals("\u8bfe\u7a0b\u5b9e\u9a8c - 1", reopened.getName());
    assertEquals("\u4e3b\u7535\u8def", reopened.getMainCircuit().getName());
    assertEquals("\u8f93\u5165\u4fe1\u53f7", reopened.getCircuit("\u5b50\u7535\u8def")
        .getNonWires().iterator().next().getAttributeSet().getValue(StdAttr.LABEL));
    assertEquals("\u5b50\u7535\u8def", reopened.getMainCircuit().getNonWires().iterator().next().getFactory().getName());
    assertTrue(reopened.getLoader().save(reopened, file));
    assertEquals("\u4e3b\u7535\u8def", new Loader(null).openLogisimFile(file).getMainCircuit().getName());
  }

  @Test
  public void templateKeepsUtf8CircuitAndLabels() throws Exception {
    Template template = Template.create(new ByteArrayInputStream(CIRCUIT_XML.getBytes(StandardCharsets.UTF_8)));
    try (java.io.InputStream stream = template.createStream()) {
      assertEquals(CIRCUIT_XML, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test
  public void loadsChineseTestVectorHeaders() throws Exception {
    File file = temporary.newFile("\u6d4b\u8bd5\u5411\u91cf.txt");
    Files.writeString(file.toPath(), "\uFEFF\u8f93\u5165 \u8f93\u51fa\n0 0\n1 1\n", StandardCharsets.UTF_8);
    TestVector vector = new TestVector(file);
    assertEquals("\u8f93\u5165", vector.columnName[0]);
    assertEquals("\u8f93\u51fa", vector.columnName[1]);
    assertEquals(2, vector.data.size());
  }

  @Test
  public void hdlCommentsKeepChineseText() throws Exception {
    File file = temporary.newFile("\u7535\u8def.vhd");
    String text = "-- \u4e2d\u6587\u6ce8\u91ca" + System.lineSeparator();
    com.cburch.logisim.vhdl.file.HdlFile.save(file, text);
    assertEquals(text, Files.readString(file.toPath(), StandardCharsets.UTF_8));
    assertEquals(text, com.cburch.logisim.vhdl.file.HdlFile.load(file));
  }

  @Test
  public void waveformExportKeepsChineseSignalNames() throws Exception {
    com.cburch.logisim.gui.chronogram.chronodata.ChronoData data =
        new com.cburch.logisim.gui.chronogram.chronodata.ChronoData();
    for (String name : new String[] {"sysclk", "\u4e2d\u6587\u4fe1\u53f7"}) {
      data.getSignalOrder().add(name);
      data.put(name, new com.cburch.logisim.gui.chronogram.chronodata.SignalData(
          name, new java.util.ArrayList<>(java.util.Arrays.asList("0", "1"))));
    }
    File file = temporary.newFile("\u6ce2\u5f62.log");
    com.cburch.logisim.gui.chronogram.chronodata.ChronoDataWriter.export(file.getPath(), null, data);
    assertTrue(Files.readString(file.toPath(), StandardCharsets.UTF_8).startsWith("sysclk\t\u4e2d\u6587\u4fe1\u53f7\t"));
  }

  @Test
  public void chineseLibraryPathSurvivesProjectRoundTrip() throws Exception {
    File directory = temporary.newFolder("\u4e2d\u6587\u5e93\u76ee\u5f55");
    File library = new File(directory, "\u5b50\u7535\u8def\u5e93.circ");
    Files.writeString(library.toPath(), CIRCUIT_XML, StandardCharsets.UTF_8);
    File parent = new File(directory, "\u5de5\u7a0b.CIRC");
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<project source=\"3.3.6\" version=\"1.0\">"
        + "<lib name=\"0\" desc=\"file#\u5b50\u7535\u8def\u5e93.circ\"/>"
        + "<main name=\"main\"/><circuit name=\"main\">"
        + "<comp lib=\"0\" name=\"\u5b50\u7535\u8def\" loc=\"(200,100)\"/>"
        + "</circuit></project>";
    Files.writeString(parent.toPath(), xml, StandardCharsets.UTF_8);
    assertTrue(Loader.LOGISIM_FILTER.accept(parent));
    Loader loader = new Loader(null);
    LogisimFile loaded = loader.openLogisimFile(parent);
    assertEquals("\u5de5\u7a0b", loaded.getName());
    assertTrue(loader.save(loaded, parent));
    LogisimFile reopened = new Loader(null).openLogisimFile(parent);
    assertEquals("\u5b50\u7535\u8def", reopened.getMainCircuit().getNonWires().iterator().next().getFactory().getName());
  }

  @Test
  public void temporaryHdlSourceKeepsChineseComments() throws Exception {
    File file = com.cburch.logisim.util.FileUtil.createTmpFile("-- \u4e2d\u6587\u6ce8\u91ca", "loongsim-test-", ".vhd");
    try {
      assertEquals("-- \u4e2d\u6587\u6ce8\u91ca", Files.readString(file.toPath(), StandardCharsets.UTF_8));
    } finally {
      Files.delete(file.toPath());
    }
  }
}

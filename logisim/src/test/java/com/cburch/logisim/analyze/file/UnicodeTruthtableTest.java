package com.cburch.logisim.analyze.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cburch.logisim.analyze.data.CsvInterpretor;
import com.cburch.logisim.analyze.data.CsvParameter;
import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Entry;
import com.cburch.logisim.analyze.model.Var;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UnicodeTruthtableTest {
  @org.junit.BeforeClass
  public static void initializeLocale() {
    com.cburch.logisim.util.LocaleManager.setLocale(java.util.Locale.ENGLISH);
  }

  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  private AnalyzerModel model() {
    AnalyzerModel model = new AnalyzerModel();
    model.setVariables(Arrays.asList(new Var("\u8f93\u5165", 2)), Arrays.asList(new Var("\u8f93\u51fa", 1)));
    for (int row = 0; row < 4; row++) {
      model.getTruthTable().setOutputEntry(row, 0, row % 2 == 0 ? Entry.ZERO : Entry.ONE);
    }
    return model;
  }

  private void assertSameTable(AnalyzerModel loaded) {
    assertEquals("\u8f93\u5165", loaded.getInputs().vars.get(0).name);
    assertEquals(2, loaded.getInputs().vars.get(0).width);
    assertEquals("\u8f93\u51fa", loaded.getOutputs().vars.get(0).name);
    for (int row = 0; row < 4; row++) {
      assertEquals(row % 2 == 0 ? Entry.ZERO : Entry.ONE, loaded.getTruthTable().getOutputEntry(row, 0));
    }
  }

  @Test
  public void textTableRoundTripInChineseDirectory() throws Exception {
    File file = new File(temporary.newFolder("\u5b9e\u9a8c\u8d44\u6599"), "\u771f\u503c\u8868.txt");
    TruthtableTextFile.doSave(file, model());
    assertTrue(Files.readString(file.toPath(), StandardCharsets.UTF_8).contains("\u8f93\u5165"));
    AnalyzerModel loaded = new AnalyzerModel();
    TruthtableTextFile.doLoad(file, loaded, null);
    assertSameTable(loaded);
  }

  @Test
  public void csvTableRoundTrip() throws Exception {
    File file = temporary.newFile("\u771f\u503c\u8868.csv");
    TruthtableCsvFile.doSave(file, model());
    assertTrue(Files.readString(file.toPath(), StandardCharsets.UTF_8).contains("\u8f93\u5165"));
    AnalyzerModel loaded = new AnalyzerModel();
    new CsvInterpretor(file, new CsvParameter(), null).getTruthTable(loaded);
    assertSameTable(loaded);
  }

  @Test
  public void importsUtf8BomInTextAndCsv() throws Exception {
    File text = temporary.newFile("bom.txt");
    Files.writeString(text.toPath(), "\uFEFF\u8f93\u5165 | \u8f93\u51fa\n0 | 0\n1 | 1\n", StandardCharsets.UTF_8);
    AnalyzerModel loaded = new AnalyzerModel();
    TruthtableTextFile.doLoad(text, loaded, null);
    assertEquals("\u8f93\u5165", loaded.getInputs().vars.get(0).name);

    File csv = temporary.newFile("bom.csv");
    Files.writeString(csv.toPath(), "\uFEFF\"\u8f93\u5165\",\"|\",\"\u8f93\u51fa\"\n0,\"|\",0\n1,\"|\",1\n", StandardCharsets.UTF_8);
    new CsvInterpretor(csv, new CsvParameter(), null).getTruthTable(loaded);
    assertEquals("\u8f93\u51fa", loaded.getOutputs().vars.get(0).name);
  }
}

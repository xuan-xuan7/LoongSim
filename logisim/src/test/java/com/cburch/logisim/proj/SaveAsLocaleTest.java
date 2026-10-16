package com.cburch.logisim.proj;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.LocaleManager;
import java.awt.Component;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SaveAsLocaleTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private final Locale locale;

  public SaveAsLocaleTest(Locale locale) {
    this.locale = locale;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> locales() {
    return Arrays.asList(new Object[][] {
        {Locale.ENGLISH}, {Locale.SIMPLIFIED_CHINESE}, {new Locale("tr", "TR")}
    });
  }

  @Test
  public void saveAsAcceptsChineseFilenameWithExtension() throws Exception {
    saveAs("\u4e2d\u6587\u7535\u8def - \u7b2c 1 \u7248.circ");
  }

  @Test
  public void saveAsAddsExtensionToChineseFilename() throws Exception {
    saveAs("\u8bfe\u7a0b\u5b9e\u9a8c");
  }

  private void saveAs(String name) throws Exception {
    // Initialize LocaleManager before the per-package Strings singletons.
    Locale originalLocale = LocaleManager.getLocale();
    Preferences preferences = AppPreferences.getPrefs();
    Map<String, String> recent = new HashMap<>();
    for (int i = 0; i < 10; i++) recent.put("recent" + i, preferences.get("recent" + i, null));
    try {
      LocaleManager.setLocale(locale);
      File directory = temporary.newFolder("\u4e2d\u6587\u76ee\u5f55");
      File selection = new File(directory, name);
      SwingUtilities.invokeAndWait(() -> {
        Loader loader = new Loader(null) {
          @Override
          public JFileChooser createChooser() {
            return new JFileChooser() {
              @Override
              public int showSaveDialog(Component parent) {
                setSelectedFile(selection);
                return APPROVE_OPTION;
              }
            };
          }
        };
        Project project = new Project(LogisimFile.createNew(loader, null));
        try {
          assertTrue(ProjectActions.doSaveAs(project));
          File saved = new File(directory, name.endsWith(".circ") ? name : name + ".circ");
          assertEquals(saved, loader.getMainFile());
          assertTrue(saved.isFile());
          LogisimFile reopened = new Loader(null).openLogisimFile(saved);
          assertEquals("main", reopened.getMainCircuit().getName());
          assertEquals(saved.getName().replaceFirst("\\.circ$", ""), reopened.getName());
        } catch (Exception exception) {
          throw new AssertionError(exception);
        } finally {
          project.getSimulator().shutDown();
        }
      });
    } finally {
      LocaleManager.setLocale(originalLocale);
      for (Map.Entry<String, String> entry : recent.entrySet()) {
        if (entry.getValue() == null) preferences.remove(entry.getKey());
        else preferences.put(entry.getKey(), entry.getValue());
      }
    }
  }
}

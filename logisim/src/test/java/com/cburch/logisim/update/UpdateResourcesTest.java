package com.cburch.logisim.update;

import static org.junit.Assert.*;
import static com.cburch.logisim.gui.Strings.S;
import com.cburch.logisim.Main;
import com.cburch.logisim.util.LocaleManager;
import java.util.Locale;
import org.junit.Test;

public class UpdateResourcesTest {
  @Test public void allUpdateMessagesExistInEnglishAndChinese() {
    Locale original=LocaleManager.getLocale();
    try {
      for(Locale locale:new Locale[]{Locale.ENGLISH,Locale.SIMPLIFIED_CHINESE}) {
        LocaleManager.setLocale(locale);
        for(String key:new String[]{"helpCheckUpdatesItem","updateTitle","updateChecking","updateCurrent","updateLatest",
            "updateAvailable","updateUpToDate","updateNoRelease","updateNoReleaseDetail","updateNoNotes",
            "updateNoInstaller","updateDownload","updateReleasePage","updateClose","updateCancel","updateSaveDirectory",
            "updateDownloading","updateDownloaded","updateOpenFolder","updateInstallInstructions","updateFailed",
            "updateErrorNetwork","updateErrorUnavailable","updateErrorRateLimit","updateErrorChecksum","updateErrorSize",
            "updateErrorResponse","updateOpenLinkManually"}) assertNotEquals(locale+" missing "+key,key,S.get(key));
      }
      LocaleManager.setLocale(Locale.SIMPLIFIED_CHINESE);
      assertEquals("检查更新…",S.get("helpCheckUpdatesItem"));
      assertTrue(S.fmt("updateCurrent",Main.LOONGSIM_VERSION_NAME).contains(Main.LOONGSIM_VERSION_NAME));
      assertTrue(S.fmt("updateInstallInstructions","C:/下载/安装包.exe").contains("C:/下载/安装包.exe"));
    } finally {LocaleManager.setLocale(original);}
  }
  @Test public void applicationVersionAndBundledLicenseAreAvailable() {
    assertTrue(ReleaseVersion.parse(Main.LOONGSIM_VERSION_NAME).isPresent());
    assertNotNull(getClass().getResource("/resources/logisim/licenses/Gson-LICENSE.txt"));
    assertNotNull(getClass().getResource("/resources/logisim/licenses/Gson-NOTICE.txt"));
  }
  @Test public void releaseVersionMatchesTheInstallerVersion() throws Exception {
    java.util.Properties build=new java.util.Properties();
    try(var input=java.nio.file.Files.newInputStream(java.nio.file.Path.of("gradle.properties"))){build.load(input);}
    assertEquals("Bump both the application and installer version before publishing an update",
        build.getProperty("version").replaceFirst("-.*$",""),Main.LOONGSIM_VERSION_NAME);
  }
}

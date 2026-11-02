package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FpgaToolchainTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void windowsDoesNotAcceptTheLegacyLoongArchRuntime() throws Exception {
    Path root = temporary.newFolder().toPath();
    Files.createDirectories(root.resolve("usr/bin"));
    Files.writeString(root.resolve("usr/bin/yosys"), "ELF");
    try { new FpgaToolchain(root, true).requireSynthesis(); fail("Wrong-platform runtime accepted"); }
    catch (IOException expected) { assertTrue(expected.getMessage().contains("yosys.exe")); }
  }

  @Test public void implementationRejectsUnmatchedToolVersionsBeforeStartingTools() throws Exception {
    Path root = temporary.newFolder().toPath();
    Files.writeString(root.resolve("runtime.properties"), "platform=windows-x64\n"
        + "ossCadSuite.version=2026-10-28\nopenxc7.version=2026-10-27\ndevice=xc7a200tfbg676-1\n");
    try { new FpgaToolchain(root, true).requireImplementation(); fail("Wrong-version runtime accepted"); }
    catch (IOException expected) { assertTrue(expected.getMessage().contains("版本不匹配")); }
  }
}

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
    try { new FpgaToolchain(root, FpgaToolchain.Platform.WINDOWS).requireSynthesis(); fail("Wrong-platform runtime accepted"); }
    catch (IOException expected) { assertTrue(expected.getMessage().contains("yosys.exe")); }
  }

  @Test public void implementationRejectsUnmatchedToolVersionsBeforeStartingTools() throws Exception {
    Path root = temporary.newFolder().toPath();
    Files.writeString(root.resolve("runtime.properties"), "platform=windows-x64\n"
        + "ossCadSuite.version=2026-10-28\nopenxc7.version=2026-10-27\ndevice=xc7a200tfbg676-1\n");
    try { new FpgaToolchain(root, FpgaToolchain.Platform.WINDOWS).requireImplementation(); fail("Wrong-version runtime accepted"); }
    catch (IOException expected) { assertTrue(expected.getMessage().contains("版本不匹配")); }
  }

  @Test public void loongNixUsesNativePathsAndRejectsAWindowsManifest() throws Exception {
    Path root = temporary.newFolder().toPath();
    FpgaToolchain tools = new FpgaToolchain(root, FpgaToolchain.Platform.LOONGNIX);
    Files.writeString(root.resolve("runtime.properties"), "platform=windows-x64\n"
        + "openxc7.version=2026-10-27\ndevice=xc7a200tfbg676-1\n");
    assertTrue(tools.nextpnr().equals(root.resolve("openxc7/bin/nextpnr-xilinx")));
    assertTrue(tools.xilinxLoader().equals(root.resolve("bin/openFPGALoader")));
    try { tools.requireImplementation(); fail("Windows manifest accepted on Loongnix"); }
    catch (IOException expected) { assertTrue(expected.getMessage().contains("版本不匹配")); }
  }

  @Test public void loongNixAcceptsYosys038ManifestAndRejectsLegacy08() throws Exception {
    Path root = temporary.newFolder().toPath();
    FpgaToolchain tools = new FpgaToolchain(root, FpgaToolchain.Platform.LOONGNIX);
    Files.createDirectories(root.resolve("bin"));
    Files.createDirectories(root.resolve("share/yosys/xilinx"));
    Files.writeString(root.resolve("bin/yosys"), "ELF");
    Files.writeString(root.resolve("bin/yosys-abc"), "ELF");
    Files.writeString(root.resolve("share/yosys/xilinx/cells_sim.v"), "// sim");
    Files.writeString(root.resolve("runtime.properties"), "platform=loongnix20-loongarch64\n"
        + "yosys.version=0.38\nopenxc7.version=2026-10-27\ndevice=xc7a200tfbg676-1\n");
    tools.requireSynthesis();
    Files.writeString(root.resolve("runtime.properties"), "platform=loongnix20-loongarch64\n"
        + "yosys.package=0.8-1.1\nopenxc7.version=2026-10-27\ndevice=xc7a200tfbg676-1\n");
    try { tools.requireSynthesis(); fail("Legacy Yosys 0.8 manifest accepted"); }
    catch (IOException expected) { assertTrue(expected.getMessage().contains("版本不匹配")); }
  }

  @Test public void loongNixPrependsBundledSharedLibraryDirectoryToLdLibraryPath() throws Exception {
    Path root = temporary.newFolder().toPath();
    Path libDir = root.resolve("lib");
    Files.createDirectories(libDir);
    FpgaToolchain tools = new FpgaToolchain(root, FpgaToolchain.Platform.LOONGNIX);
    java.util.Map<String, String> env = new java.util.HashMap<>();
    env.put("LD_LIBRARY_PATH", "/usr/local/lib");
    tools.configureEnvironment(env);
    assertTrue(env.get("LD_LIBRARY_PATH").startsWith(libDir.toAbsolutePath().normalize().toString()));
    assertTrue(env.get("LD_LIBRARY_PATH").endsWith("/usr/local/lib"));
  }
}

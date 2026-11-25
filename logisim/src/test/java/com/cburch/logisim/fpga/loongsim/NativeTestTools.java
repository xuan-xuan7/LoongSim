package com.cburch.logisim.fpga.loongsim;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class NativeTestTools {
  private NativeTestTools() {}

  static Path simulatorBin(FpgaToolchain tools) {
    String override=System.getenv("LOONGSIM_FPGA_TEST_SIMULATOR");
    if (override != null && !override.isBlank()) {
      return WindowsNativePath.resolve(Path.of(override).toAbsolutePath().normalize()).resolve("bin");
    }
    return FpgaToolchain.isWindows() ? tools.yosys().getParent() : Path.of("/usr/bin");
  }

  static List<String> iverilogCommand(Path bin, String outputVvp, String testbench) {
    boolean windows = FpgaToolchain.isWindows();
    List<String> command = new ArrayList<>();
    command.add(bin.resolve("iverilog" + (windows ? ".exe" : "")).toString());
    if (windows) {
      command.add("-B");
      command.add(bin.resolve("../lib/ivl").normalize().toString());
    }
    command.addAll(List.of("-g2012", "-s", "tb", "-o", outputVvp, testbench));
    return command;
  }

  static List<String> vvpCommand(Path bin, String vvp) {
    return List.of(bin.resolve("vvp" + (FpgaToolchain.isWindows() ? ".exe" : "")).toString(), vvp);
  }
}

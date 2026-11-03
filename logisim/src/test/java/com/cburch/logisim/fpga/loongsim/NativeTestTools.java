package com.cburch.logisim.fpga.loongsim;

import java.nio.file.Path;

final class NativeTestTools {
  private NativeTestTools() {}
  static Path simulatorBin(FpgaToolchain tools) {
    String override=System.getenv("LOONGSIM_FPGA_TEST_SIMULATOR");
    return override==null||override.isBlank()?tools.yosys().getParent()
        :WindowsNativePath.resolve(Path.of(override).toAbsolutePath().normalize()).resolve("bin");
  }
}

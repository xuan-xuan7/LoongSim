package com.cburch.logisim.fpga.loongsim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Locates official cable firmware as data; does not launch vendor FPGA tools. */
final class XilinxCableFirmware {
  private XilinxCableFirmware() {}

  static Path find(FpgaToolchain tools, String cable) throws IOException {
    String configured = System.getProperty("loongsim.fpga.xpcuFirmware");
    if (configured == null || configured.isBlank()) configured = System.getenv("LOONGSIM_XPCU_FIRMWARE");
    if (configured != null && !configured.isBlank()) {
      Path file = Path.of(configured).toAbsolutePath().normalize();
      if (!Files.isRegularFile(file)) throw new IOException("指定的下载器初始化文件不存在: " + file);
      return WindowsNativePath.resolve(file);
    }
    List<Path> directories = new ArrayList<>();
    directories.add(tools.getRoot().resolve("loongsim/firmware"));
    String vivado = System.getenv("XILINX_VIVADO");
    if (vivado != null && !vivado.isBlank()) directories.add(Path.of(vivado).resolve("data/xicom"));
    List<Path> installations = new ArrayList<>();
    if (FpgaToolchain.isWindows()) {
      String systemDrive = System.getenv().getOrDefault("SystemDrive", "C:");
      installations.add(Path.of(systemDrive + "/Xilinx/Vivado"));
      installations.add(Path.of(systemDrive + "/AMD/Vivado"));
      for (String variable : List.of("ProgramFiles", "ProgramFiles(x86)")) {
        String location = System.getenv(variable);
        if (location != null) {
          installations.add(Path.of(location).resolve("Xilinx/Vivado"));
          installations.add(Path.of(location).resolve("AMD/Vivado"));
        }
      }
    } else {
      installations.addAll(List.of(Path.of("/tools/Xilinx/Vivado"), Path.of("/opt/Xilinx/Vivado"),
          Path.of("/opt/AMD/Vivado")));
    }
    for (Path installation : installations) {
      if (!Files.isDirectory(installation)) continue;
      try (java.util.stream.Stream<Path> versions = Files.list(installation)) {
        for (Path version : versions.filter(Files::isDirectory).sorted(Comparator.reverseOrder())
            .collect(Collectors.toList())) directories.add(version.resolve("data/xicom"));
      }
    }
    Path found = findInDirectories(cable, directories);
    return found == null ? null : WindowsNativePath.resolve(found);
  }

  static Path findInDirectories(String cable, List<Path> directories) {
    String name = cable.endsWith("_legacy") || cable.endsWith("_ready") ? "xusbdfwu.hex"
        : cable.endsWith("_alt") ? "xusb_emb.hex" : "xusb_xp2.hex";
    for (Path directory : directories) {
      Path file = directory.resolve(name);
      if (Files.isRegularFile(file)) return file;
    }
    return null;
  }
}

package com.cburch.logisim.fpga.loongsim;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Application-local tools; never select a different version from the system PATH. */
public final class FpgaToolchain {
  private final Path root;
  private final Path nativeRoot;
  private final boolean windows;

  FpgaToolchain(Path root, boolean windows) {
    this.root = root.toAbsolutePath().normalize();
    this.windows = windows;
    this.nativeRoot = windows ? WindowsNativePath.resolve(this.root) : this.root;
  }

  public static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }

  public static FpgaToolchain discover() {
    String override = System.getProperty("loongsim.fpga.runtime");
    if (override == null || override.isBlank()) override = System.getenv("LOONGSIM_FPGA_RUNTIME");
    if (override != null && !override.isBlank()) {
      return new FpgaToolchain(Paths.get(override), isWindows());
    }
    for (Path root : applicationRoots()) {
      Path candidate = root.resolve(isWindows() ? "fpga/runtime/windows-x64" : "fpga/yosys-runtime");
      if (Files.isDirectory(candidate)) return new FpgaToolchain(candidate, isWindows());
    }
    return new FpgaToolchain(Paths.get(isWindows()
        ? "fpga/runtime/windows-x64" : "fpga/yosys-runtime"), isWindows());
  }

  static List<Path> applicationRoots() {
    Set<Path> roots = new LinkedHashSet<>();
    try {
      Path location = Paths.get(FpgaToolchain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      addParents(roots, Files.isRegularFile(location) ? location.getParent() : location);
    } catch (Exception ignored) {
      // Source checkouts are also resolved from the working directory.
    }
    addParents(roots, Paths.get(System.getProperty("user.dir")));
    return new ArrayList<>(roots);
  }

  private static void addParents(Set<Path> roots, Path path) {
    for (int i = 0; i < 8 && path != null; i++, path = path.getParent()) {
      roots.add(path.toAbsolutePath().normalize());
    }
  }

  public Path getRoot() { return root; }
  public Path yosys() { return nativeRoot.resolve(windows ? "oss-cad-suite/bin/yosys.exe" : "usr/bin/yosys"); }
  public Path abc() { return nativeRoot.resolve(windows ? "oss-cad-suite/bin/yosys-abc.exe" : "usr/bin/berkeley-abc"); }
  public Path yosysData() { return nativeRoot.resolve(windows ? "oss-cad-suite/share/yosys" : "usr/share/yosys"); }
  public Path nextpnr() { return nativeRoot.resolve("openxc7/bin/nextpnr-xilinx.exe"); }
  public Path python() { return nativeRoot.resolve("oss-cad-suite/lib/python3.exe"); }
  public Path fasm2frames() { return nativeRoot.resolve("openxc7/libexec/fasm2frames"); }
  public Path frames2bit() { return nativeRoot.resolve("openxc7/bin/xc7frames2bit.exe"); }
  public Path loader() { return nativeRoot.resolve("oss-cad-suite/bin/openFPGALoader.exe"); }
  public Path xilinxLoader() { return nativeRoot.resolve("loongsim/bin/openFPGALoader-xpcu.exe"); }
  boolean hasD2xxLoader() throws IOException {
    Path buildInfo = root.resolve("loongsim/BUILD-INFO.json");
    return windows && Files.isRegularFile(buildInfo)
        && Files.readString(buildInfo).contains("\"d2xxPatchSha256\"");
  }
  public Path loaderForCable(String cable) {
    return cable.startsWith("xilinxPlatformCableUsb") ? xilinxLoader() : loader();
  }
  public Path database() { return nativeRoot.resolve("openxc7/share/nextpnr/external/prjxray-db/artix7"); }

  public void requireSynthesis() throws IOException {
    requireFile(yosys());
    requireFile(abc());
    requireFile(yosysData().resolve("xilinx/cells_sim.v"));
    if (windows) requireIdentity();
  }

  public void requireImplementation() throws IOException {
    if (!windows) throw new IOException("本次集成的布局布线与位流工具仅支持 Windows x64");
    requireIdentity();
    for (Path file : List.of(nextpnr(), python(), fasm2frames(), frames2bit(),
        database().resolve(LoongSimBoardCatalog.DEVICE + "/part.yaml"),
        root.resolve("openxc7/share/nextpnr/himbaechel/xilinx/chipdb-xc7a200t.bin"))) {
      requireFile(file);
    }
  }

  public void requireLoader() throws IOException {
    if (!windows) throw new IOException("本次集成的下载工具仅支持 Windows x64");
    requireIdentity();
    requireFile(loader());
    requireFile(xilinxLoader());
  }

  private void requireIdentity() throws IOException {
    if (!nativeRoot.toString().matches("[\\x20-\\x7e]+")) {
      throw new IOException("当前磁盘未提供可用的 Windows 短路径，开源工具不能读取此中文安装目录；请将 LoongSim 移到英文目录后重试");
    }
    Properties identity = new Properties();
    Path file = root.resolve("runtime.properties");
    requireFile(file);
    try (InputStream stream = Files.newInputStream(file)) { identity.load(stream); }
    if (!"windows-x64".equals(identity.getProperty("platform"))
        || !"2026-10-21".equals(identity.getProperty("ossCadSuite.version"))
        || !"2026-10-27".equals(identity.getProperty("openxc7.version"))
        || !LoongSimBoardCatalog.DEVICE.equals(identity.getProperty("device"))) {
      throw new IOException("FPGA 工具版本不匹配，请使用 toolchain/install-windows-tools.ps1 安装配套工具");
    }
  }

  private static void requireFile(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      throw new IOException("FPGA 工具未安装完整，缺少 " + file
          + "；请运行 toolchain/install-windows-tools.ps1");
    }
  }

  void configureEnvironment(Map<String, String> environment) {
    String path = environment.getOrDefault("PATH", "");
    String separator = java.io.File.pathSeparator;
    if (windows) {
      environment.put("PATH", nativeRoot.resolve("openxc7/bin") + separator
          + nativeRoot.resolve("oss-cad-suite/bin") + separator + nativeRoot.resolve("oss-cad-suite/lib")
          + separator + path);
      // Use the packaged pure-Python X-Ray modules, not any user's pip environment.
      environment.put("PYTHONPATH", nativeRoot.resolve("openxc7/lib/python3.12/site-packages").toString());
      environment.put("PYTHONNOUSERSITE", "1");
      environment.put("PYTHONUTF8", "1");
      environment.put("OPENFPGALOADER_SOJ_DIR", nativeRoot.resolve("oss-cad-suite/share/openFPGALoader").toString());
      environment.remove("PYTHONHOME");
    }
    environment.put("YOSYS_DATDIR", yosysData().toString());
    environment.put("ABCEXE", abc().toString());
  }

  public String describe() {
    try {
      requireSynthesis();
      if (windows) {
        requireImplementation();
        requireLoader();
        return "Windows FPGA 工具已安装（Yosys / openXC7 / openFPGALoader）";
      }
      return "LoongArch Yosys 已找到；布局布线与下载尚未接入此平台";
    } catch (IOException exception) {
      return exception.getMessage();
    }
  }
}

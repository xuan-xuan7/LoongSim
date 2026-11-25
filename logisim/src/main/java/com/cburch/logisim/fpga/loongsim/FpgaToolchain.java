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
  enum Platform {
    WINDOWS("fpga/runtime/windows-x64"),
    LOONGNIX("fpga/runtime/loongnix20-loongarch64"),
    LEGACY("fpga/yosys-runtime");

    final String directory;

    Platform(String directory) { this.directory = directory; }
  }

  private final Path root;
  private final Path nativeRoot;
  private final Platform platform;
  private final boolean windows;

  FpgaToolchain(Path root, Platform platform) {
    this.root = root.toAbsolutePath().normalize();
    this.platform = platform;
    this.windows = platform == Platform.WINDOWS;
    this.nativeRoot = windows ? WindowsNativePath.resolve(this.root) : this.root;
  }

  public static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }

  private static Platform currentPlatform() {
    if (isWindows()) return Platform.WINDOWS;
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    return os.startsWith("linux") && (arch.equals("loongarch64") || arch.equals("loong64"))
        ? Platform.LOONGNIX : Platform.LEGACY;
  }

  public static boolean supportsNativeImplementation() {
    return currentPlatform() != Platform.LEGACY;
  }

  public static FpgaToolchain discover() {
    Platform platform = currentPlatform();
    String override = System.getProperty("loongsim.fpga.runtime");
    if (override == null || override.isBlank()) override = System.getenv("LOONGSIM_FPGA_RUNTIME");
    if (override != null && !override.isBlank()) {
      Path path = Paths.get(override);
      if (platform == Platform.LOONGNIX && Files.isRegularFile(path.resolve("usr/bin/yosys"))
          && !Files.exists(path.resolve("runtime.properties"))) {
        return new FpgaToolchain(path, Platform.LEGACY);
      }
      return new FpgaToolchain(path, platform);
    }
    for (Path root : applicationRoots()) {
      Path candidate = root.resolve(platform.directory);
      if (Files.isDirectory(candidate)) return new FpgaToolchain(candidate, platform);
    }
    return new FpgaToolchain(Paths.get(platform.directory), platform);
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

  private String yosysPrefix() {
    if (windows) return "oss-cad-suite/";
    return platform == Platform.LEGACY ? "usr/" : "";
  }

  private String executable(String name) { return windows ? name + ".exe" : name; }

  public Path yosys() { return nativeRoot.resolve(yosysPrefix() + "bin/" + executable("yosys")); }
  public Path abc() {
    return nativeRoot.resolve(yosysPrefix() + "bin/"
        + (platform == Platform.LEGACY ? "berkeley-abc" : executable("yosys-abc")));
  }
  public Path yosysData() { return nativeRoot.resolve(yosysPrefix() + "share/yosys"); }
  public Path nextpnr() { return nativeRoot.resolve("openxc7/bin/" + executable("nextpnr-xilinx")); }
  public Path python() { return nativeRoot.resolve(windows ? "oss-cad-suite/lib/python3.exe" : "bin/python3"); }
  public Path fasm2frames() { return nativeRoot.resolve("openxc7/libexec/fasm2frames"); }
  public Path frames2bit() { return nativeRoot.resolve("openxc7/bin/" + executable("xc7frames2bit")); }
  public Path loader() { return nativeRoot.resolve(yosysPrefix() + "bin/" + executable("openFPGALoader")); }
  public Path xilinxLoader() {
    return windows ? nativeRoot.resolve("loongsim/bin/openFPGALoader-xpcu.exe") : loader();
  }
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
    if (platform != Platform.LEGACY) requireIdentity();
  }

  public void requireImplementation() throws IOException {
    if (platform == Platform.LEGACY) throw new IOException("请安装适用于当前系统的 FPGA 布局布线与位流工具");
    requireIdentity();
    for (Path file : List.of(nextpnr(), python(), fasm2frames(), frames2bit(),
        database().resolve(LoongSimBoardCatalog.DEVICE + "/part.yaml"),
        root.resolve("openxc7/share/nextpnr/himbaechel/xilinx/chipdb-xc7a200t.bin"))) {
      requireFile(file);
    }
  }

  public void requireLoader() throws IOException {
    if (platform == Platform.LEGACY) throw new IOException("请安装适用于当前系统的 FPGA 下载工具");
    requireIdentity();
    requireFile(loader());
    requireFile(xilinxLoader());
  }

  private void requireIdentity() throws IOException {
    if (windows && !nativeRoot.toString().matches("[\\x20-\\x7e]+")) {
      throw new IOException("当前磁盘未提供可用的 Windows 短路径，开源工具不能读取此中文安装目录；请将 LoongSim 移到英文目录后重试");
    }
    Properties identity = new Properties();
    Path file = root.resolve("runtime.properties");
    requireFile(file);
    try (InputStream stream = Files.newInputStream(file)) { identity.load(stream); }
    String expectedPlatform = windows ? "windows-x64" : "loongnix20-loongarch64";
    if (!expectedPlatform.equals(identity.getProperty("platform"))
        || (windows && !"2026-10-21".equals(identity.getProperty("ossCadSuite.version")))
        || (!windows && !"0.38".equals(identity.getProperty("yosys.version")))
        || !"2026-10-27".equals(identity.getProperty("openxc7.version"))
        || !LoongSimBoardCatalog.DEVICE.equals(identity.getProperty("device"))) {
      throw new IOException("FPGA 工具版本不匹配；请运行 " + installScript());
    }
  }

  private String installScript() {
    return windows ? "toolchain/install-windows-tools.ps1" : "toolchain/build-loongnix20-tools.sh";
  }

  private void requireFile(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      throw new IOException("FPGA 工具未安装完整，缺少 " + file
          + "；请运行 " + installScript());
    }
  }

  void configureEnvironment(Map<String, String> environment) {
    String path = environment.getOrDefault("PATH", "");
    String separator = java.io.File.pathSeparator;
    if (windows) {
      environment.put("PATH", nativeRoot.resolve("openxc7/bin") + separator
          + nativeRoot.resolve("oss-cad-suite/bin") + separator + nativeRoot.resolve("oss-cad-suite/lib")
          + separator + path);
    } else if (platform == Platform.LOONGNIX) {
      environment.put("PATH", nativeRoot.resolve("bin") + separator + path);
    }
    if (platform != Platform.LEGACY) {
      // Use the Python modules packaged with the matching X-Ray tools.
      environment.put("PYTHONPATH", nativeRoot.resolve(windows
          ? "openxc7/lib/python3.12/site-packages" : "openxc7/lib/python").toString());
      environment.put("PYTHONNOUSERSITE", "1");
      environment.put("PYTHONUTF8", "1");
      environment.put("OPENFPGALOADER_SOJ_DIR", nativeRoot.resolve(yosysPrefix() + "share/openFPGALoader").toString());
      environment.remove("PYTHONHOME");
    }
    environment.put("YOSYS_DATDIR", yosysData().toString());
    environment.put("ABCEXE", abc().toString());
  }

  public String describe() {
    try {
      requireSynthesis();
      if (platform != Platform.LEGACY) {
        requireImplementation();
        requireLoader();
        return (windows ? "Windows" : "Loongnix 20") + " FPGA 工具已安装（Yosys / openXC7 / openFPGALoader）";
      }
      return "LoongArch Yosys 已找到；布局布线与下载尚未接入此平台";
    } catch (IOException exception) {
      return exception.getMessage();
    }
  }
}

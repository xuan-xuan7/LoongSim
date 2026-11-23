package com.cburch.logisim.fpga.loongsim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.function.Consumer;

/** Native Xilinx 7-series implementation and volatile JTAG programming. */
public final class FpgaImplementation {
  public enum CableSelection {
    AUTO("auto", "自动识别"),
    XILINX("auto-xilinx", "Xilinx Platform Cable USB"),
    DIGILENT("auto-digilent", "Digilent USB/JTAG");

    private final String argument;
    private final String label;

    CableSelection(String argument, String label) {
      this.argument = argument;
      this.label = label;
    }

    public String argument() { return argument; }

    @Override public String toString() { return label; }

    boolean matchesUsbLine(String line) {
      return this == AUTO || (this == XILINX ? line.contains("03fd:")
          : line.contains("0403:6010") || line.contains("0403:6014"));
    }
  }
  private static final Pattern D2XX_CABLE = Pattern.compile(
      "^LoongSim D2XX (digilent(?:_hs[23])?) ([A-Za-z0-9]{1,15})$");

  static final class DownloadCable {
    final String name;
    final String d2xxSerial;

    DownloadCable(String name) { this(name, null); }

    DownloadCable(String name, String d2xxSerial) {
      this.name = name;
      this.d2xxSerial = d2xxSerial;
    }
  }

  public void buildBitstream(Path project, String top, Consumer<String> sink)
      throws IOException, InterruptedException {
    project = project.toAbsolutePath().normalize();
    FpgaToolchain tools = FpgaToolchain.discover();
    tools.requireImplementation();
    LoongSimFpgaFlow.validateProject(project, top);
    FpgaBuildArtifacts.verifySynthesis(project);
    FpgaBuildArtifacts.clearImplementation(project);
    writeImplementationXdc(project, sink);
    log(sink, "开始布局布线，目标器件: " + LoongSimBoardCatalog.DEVICE);
    FpgaToolProcess.run(tools, project, List.of(tools.nextpnr().toString(),
        "--device", LoongSimBoardCatalog.DEVICE, "--json", "yosys-netlist.json",
        "-o", "xdc=implementation.xdc", "-o", "fasm=design.fasm", "--freq", "100", "--write", "routed.json",
        "--report", "timing.json"), "place-route.log", sink);
    FpgaBuildArtifacts.requireOutput(project.resolve("design.fasm"));
    FpgaBuildArtifacts.requireOutput(project.resolve("timing.json"));
    log(sink, "布局布线完成，开始生成位流");
    FpgaToolProcess.run(tools, project, List.of(tools.python().toString(), tools.fasm2frames().toString(),
        "--part", LoongSimBoardCatalog.DEVICE, "--db-root", tools.database().toString(),
        "design.fasm", "design.frames"), "frames.log", sink);
    FpgaBuildArtifacts.requireOutput(project.resolve("design.frames"));
    FpgaToolProcess.run(tools, project, List.of(tools.frames2bit().toString(),
        "--part_file", tools.database().resolve(LoongSimBoardCatalog.DEVICE + "/part.yaml").toString(),
        "--part_name", LoongSimBoardCatalog.DEVICE, "--frm_file", "design.frames",
        "--output_file", "design.bit"), "bitstream.log", sink);
    FpgaBuildArtifacts.recordImplementation(project);
    log(sink, "位流生成完成: " + project.resolve("design.bit"));
  }

  public void detect(Path logDirectory, String cable, Consumer<String> sink)
      throws IOException, InterruptedException {
    FpgaToolchain tools = FpgaToolchain.discover();
    tools.requireLoader();
    Files.createDirectories(logDirectory);
    DownloadCable resolved = resolveCable(tools, logDirectory, cable, sink);
    List<String> detection = loaderCommand(tools, resolved, sink);
    detection.add("--detect");
    FpgaToolProcess.run(tools, logDirectory, detection, "device-detect.log", sink);
    verifyDetectedDevice(logDirectory);
  }

  public void program(Path project, String cable, Consumer<String> sink)
      throws IOException, InterruptedException {
    program(project, cable, sink, () -> true);
  }

  public void program(Path project, String cable, Consumer<String> sink,
      java.util.function.BooleanSupplier designCurrent) throws IOException, InterruptedException {
    project = project.toAbsolutePath().normalize();
    FpgaToolchain tools = FpgaToolchain.discover();
    tools.requireLoader();
    String top = Files.readString(project.resolve("top-module.txt"), StandardCharsets.UTF_8).trim();
    LoongSimFpgaFlow.validateProject(project, top);
    FpgaBuildArtifacts.verifyImplementation(project);
    if (!designCurrent.getAsBoolean()) throw new IOException("电路已修改，请重新生成位流");
    DownloadCable resolved = resolveCable(tools, project, cable, sink);
    List<String> detection = loaderCommand(tools, resolved, sink);
    detection.add("--detect");
    FpgaToolProcess.run(tools, project, detection, "device-detect.log", sink);
    verifyDetectedDevice(project);
    FpgaBuildArtifacts.verifyImplementation(project);
    if (!designCurrent.getAsBoolean()) throw new IOException("电路已修改，已停止下载，请重新生成位流");
    // No --write-flash: default FPGA SRAM programming is lost on power-off.
    List<String> download = loaderCommand(tools, resolved, sink);
    download.add("design.bit");
    FpgaToolProcess.run(tools, project, download, "program.log", sink);
    if (!configurationSucceeded(Files.readString(project.resolve("logs/program.log"), StandardCharsets.UTF_8))) {
      throw new IOException("下载工具未确认 FPGA 的 INIT/DONE 成功状态，请检查 logs/program.log 后重试");
    }
    log(sink, "下载完成（临时运行，断电后清除）");
  }

  private static void validateCable(String cable) throws IOException {
    if (cable == null || !cable.matches("[A-Za-z][A-Za-z0-9_-]*")) {
      throw new IOException("USB/JTAG 下载器名称无效");
    }
  }

  private static List<String> loaderCommand(FpgaToolchain tools, DownloadCable device, Consumer<String> sink)
      throws IOException {
    String cable = device.name;
    Path loader = device.d2xxSerial == null ? tools.loaderForCable(cable) : tools.xilinxLoader();
    List<String> command = new ArrayList<>(List.of(loader.toString(), "--cable", cable));
    if (device.d2xxSerial != null) {
      command.add("--usb-serial-num");
      command.add(device.d2xxSerial);
    }
    if (cable.startsWith("xilinxPlatformCableUsb")) {
      Path firmware = XilinxCableFirmware.find(tools, cable);
      if (firmware != null) {
        command.add("--probe-firmware");
        command.add(firmware.toString());
        log(sink, "下载器初始化固件: " + firmware);
      } else {
        log(sink, "未找到配套下载器初始化文件；若设备无法初始化，请检查 A7 工具组件是否安装完整。");
      }
    }
    return command;
  }

  private static DownloadCable resolveCable(FpgaToolchain tools, Path directory, String cable, Consumer<String> sink)
      throws IOException, InterruptedException {
    CableSelection selection = java.util.Arrays.stream(CableSelection.values())
        .filter(choice -> choice.argument().equals(cable)).findFirst().orElse(null);
    if (selection == null) {
      validateCable(cable);
      return new DownloadCable(cable);
    }
    FpgaToolProcess.run(tools, directory, List.of(tools.xilinxLoader().toString(), "--scan-usb"), "usb-scan.log", null);
    String scan = Files.readString(directory.resolve("logs/usb-scan.log"), StandardCharsets.UTF_8);
    String d2xx = "";
    if (selection != CableSelection.XILINX && tools.hasD2xxLoader()) {
      FpgaToolProcess.run(tools, directory, List.of(tools.xilinxLoader().toString(), "--scan-d2xx"),
          "d2xx-scan.log", null);
      d2xx = Files.readString(directory.resolve("logs/d2xx-scan.log"), StandardCharsets.UTF_8);
    }
    DownloadCable resolved = cableFromScans(scan, d2xx, selection);
    String family = resolved.name.startsWith("xilinxPlatformCableUsb")
        ? "Xilinx Platform Cable USB" : "Digilent USB/JTAG";
    log(sink, "已识别 USB/JTAG 下载器：" + family
        + (resolved.d2xxSerial == null ? "" : "（使用 Windows FTDI 驱动）"));
    return resolved;
  }

  static String cableFromScan(String scan) throws IOException {
    return cableFromScan(scan, CableSelection.AUTO);
  }

  static String cableFromScan(String scan, CableSelection selection) throws IOException {
    return cableFromScans(scan, "", selection).name;
  }

  static DownloadCable cableFromScans(String scan, String d2xx, CableSelection selection) throws IOException {
    List<DownloadCable> candidates = new ArrayList<>();
    if (selection != CableSelection.XILINX) {
      for (String line : d2xx.split("\\R")) {
        var cable = D2XX_CABLE.matcher(line.strip());
        if (cable.matches()) candidates.add(new DownloadCable(cable.group(1), cable.group(2)));
      }
    }
    boolean driverIncompatible = false;
    for (String line : scan.split("\\R")) {
      String lower = line.toLowerCase(java.util.Locale.ROOT).replace("0x", "");
      if (!selection.matchesUsbLine(lower)) continue;
      boolean sameD2xxDevice = candidates.stream().anyMatch(cable -> cable.d2xxSerial != null
          && lower.contains(cable.d2xxSerial.toLowerCase(java.util.Locale.ROOT)));
      if (sameD2xxDevice) continue;
      if (lower.contains("can't open device")) {
        if ((lower.contains("0403:6014") || lower.contains("0403:6010") || lower.contains("03fd:"))
            && lower.contains("error code -12")) {
          driverIncompatible = true;
        }
        continue;
      }
      if (lower.contains("03fd:0013")) candidates.add(new DownloadCable("xilinxPlatformCableUsb"));
      else if (lower.contains("03fd:000d")) candidates.add(new DownloadCable("xilinxPlatformCableUsb_alt"));
      else if (lower.contains("03fd:0007")) candidates.add(new DownloadCable("xilinxPlatformCableUsb_legacy"));
      else if (lower.contains("03fd:0008")) candidates.add(new DownloadCable("xilinxPlatformCableUsb_ready"));
      else if (lower.contains("0403:6010") && lower.contains("digilent")) candidates.add(new DownloadCable("digilent"));
      else if (lower.contains("0403:6014") && lower.contains("digilent")) {
        if (lower.matches(".*\\b(?:jtag[- ]?)?hs3\\b.*")) candidates.add(new DownloadCable("digilent_hs3"));
        else if (lower.matches(".*\\b(?:jtag[- ]?)?hs2\\b.*")) candidates.add(new DownloadCable("digilent_hs2"));
        else if (lower.matches(".*\\b(?:jtag[- ]?)?smt2(?:-nc)?\\b.*")
            || lower.matches(".*\\b210251[0-9a-z]+\\b.*")) {
          // SMT2 and HS2 use the same openFPGALoader MPSSE pin configuration.
          candidates.add(new DownloadCable("digilent_hs2"));
        }
      }
    }
    if (candidates.isEmpty() && driverIncompatible) {
      throw new IOException("系统已识别下载器，但 openFPGALoader 无法打开当前 USB 驱动（错误 -12）。"
          + "请重新插拔下载器。若仍无法打开，请检查 LoongSim 安装时是否已完成下载器驱动安装。");
    }
    if (candidates.size() != 1) {
      throw new IOException(candidates.isEmpty()
          ? (selection == CableSelection.AUTO ? "未识别到实验箱下载器"
              : "未识别到所选下载器：" + selection) + "。请检查 USB 连接、实验箱供电和驱动。"
          : "检测到多个实验箱下载器。请只连接本次实验使用的下载器。");
    }
    return candidates.get(0);
  }

  private static void verifyDetectedDevice(Path directory) throws IOException {
    String detected = Files.readString(directory.resolve("logs/device-detect.log"), StandardCharsets.UTF_8);
    if (!isA7Device(detected)) {
      throw new IOException("JTAG 未检测到目标芯片 XC7A200T，请检查实验箱型号、供电和 JTAG 接口");
    }
    long devices = java.util.regex.Pattern.compile("(?i)\\bidcode\\b").matcher(detected).results().count();
    if (devices > 1) throw new IOException("检测到多器件 JTAG 链，本版仅支持自动下载到单器件链");
  }

  static boolean isA7Device(String detected) {
    return java.util.regex.Pattern.compile("(?i)\\bidcode\\s+(?:0x)?[0-9a-f]?3636093\\b")
        .matcher(detected).find();
  }

  static boolean configurationSucceeded(String report) {
    // openFPGALoader can exit zero even when DONE is low: require its final IR status.
    return java.util.regex.Pattern.compile("(?im)^ir:\\s*[0-9a-f]+\\s+isc_done\\s+1\\s+isc_ena\\s+0\\s+init\\s+1\\s+done\\s+1\\s*$")
        .matcher(report).find();
  }

  static void writeImplementationXdc(Path project, Consumer<String> sink) throws IOException {
    java.util.ArrayList<String> lines = new java.util.ArrayList<>();
    for (String line : Files.readAllLines(project.resolve("constraints.xdc"), StandardCharsets.UTF_8)) {
      if (line.trim().matches("set_property\\s+CLOCK_DEDICATED_ROUTE\\s+BACKBONE\\s+\\[get_nets\\s+[^\\]]+\\]")) {
        lines.add("# Vivado CLOCK_DEDICATED_ROUTE BACKBONE is not an openXC7 XDC property.");
        log(sink, "时钟由布局布线引擎分配专用网络；保留 100 MHz 时钟约束。");
      } else {
        lines.add(line);
      }
    }
    Files.write(project.resolve("implementation.xdc"), lines, StandardCharsets.UTF_8);
  }

  private static void log(Consumer<String> sink, String message) {
    if (sink != null) sink.accept(message);
  }
}

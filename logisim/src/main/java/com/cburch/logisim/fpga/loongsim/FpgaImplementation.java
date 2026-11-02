package com.cburch.logisim.fpga.loongsim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Native Windows Xilinx 7-series implementation and volatile JTAG programming. */
public final class FpgaImplementation {
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
    cable = resolveCable(tools, logDirectory, cable, sink);
    List<String> detection = loaderCommand(tools, cable, sink);
    detection.add("--detect");
    FpgaToolProcess.run(tools, logDirectory, detection, "device-detect.log", sink);
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
    cable = resolveCable(tools, project, cable, sink);
    String top = Files.readString(project.resolve("top-module.txt"), StandardCharsets.UTF_8).trim();
    LoongSimFpgaFlow.validateProject(project, top);
    FpgaBuildArtifacts.verifyImplementation(project);
    if (!designCurrent.getAsBoolean()) throw new IOException("电路已修改，请重新生成位流");
    List<String> detection = loaderCommand(tools, cable, sink);
    detection.add("--detect");
    FpgaToolProcess.run(tools, project, detection, "device-detect.log", sink);
    String detected = Files.readString(project.resolve("logs/device-detect.log"), StandardCharsets.UTF_8);
    if (!isA7Device(detected)) {
      throw new IOException("未检测到 XC7A200T，已停止下载，请检查实验箱、下载接口及供电");
    }
    long devices = java.util.regex.Pattern.compile("(?i)\\bidcode\\b").matcher(detected).results().count();
    if (devices > 1) throw new IOException("检测到多器件 JTAG 链，请先确认目标器件位置；本版仅自动下载到单器件链");
    FpgaBuildArtifacts.verifyImplementation(project);
    if (!designCurrent.getAsBoolean()) throw new IOException("电路已修改，已停止下载，请重新生成位流");
    // No --write-flash: default FPGA SRAM programming is lost on power-off.
    List<String> download = loaderCommand(tools, cable, sink);
    download.add("design.bit");
    FpgaToolProcess.run(tools, project, download, "program.log", sink);
    if (!configurationSucceeded(Files.readString(project.resolve("logs/program.log"), StandardCharsets.UTF_8))) {
      throw new IOException("下载工具未确认 FPGA 的 INIT/DONE 成功状态，请检查 logs/program.log 后重试");
    }
    log(sink, "下载完成（临时运行，断电后清除）");
  }

  private static void validateCable(String cable) throws IOException {
    if (cable == null || !cable.matches("[A-Za-z][A-Za-z0-9_-]*")) {
      throw new IOException("请先选择或填写已确认的 USB/JTAG 下载器名称");
    }
  }

  private static List<String> loaderCommand(FpgaToolchain tools, String cable, Consumer<String> sink)
      throws IOException {
    List<String> command = new java.util.ArrayList<>(List.of(tools.loaderForCable(cable).toString(), "--cable", cable));
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

  private static String resolveCable(FpgaToolchain tools, Path directory, String cable, Consumer<String> sink)
      throws IOException, InterruptedException {
    if (!"auto".equals(cable)) { validateCable(cable); return cable; }
    FpgaToolProcess.run(tools, directory, List.of(tools.xilinxLoader().toString(), "--scan-usb"), "usb-scan.log", sink);
    String resolved = cableFromScan(Files.readString(directory.resolve("logs/usb-scan.log"), StandardCharsets.UTF_8));
    log(sink, "已识别 USB/JTAG 下载器: " + resolved);
    return resolved;
  }

  static String cableFromScan(String scan) throws IOException {
    java.util.List<String> candidates = new java.util.ArrayList<>();
    for (String line : scan.split("\\R")) {
      String lower = line.toLowerCase(java.util.Locale.ROOT).replace("0x", "");
      if (lower.contains("03fd:0013")) candidates.add("xilinxPlatformCableUsb");
      else if (lower.contains("03fd:000d")) candidates.add("xilinxPlatformCableUsb_alt");
      else if (lower.contains("03fd:0007")) candidates.add("xilinxPlatformCableUsb_legacy");
      else if (lower.contains("03fd:0008")) candidates.add("xilinxPlatformCableUsb_ready");
      else if (lower.contains("0403:6010") && lower.contains("digilent")) candidates.add("digilent");
    }
    if (candidates.size() != 1) {
      throw new IOException(candidates.isEmpty()
          ? "未识别到实验箱下载器，请检查 USB 连接、实验箱供电和驱动"
          : "检测到多个下载器，请仅连接当前实验箱的下载器后重试");
    }
    return candidates.get(0);
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

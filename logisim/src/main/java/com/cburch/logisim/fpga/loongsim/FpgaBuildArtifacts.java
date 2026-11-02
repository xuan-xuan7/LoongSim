package com.cburch.logisim.fpga.loongsim;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Keep successful outputs tied to the exact exported sources and constraints. */
final class FpgaBuildArtifacts {
  private FpgaBuildArtifacts() {}

  static void clearImplementation(Path directory) throws IOException {
    for (String name : List.of("implementation.properties", "design.bit", "design.frames", "design.fasm",
        "routed.json", "timing.json", "implementation.xdc")) Files.deleteIfExists(directory.resolve(name));
  }

  static void clearSynthesis(Path directory) throws IOException {
    clearImplementation(directory);
    Files.deleteIfExists(directory.resolve("synthesis.properties"));
    Files.deleteIfExists(directory.resolve("yosys-netlist.json"));
  }

  static void recordSynthesis(Path directory) throws IOException {
    record(directory, "synthesis.properties", "yosys-netlist.json");
  }

  static void recordImplementation(Path directory) throws IOException {
    verifySynthesis(directory);
    record(directory, "implementation.properties", "design.bit");
  }

  static void verifySynthesis(Path directory) throws IOException {
    verify(directory, "synthesis.properties", "yosys-netlist.json");
  }

  static void verifyImplementation(Path directory) throws IOException {
    verifySynthesis(directory);
    verify(directory, "implementation.properties", "design.bit");
  }

  private static void record(Path directory, String stamp, String artifact) throws IOException {
    Path output = directory.resolve(artifact);
    requireOutput(output);
    Properties state = new Properties();
    state.setProperty("device", LoongSimBoardCatalog.DEVICE);
    state.setProperty("inputs", inputDigest(directory));
    state.setProperty("output", digest(Files.readAllBytes(output)));
    try (OutputStream stream = Files.newOutputStream(directory.resolve(stamp))) {
      state.store(stream, "LoongSim successful FPGA stage");
    }
  }

  private static void verify(Path directory, String stamp, String artifact) throws IOException {
    requireOutput(directory.resolve(stamp));
    requireOutput(directory.resolve(artifact));
    Properties state = new Properties();
    try (InputStream stream = Files.newInputStream(directory.resolve(stamp))) { state.load(stream); }
    if (!LoongSimBoardCatalog.DEVICE.equals(state.getProperty("device"))
        || !inputDigest(directory).equals(state.getProperty("inputs"))
        || !digest(Files.readAllBytes(directory.resolve(artifact))).equals(state.getProperty("output"))) {
      throw new IOException("源文件、约束或生成结果已改变，请重新综合并生成位流");
    }
  }

  static String inputDigest(Path directory) throws IOException {
    MessageDigest digest = sha256();
    List<Path> inputs = new ArrayList<>();
    for (String name : List.of("top-module.txt", "sources.list", "constraints.xdc")) inputs.add(directory.resolve(name));
    inputs.addAll(LoongSimFpgaFlow.readSourceList(directory));
    for (Path input : inputs) {
      requireOutput(input);
      digest.update(directory.relativize(input).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(Files.readAllBytes(input));
      digest.update((byte) 0);
    }
    return hex(digest.digest());
  }

  static void requireOutput(Path path) throws IOException {
    if (!Files.isRegularFile(path) || Files.size(path) == 0) throw new IOException("缺少有效的生成结果: " + path);
  }

  private static MessageDigest sha256() {
    try { return MessageDigest.getInstance("SHA-256"); }
    catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
  }

  private static String digest(byte[] bytes) { return hex(sha256().digest(bytes)); }
  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
    return result.toString();
  }
}

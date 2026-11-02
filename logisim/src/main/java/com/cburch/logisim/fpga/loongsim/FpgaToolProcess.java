package com.cburch.logisim.fpga.loongsim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Runs native tools without a command shell and records the output in the project. */
final class FpgaToolProcess {
  private FpgaToolProcess() {}

  static void run(FpgaToolchain tools, Path directory, List<String> arguments,
      String logName, Consumer<String> sink) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(arguments).directory(directory.toFile());
    tools.configureEnvironment(builder.environment());
    builder.redirectErrorStream(true);
    Path logDirectory = directory.resolve("logs");
    Files.createDirectories(logDirectory);
    Process process = builder.start();
    try (java.io.BufferedWriter log = Files.newBufferedWriter(logDirectory.resolve(logName), StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        log.write(line);
        log.newLine();
        if (sink != null) sink.accept(line);
      }
      int code = process.waitFor();
      if (code != 0) throw new IOException("工具执行失败（退出码 " + code + "），日志: " + logDirectory.resolve(logName));
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }
}

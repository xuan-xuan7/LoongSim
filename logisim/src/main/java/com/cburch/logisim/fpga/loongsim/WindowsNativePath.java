package com.cburch.logisim.fpga.loongsim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Adapt upstream ANSI-only tools to installed paths containing Unicode or spaces. */
final class WindowsNativePath {
  private static final Map<Path, Path> CACHE = new ConcurrentHashMap<>();
  private WindowsNativePath() {}

  static Path resolve(Path original) {
    if (!FpgaToolchain.isWindows() || !Files.isDirectory(original)
        || original.toString().matches("[\\x20-\\x7e&&[^ ]]+")) return original;
    return CACHE.computeIfAbsent(original, WindowsNativePath::query);
  }

  private static Path query(Path original) {
    // Fixed code only. The path travels in the child environment, never in shell code.
    String declaration = "using System.Text; using System.Runtime.InteropServices; "
        + "public static class LoongSimNativePath { "
        + "[DllImport(\"kernel32.dll\", CharSet=CharSet.Unicode, SetLastError=true)] "
        + "public static extern uint GetShortPathName(string input, StringBuilder output, uint capacity); }";
    String script = "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; "
        + "Add-Type -TypeDefinition '" + declaration + "'; "
        + "$b=[Text.StringBuilder]::new(32768); "
        + "$n=[LoongSimNativePath]::GetShortPathName($env:LOONGSIM_NATIVE_RUNTIME_PATH,$b,32768); "
        + "if($n -gt 0 -and $n -lt 32768){[Console]::Write('__LOONGSIM_SHORT_PATH__'"
        + "+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($b.ToString())))}";
    Path powershell = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"),
        "System32/WindowsPowerShell/v1.0/powershell.exe");
    Process process = null;
    try {
      ProcessBuilder builder = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive",
          "-WindowStyle", "Hidden", "-EncodedCommand",
          Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)));
      builder.environment().put("LOONGSIM_NATIVE_RUNTIME_PATH", original.toString());
      builder.redirectErrorStream(true);
      process = builder.start();
      if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) return original;
      String response = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      java.util.regex.Matcher match = java.util.regex.Pattern.compile("__LOONGSIM_SHORT_PATH__([A-Za-z0-9+/=]+)").matcher(response);
      if (!match.find()) return original;
      String output = new String(Base64.getDecoder().decode(match.group(1)), StandardCharsets.UTF_8);
      if (output.matches("[\\x20-\\x7e]+") && Files.isDirectory(Path.of(output))) return Path.of(output);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } catch (Exception ignored) {
      // The caller gives an actionable error if no usable native path is available.
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
    return original;
  }
}

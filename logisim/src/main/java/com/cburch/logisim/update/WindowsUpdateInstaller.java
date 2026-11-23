package com.cburch.logisim.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;

/** Hands the verified Windows installer to a process outside LoongSim's bundled runtime. */
public final class WindowsUpdateInstaller {
  private static final String SCRIPT = String.join("\n",
      "$ErrorActionPreference = 'Stop'",
      "try {",
      "  $parent = Get-Process -Id ([int]$env:LOONGSIM_UPDATE_PARENT_PID) -ErrorAction SilentlyContinue",
      "  if ($parent) { Wait-Process -InputObject $parent -Timeout 60 }",
      "  $setup = Start-Process -FilePath $env:LOONGSIM_UPDATE_INSTALLER "
          + "-ArgumentList '/passive','/norestart' -PassThru -Wait",
      "  if ($setup.ExitCode -notin 0, 1641, 3010) { throw 'LoongSim setup failed' }",
      "  Remove-Item -LiteralPath $env:LOONGSIM_UPDATE_INSTALLER -ErrorAction SilentlyContinue",
      "  try { [IO.Directory]::Delete([IO.Path]::GetDirectoryName($env:LOONGSIM_UPDATE_INSTALLER)) } catch {}",
      "} catch {",
      "  Add-Type -AssemblyName System.Windows.Forms",
      "  [void][System.Windows.Forms.MessageBox]::Show("
          + "$env:LOONGSIM_UPDATE_ERROR, $env:LOONGSIM_UPDATE_TITLE)",
      "  exit 1",
      "}");

  private WindowsUpdateInstaller() {}

  public static void launchAfterExit(Path installer, String title, String failureText)
      throws IOException {
    String systemRoot = System.getenv("SystemRoot");
    if (systemRoot == null || systemRoot.isBlank()) {
      throw new IOException("Windows system directory is unavailable");
    }
    Path shell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
    launchAfterProcessExit(shell, installer, ProcessHandle.current().pid(), title, failureText);
  }

  static Process launchAfterProcessExit(Path shell, Path installer, long pid,
      String title, String failureText) throws IOException {
    Path verifiedInstaller = installer.toAbsolutePath().normalize();
    if (!Files.isRegularFile(verifiedInstaller, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Verified LoongSim installer is unavailable");
    }

    // The installer path is passed separately from the static PowerShell program.
    String command = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
    ProcessBuilder helper = new ProcessBuilder(shell.toString(), "-NoLogo", "-NoProfile",
        "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", command);
    helper.directory(shell.toAbsolutePath().getParent().toFile());
    helper.environment().put("LOONGSIM_UPDATE_PARENT_PID", Long.toString(pid));
    helper.environment().put("LOONGSIM_UPDATE_INSTALLER", verifiedInstaller.toString());
    helper.environment().put("LOONGSIM_UPDATE_TITLE", title);
    helper.environment().put("LOONGSIM_UPDATE_ERROR", failureText);
    return helper.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
  }
}

package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class XilinxCableFirmwareTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void selectsFirmwareForTheDetectedCableRatherThanAnUnrelatedHex() throws Exception {
    Path directory = temporary.newFolder().toPath();
    for (String name : List.of("xusbdfwu.hex", "xusb_xp2.hex", "xusb_emb.hex")) {
      Files.writeString(directory.resolve(name), "firmware fixture");
    }
    assertEquals(directory.resolve("xusbdfwu.hex"), XilinxCableFirmware.findInDirectories(
        "xilinxPlatformCableUsb_legacy", List.of(directory)));
    assertEquals(directory.resolve("xusb_xp2.hex"), XilinxCableFirmware.findInDirectories(
        "xilinxPlatformCableUsb", List.of(directory)));
    assertEquals(directory.resolve("xusb_emb.hex"), XilinxCableFirmware.findInDirectories(
        "xilinxPlatformCableUsb_alt", List.of(directory)));
    assertNull(XilinxCableFirmware.findInDirectories("xilinxPlatformCableUsb", List.of(directory.resolve("missing"))));
  }
}

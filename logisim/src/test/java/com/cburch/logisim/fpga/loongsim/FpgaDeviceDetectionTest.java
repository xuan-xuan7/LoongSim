package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import org.junit.Test;

public class FpgaDeviceDetectionTest {
  @Test public void recognizesXilinxAndNamedDigilentCables() throws Exception {
    assertEquals("xilinxPlatformCableUsb", FpgaImplementation.cableFromScan("001 002 03fd:0013 xilinx"));
    assertEquals("xilinxPlatformCableUsb_alt", FpgaImplementation.cableFromScan("001 002 03FD:000D xilinx"));
    assertEquals("digilent", FpgaImplementation.cableFromScan("001 002 0403:6010 FT2232 Digilent"));
    assertEquals("xilinxPlatformCableUsb_legacy", FpgaImplementation.cableFromScan(
        "005 001 0x03fd:0x0007 xilinxPlatformCableUsb_legacy none none none"));
    assertEquals("xilinxPlatformCableUsb_ready", FpgaImplementation.cableFromScan("005 001 0x03fd:0x0008 cable"));
  }

  @Test public void acceptsActualA7IdcodeWithOrWithoutLeadingRevisionZero() {
    assertTrue(FpgaImplementation.isA7Device("index 0:\n idcode 0x3636093\n model xc7a200"));
    assertTrue(FpgaImplementation.isA7Device("idcode 0x13636093"));
    assertTrue(!FpgaImplementation.isA7Device("idcode 0x0362d093 model xc7a35t"));
    assertTrue(!FpgaImplementation.isA7Device("log path xc7a200t"));
  }

  @Test public void downloadSuccessRequiresConfirmedFpgaConfigurationStatus() {
    String success = "Load SRAM: 100.00%\nDone\nShift IR 75\nir: 1 isc_done 1 isc_ena 0 init 1 done 1\n";
    assertTrue(FpgaImplementation.configurationSucceeded(success));
    assertTrue(!FpgaImplementation.configurationSucceeded(success.replace("init 1 done 1", "init 0 done 0")));
    assertTrue(!FpgaImplementation.configurationSucceeded("Load SRAM: 100.00%\nDone\n"));
  }

  @Test public void doesNotGuessGenericFtdiWiringOrChooseOneOfTwoCables() throws Exception {
    for (String scan : new String[]{"001 002 0403:6010 FT2232", "", "03fd:0013\n03fd:000d"}) {
      try { FpgaImplementation.cableFromScan(scan); fail("Ambiguous scan accepted"); }
      catch (IOException expected) { assertTrue(expected.getMessage().length() > 0); }
    }
  }
}

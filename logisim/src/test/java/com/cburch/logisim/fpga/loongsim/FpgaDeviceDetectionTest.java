package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cburch.logisim.fpga.loongsim.FpgaImplementation.CableSelection;
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

  @Test public void recognizesKnownDigilentFt232hWiring() throws Exception {
    assertEquals("digilent_hs2", FpgaImplementation.cableFromScan(
        "001 002 0x0403:0x6014 ft232H Digilent 210251A08870 Digilent USB Device"));
    assertEquals("digilent_hs3", FpgaImplementation.cableFromScan(
        "001 002 0x0403:0x6014 ft232H Digilent 210299A00000 Digilent JTAG-HS3"));
    try {
      FpgaImplementation.cableFromScan("001 002 0403:6014 ft232H Digilent UNKNOWN Digilent USB Device");
      fail("Unknown FT232H pin layout was guessed");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("未识别"));
    }
  }

  @Test public void distinguishesUnsupportedUsbDriverFromAnUnpluggedCable() throws Exception {
    try {
      FpgaImplementation.cableFromScan(
          "Error: can't open device with vid:vid = 0x0403:0x6014. Error code -12 Operation not supported");
      fail("USB open failure was accepted");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("驱动"));
    }
  }

  @Test public void distinguishesLinuxUsbPermissionErrorFromAnUnpluggedCable() throws Exception {
    try {
      FpgaImplementation.cableFromScan(
          "Error: can't open device with vid:vid = 0x0403:0x6014. Error code -3 Access denied (insufficient permissions)");
      fail("USB permission failure was accepted");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("权限"));
    }
  }

  @Test public void usesWindowsFtdiDriverWhenLibusbCannotOpenDigilent() throws Exception {
    String usb = "Error: can't open device with vid:vid = 0x0403:0x6014. Error code -12";
    String d2xx = "LoongSim D2XX digilent_hs2 210251A08870";
    var cable = FpgaImplementation.cableFromScans(usb, d2xx, CableSelection.AUTO);
    assertEquals("digilent_hs2", cable.name);
    assertEquals("210251A08870", cable.d2xxSerial);
  }

  @Test public void doesNotChooseBetweenTwoDetectedDownloadCables() throws Exception {
    String d2xx = "LoongSim D2XX digilent_hs2 210251A08870";
    try {
      FpgaImplementation.cableFromScans("005 001 03fd:0007 xilinx", d2xx, CableSelection.AUTO);
      fail("Two connected download cables were accepted");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("多个"));
    }
  }

  @Test public void selectedFamilyChoosesBetweenXilinxAndDigilent() throws Exception {
    String xilinx = "005 001 0x03fd:0x0007 xilinxPlatformCableUsb_legacy none none none";
    String digilent = "001 002 0x0403:0x6014 ft232H Digilent 210251A08870 Digilent USB Device";
    String both = xilinx + "\n" + digilent;
    assertEquals("xilinxPlatformCableUsb_legacy", FpgaImplementation.cableFromScan(both, CableSelection.XILINX));
    assertEquals("digilent_hs2", FpgaImplementation.cableFromScan(both, CableSelection.DIGILENT));
    try {
      FpgaImplementation.cableFromScan(digilent, CableSelection.XILINX);
      fail("Selected Xilinx family silently used a Digilent cable");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("所选下载器"));
    }
  }

  @Test public void reportsUnavailableDriverForTheSelectedFamily() throws Exception {
    String scan = "005 001 0x03fd:0x0007 xilinx\n"
        + "Error: can't open device with vid:vid = 0x0403:0x6014. Error code -12 Operation not supported";
    assertEquals("xilinxPlatformCableUsb_legacy", FpgaImplementation.cableFromScan(scan, CableSelection.XILINX));
    try {
      FpgaImplementation.cableFromScan(scan, CableSelection.DIGILENT);
      fail("Selected Digilent family silently used a Xilinx cable");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("驱动"));
    }
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

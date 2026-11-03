package com.cburch.logisim.fpga.loongsim;

import static org.junit.Assume.assumeTrue;
import java.nio.file.Path;
import org.junit.Test;

/** Scanned physical I/O is tested with electrical models and complete native bitstream builds. */
public class MappedIoWorkflowIntegrationTest {
  private Path directory(String name) {
    assumeTrue(FpgaToolchain.isWindows()&&"1".equals(System.getenv("LOONGSIM_RUN_FPGA_INTEGRATION")));
    return Path.of(System.getenv().getOrDefault("LOONGSIM_FPGA_SMOKE_DIRECTORY","../local/windows-fpga-smoke"))
        .toAbsolutePath().resolve(name);
  }
  @Test public void matrixKeypadAndDisplayMatchPhysicalContactModelThenBuild() throws Exception {
    KeypadHardwareFixture.main(new String[]{directory("矩阵键盘与显示联动").toString()});
  }
  @Test public void everySegmentPatternHexDigitAndPartiallyMappedDigitBuilds() throws Exception {
    SegmentHardwareFixture.main(new String[]{directory("逐段映射与十六进制").toString()});
  }
}

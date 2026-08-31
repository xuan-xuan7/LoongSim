LoongSim 1.0.0 - Logisim-only release
=====================================

This package contains the Java Logisim circuit editor and simulator only.

Available features:
- Create and edit digital circuits.
- Simulate circuits.
- Open and save .circ projects.
- Use analyzers, probes, clocks, memories, and other Logisim components.
- Export HDL/Verilog where supported by the circuit.

Not included in this release:
- Yosys synthesis.
- nextpnr-xilinx place and route.
- Project X-Ray bitstream generation.
- openFPGALoader FPGA programming.
- Any FPGA runtime binaries or chip databases.

Requirements
------------

Java 11 or newer is required. Java is not bundled in this package.
The same JAR can run on x86_64 Linux, Windows x86_64, and other platforms
with a compatible Java runtime.

Linux:
  ./run-loongsim.sh

Windows:
  Double-click LoongSim.bat, or run it from a Command Prompt.

The FPGA workbench may be visible in this development build, but the FPGA
backend is intentionally not part of this release and cannot run from it.

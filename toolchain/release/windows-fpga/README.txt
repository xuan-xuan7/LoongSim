LoongSim 1.2.0 - Windows x64 full edition (master)
================================================

Install using LoongSim-1.2.0-master-windows-x64-setup-unsigned.exe.
The installer includes Java and the FPGA tools. Start LoongSim from the desktop
shortcut or Start menu after installation. No separate Java or Python installation
is needed. Close an older LoongSim before upgrading; keep your own .circ files.

To upgrade from 1.1.0, open Help > Check for updates and download the installer.
Save your circuit files and close LoongSim before running the downloaded EXE.

The FPGA flow supports edge-triggered RAM with separate data buses, and
SimpleCounter with an unconnected or constant maximum input. Level-triggered
register latches remain unsupported on the A7 backend.

FPGA workflow
-------------
Open a circuit, select FPGA implementation, then:
1. Map the circuit ports to A7 board resources.
2. Generate the bitstream (synthesis and place/route are included).
3. Detect the device and download to the FPGA's temporary configuration memory.
The configuration is lost when the experiment board loses power.

The supported board is the Loongson A7 XC7A200T-FBG676-1 experiment box.
LoongSim automatically detects supported Xilinx and Digilent cables.
The EXE installer prepares the signed FTDI driver. Bundles that include the
separate Xilinx driver and firmware also prepare that cable during setup.
The standalone MSI and portable app use the drivers already on the computer.

Tools and licenses
------------------
FPGA engines and databases are in app/fpga/runtime/windows-x64.
Original upstream license directories, build metadata and notices are preserved.
The LoongSim USB/JTAG helper is based on openFPGALoader v1.2.0; its license, patches,
source revision and compiler record are included in the runtime's loongsim folder.

Project: https://github.com/xuan-xuan7/LoongSim
Tools: https://github.com/YosysHQ/oss-cad-suite-build
       https://github.com/cavearr/toolchain-openxc7-releases
       https://github.com/trabucayre/openFPGALoader

This build is unsigned. No code-signing certificate is bundled.

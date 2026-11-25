#!/usr/bin/env bash
set -euo pipefail

repo=$(cd -- "$(dirname -- "$0")/.." && pwd)
if [[ $# -ne 1 ]]; then
    echo "Usage: bash toolchain/package-loongnix20.sh LOONGSIM.jar" >&2
    exit 2
fi
jar=$(realpath -e -- "$1")
runtime=$(realpath -e -- "${LOONGSIM_FPGA_RUNTIME:-$repo/fpga/runtime/loongnix20-loongarch64}")
architecture=$(dpkg --print-architecture)
if [[ $architecture != loongarch64 ]] || ! grep -qx 'VersionID=20' /etc/loongnix_version; then
    echo "Build this package on Loongnix 20 loongarch64." >&2
    exit 1
fi
grep -qx 'platform=loongnix20-loongarch64' "$runtime/runtime.properties"
grep -qx 'device=xc7a200tfbg676-1' "$runtime/runtime.properties"
version=${LOONGSIM_DEB_VERSION:-1.2.0~loongnix20.1}
dpkg --validate-version "$version"
output=${LOONGSIM_DEB_OUTPUT:-"$repo/dist"}
mkdir -p "$output"

work=$(mktemp -d "$output/.loongsim-package.XXXXXX")
work=$(realpath -- "$work")
trap 'rm -rf -- "$work"' EXIT
pkg="$work/package"
app="$pkg/opt/loongsim"
icon=logisim/src/main/resources/resources/logisim/img/LoongSim.png
install -Dm644 "$jar" "$app/LoongSim.jar"
mkdir -p "$app/fpga/runtime" "$pkg/DEBIAN" "$work/debian"
cp -a "$repo/fpga/boards" "$repo/fpga/examples" "$app/fpga/"
cp -a "$runtime" "$app/fpga/runtime/loongnix20-loongarch64"
packaged_runtime="$app/fpga/runtime/loongnix20-loongarch64"
install -Dm644 "$repo/$icon" "$pkg/usr/share/pixmaps/loongsim.png"
install -Dm644 "$repo/logisim/LICENSE.md" "$pkg/usr/share/doc/loongsim/copyright"

mkdir -p "$pkg/usr/bin" "$pkg/usr/share/applications" "$pkg/lib/udev/rules.d"
cat > "$pkg/usr/bin/loongsim" <<'EOF'
#!/bin/sh
java=/usr/lib/jvm/java-11-openjdk-loongarch64/bin/java
if [ ! -x "$java" ]; then java=java; fi
exec "$java" -Dfile.encoding=UTF-8 -jar /opt/loongsim/LoongSim.jar "$@"
EOF
chmod 755 "$pkg/usr/bin/loongsim"
cat > "$pkg/usr/share/applications/loongsim.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=LoongSim
Exec=loongsim %f
Icon=loongsim
Terminal=false
Categories=Education;Electronics;
EOF
cat > "$pkg/lib/udev/rules.d/70-loongsim-a7.rules" <<'EOF'
SUBSYSTEM=="usb", ENV{DEVTYPE}=="usb_device", ATTR{idVendor}=="03fd", ATTR{idProduct}=="0007|0008|000d|0013", GROUP="plugdev", MODE="0660", TAG+="uaccess"
SUBSYSTEM=="usb", ENV{DEVTYPE}=="usb_device", ATTR{idVendor}=="0403", ATTR{idProduct}=="6010|6011|6014", ATTR{manufacturer}=="Digilent", GROUP="plugdev", MODE="0660", TAG+="uaccess"
EOF

cat > "$pkg/DEBIAN/postinst" <<'EOF'
#!/bin/sh
set -e
if command -v udevadm >/dev/null 2>&1; then
    udevadm control --reload-rules >/dev/null 2>&1 || true
    udevadm trigger --subsystem-match=usb >/dev/null 2>&1 || true
fi
EOF
chmod 755 "$pkg/DEBIAN/postinst"

cat > "$work/debian/control" <<'EOF'
Source: loongsim
Section: electronics
Priority: optional
Maintainer: LoongSim contributors

Package: loongsim
Architecture: any
Description: Digital circuit simulation and FPGA workbench
EOF
native_dependencies=$(cd "$work" && dpkg-shlibdeps -O \
    -e"$packaged_runtime/bin/yosys" -e"$packaged_runtime/bin/yosys-abc" \
    -e"$packaged_runtime/bin/openFPGALoader" -e"$packaged_runtime/openxc7/bin/nextpnr-xilinx" \
    -e"$packaged_runtime/openxc7/bin/xc7frames2bit")
cat > "$pkg/DEBIAN/control" <<EOF
Package: loongsim
Version: $version
Architecture: $architecture
Maintainer: LoongSim contributors
Section: electronics
Priority: optional
Depends: openjdk-11-jre | java11-runtime, python3, udev, ${native_dependencies#shlibs:Depends=}
Description: Digital circuit simulation and FPGA workbench
 LoongSim edits and simulates digital circuits and builds bitstreams for
 the Loongson A7 experiment board using native open-source FPGA tools.
EOF

artifact="$output/loongsim_${version}_${architecture}.deb"
dpkg-deb -Zgzip -z3 --root-owner-group --build "$pkg" "$work/loongsim.deb"
mv -f -- "$work/loongsim.deb" "$artifact"
echo "$artifact"

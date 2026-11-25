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

# Bundle a self-contained Java 11 runtime so air-gapped Loongnix 20 machines need no apt/network.
if [[ -n ${LOONGSIM_BUNDLED_JRE:-} ]]; then
    cp -a "$LOONGSIM_BUNDLED_JRE" "$app/jre"
else
    jdk_home=${LOONGSIM_JDK11_HOME:-}
    for candidate in "$jdk_home" /usr/lib/jvm/java-11-openjdk-loongarch64 "$HOME/.local/share/loongsim-dev/jdk-11"; do
        if [[ -n $candidate && -x "$candidate/bin/jlink" && -d "$candidate/jmods" ]]; then
            jdk_home=$candidate
            break
        fi
    done
    if [[ -n $jdk_home && -x "$jdk_home/bin/jlink" ]]; then
        "$jdk_home/bin/jlink" --module-path "$jdk_home/jmods" \
            --add-modules java.base,java.desktop,java.logging,java.prefs,java.xml,java.net.http,jdk.crypto.ec,java.management,java.naming,java.sql \
            --strip-debug --no-man-pages --no-header-files --compress=2 \
            --output "$app/jre"
    elif [[ -d /usr/lib/jvm/java-11-openjdk-loongarch64 ]]; then
        cp -a /usr/lib/jvm/java-11-openjdk-loongarch64 "$app/jre"
    else
        echo "No Java 11 JDK/JRE found to bundle; set LOONGSIM_JDK11_HOME or LOONGSIM_BUNDLED_JRE." >&2
        exit 1
    fi
fi

# Bundle non-base shared libraries (Boost, FTDI, libusb, readline, etc.) into runtime/lib/.
python3 - "$packaged_runtime" <<'PY'
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

runtime = Path(sys.argv[1])
lib_dir = runtime / "lib"
license_dir = runtime / "share/licenses/bundled-libs"
lib_dir.mkdir(parents=True, exist_ok=True)
license_dir.mkdir(parents=True, exist_ok=True)

binaries = [
    runtime / "bin/yosys",
    runtime / "bin/yosys-abc",
    runtime / "bin/openFPGALoader",
    runtime / "openxc7/bin/nextpnr-xilinx",
    runtime / "openxc7/bin/xc7frames2bit",
]
base_prefixes = (
    "libc.so.", "libm.so.", "libpthread.so.", "libdl.so.", "librt.so.",
    "libstdc++.so.", "libgcc_s.so.", "libz.so.", "libbz2.so.", "libudev.so.",
    "ld.so", "ld-linux", "libresolv.so.", "libnss_", "libnsl.so.",
)

copied = {}
for binary in binaries:
    out = subprocess.check_output(["ldd", str(binary)], text=True)
    for line in out.splitlines():
        m = re.search(r"=>\s+(\S+\.so\S*)\s+\(", line)
        if not m:
            continue
        so_path = Path(m.group(1))
        name = so_path.name
        if any(name.startswith(p) for p in base_prefixes):
            continue
        if name not in copied:
            shutil.copy2(so_path, lib_dir / name, follow_symlinks=True)
            copied[name] = so_path.resolve()

packages = set()
for resolved in copied.values():
    res = subprocess.run(["dpkg-query", "-S", str(resolved)], capture_output=True, text=True)
    if res.returncode == 0:
        for line in res.stdout.splitlines():
            if ":" in line:
                packages.add(line.split(":", 1)[0].split(",", 1)[0].strip())

for pkg in sorted(packages):
    doc = Path(f"/usr/share/doc/{pkg}/copyright")
    if doc.is_file():
        shutil.copy2(doc, license_dir / f"{pkg}.txt")
print(f"Bundled {len(copied)} shared libraries from {len(packages)} packages.")
PY

if command -v patchelf >/dev/null 2>&1; then
    patchelf --set-rpath '$ORIGIN/../lib' "$packaged_runtime/bin/yosys" "$packaged_runtime/bin/yosys-abc" "$packaged_runtime/bin/openFPGALoader"
    patchelf --set-rpath '$ORIGIN/../../lib' "$packaged_runtime/openxc7/bin/nextpnr-xilinx" "$packaged_runtime/openxc7/bin/xc7frames2bit"
fi

install -Dm644 "$repo/$icon" "$pkg/usr/share/pixmaps/loongsim.png"
install -Dm644 "$repo/logisim/LICENSE.md" "$pkg/usr/share/doc/loongsim/copyright"

mkdir -p "$pkg/usr/bin" "$pkg/usr/share/applications" "$pkg/lib/udev/rules.d"
cat > "$pkg/usr/bin/loongsim" <<'EOF'
#!/bin/sh
if [ -x /opt/loongsim/jre/bin/java ]; then
    java=/opt/loongsim/jre/bin/java
elif [ -x /usr/lib/jvm/java-11-openjdk-loongarch64/bin/java ]; then
    java=/usr/lib/jvm/java-11-openjdk-loongarch64/bin/java
else
    java=java
fi
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
cat > "$pkg/DEBIAN/control" <<EOF
Package: loongsim
Version: $version
Architecture: $architecture
Maintainer: LoongSim contributors
Section: electronics
Priority: optional
Depends: python3, udev, libc6 (>= 2.28), libstdc++6, libgcc1 (>= 1:3.0), zlib1g (>= 1:1.1.4), libbz2-1.0, libudev1 (>= 183)
Description: Digital circuit simulation and FPGA workbench
 LoongSim edits and simulates digital circuits and builds bitstreams for
 the Loongson A7 experiment board using native open-source FPGA tools.
EOF

artifact="$output/loongsim_${version}_${architecture}.deb"
dpkg-deb -Zgzip -z3 --root-owner-group --build "$pkg" "$work/loongsim.deb"
mv -f -- "$work/loongsim.deb" "$artifact"
echo "$artifact"

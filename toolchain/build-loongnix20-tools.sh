#!/usr/bin/env bash
set -euo pipefail

repo=$(cd -- "$(dirname -- "$0")/.." && pwd)
if [[ $# -ne 1 ]]; then
    echo "Usage: bash toolchain/build-loongnix20-tools.sh OPENXC7_RELEASE.tgz" >&2
    exit 2
fi
archive=$(realpath -- "$1")
if [[ $(uname -m) != loongarch64 ]] || ! grep -qx 'VersionID=20' /etc/loongnix_version; then
    echo "Build this runtime on Loongnix 20 loongarch64." >&2
    exit 1
fi

work=${LOONGSIM_LOONGNIX_BUILD_DIR:-"$repo/local/loongnix20-build"}
runtime=$(realpath -m -- "${LOONGSIM_FPGA_RUNTIME:-$repo/fpga/runtime/loongnix20-loongarch64}")
cmake=${CMAKE:-cmake}
jobs=${JOBS:-2}
yosys_version=$(dpkg-query -W -f='${Version}' yosys)
abc_version=$(dpkg-query -W -f='${Version}' berkeley-abc)
if [[ $yosys_version != 0.8-* ]]; then
    echo "Use the Loongnix 20 Yosys 0.8 package for this runtime." >&2
    exit 1
fi
nextpnr_revision=ab72e722965617898fc9220fe419a63a3f94878b
xray_revision=317fdc1319a225f57b6a0e6993a37d1a4ae75e5a
fasm_revision=2f57ccb1727a120e8cacbb95c578f3c71bdcc95a
pyyaml_revision=c42fa3bff1eabdb64763bb1526d9ea1ccb708479
loader_revision=befbab4f646207dbaf1daf8b144dd980ad283dd5

python3 - "$repo/toolchain/windows-tools.lock.json" "$archive" <<'PY'
import hashlib
import json
import sys

with open(sys.argv[1]) as lock:
    expected = json.load(lock)["openxc7"]["sha256"]
digest = hashlib.sha256()
with open(sys.argv[2], "rb") as archive:
    for block in iter(lambda: archive.read(1048576), b""):
        digest.update(block)
if digest.hexdigest() != expected:
    sys.exit("openXC7 archive SHA256 mismatch")
PY

mkdir -p "$work/src" "$(dirname -- "$runtime")"
work=$(realpath -- "$work")
exec 9>"$work/.build.lock" 8>"$runtime.lock"
flock 9
flock 8
stage=$(mktemp -d "$(dirname -- "$runtime")/.loongnix20-tools.XXXXXX")
trap 'rm -rf -- "$stage"' EXIT
mkdir -p "$stage/bin" "$stage/openxc7/bin" "$stage/openxc7/libexec" "$stage/openxc7/lib/python"
mkdir -p "$stage/share/licenses"

checkout() {
    local name=$1 url=$2 revision=$3 dir="$work/src/$1"
    mkdir -p "$dir"
    git -C "$dir" init -q
    if ! git -C "$dir" cat-file -e "$revision^{commit}" 2>/dev/null; then
        git -C "$dir" -c http.version=HTTP/1.1 fetch -q --depth=1 "$url" "$revision"
    fi
    git -C "$dir" checkout -q --detach "$revision"
    echo "$name $(git -C "$dir" rev-parse HEAD)"
}

submodules() {
    local dir=$1
    shift
    git -C "$dir" -c http.version=HTTP/1.1 submodule update --init --depth=1 --jobs="$jobs" -- "$@"
}

apply_tool_patch() {
    local dir=$1 patch="$repo/toolchain/patches/$2"
    if ! git -C "$dir" apply --reverse --check "$patch" 2>/dev/null; then
        git -C "$dir" apply "$patch"
    fi
}

checkout nextpnr https://github.com/openXC7/nextpnr.git "$nextpnr_revision"
checkout prjxray https://github.com/openXC7/prjxray.git "$xray_revision"
checkout fasm https://github.com/openXC7/fasm.git "$fasm_revision"
checkout pyyaml https://github.com/yaml/pyyaml.git "$pyyaml_revision"
checkout openFPGALoader https://github.com/trabucayre/openFPGALoader.git "$loader_revision"

src="$work/src"
submodules "$src/prjxray" third_party/abseil-cpp third_party/cctz third_party/googletest \
    third_party/gflags third_party/yaml-cpp

install -m755 /usr/bin/yosys /usr/bin/berkeley-abc "$stage/bin/"
cp -a /usr/share/yosys "$stage/share/"
ln -s berkeley-abc "$stage/bin/yosys-abc"
ln -s /usr/bin/python3 "$stage/bin/python3"

# Extract only architecture-independent data from the pinned release.
database=share/nextpnr/external/prjxray-db
chipdb=share/nextpnr/himbaechel/xilinx
tar -xzf "$archive" -C "$stage/openxc7" "./$database/artix7" \
    "./$chipdb/chipdb-xc7a200t.bin" "./$chipdb/chipdb-id.txt"
[[ $(<"$stage/openxc7/$chipdb/chipdb-id.txt") == 3120dc99e7ad324c ]]

apply_tool_patch "$src/nextpnr" nextpnr-loongnix20-cmake.patch
apply_tool_patch "$src/nextpnr" nextpnr-loongnix20-boost.patch
apply_tool_patch "$src/prjxray/third_party/abseil-cpp" prjxray-abseil-loongnix20-cmake.patch
apply_tool_patch "$src/prjxray/third_party/cctz" prjxray-cctz-loongnix20-cmake.patch
"$cmake" -S "$src/nextpnr" -B "$work/build-nextpnr" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_STANDARD_LIBRARIES=-lstdc++fs \
    -DARCH=himbaechel -DHIMBAECHEL_UARCH=xilinx -DBUILD_GUI=OFF -DBUILD_PYTHON=OFF \
    -DBUILD_TESTS=OFF -DUSE_IPO=OFF -DHIMBAECHEL_XILINX_DEVICES= \
    -DHIMBAECHEL_PRJXRAY_DB="$stage/openxc7/$database"
"$cmake" --build "$work/build-nextpnr" --parallel "$jobs" --target nextpnr-himbaechel
install -m755 "$work/build-nextpnr/nextpnr-himbaechel" "$stage/openxc7/bin/nextpnr-xilinx"

"$cmake" -S "$src/prjxray" -B "$work/build-prjxray" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_STANDARD=17 -DPRJXRAY_BUILD_TESTING=OFF
"$cmake" --build "$work/build-prjxray" --parallel "$jobs" --target xc7frames2bit
install -m755 "$work/build-prjxray/tools/xc7frames2bit" "$stage/openxc7/bin/"
install -m644 "$src/prjxray/utils/fasm2frames.py" "$stage/openxc7/libexec/fasm2frames"
cp -a "$src/prjxray/prjxray" "$src/fasm/fasm" "$stage/openxc7/lib/python/"
# The system PyYAML predates FullLoader, which the matching Project X-Ray uses.
cp -a "$src/pyyaml/lib/yaml" "$stage/openxc7/lib/python/"
python3 -m pip install --disable-pip-version-check --no-deps --no-compile \
    --target="$stage/openxc7/lib/python" textX==3.0.0 Arpeggio==2.0.2 \
    future==0.18.3 intervaltree==3.1.0 sortedcontainers==2.4.0 simplejson==3.19.2

apply_tool_patch "$src/openFPGALoader" openfpgaloader-v1.2.0-xpcu.patch
"$cmake" -S "$src/openFPGALoader" -B "$work/build-loader" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release -DENABLE_LIBGPIOD=OFF -DENABLE_CMSISDAP=OFF \
    -DENABLE_UDEV=ON -DISE_PATH= -DVIVADO_PATH=
"$cmake" --build "$work/build-loader" --parallel "$jobs"
install -m755 "$work/build-loader/openFPGALoader" "$stage/bin/"

for source in yosys berkeley-abc; do
    install -m644 "/usr/share/doc/$source/copyright" "$stage/share/licenses/$source.txt"
done
install -m644 "$src/nextpnr/COPYING" "$stage/share/licenses/nextpnr.txt"
for source in prjxray fasm pyyaml openFPGALoader; do
    install -m644 "$src/$source/LICENSE" "$stage/share/licenses/$source.txt"
done
install -m644 "$repo/toolchain/patches/openfpgaloader-v1.2.0-xpcu.patch" "$stage/share/licenses/"
cat > "$stage/runtime.properties" <<EOF
platform=loongnix20-loongarch64
device=xc7a200tfbg676-1
yosys.package=$yosys_version
abc.package=$abc_version
openxc7.version=2026-10-27
nextpnr.revision=$nextpnr_revision
prjxray.revision=$xray_revision
fasm.revision=$fasm_revision
pyyaml.revision=$pyyaml_revision
openFPGALoader.revision=$loader_revision
EOF

"$stage/bin/yosys" -V
"$stage/openxc7/bin/nextpnr-xilinx" --version
PYTHONPATH="$stage/openxc7/lib/python" PYTHONNOUSERSITE=1 \
    "$stage/bin/python3" "$stage/openxc7/libexec/fasm2frames" --help >/dev/null

chmod 755 "$stage"
if [[ -e "$runtime" ]]; then
    previous=$(mktemp -d "$(dirname -- "$runtime")/.loongnix20-previous.XXXXXX")
    mv -- "$runtime" "$previous/runtime"
    if ! mv -- "$stage" "$runtime"; then
        mv -- "$previous/runtime" "$runtime"
        exit 1
    fi
    echo "Previous runtime: $previous/runtime"
else
    mv -- "$stage" "$runtime"
fi
trap - EXIT
echo "Installed Loongnix FPGA runtime: $runtime"

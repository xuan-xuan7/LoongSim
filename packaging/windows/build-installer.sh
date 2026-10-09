#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$ROOT_DIR/logisim/loongsim_version.xml")
COMMIT=$(git -C "$ROOT_DIR" rev-parse --short=8 HEAD)
BUILD_DIR="$SCRIPT_DIR/build"
DOWNLOAD_DIR="$BUILD_DIR/downloads"
UNPACK_DIR="$BUILD_DIR/unpacked"
STAGE_DIR="$BUILD_DIR/stage"
DIST_DIR="$ROOT_DIR/dist"
JAR_FILE="$ROOT_DIR/logisim/build/libs/LoongSim-${VERSION}-java11-all.jar"
JRE_ARCHIVE="$DOWNLOAD_DIR/OpenJDK11U-jre_x64_windows_hotspot_11.0.32.1_1.zip"
JRE_URL="https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.32.1%2B1/OpenJDK11U-jre_x64_windows_hotspot_11.0.32.1_1.zip"
JRE_SHA256="f8c7da672f5dba36b6f870608820b6b598cfae91296929f1b8f21ef2f1e8a0dd"
OUTPUT_FILE="$DIST_DIR/LoongSim-${VERSION}-logisim-only-windows-x64-setup.exe"

for command in curl git makensis sed shasum unzip; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "错误：缺少命令 $command" >&2
    exit 1
  fi
done

mkdir -p "$DOWNLOAD_DIR" "$DIST_DIR"

echo "[1/5] 编译当前分支的 LoongSim JAR"
"$ROOT_DIR/logisim/gradlew" -p "$ROOT_DIR/logisim" clean shadowJar --no-daemon

echo "[2/5] 准备 Eclipse Temurin Windows x64 Java 11 运行时"
if [ ! -f "$JRE_ARCHIVE" ]; then
  curl --fail --location --retry 3 --output "$JRE_ARCHIVE" "$JRE_URL"
fi
ACTUAL_JRE_SHA256=$(shasum -a 256 "$JRE_ARCHIVE" | awk '{print $1}')
if [ "$ACTUAL_JRE_SHA256" != "$JRE_SHA256" ]; then
  echo "错误：JRE SHA-256 校验失败" >&2
  echo "期望：$JRE_SHA256" >&2
  echo "实际：$ACTUAL_JRE_SHA256" >&2
  exit 1
fi

echo "[3/5] 组装安装目录"
rm -rf "$UNPACK_DIR" "$STAGE_DIR"
mkdir -p "$UNPACK_DIR" "$STAGE_DIR"
unzip -q "$JRE_ARCHIVE" -d "$UNPACK_DIR"
JRE_ROOT=$(find "$UNPACK_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)
if [ -z "$JRE_ROOT" ] || [ ! -f "$JRE_ROOT/bin/javaw.exe" ]; then
  echo "错误：JRE 压缩包结构不符合预期" >&2
  exit 1
fi
mv "$JRE_ROOT" "$STAGE_DIR/runtime"
cp "$JAR_FILE" "$STAGE_DIR/LoongSim.jar"
cp "$ROOT_DIR/logisim/LICENSE.md" "$STAGE_DIR/LICENSE.md"
sed -e "s/VERSION/$VERSION/g" -e "s/COMMIT/$COMMIT/g" \
  "$SCRIPT_DIR/README.txt" > "$STAGE_DIR/README.txt"
cat > "$STAGE_DIR/VERSION.txt" <<EOF
LoongSim $VERSION
Branch: logisim-only
Commit: $COMMIT
Platform: Windows x64
Runtime: Eclipse Temurin JRE 11.0.32.1+1
EOF

echo "[4/5] 生成 Windows 安装程序"
rm -f "$OUTPUT_FILE" "$OUTPUT_FILE.sha256"
makensis \
  -DAPP_VERSION="$VERSION" \
  -DSOURCE_COMMIT="$COMMIT" \
  -DSOURCE_DIR="$STAGE_DIR" \
  -DOUTPUT_FILE="$OUTPUT_FILE" \
  "$SCRIPT_DIR/LoongSim.nsi"

echo "[5/5] 生成 SHA-256 校验文件"
(cd "$DIST_DIR" && shasum -a 256 "$(basename "$OUTPUT_FILE")" > "$(basename "$OUTPUT_FILE").sha256")

echo "安装包：$OUTPUT_FILE"
echo "校验文件：$OUTPUT_FILE.sha256"

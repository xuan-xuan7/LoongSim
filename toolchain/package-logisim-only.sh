#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
LOGISIM_DIR="$ROOT_DIR/logisim"
VERSION=$(sed -n 's/.*LOONGSIM_VERSION_NAME = "\([^"]*\)".*/\1/p' \
  "$LOGISIM_DIR/src/main/java/com/cburch/logisim/Main.java")
JAR_FILE="$LOGISIM_DIR/build/libs/LoongSim-${VERSION}-java11-all.jar"
DIST_DIR="$ROOT_DIR/dist"
PACKAGE_NAME="LoongSim-${VERSION}-logisim-only"
STAGE_DIR="$DIST_DIR/$PACKAGE_NAME"
ZIP_FILE="$DIST_DIR/$PACKAGE_NAME.zip"

if [ -z "$VERSION" ]; then
  echo "Error: could not determine the LoongSim version."
  exit 1
fi

if [ ! -f "$JAR_FILE" ]; then
  echo "The fat JAR was not found; building it now."
  (
    cd "$LOGISIM_DIR"
    ./gradlew shadowJar --no-daemon
  )
fi

if [ ! -f "$JAR_FILE" ]; then
  echo "Error: build did not produce $JAR_FILE"
  exit 1
fi

rm -rf "$STAGE_DIR" "$ZIP_FILE" "$ZIP_FILE.sha256"
mkdir -p "$STAGE_DIR"

cp "$JAR_FILE" "$STAGE_DIR/LoongSim.jar"
cp "$SCRIPT_DIR/release/logisim-only/README.txt" "$STAGE_DIR/"
cp "$SCRIPT_DIR/release/logisim-only/run-loongsim.sh" "$STAGE_DIR/"
cp "$SCRIPT_DIR/release/logisim-only/LoongSim.bat" "$STAGE_DIR/"
cp "$LOGISIM_DIR/LICENSE.md" "$STAGE_DIR/"
chmod +x "$STAGE_DIR/run-loongsim.sh"

(
  cd "$DIST_DIR"
  zip -qr "$ZIP_FILE" "$PACKAGE_NAME"
)

shasum -a 256 "$ZIP_FILE" > "$ZIP_FILE.sha256"

echo "Created: $ZIP_FILE"
echo "Checksum: $ZIP_FILE.sha256"

#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR_FILE="$SCRIPT_DIR/LoongSim.jar"

if ! command -v java >/dev/null 2>&1; then
  echo "Error: Java 11 or newer was not found."
  exit 1
fi

exec java \
  -Dflatlaf.useNativeLibrary=false \
  -Dflatlaf.useSystemFileChooser=false \
  -jar "$JAR_FILE" "$@"

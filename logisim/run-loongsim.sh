#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR_FILE="$SCRIPT_DIR/build/libs/LoongSim-1.0.1-java11-all.jar"

if ! command -v java >/dev/null 2>&1; then
  echo "错误：未找到 Java，请先安装 Java 11 或更高版本。"
  exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '[\".]' '/version/ { print ($2 == "1" ? $3 : $2); exit }')
case "$JAVA_VERSION" in
  ''|*[!0-9]*)
    echo "错误：无法识别当前 Java 版本。需要 Java 11 或更高版本。"
    exit 1
    ;;
esac
if [ "$JAVA_VERSION" -lt 11 ]; then
  echo "错误：当前 Java 版本为 $JAVA_VERSION，需要 Java 11 或更高版本。"
  exit 1
fi

if [ ! -f "$JAR_FILE" ] \
  || [ "$SCRIPT_DIR/build.gradle" -nt "$JAR_FILE" ] \
  || [ "$SCRIPT_DIR/settings.gradle" -nt "$JAR_FILE" ] \
  || find "$SCRIPT_DIR/src" -type f -newer "$JAR_FILE" -print -quit | grep -q .; then
  echo "检测到源码更新，开始编译 LoongSim……"
  "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" shadowJar --no-daemon
fi

exec java \
  -Dflatlaf.useNativeLibrary=false \
  -Dflatlaf.useSystemFileChooser=false \
  -jar "$JAR_FILE" "$@"

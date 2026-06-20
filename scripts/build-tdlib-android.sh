#!/usr/bin/env bash
set -euo pipefail

TDLIB_SRC_DIR="${TDLIB_SRC_DIR:?TDLIB_SRC_DIR must be set}"
TDLIB_OUT_DIR="${TDLIB_OUT_DIR:?TDLIB_OUT_DIR must be set}"
TDLIB_JAR_OUT="${TDLIB_JAR_OUT:?TDLIB_JAR_OUT must be set}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must be set}"
ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-$ANDROID_NDK_HOME}"

TDLIB_SRC_DIR="$(cd "$TDLIB_SRC_DIR" && pwd)"
TDLIB_OUT_DIR="$(mkdir -p "$TDLIB_OUT_DIR" && cd "$TDLIB_OUT_DIR" && pwd)"
mkdir -p "$(dirname "$TDLIB_JAR_OUT")"

echo "==> TDLib source: $TDLIB_SRC_DIR"
echo "==> Output .so dir: $TDLIB_OUT_DIR"
echo "==> Output .jar: $TDLIB_JAR_OUT"
echo "==> NDK: $ANDROID_NDK_HOME"

[ -d "$ANDROID_NDK_HOME" ] || { echo "ERROR: NDK dir missing"; exit 1; }
[ -f "$TDLIB_SRC_DIR/CMakeLists.txt" ] || { echo "ERROR: TDLib source missing CMakeLists.txt"; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

for ABI in "${ABIS[@]}"; do
  BUILD_DIR="$WORK/build-$ABI"
  echo ""
  echo "==> Building TDLib for $ABI..."
  mkdir -p "$BUILD_DIR"
  cd "$BUILD_DIR"

  cmake "$TDLIB_SRC_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=Release \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=android-21 \
    -DANDROID_STL=c++_static \
    -DCMAKE_INSTALL_PREFIX="$BUILD_DIR/install" \
    -DTD_ENABLE_LTO=OFF \
    -DBUILD_SHARED_LIBS=ON

  cmake --build . --target tdlib -- -j"$(nproc)"

  mkdir -p "$TDLIB_OUT_DIR/$ABI"
  find "$BUILD_DIR" -name 'libtdjni.so' -exec cp -v {} "$TDLIB_OUT_DIR/$ABI/" \;
done

echo ""
echo "==> Built libtdjni.so for ABIs:"
find "$TDLIB_OUT_DIR" -name '*.so' -exec ls -lh {} \;

echo ""
echo "==> Building TDLib Java bindings JAR..."
JAVA_SRC_DIR="$TDLIB_SRC_DIR/example/java/tdlib/src/main/java"
[ -d "$JAVA_SRC_DIR" ] || { echo "ERROR: Java sources not found at $JAVA_SRC_DIR"; exit 1; }

JAVA_CLASSES="$WORK/java-classes"
mkdir -p "$JAVA_CLASSES"
find "$JAVA_SRC_DIR" -name '*.java' -print0 | xargs -0 javac -d "$JAVA_CLASSES"
jar cf "$TDLIB_JAR_OUT" -C "$JAVA_CLASSES" .

echo ""
echo "==> JAR contents:"
jar tf "$TDLIB_JAR_OUT" | head -20
echo "==> DONE"

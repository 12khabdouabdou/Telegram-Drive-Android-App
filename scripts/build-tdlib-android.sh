#!/usr/bin/env bash
#
# Builds TDLib native libraries (libtdjni.so) for Android across 4 ABIs,
# using CMake + the Android NDK. Produces the JAR with Java bindings too.
#
# Heavily based on the official TDLib Android build instructions at
# https://github.com/tdlib/td#building — adapted for CI.
#
# Usage (env vars):
#   TDLIB_SRC_DIR  - path to a checkout of tdlib/td (default: ../tdlib-src)
#   TDLIB_OUT_DIR  - where to place libtdjni.so per-ABI (default: ../app/src/main/jniLibs)
#   ANDROID_NDK_HOME / ANDROID_NDK_ROOT - NDK root (must be set)
#
set -euo pipefail

TDLIB_SRC_DIR="${TDLIB_SRC_DIR:-$(pwd)/tdlib-src}"
TDLIB_OUT_DIR="${TDLIB_OUT_DIR:-$(pwd)/app/src/main/jniLibs}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must be set}"
ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-$ANDROID_NDK_HOME}"

echo "==> TDLib source: $TDLIB_SRC_DIR"
echo "==> Output dir:   $TDLIB_OUT_DIR"
echo "==> NDK:          $ANDROID_NDK_HOME"

mkdir -p "$TDLIB_OUT_DIR"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# ABI → CMAKE_ANDROID_ARCH mapping
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")
ARCHS=("arm" "arm64" "x86" "x86_64")

for i in "${!ABIS[@]}"; do
  ABI="${ABIS[$i]}"
  ARCH="${ARCHS[$i]}"
  BUILD_DIR="$WORK/build-$ABI"
  echo ""
  echo "==> Building TDLib for $ABI ($ARCH)..."
  mkdir -p "$BUILD_DIR"
  pushd "$BUILD_DIR" >/dev/null

  cmake "$TDLIB_SRC_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=Release \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_static \
    -DCMAKE_INSTALL_PREFIX="$BUILD_DIR/install" \
    -DTD_ENABLE_LTO=OFF \
    -DBUILD_SHARED_LIBS=ON

  cmake --build . --target tdjni -- -j"$(nproc)"
  cmake --install .

  # Copy the produced .so to jniLibs/<ABI>/
  mkdir -p "$TDLIB_OUT_DIR/$ABI"
  find "$BUILD_DIR/install" -name 'libtdjni.so' -exec cp -v {} "$TDLIB_OUT_DIR/$ABI/" \;

  popd >/dev/null
done

echo ""
echo "==> Built libtdjni.so for ABIs:"
ls -lh "$TDLIB_OUT_DIR"/*/*.so

# Build the Java bindings JAR (TdApi + Client classes)
echo ""
echo "==> Building TDLib Java bindings JAR..."
JAVA_BUILD="$WORK/java-build"
mkdir -p "$JAVA_BUILD"
pushd "$JAVA_BUILD" >/dev/null

cmake "$TDLIB_SRC_DIR" \
  -DCMAKE_BUILD_TYPE=Release \
  -DTD_ENABLE_JNI=ON

cmake --build . --target td_generate_java_api -- -j"$(nproc)"

# The generated Java sources land under tdlib/td/generate/auto/java/
# We compile them into a JAR.
JAVA_SRC_DIR="$TDLIB_SRC_DIR/generate/auto/java/tdlib/src/main/java"
JAR_OUT="$WORK/tdlib-java.jar"
mkdir -p classes
find "$JAVA_SRC_DIR" -name '*.java' -print0 | xargs -0 javac -d classes
jar cf "$JAR_OUT" -C classes .

# Install the JAR into a local Maven repo so Gradle can consume it
# (placed under app/libs so the project picks it up automatically)
mkdir -p "$TDLIB_OUT_DIR/../libs"
cp -v "$JAR_OUT" "$TDLIB_OUT_DIR/../libs/tdlib-java.jar"

popd >/dev/null
echo ""
echo "==> DONE. libtdjni.so is in: $TDLIB_OUT_DIR"
echo "==> Java bindings JAR is in: $TDLIB_OUT_DIR/../libs/tdlib-java.jar"

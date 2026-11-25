#!/usr/bin/env bash
# tools/android/ffmpeg_android.sh -- FFmpeg for the Android app: the engine's static LGPL recipe (engine/tools/ci/
# ffmpeg_static.sh: the mpeg1/mpeg2 video decoders, the mpegvideo parser and swscale, nothing else) cross-compiled for
# arm64 with the NDK. The same recipe and the same pinned release as tools/vita/ffmpeg_vita.sh; only the toolchain differs.
#
#   tools/android/ffmpeg_android.sh <prefix>      build into <prefix> (lib/{libavcodec,libavutil,libswscale}.a)
#   tools/android/ffmpeg_android.sh --print-args  the configure arguments, one per line
#   FFMPEG_SOURCE=<file>                          a local ffmpeg-6.1.2.tar.xz instead of the download
#   ANDROID_NDK_HOME                              the NDK root (r28c); API level ANDROID_API (default 26)
#
# Licence: LGPL v2.1 or later (--disable-gpl --disable-nonfree --disable-version3, as the recipe).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RECIPE="$ROOT/engine/tools/ci/ffmpeg_static.sh"
VERSION=6.1.2
TAG=n6.1.2
URL="https://ffmpeg.org/releases/ffmpeg-$VERSION.tar.xz"
SHA256=3b624649725ecdc565c903ca6643d41f33bd49239922e45c9b1442c63dca4e38
API="${ANDROID_API:-26}"

RECIPE_TAG="$(env -u FFMPEG_TAG bash "$RECIPE" --print-configure | awk 'NR == 1 {print $2}')"
if [ "$RECIPE_TAG" != "$TAG" ]; then
  echo "ffmpeg_android: the engine recipe builds FFmpeg $RECIPE_TAG; this script pins $TAG: update VERSION / TAG / SHA256" >&2
  exit 1
fi

# The one list of configure arguments: the recipe's, unchanged. Its --enable-pic stays (unlike the Vita's eboot, the
# Android build links these archives into a shared library, which needs position-independent code).
ARGS=()
while IFS= read -r a; do ARGS+=("$a"); done < <(env -u FFMPEG_TAG bash "$RECIPE" --print-args)
ARGS+=(
  --enable-cross-compile --arch=aarch64 --target-os=android
  --disable-runtime-cpudetect
  --cc=aarch64-linux-android${API}-clang --cxx=aarch64-linux-android${API}-clang++
  --ar=llvm-ar --ranlib=llvm-ranlib --strip=llvm-strip --nm=llvm-nm
)
if [ "${1:-}" = "--print-args" ]; then printf '%s\n' "${ARGS[@]}"; exit 0; fi

PREFIX="${1:?usage: ffmpeg_android.sh <prefix> | --print-args}"
case "$PREFIX" in /*) ;; *) PREFIX="$(pwd)/$PREFIX" ;; esac
case "$PREFIX" in *" "*) echo "ffmpeg_android: the prefix '$PREFIX' contains a space" >&2; exit 1 ;; esac

NDK="${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to the NDK r28c root}"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -x "$BIN/aarch64-linux-android${API}-clang" ] || { echo "ffmpeg_android: $BIN has no aarch64-linux-android${API}-clang" >&2; exit 1; }
export PATH="$BIN:$PATH"
JOBS="${FFMPEG_JOBS:-$(nproc 2>/dev/null || echo 4)}"

sha256_of() { sha256sum "$1" | awk '{print $1}'; }
DOWNLOADS="$PREFIX.download"
if [ -n "${FFMPEG_SOURCE:-}" ]; then
  TARBALL="$FFMPEG_SOURCE"
  [ "$(sha256_of "$TARBALL")" = "$SHA256" ] || { echo "ffmpeg_android: $TARBALL is not the pinned FFmpeg $VERSION" >&2; exit 1; }
else
  TARBALL="$DOWNLOADS/ffmpeg-$VERSION.tar.xz"
  if [ ! -f "$TARBALL" ] || [ "$(sha256_of "$TARBALL")" != "$SHA256" ]; then
    mkdir -p "$DOWNLOADS"; rm -f "$TARBALL"
    echo "ffmpeg_android: downloading $URL"
    curl -fsSL --retry 3 -o "$TARBALL.part" "$URL"
    [ "$(sha256_of "$TARBALL.part")" = "$SHA256" ] || { echo "ffmpeg_android: sha256 mismatch" >&2; exit 1; }
    mv "$TARBALL.part" "$TARBALL"
  fi
fi

SRC="$PREFIX.src"; BLD="$PREFIX.build"
rm -rf "$SRC" "$BLD" "$PREFIX"; mkdir -p "$SRC" "$BLD"
tar -xJf "$TARBALL" -C "$SRC" --strip-components=1
echo "ffmpeg_android: configure (FFmpeg $VERSION, mpeg1/mpeg2 + mpegvideo + swscale, static, LGPL, aarch64 Android API $API)"
(cd "$BLD" && "$SRC/configure" --prefix="$PREFIX" "${ARGS[@]}") > "$BLD/configure.log" 2>&1 ||
  { tail -n 40 "$BLD/configure.log" >&2; echo "ffmpeg_android: configure FAILED (log $BLD/configure.log)" >&2; exit 1; }
echo "ffmpeg_android: make -j$JOBS"
make -C "$BLD" -j"$JOBS" > "$BLD/make.log" 2>&1 || { tail -n 40 "$BLD/make.log" >&2; echo "ffmpeg_android: make FAILED" >&2; exit 1; }
make -C "$BLD" install > "$BLD/install.log" 2>&1
# The licence check the Vita script does too: no GPL, no non-free, no version 3 in the build
if grep -q -- "--enable-gpl\|--enable-nonfree\|--enable-version3" "$PREFIX/lib/libavcodec.a" 2>/dev/null; then
  echo "ffmpeg_android: the build enables a non-LGPL option" >&2; exit 1
fi
echo "ffmpeg_android: OK -> $PREFIX/lib"
ls -la "$PREFIX/lib"

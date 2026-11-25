#!/usr/bin/env bash
# Downloads the game's split (SLES_525.68, asm/, assets/) from the GAME_DATA_URL secret into build-input/.
# The URL is never printed. GAME_DATA_SHA256 (a repository variable, not a secret) checks the zip when set.
set -euo pipefail

: "${GAME_DATA_URL:?set the GAME_DATA_URL secret}"
out=build-input
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

if ! curl --fail --location --silent --retry 3 -o "$tmp" "$GAME_DATA_URL"; then
  echo "game data download failed (the URL is hidden on purpose)" >&2
  exit 1
fi

if [ -n "${GAME_DATA_SHA256:-}" ]; then
  if ! echo "$GAME_DATA_SHA256  $tmp" | sha256sum -c --quiet -; then
    echo "game data checksum does not match GAME_DATA_SHA256" >&2
    exit 1
  fi
fi

mkdir -p "$out"
unzip -q -o "$tmp" -d "$out"

for needed in SLES_525.68 asm/data assets/vutext.textbin.bin; do
  if [ ! -e "$out/$needed" ]; then
    echo "game data incomplete: $needed is missing from the zip" >&2
    exit 1
  fi
done
echo "game data ready in $out (not printed, not uploaded)"

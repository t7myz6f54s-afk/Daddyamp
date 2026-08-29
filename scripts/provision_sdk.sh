#!/bin/bash
# DaddyAmp APK toolchain provisioning (build-tools 34.0.0 + platform android-34).
# Makes build_apk.sh reproducible on a clean machine. Requires: curl, unzip, java.
set -e
SDK_ROOT="${1:-$HOME/android-sdk}"
BT_DIR="$SDK_ROOT/build-tools/34.0.0"
PLAT_DIR="$SDK_ROOT/platforms/android-34"

need_dl=0
[ ! -x "$BT_DIR/aapt2" ] && need_dl=1
[ ! -f "$PLAT_DIR/android.jar" ] && need_dl=1
if [ "$need_dl" -eq 0 ]; then echo "SDK already provisioned at $SDK_ROOT"; exit 0; fi

TMP=$(mktemp -d)
echo "→ downloading build-tools r34 …"
curl -fSL -o "$TMP/bt.zip" https://dl.google.com/android/repository/build-tools_r34-linux.zip
echo "→ downloading platform android-34 …"
# Google retired plain platform-34_rNN zips; ext12 is the API-34 platform (android.jar
# is API level 34 either way).
curl -fSL -o "$TMP/plat.zip" https://dl.google.com/android/repository/platform-34-ext12_r01.zip

echo "→ extracting …"
unzip -q "$TMP/bt.zip" -d "$TMP/bt"
unzip -q "$TMP/plat.zip" -d "$TMP/plat"

mkdir -p "$BT_DIR" "$PLAT_DIR"
BT_TOP=$(find "$TMP/bt" -maxdepth 1 -mindepth 1 -type d | head -1)
PLAT_TOP=$(find "$TMP/plat" -maxdepth 1 -mindepth 1 -type d | head -1)
cp -r "$BT_TOP"/. "$BT_DIR/"
cp -r "$PLAT_TOP"/. "$PLAT_DIR/"
chmod +x "$BT_DIR"/aapt2 "$BT_DIR"/d8 "$BT_DIR"/zipalign "$BT_DIR"/apksigner 2>/dev/null || true
rm -rf "$TMP"

"$BT_DIR/aapt2" version >/dev/null && echo "aapt2 OK"
ls "$PLAT_DIR/android.jar" >/dev/null && echo "android.jar OK"
echo "SDK provisioned at $SDK_ROOT"

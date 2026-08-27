#!/bin/bash
set -e
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_TOOLS="/home/user/android-sdk/build-tools/34.0.0"
PLATFORM_JAR="/home/user/android-sdk/platforms/android-34/android.jar"
KEYSTORE="$PROJECT_DIR/keystore/audify.keystore"
KEY_ALIAS="audify"
KEY_PASS="audify123"

TEMP_DIR="/tmp/apk_build"
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR" "$PROJECT_DIR/gen" "$PROJECT_DIR/classes"

echo "1. Compiling resources..."
$SDK_TOOLS/aapt2 compile --dir "$PROJECT_DIR/android/res" -o "$TEMP_DIR/compiled.zip"

echo "2. Linking resources..."
$SDK_TOOLS/aapt2 link -o "$TEMP_DIR/base.apk" \
  -I "$PLATFORM_JAR" \
  --manifest "$PROJECT_DIR/android/AndroidManifest.xml" \
  --java "$PROJECT_DIR/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 7 \
  --version-name "1.6" \
  "$TEMP_DIR/compiled.zip"

echo "3. Compiling Java sources..."
javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$PLATFORM_JAR" \
  -classpath "$PLATFORM_JAR:$PROJECT_DIR/gen" \
  -d "$PROJECT_DIR/classes" \
  "$PROJECT_DIR/android/java/com/audify/music/"*.java "$PROJECT_DIR/gen/com/audify/music/R.java"

echo "4. D8 dexing..."
$SDK_TOOLS/d8 --output "$TEMP_DIR" --lib "$PLATFORM_JAR" --min-api 21 $(find "$PROJECT_DIR/classes" -name "*.class")

echo "5. Adding DEX to base APK..."
(cd "$TEMP_DIR" && zip -uj "$TEMP_DIR/base.apk" classes.dex)

echo "6. Syncing web assets and adding to APK..."
cp -f "$PROJECT_DIR/web/index.html" "$PROJECT_DIR/assets/index.html"
(cd "$PROJECT_DIR" && zip -ur "$TEMP_DIR/base.apk" assets/)

echo "7. Zipalign..."
$SDK_TOOLS/zipalign -f 4 "$TEMP_DIR/base.apk" "$TEMP_DIR/aligned.apk"

echo "8. Signing with apksigner..."
$SDK_TOOLS/apksigner sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEY_PASS" \
  --key-pass "pass:$KEY_PASS" \
  --out "$PROJECT_DIR/DaddyAmp.apk" \
  "$TEMP_DIR/aligned.apk"

echo "9. Verifying signature..."
$SDK_TOOLS/apksigner verify --verbose --print-certs "$PROJECT_DIR/DaddyAmp.apk"
$SDK_TOOLS/zipalign -c 4 "$PROJECT_DIR/DaddyAmp.apk"

rm -rf "$TEMP_DIR"
echo "Build successful! Deliverable: $PROJECT_DIR/DaddyAmp.apk"

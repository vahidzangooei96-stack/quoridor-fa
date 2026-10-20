#!/usr/bin/env bash
# Quoridor FA — APK build pipeline (aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner)
# Requires: ANDROID_HOME with build-tools;35.0.0 and platforms;android-34, plus JDK with javac.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-/home/z/my-project/tools/android-sdk}"
BT="$SDK/build-tools/35.0.0"
AJ="$SDK/platforms/android-34/android.jar"
VERSION="${VERSION:-1.0.1}"
VERSION="${VERSION#v}"   # allow passing tag names like v1.0.1
OUT="$ROOT/build/apk"
# versionCode derived from versionName: 1.0.1 -> 101 (monotonic for store updates)
VERSION_CODE=$(echo "$VERSION" | awk -F. '{printf "%d%02d%02d", $1, $2, $3}' | sed 's/^0*//')

if [ ! -x "$BT/aapt2" ]; then
  # try to install into the default runner SDK
  if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "build-tools;35.0.0" "platforms;android-34" > /dev/null 2>&1 || true
  fi
fi
if [ ! -x "$BT/aapt2" ]; then echo "ERROR: build-tools 35.0.0 not found under $SDK" >&2; exit 1; fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
echo "[1/6] aapt2 compile+link..."
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$AJ" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 21 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION" \
  -R "$OUT/res.zip" --java "$OUT/gen" --auto-add-overlay

echo "[2/6] javac..."
find "$ROOT/app/src/core" "$ROOT/app/src/main/java" "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -classpath "$AJ" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[3/6] d8 (dex)..."
"$JAVA_HOME/bin/jar" cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8" --release --lib "$AJ" --min-api 21 --output "$OUT/dex" "$OUT/classes.jar"

echo "[4/6] package..."
(cd "$OUT/dex" && zip -q -j "$OUT/base.apk" classes.dex)
# assets (fonts) MUST be packaged too — aapt2 link does not include them
if [ -d "$ROOT/app/src/main/assets" ]; then
  (cd "$ROOT/app/src/main" && zip -qr "$OUT/base.apk" assets)
fi

echo "[5/6] zipalign..."
KS="${QUORIDOR_KEYSTORE:-$ROOT/quoridor-signing.keystore}"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias quoridor -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass quoridorfa2026 -keypass quoridorfa2026 \
    -dname "CN=Quoridor FA, O=QuoridorFa, C=IR" 2>/dev/null
fi
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[6/6] sign..."
APK="$ROOT/Quoridor-FA-v$VERSION.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:quoridorfa2026 --key-pass pass:quoridorfa2026 \
  --out "$APK" "$OUT/aligned.apk"
"$BT/apksigner" verify "$APK" && echo "SIGNED+VERIFIED: $APK ($(du -h "$APK" | cut -f1))"

#!/usr/bin/env bash
# Builds the Sony Bravia IR Remote apk with the android sdk build-tools.
# No gradle, no third party libraries. The sdk binaries are x86_64 and this
# phone is aarch64, so aapt2/zipalign run through the qemu wrappers in
# /workspace/kingstrade/tools/bin (qemu + glibc sysroot at /tmp/opencode/sysroot).
set -euo pipefail

APP=/workspace/sony-bravia-ir-remote
OUT=/workspace/sony-bravia-ir-remote/build
SDK=/tmp/opencode/android/sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export JAVA_HOME
export PATH="$JAVA_HOME/bin:/workspace/kingstrade/tools/bin:/usr/bin:/bin"

AAPT2=${AAPT2:-/workspace/kingstrade/tools/bin/aapt2}
ZIPALIGN=${ZIPALIGN:-/workspace/kingstrade/tools/bin/zipalign}
D8=$BT/d8
APKSIGNER=$BT/apksigner

# Release signing identity. Keystore is created here if missing; back it up
# so future updates can be signed with the same key (same application id).
KS=${KS:-/workspace/sony-bravia-ir-remote/sony-bravia-ir-remote.jks}
KS_ALIAS=sony-bravia-ir-remote
KSPASS=${KSPASS:-$(cat /workspace/sony-bravia-ir-remote/.ks-pass 2>/dev/null || echo sony-bravia-ir-remote)}
KS_KEYPASS=${KS_KEYPASS:-$KSPASS}

VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-1.0}

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes"

echo "== aapt2 compile =="
"$AAPT2" compile --dir "$APP/res" -o "$OUT/compiled/res.zip"

echo "== aapt2 link =="
"$AAPT2" link \
  -o "$OUT/app-unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 21 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$OUT/compiled/res.zip"

echo "== javac =="
find "$APP/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$PLATFORM" -classpath "$PLATFORM" \
  -d "$OUT/classes" @"$OUT/sources.txt" 2> "$OUT/javac.log" || {
    echo "javac failed:"; cat "$OUT/javac.log"; exit 1; }

echo "== d8 (release) =="
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$D8" --release --min-api 21 --lib "$PLATFORM" --output "$OUT" @"$OUT/classes.txt"

echo "== package dex into apk =="
cp "$OUT/app-unsigned.apk" "$OUT/app-withdex.apk"
( cd "$OUT" && zip -q -j app-withdex.apk classes.dex )

echo "== zipalign (4k pages) =="
"$ZIPALIGN" -f -p 4 "$OUT/app-withdex.apk" "$OUT/app-aligned.apk"

echo "== signing =="
if [ ! -f "$KS" ]; then
  echo "creating release keystore: $KS"
  "$JAVA_HOME/bin/keytool" -genkeypair -v -keystore "$KS" \
    -alias "$KS_ALIAS" -keyalg RSA -keysize 4096 -validity 10950 \
    -storetype PKCS12 \
    -storepass "$KSPASS" -keypass "$KS_KEYPASS" \
    -dname "CN=Sony Bravia IR Remote, OU=Mobile, O=SonyBraviaIR, L=Dhaka, C=BD" >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass "pass:$KSPASS" --key-pass "pass:$KS_KEYPASS" \
  --ks-key-alias "$KS_ALIAS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT/sony-bravia-ir-remote.apk" "$OUT/app-aligned.apk"

echo "== verify =="
"$APKSIGNER" verify --verbose --print-certs "$OUT/sony-bravia-ir-remote.apk" \
  | grep -E "Verified using|DN:|SHA-256 digest" | head -8
echo "APK: $OUT/sony-bravia-ir-remote.apk  $(wc -c < "$OUT/sony-bravia-ir-remote.apk") bytes"
echo "SHA-256: $(sha256sum "$OUT/sony-bravia-ir-remote.apk" | cut -d' ' -f1)"

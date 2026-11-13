#!/bin/sh
set -e
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)
AJ=$SDK/platforms/android-36/android.jar
JDK=${JDK:-${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}}
OUT=build
VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-0.1.$VERSION_CODE}
DEBUG_FLAG=--debug-mode
[ -z "${KEYSTORE_FILE:-}" ] || DEBUG_FLAG=
export JAVA_HOME=$JDK PATH=$JDK/bin:$PATH

rm -rf $OUT && mkdir -p $OUT/gen $OUT/classes $OUT/dex $OUT/test

$JDK/bin/javac -d $OUT/test src/com/github/ad/reeder/Logic.java test/LogicTest.java
$JDK/bin/java -ea -cp $OUT/test com.github.ad.reeder.LogicTest

$BT/aapt2 compile --dir res -o $OUT/res.zip
$BT/aapt2 link -o $OUT/app.unsigned.apk -I $AJ --manifest AndroidManifest.xml \
  --java $OUT/gen -A assets --min-sdk-version 26 --target-sdk-version 36 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" $DEBUG_FLAG $OUT/res.zip

$JDK/bin/javac -nowarn -Xlint:-options -source 11 -target 11 -cp $AJ -d $OUT/classes \
  $(find src $OUT/gen -name '*.java')

$BT/d8 --release --min-api 26 --lib $AJ --output $OUT/dex $(find $OUT/classes -name '*.class')
(cd $OUT/dex && zip -q ../app.unsigned.apk classes.dex)
$BT/zipalign -f 4 $OUT/app.unsigned.apk $OUT/app.aligned.apk
if [ -n "${KEYSTORE_FILE:-}" ]; then
  $BT/apksigner sign --ks "$KEYSTORE_FILE" --ks-pass env:KEYSTORE_PASSWORD --ks-key-alias "$KEY_ALIAS" \
    --key-pass env:KEY_PASSWORD --out $OUT/reeder.apk $OUT/app.aligned.apk
else
  DEBUG_KS="$HOME/.android/debug.keystore"
  [ -f "$DEBUG_KS" ] || { mkdir -p "$HOME/.android" && $JDK/bin/keytool -genkeypair -keystore "$DEBUG_KS" \
    -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1; }
  $BT/apksigner sign --ks "$DEBUG_KS" --ks-pass pass:android --out $OUT/reeder.apk $OUT/app.aligned.apk
fi
echo "built $OUT/reeder.apk"

if [ "$1" = install ]; then
  adb install -r $OUT/reeder.apk
  adb shell am start -n com.github.ad.reeder/.MainActivity
fi

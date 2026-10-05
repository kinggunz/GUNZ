#!/data/data/com.termux/files/usr/bin/bash
# Build Gunz Launcher langsung di Termux (butuh HP ARM64, internet, ~2-3 GB ruang kosong)
set -e
cd "$(dirname "$0")"
echo "== Gunz Launcher: build di Termux =="
pkg update -y
pkg install -y openjdk-17 wget unzip aapt2 git

export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p "$ANDROID_HOME/cmdline-tools"
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  wget -O "$HOME/cmdtools.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$HOME/cmdtools.zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -f "$HOME/cmdtools.zip"
fi

if [ ! -d "$ANDROID_HOME/platforms/android-34" ] || [ ! -d "$ANDROID_HOME/build-tools/34.0.0" ]; then
  yes | bash "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "platforms;android-34" "build-tools;34.0.0" || true
fi
if [ ! -d "$ANDROID_HOME/platforms/android-34" ]; then
  echo "GAGAL: Android SDK 34 belum terpasang. Cek koneksi internet lalu jalankan ulang."
  exit 1
fi

if [ ! -d "$HOME/gradle-8.9" ]; then
  wget -O "$HOME/gradle.zip" https://services.gradle.org/distributions/gradle-8.9-bin.zip
  unzip -q "$HOME/gradle.zip" -d "$HOME"
  rm -f "$HOME/gradle.zip"
fi
export PATH="$HOME/gradle-8.9/bin:$PATH"

echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleDebug --no-daemon -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2"

APK=app/build/outputs/apk/debug/app-debug.apk
cp "$APK" "$HOME/GunzLauncher.apk"
if [ -d "$HOME/storage/downloads" ]; then cp "$APK" "$HOME/storage/downloads/GunzLauncher.apk"; fi
echo "SELESAI! APK: ~/GunzLauncher.apk (dan Download jika termux-setup-storage sudah dijalankan)"

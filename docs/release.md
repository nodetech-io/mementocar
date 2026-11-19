# Building and validating a release

Run these steps from the repository root, with the JDK and Android SDK set up as described in the [README](../README.md#build).

## 1. Signing

Release builds are signed only when a local `release-signing.properties` file exists. Without it, `bundleRelease` produces an unsigned bundle and debug and CI builds work as usual.

Never commit keys or passwords. The repository ignores `release-signing.properties`, `*.jks`, `*.keystore`, `*.pem`, `*.p12` and `*.pfx`.

Generate an upload key outside the repository and keep it, with its passwords, in a password manager and a secure backup:

```sh
keytool -genkeypair -v \
  -keystore upload.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then copy [`release-signing.properties.sample`](../release-signing.properties.sample) to `release-signing.properties` in the repository root and fill in your key.

With Google Play App Signing, Play keeps the app signing key and this key is only used to upload. Forks must use their own key and `applicationId` (see [Forks](../README.md#forks)).

## 2. Version and release notes

Increase `versionCode` and `versionName` in `app/build.gradle.kts`, add the version to [CHANGELOG.md](../CHANGELOG.md), and write the store release notes in `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for every locale. `python3 scripts/check_listing.py` checks their length.

## 3. Build and test

```sh
./gradlew --no-daemon --max-workers=1 \
  testDebugUnitTest testReleaseUnitTest lintRelease bundleRelease assembleRelease
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Release unit tests are enabled explicitly, so both variants are covered. Reports are written to `app/build/reports/`.

Verify the signature and record the hash of the exact bundle you upload:

```sh
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
sha256sum app/build/outputs/bundle/release/app-release.aab
```

## 4. 16 KB page size

`scripts/check_16k.py` checks ELF64 LOAD segment alignment, RELRO and the ZIP offsets of uncompressed entries, using only the Python standard library. ELF32 libraries are reported and skipped.

```sh
python3 scripts/check_16k.py app/build/outputs/bundle/release/app-release.aab
```

AAB ZIP offsets are not the offsets of the installed APKs, so also check the APK set generated with the official [bundletool](https://github.com/google/bundletool/releases). Set `BUNDLETOOL_JAR` to the downloaded JAR and verify its checksum against the release page.

```sh
java -jar "$BUNDLETOOL_JAR" dump config \
  --bundle=app/build/outputs/bundle/release/app-release.aab
# Expect uncompressNativeLibraries.alignment = PAGE_ALIGNMENT_16K.

java -jar "$BUNDLETOOL_JAR" build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=build/app-release.apks --overwrite
python3 scripts/check_16k.py build/app-release.apks
```

The script checks every APK in the set and exits with code 1 on failure. As an independent check, run the SDK's `zipalign -c -P 16 4` on each extracted APK.

APKs built this way are signed with the local debug key. They contain the release code but do not validate the upload key; do not distribute them.

## 5. Run on a 16 KB device

Start an emulator with a 16 KB system image and confirm the page size:

```sh
adb -s "$ANDROID_SERIAL" shell getconf PAGE_SIZE
# Expect 16384.
```

On a dedicated test emulator, disable the compatibility mode so it does not hide problems. This affects the whole emulator session; do not do it on a personal phone.

```sh
adb -s "$ANDROID_SERIAL" shell setprop bionic.linker.16kb.app_compat.enabled false
adb -s "$ANDROID_SERIAL" shell setprop pm.16kb.app_compat.disabled true
```

Install the generated APKs, start the app, and run the instrumented tests:

```sh
java -jar "$BUNDLETOOL_JAR" install-apks \
  --apks=build/app-release.apks --device-id="$ANDROID_SERIAL"
adb -s "$ANDROID_SERIAL" shell am start -W -n io.nodetech.mementocar/.MainActivity
./gradlew --no-daemon --max-workers=1 connectedDebugAndroidTest
```

The instrumented tests install the debug build. An x86_64 emulator does not replace testing on arm64 hardware, or real Bluetooth, voice and battery-saving behaviour.

See also the [Android guide on 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes).

## 6. Language check

Before a release, go through the app with the device set to Spanish, English and Catalan:

- Settings, Reminders, Alerts, Status, backup and the voice confirmation dialog show no text in another language.
- Plurals read correctly with 1 and with 2 or more items: cars, reminders, open alerts, backup counts and the "+N more" notification line.
- At 1.6× font scale, long texts and the weekday buttons wrap or truncate without cutting buttons or the bottom navigation.
- Voice input in each language creates a reminder with the right trigger, and confirmation commands ("save", "cancel", "repeat", "change text", "on entry", "on exit") work. See [voice commands](voice-commands.md).
- Without a speech recognition model for the language, the app asks to install or enable one instead of failing silently. Without a text-to-speech voice, the confirmation dialog still shows the summary as text and keeps the "Reply by voice" button working.

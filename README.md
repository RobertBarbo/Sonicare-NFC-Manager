# Sonicare NFC Manager

**Version 1.0.0** · **Author: Robert Barbirič** · **License: MIT**

Sonicare NFC Manager is an unofficial, open-source Android application for reading and, after explicit confirmation, resetting the electronic usage counter on certain compatible Philips Sonicare BrushSync NFC brush heads. It is distributed directly through GitHub and is not published on Google Play.

> [!WARNING]
> Resetting the electronic counter does not restore or renew worn bristles. Replace a brush head whenever the bristles are worn, damaged or unhygienic, regardless of the displayed counter. Use the reset function only with your own compatible brush heads and at your own risk.

This project is not affiliated with, sponsored by or endorsed by Philips.

## Features

- Reads compatible NFC-A BrushSync tags and displays their UID, manufacturing code, used time and remaining time.
- Keeps scanning read-only unless the user explicitly confirms a reset.
- Requires the same brush head to be removed and scanned again before writing.
- Writes only the usage-counter bytes, preserves the remaining bytes and verifies the result by reading it back.
- Slovenian and English interface with a locally stored language choice.
- Automatic light or dark appearance based on the Android system theme.
- Local history of the last 20 scans and resets.
- Fully offline: no Internet permission, account, advertising, analytics or tracking.

## Requirements

- Android 8.0 (API 26) or newer.
- A phone with an enabled NFC-A reader.
- A compatible BrushSync NFC brush head. Tag layouts can differ, so unsupported tags are rejected rather than written.

## Install from GitHub

After release `v1.0.0` is published:

1. Open the release page for `v1.0.0` on this repository.
2. Download `Sonicare-NFC-Manager-v1.0.0.apk` from **Assets**.
3. Compare its SHA-256 checksum with the checksum shown in the release notes.
4. Open the APK on the Android phone and allow installation from that source when Android asks.

The signing certificate should remain the same for all future releases so Android can install updates without removing the existing app.

## Use

1. Enable NFC and open Sonicare NFC Manager.
2. Hold a supported brush head near the phone's NFC antenna.
3. Review the displayed usage and physical condition of the bristles.
4. To clear the electronic counter, tap **Reset counter** and confirm the warning.
5. Move the head away, then scan the same head again. The app makes one authenticated write attempt and verifies the result.

The supported layout is intentionally conservative: a seven-byte UID, a manufacturing code in the `YYMMDD NN[L]` format and a reported lifetime of 21,600 seconds. Do not repeatedly retry a failed authentication or write.

## Build from source

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9 and Java 17.

1. Clone the repository and open it in Android Studio.
2. Let Android Studio use the included Gradle wrapper and install Android SDK 35 if required.
3. Run the following from the project root:

   ```powershell
   .\gradlew.bat --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
   ```

4. The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

The debug APK is for development only and is signed with a local debug certificate.

## Create the signed release APK

The release APK must be signed locally. The private key and its passwords must never be committed, uploaded as workflow artifacts or stored in GitHub Actions.

1. In Android Studio, select **Build > Generate Signed App Bundle or APK**.
2. Select **APK**, then create or choose a private keystore stored outside this repository.
3. Keep at least two secure offline backups of the keystore and its passwords. Losing the key prevents signing compatible updates.
4. Select the `release` build variant and finish the wizard.
5. Copy the generated signed APK to a temporary release folder and name it `Sonicare-NFC-Manager-v1.0.0.apk`.
6. Verify the signature with the Android SDK tool:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs .\Sonicare-NFC-Manager-v1.0.0.apk
   ```

7. Generate the checksum for the GitHub release notes:

   ```powershell
   Get-FileHash .\Sonicare-NFC-Manager-v1.0.0.apk -Algorithm SHA256
   ```

Only the signed APK and its SHA-256 checksum are published. The keystore and passwords remain exclusively with the author.

## Continuous integration

`.github/workflows/android.yml` runs lint, local unit-test tasks and a debug build on pushes to `main`, pull requests and manual dispatches. It uploads the debug APK as a short-lived workflow artifact. CI intentionally does not create or sign the public release APK.

## Privacy

The app requests only the NFC permission and does not request Internet access. The selected language and up to 20 recent events (tag UID, manufacturing code, timestamp, usage and action) are stored in Android `SharedPreferences` on the device. They can be removed through **Android Settings > Apps > Sonicare NFC Manager > Storage > Clear data**.

## Acknowledgements and licensing

The password-generation method is credited to [@atc1441](https://gist.github.com/atc1441/41af75048e4c22af1f5f0d4c1d94bb56). [nicjes/SonicareGenerator](https://github.com/nicjes/SonicareGenerator) was also reviewed as a compatibility reference and credits the same method. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for provenance and licensing details.

The original code in this repository is available under the [MIT License](LICENSE). Copyright © 2026 Robert Barbirič.

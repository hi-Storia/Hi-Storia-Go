# Android Setup

## Build

Open the `android` directory in Android Studio, or build from the command line:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

The project uses:

- Kotlin + Jetpack Compose;
- Android Gradle Plugin 9.0.1;
- Kotlin 2.2.10;
- Media3 ExoPlayer;
- usb-serial-for-android.

## Device Requirements

- Android 8.0 or newer.
- USB host / OTG support.
- Enough storage for imported audio files.
- Stable power supply for long installations.

The project is intended to work well on refurbished smartphones. For museum/classroom installations, prefer devices that can:

- keep the screen on while powered;
- avoid aggressive battery optimization for the app;
- provide stable USB OTG behavior;
- play audio reliably through speaker, jack, USB audio or Bluetooth.

## Admin Access

The player home opens admin mode through repeated taps on the logo. The admin area contains:

- `Scheda elettronica`: USB/Arduino diagnostics and threshold controls;
- `Contenuti`: audio tracks and project content;
- `Smartphone`: idle/exploration/music settings and phone diagnostics.

## Audio Storage

Imported user audio is copied into the app private files directory. Default assets live in:

```text
android/app/src/main/assets/audio/
```

Public releases should only include neutral default assets. Project-specific audio should be imported on the target device or distributed separately.

## Project Transfer

`Progetto` → `Esporta progetto` / `Importa progetto` saves the whole project (profiles, languages,
series, mappings, labels, volumes, idle/exploration/presentation/background settings and imported
audio) into a single `nome-progetto.historia.zip` file and restores it on another phone through the
Storage Access Framework. Format, safety checks, rollback strategy and manual test steps are
described in [project-transfer.md](project-transfer.md).

Unit tests (Robolectric) can be run with:

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
```

## Release Notes

Current public version:

```text
0.1.0-alpha
```

This is not yet a Play Store ready release. Before production use:

- complete long-running hardware tests;
- verify USB stability on target phones;
- replace placeholder/default audio where needed;
- configure the release signing key and publish through GitHub Releases (see [release.md](release.md));
- review privacy policy and permissions.


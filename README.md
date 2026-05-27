# Hi-Storia Go

Hi-Storia Go is the 2026 mobile Android app for hi-Storia tactile audio devices.

The app turns an Android smartphone into the audio player for a tactile cultural-heritage model: an Arduino-compatible board reads capacitive/tactile activators and sends serial events such as `T1`, `T2`, `T3`. The Android app receives those events over USB and plays the audio track associated with each activator.

This version is designed with two goals:

- make hi-Storia installations easier to configure from the phone itself;
- support reuse and refurbishment of older Android smartphones as dedicated cultural-accessibility players.

## Project Status

**Alpha.**

The core Android player, USB serial protocol, project editor, audio import, exploration mode, idle track, presentation track and admin diagnostics are implemented. The project is not yet beta because long-running hardware stability on Arduino UNO R4 touch sensing is still being tested.

Use this repository as a working public alpha, not as a final production release.

## Features

- USB serial connection to an Arduino/hi-Storia tactile board.
- Audio playback by activator signal (`T1`, `T2`, ...).
- Project editor for cultural asset, school, profiles, languages and number of tracks.
- Audio import from the phone for activator tracks.
- Per-track custom labels and volume reduction.
- Idle track (`track 0`) after inactivity.
- Exploration / silent mode with intro audio and beep feedback.
- Optional presentation track from the player home.
- Optional background music with ducking under spoken tracks.
- Admin area with board diagnostics and smartphone diagnostics.
- Designed for unattended or semi-attended use on reused Android phones.

## Repository Layout

```text
android/      Android app source code
arduino/      Reference Arduino sketches
docs/         Technical notes and setup guides
```

Temporary material, experiments, build outputs and private test assets are intentionally not part of the public repository.

## Android Requirements

- Android Studio with JDK 17.
- Android SDK 35.
- Minimum Android API: 26.
- USB host support on the target phone.

Build from the `android` directory:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS/Linux:

```bash
./gradlew :app:assembleDebug
```

## Arduino Requirements

The current stable test sketch uses Arduino UNO R4 + `R4_Touch` in single-scan mode.

Required Arduino library:

- R4_Touch: https://github.com/delta-G/R4_Touch

Reference sketch:

```text
arduino/historia_touch_r4_single_scan/historia_touch_r4_single_scan.ino
```

The sketch emits raw sensor lines and player events over USB serial at 115200 baud.

## Serial Protocol

Arduino to Android:

```text
T1
T2
T3
MSG:HI-STORIA READY
S1:5400    S2:5200    S3:5300
```

Android to Arduino:

```text
Z
SET:6500
PING
```

See [docs/protocol.md](docs/protocol.md).

## Default Content

The app includes neutral default audio assets for first-run use:

- sample track;
- idle track;
- exploration intro and beep;
- background music.

Final installations should normally replace these with project-specific audio from the admin area.

See [docs/assets.md](docs/assets.md) before publishing or adding new media files.

## License

European Union Public Licence v1.2 (EUPL-1.2). See [LICENSE](LICENSE).

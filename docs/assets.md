# Assets

The Android app includes a small set of neutral default assets under:

```text
android/app/src/main/assets/audio/
android/app/src/main/res/drawable/
```

Before publishing a public repository or release, verify that every included asset is redistributable under the project license or under a compatible license.

Recommended policy:

- keep only neutral onboarding/default audio in the public repository;
- do not commit project-specific museum/classroom audio unless it is explicitly licensed for redistribution;
- do not commit private test recordings;
- document third-party asset licenses if any are added;
- import project audio on the target device through the admin UI.

Default audio files currently included:

```text
audio/default/tracks/00.mp3
audio/idle/default/idle.mp3
audio/exploration/default/m.wav
audio/exploration/default/s.wav
audio/background/default/santelena.mp3
```


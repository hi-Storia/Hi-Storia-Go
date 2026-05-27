# Arduino / Touch Board

Hi-Storia Go expects a touch board that sends simple serial events over USB.

The current stable test setup is:

- Arduino UNO R4;
- R4_Touch library;
- single-scan mode;
- 115200 baud USB serial;
- five activators on pins `2, 3, 8, 9, 11`.

Reference sketch:

```text
arduino/historia_touch_r4_single_scan/historia_touch_r4_single_scan.ino
```

## Required Library

Install:

```text
R4_Touch
https://github.com/delta-G/R4_Touch
```

## Touch Pins

The default sketch uses:

```cpp
const uint8_t pinSensori[] = {2, 3, 8, 9, 11};
```

These pins are supported by R4_Touch on UNO R4 Minima and UNO R4 WiFi.

## Threshold

The default threshold is:

```cpp
uint16_t soglia = 6500;
```

The Android app can send:

```text
SET:6500
```

to update the board threshold.

## End-of-Track Reset

The Android app sends:

```text
Z
```

when a track ends or is stopped. The board uses this to clear `tracciaAttiva`, allowing the same activator to trigger again.

## Hardware Stability Notes

Long-running stability depends on:

- stable USB power;
- short and clean wiring;
- correct capacitive touch setup;
- reliable ground/reference layout;
- avoiding overly large or noisy conductive surfaces.

If raw values freeze or repeat identically, test:

1. a single pin;
2. one sensor at a time;
3. the same sensor on different pins;
4. the board without the final 3D model;
5. direct USB connection without splitters or chargers.

The single-scan sketch is currently preferred because it avoids the free-running CTSU behavior that showed freezes during testing.

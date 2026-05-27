# Serial Protocol

The Android app and Arduino board communicate over USB serial at 115200 baud.

Lines should end with CRLF or LF.

## Arduino to Android

### Touch Event

```text
T1
T2
T3
```

`Tn` means activator `n` was touched. The Android app maps this signal to the selected audio track.

### Message

```text
MSG:HI-STORIA READY
MSG:RESET OK
MSG:Soglia salvata a 6500
```

Messages are shown in diagnostics and may update UI state.

### Diagnostic Raw Values

```text
S1:5411    S2:5205    S3:5180    S4:5292    S5:5567
```

Raw values are displayed in the admin board monitor. The app does not use these values to trigger audio.

## Android to Arduino

### End Track

```text
Z
```

Clears the currently active track on the board. This lets the same activator be triggered again after audio playback ends.

### Set Threshold

```text
SET:6500
```

Updates the touch threshold on the board.

### Ping

```text
PING
```

Optional diagnostic command. The board can respond with:

```text
PONG
```

## Design Notes

The board should avoid sending rapid repeated `Tn` events for noisy or alternating touches. The app is also expected to be defensive, but the board should keep the protocol clean:

- send one `Tn` per activator change;
- wait for `Z` before allowing the same activator again;
- debounce noisy touches;
- keep diagnostic raw value output separate from touch events.


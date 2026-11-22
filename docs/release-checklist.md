# Release Checklist

Before publishing a public release:

- [ ] Build `:app:assembleDebug`.
- [ ] Run `:app:testDebugUnitTest`.
- [ ] Export a project on one phone and import it on another (see `docs/project-transfer.md`).
- [ ] Verify no build outputs are tracked.
- [ ] Verify `.temp/` is not tracked.
- [ ] Verify no private test audio is tracked.
- [ ] Verify default audio assets are neutral and redistributable.
- [ ] Verify logo/image assets are redistributable.
- [ ] Verify Arduino sketch matches the documented hardware.
- [ ] Test USB permission flow.
- [ ] Test audio import from local storage.
- [ ] Test all activators.
- [ ] Run at least a one-hour stability test on the target phone and board.
- [ ] Decide Android signing strategy.
- [ ] Decide whether the release is GitHub-only, APK download, F-Droid candidate or Play Store candidate.

Current maturity:

```text
0.1.0-alpha
```

Reason: Android app features are implemented, but long-running hardware stability on the Arduino touch board is still being tested.

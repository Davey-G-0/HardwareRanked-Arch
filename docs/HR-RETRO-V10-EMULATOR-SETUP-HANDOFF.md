# HR Retro v10 emulator setup handoff

## What changed

This pass adds a reviewed external-emulator setup flow to the existing HR Retro Android frontend. The source catalog is curated; Azahar downloads are pinned to an official GitHub release asset and checked against its published SHA-256 digest. Dolphin is documented as a manual install from its official site. The app can also inspect an APK selected from local storage, show package/version/SDK/ABI and checksum information, stage the reviewed bytes in app-private read-only storage, recheck them, then invoke Android's user-confirmed package installer. It does not silently install APKs or fetch arbitrary APKs.

The flow adds emulator availability/setup guidance, including PS2 BIOS guidance and existing controller, save-state, fast-forward, and Start+Select help. ROM scanning continues to use the user-selected folder. Existing game files, artwork, cores, and BIOS data are not changed by this installer feature.

## Build and checks

- Built `assembleAarch64Debug` with Android Gradle Plugin using Android NDK `29.0.14206865`.
- Build result: successful. The APK contains ARM64 and x86_64 RetroArch native libraries.
- Source/XML check: passed.
- External emulator catalog tests: 17 checks passed.
- Reviewed APK staging tests: 20 checks passed.
- Setup guide tests: 9 checks passed.
- Existing core catalog tests: 15 checks passed.
- Existing ROM classifier tests: 23 checks passed.
- Existing C handheld model test: passed.
- Android Java sources compiled against API 36 with generated resource/build-config stubs.
- APK package/SDK metadata inspection, 16 KB zip alignment, and `apksigner verify`: passed.
- `git diff --check`: passed.
- No physical device or emulator install/play test was performed.

## v10 build artifact

- File: `HR-Retro-v10-emulator-setup-arm64-debug.apk`
- Package: `com.hardwareranked.retro.aarch64`
- Version name: `1.22.2_GIT`
- Version code: `1794787093` (time-derived by the current build)
- Minimum SDK: 21; target SDK: 36
- SHA-256: `025f3de378552ad9a1d2a2309419e7f130419029dda68438ccda34231f2c796a`
- This is a debug-signed APK. It may not update an installation signed with a different key. Because the version code is time-derived, rebuilding later may produce a different version code and APK bytes.

## Original v9 CI artifact

The original v9 artifact is retained separately and was not rebuilt or relabeled as v10.

- Workflow run: [42688435846](https://github.com/Davey-G-0/HardwareRanked-Arch/actions/runs/42688435846)
- Source commit: `25a8131934bf68ec4fc6a700ecccf6925b3c435b`
- Original artifact ZIP: `HR-Retro-v9-CI-run-42688435846.zip`
- ZIP SHA-256: `572cde519c707a1f88e19b1b414dc16cfe814e160bda8a5c18b02118169eeb29`
- Extracted original APK: `HR-Retro-v9-CI-run-42688435846.apk`
- APK SHA-256: `cce1402517706c1fdbd2abe043107a7c18161386b9d481ff6c1f16d96e44912b`
- GitHub artifact expiration: 2026-11-23 20:00:05 UTC

## Rebuild

From `pkg/android/phoenix`, install Android NDK `29.0.14206865` and run:

```sh
./gradlew --no-daemon assembleAarch64Debug
```

The local debug APK is written to `build/outputs/apk/aarch64/debug/phoenix-aarch64-debug.apk`.

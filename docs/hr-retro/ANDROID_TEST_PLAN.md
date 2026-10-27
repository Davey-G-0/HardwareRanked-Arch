# HR Retro — Android APK Test Checklist

1. In `Davey-G-0/HardwareRanked-Arch`, open the **Actions** tab. If Actions is disabled on the fork, enable workflows for **this repository only**.
2. Run the **HR Retro Android APK** workflow against `feature/hardware-ranked-android-v0`, or open the automatic run triggered by a commit.
3. Confirm the `build-arm64` job turns green. Download the artifact named `HR-Retro-Android-ARM64-...`, unzip, and locate `phoenix-aarch64-debug.apk`.
4. On an ARM64 Android handheld, backup existing save files before sideloading the debug APK. The test app should install independently of standard RetroArch.
5. Boot and inspect yellow/black UI, controller navigation, game/core loading and content scanning.
6. In game, use Pause/Quick Menu: Resume, Save State, Load State, Core Options, Controls, shader access and Exit Game. Confirm Exit Game is last.
7. Verify `com.hardwareranked.retro.aarch64` with Android app details or `adb shell pm list packages`.
8. Test touch gestures, portrait and landscape mode, hardware back, gamepad remapping, audio and storage permissions.
9. If crashes occur, capture device model, Android version, steps, logcat output. Never share personal ROMs, BIOS or signing credentials.

These builds are DEBUG ONLY. They are not verified, signed-for-release, or fit for public distribution.
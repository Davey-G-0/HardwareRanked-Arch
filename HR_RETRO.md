# HR Retro — Android fork (early development)

An independent Hardware Ranked–branded fork of RetroArch. This work is isolated from the separate HardwareRanked website repositories.

## First development changes
- Android application ID: `com.hardwareranked.retro` (intended to install independently).
- Android launcher label: `HR Retro`.
- Simplified Android defaults: advanced settings and full file paths hidden by default, and specialized Quick Menu actions (recording, streaming, core associations) hidden by default.
- Emulation cores and runtime are unchanged in this commit.

These are initial code changes **not yet built or tested on hardware**. This is not the completed redesigned menu.

## Immediate priorities
1. Set up Android CI and compile a debug APK; fix any applicationId-specific integration assumptions.
2. Implement Hardware Ranked yellow `#FFD90F` and black native MaterialUI/Ozone themes.
3. Implement gamepad-first Quick Menu layout: Resume, Save, Load, Display, Controls, Game Options, Exit.
4. Verify touchscreen, portrait/landscape and controller behavior.
5. Audit licensing (RetroArch GPLv3, independent libretro core licenses) and retain attribution.

Upstream: https://github.com/libretro/RetroArch

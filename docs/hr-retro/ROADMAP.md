# HR Retro — Android Completion Plan

Repository: `Davey-G-0/HardwareRanked-Arch`. Independent from all HardwareRanked website repositories.
Upstream: `libretro/RetroArch` (GPLv3). Never commit to the website repos.

## Implemented on development branch
- Independent Android application ID and HR Retro app label.
- Hardware Ranked yellow (`#FFD90F`) and dark native Ozone / MaterialUI palettes.
- Simpler Android defaults, direct Quick Menu save/load, and larger Ozone text and spacing.
- Exit Game moved to the bottom of the Quick Menu on Android.
- Initial launcher icon and Android TV banner.
- CI workflow for ARM64 APK builds and source regression checks.

## Gate A — Working Android APK
- Enable GitHub Actions in this fork if workflows are disabled after forking.
- Run `HR Retro Android APK` on `feature/hardware-ranked-android-v0`.
- Fix native C and Gradle failures, validate ARM64 libraries and package identity.
- Download the GitHub Actions APK artifact. Do not call this a release until tests pass.

## Gate B — Device usability
- Android handheld with built-in gamepad: D-pad, face buttons, L/R shoulders, Start and Back.
- Android touchscreen phone: tap, swipes, portrait/landscape, permission dialogs.
- Boot, core downloading, game loading, in-game Quick Menu, Save/Load, shader options, Controls, Resume and Exit Game.
- Stock RetroArch and HR Retro must coexist without app-data or save conflicts.
- Confirm controller mappings, B/A swap preference, safe backup/restore, and accessibility.
- Validate Android TV visuals, navigation and banner.

## One-folder Android setup — source implemented, hardware test pending
- Java first-run screen: one folder selection via Android's standard DocumentsUI.
- Read-only recursive SAF scan and extension/folder-based console classification.
- App-owned HR playlists generated without changing original game files or existing custom playlists.
- Android default menu categories reduced and all-files startup permission replaced by folder permission.
- Host-side Java classification checks and Android APK CI verify the first implementation.
- See docs/hr-retro/ONE_FOLDER_SETUP.md for the user's three-step walkthrough.
- **Remaining:** verify picked games launch via saf:// on Odin 3, validate SD-card permissions and rescans, then add automatic core suggestions.

## Gate C — Full visual product (NOT implemented yet)
- Native responsive six-action pause layout (Resume, Save, Load, Display, Controls, Exit).
- Compact, touch-safe settings UI with understandable option names and full Advanced escape hatch.
- Polish first-run error/empty states, add core suggestions and installation guidance, and improve game metadata.
- Strong gamepad focus indicators, predictable back navigation, adaptive screen layouts.
- UI regression tests and controller/touch acceptance tests.

## Gate D — Distribution
- Audit each libretro core license, preserve RetroArch source attribution and GPLv3 compliance.
- Supply full corresponding source for distributed binaries; omit ROMs/BIOS.
- Set separate Android app signing/upgrade strategy, versioning and release channels.
- Publish opt-in beta, then stable after a complete test matrix.

Stop condition: no stable APK release until successful CI, device testing and licensing review.
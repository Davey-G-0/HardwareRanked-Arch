# HR Retro v4 — Scan, Install Emulators, Play

This update is built on the verified one-folder Android setup and focuses on making games the home screen instead of RetroArch technical menus.

## First launch
1. Tap **Choose Game Folder** in the Android picker. Your ROM collection stays in its original location, including an SD card.
2. The scanner sorts game files by console and builds read-only, app-owned system playlists. Matching CUE/CHD/M3U formats with the same title are deduplicated; multi-disc lists take precedence when present.
3. Tap **Start HR Retro**. HR Retro opens a full-width **Games** library, not the technical main menu.
4. HR Retro checks which supported console cores your library needs. You can approve all suggested downloads at once from the official Libretro ARM64 buildbot. Nothing downloads without approval.
5. After installation, choose a game and press **A** (or tap it) to launch directly with its saved core. You don't have to select a core again.

## Controller navigation
- **L1/R1:** Switch between All Games and short, readable console tabs
- **D-pad:** Move through games or buttons
- **A:** Play selected game or activate focused action
- **B:** Exit the library
- **ADD GAMES:** Choose another folder and rescan
- **EMULATORS:** Install missing supported cores later
- **ADVANCED:** Enter the original RetroArch menus for power-user settings

## Compatibility and limitations
- This is a **development/test build**, not a stable release. Please back up saves and settings before replacing a previous debug APK.
- Automatic setup is intentionally limited to an explicit catalog of officially available ARM64 Libretro cores (Game Boy/Game Boy Color, GBA, NES, SNES, N64, DS, Genesis, Game Gear, Master System, Dreamcast, PlayStation, PSP, PC Engine, Neo Geo Pocket, WonderSwan and Atari 2600).
- Systems without a curated install recipe, including GameCube, Wii, PlayStation 2, 3DS and Arcade, remain visible but require manual/emulator-specific setup. Some systems need legal BIOS or other files that HR Retro does **not** download.
- Each core carries its own upstream license. HR Retro does not bundle cores, ROMs or BIOS; the user must approve core downloads. Core binaries are downloaded over HTTPS from Libretro, checked for ARM64 ELF format and installed to app-owned storage.
- The source and Android build can be tested automatically; **on-device Play launch, save-state compatibility and shader/UI flow still require Odin 3 testing.**
- Core binaries and metadata may update upstream without notice, so downloads can fail; the library remains usable for supported already-installed cores.
- Multi-disc detection and duplicate removal are conservative, using matching game labels. The original files are never edited.
- The custom game library is an Android Java interface; native RetroArch's menu and familiar pixel-font appearance remain accessible under Advanced.

## Development
- Feature branch: `feature/hr-retro-core-library`
- Base branch: `feature/hr-retro-one-folder-setup`
- The separate HardwareRanked website repository must remain untouched.

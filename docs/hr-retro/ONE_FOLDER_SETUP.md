# HR Retro — Set up a new Android handheld

Your game files can stay wherever you already keep them. HR Retro's setup finds and sorts them without making you rename or move folders.

## The whole setup

1. **Open HR Retro.** On a new setup, you'll see **Your games. One folder.**
2. Select **Choose Game Folder**. Android opens its normal folder picker. Navigate to your main Games or ROMs folder (internal storage or SD card), then choose **Use this folder** and approve read access.
3. Wait while HR Retro scans your games. You'll see how many files it checked and how many games were found.
4. Select **Start HR Retro**. Your system playlists should appear in the native menu. Use your Odin 3 controller or touchscreen to open them.

Example of a folder you can select:

    Games/
      Pokemon Emerald.gba
      Zelda - Oracle of Ages.gbc
      SNES/
        Super Metroid.sfc
        Chrono Trigger.zip
      PS1/
        Metal Gear Solid.chd
      Arcade/
        sf2.zip

You only select the outer **Games** folder. HR Retro recognizes Game Boy Advance, Game Boy Color, SNES, PlayStation and Arcade without you arranging files in a specific way.

## What HR Retro does (and doesn't do)

- Keeps every game in its original location. It **does not** move, rename, delete, copy or upload your ROMs.
- Groups recognizable game files by console using extensions and familiar folder names.
- Puts ambiguous archives/disc files without a clear system folder into **HR - Needs Review**. It will not guess a console incorrectly.
- Skips BIOS, save files, documentation and unsupported file types.
- Creates only its own system playlists. Your existing custom RetroArch playlists are not touched.
- Remembers permission to read your selected folder so you don't have to pick it every time.

## Important: emulator cores

The first-run importer sorts your **game library**. It does **not** yet install emulator cores automatically. If a game cannot start because no core is installed, use **Online Updater > Core Downloader** or choose a compatible installed core. Automatic core recommendations are on the roadmap.

## Choose a different folder or rescan

On supported Android launchers, **long-press the HR Retro icon** and choose **Manage Games**. Select a replacement Games folder and rescan. This updates HR Retro-generated playlists; it leaves your ROMs and user-created playlists alone.

If the Android picker refuses to let you choose a storage root, make or select a dedicated Games or ROMs folder *within* the storage location. Android 11 and later restrict broad access to storage roots.

## Odin 3 early test

The controller and earlier HR Retro debug build were confirmed working on an Odin 3. The new one-folder wizard still needs device testing. Please test one folder on internal storage and one on SD card, then verify the listed games load. Back up your saves before switching debug builds.

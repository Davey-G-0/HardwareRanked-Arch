# HR Retro — One-folder importer technical design

## Contract

1. The original launcher alias points to a lightweight, Android-native first-run activity. The existing RetroActivityFuture NativeActivity stays untouched as the emulator backend.
2. A single Android ACTION_OPEN_DOCUMENT_TREE read-only grant is stored with takePersistableUriPermission. The app does not request MANAGE_EXTERNAL_STORAGE.
3. A worker thread queries DocumentsContract for file and folder names. It never reads or edits ROM bytes and never moves/renames/deletes files in the user's selected tree.
4. Unambiguous extensions determine the system. Where an archive/disc extension is shared, the nearest recognized parent system folder determines the system. Otherwise classify as Needs Review.
5. System lists use RetroArch JSON playlist format 1.5. Item paths encode a full document URI using the upstream saf:// URI serialization format, so Android's existing SAF VFS opens each game by URI rather than a brittle storage filesystem path.
6. The stable playlist directory defaults to the native app's files/playlists directory, matching Java getFilesDir()/playlists. Existing configured playlist_directory paths from retroarch.cfg remain authoritative.
7. Only file names beginning with 'HR - ' and ending in '.lpl' may be replaced/pruned. All other playlists remain untouched. Pruning is suppressed for scans that reached file/depth limits.
8. The first-run screen stores a completed flag, last selected folder URI, and game count. On supported Android launchers, the dynamic Manage Games shortcut reopens the importer for a rescan.
9. No core or BIOS is downloaded or installed; core_path DETECT leaves core selection to the existing RetroArch backend.
10. Android's prior broad all-files startup prompt is bypassed for the HR Retro application ID. SAF tree access is independent from broad storage permissions.

## Tests and remaining engineering

- A host-side Java classifier test verifies known consoles, ambiguous archives, ignored sidecar files and non-game extensions.
- The existing source/XML regression checker verifies launcher routing, SAF permission and independent playlist locations.
- The dedicated GitHub Android CI workflow must compile and verify an ARM64 debug APK.
- Validate on Odin 3: first install, permission persistence across restart, 100+ games, one zipped game, one CHD game, SD card tree, no ROM changes, correct systems, rescan and cancellation.
- Confirm app-generated playlists appear on the native menu, with correct core prompts and working file opens through saf://.
- Future work: automatic core suggestions/download approval, deduplicate multi-disc playlists, preview metadata/artwork, first-run localization and dedicated native six-action menu layout.

## Limits in this initial implementation

- 25,000 documents visited, maximum 16 folder levels, maximum 20,000 candidate games per scan.
- Loose ZIP/7z and ambiguous disc images without a known system folder go into Needs Review; no archive inspection or ROM hashing yet.
- The importer does not alter existing RetroArch configuration, including custom playlist paths.
- Debug build signing is not release-stable yet; do not rely on in-place upgrading across separate GitHub Actions runs.

Security: treat the scanned content as untrusted names; never execute files, scripts or downloaded code from game directories. Preserve RetroArch GPLv3 source attribution and audit each libretro core license before distribution.

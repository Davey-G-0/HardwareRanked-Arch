package com.retroarch.browser.retroactivity;

final class HRRetroGuides {
    static final String BIOS = "HR Retro doesn't include or download BIOS or firmware. For RetroArch cores, open Settings → Directory → System/BIOS and select the folder containing your own legally dumped files. Standalone emulators manage BIOS in their own settings; select the file from SD during that emulator's setup. You can return here and do this later.";

    static final String CONTROLS = "During a game, press START + SELECT once to open RetroArch's in-game menu. Quick Menu → Save States has Save State, Load State, and state-slot options. For controller shortcuts, open Settings → Input → Hotkeys, assign Hotkey Enable, then bind Save State, Load State, or Fast-Forward (Hold) to buttons you choose. Three quick START + SELECT chords return to HR Retro.";

    static String ps2(boolean installed) {
        return "PS2 games need a separate Android PS2 emulator and a PS2 BIOS file you legally dumped from your own console. HR Retro doesn't include or download BIOS files. "
            + (installed
                ? "Open the installed PS2 app and use its first-run BIOS setup to select the BIOS file from your SD card."
                : "First install a compatible Android PS2 emulator from a source you trust. On its first run, choose BIOS setup and select your BIOS file from the SD card.")
            + " Then add the PS2 games folder inside that emulator. HR Retro keeps the scanned games in your library, but doesn't yet launch PS2 files directly into the separate app.";
    }

    private HRRetroGuides() {}
}

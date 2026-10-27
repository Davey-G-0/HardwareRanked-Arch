/*
 * HR Retro game system classifier. Pure Java for host-side tests.
 * GPL-3.0-or-later. Copyright (C) 2026 HR Retro contributors.
 */
package com.retroarch.browser.retroactivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class HRGameLibraryClassifier {
    private static final Map<String, String> FOLDERS = new HashMap<>();
    private static final Map<String, String> EXTENSIONS = new HashMap<>();
    private static final Set<String> AMBIGUOUS = new HashSet<>();

    private static void folder(String system, String... aliases) {
        for (String alias : aliases)
            FOLDERS.put(normalize(alias), system);
    }

    private static void extensions(String system, String... ext) {
        for (String value : ext)
            EXTENSIONS.put(value, system);
    }

    static {
        folder("Nintendo - Game Boy Advance", "gba", "game boy advance", "gameboyadvance", "nintendo game boy advance");
        folder("Nintendo - Game Boy Color", "gbc", "game boy color", "gameboycolor", "nintendo game boy color");
        folder("Nintendo - Game Boy", "gb", "game boy", "gameboy", "nintendo game boy");
        folder("Nintendo - Nintendo Entertainment System", "nes", "famicom", "nintendo entertainment system", "nintendo nes");
        folder("Nintendo - Super Nintendo Entertainment System", "snes", "super nintendo", "super famicom", "super nintendo entertainment system", "sfc");
        folder("Nintendo - Nintendo 64", "n64", "nintendo 64", "nintendo64");
        folder("Nintendo - Nintendo DS", "nds", "nintendo ds", "nintendo ds");
        folder("Nintendo - Nintendo 3DS", "3ds", "nintendo 3ds");
        folder("Nintendo - GameCube", "gamecube", "gc", "ngc", "nintendo gamecube");
        folder("Nintendo - Wii", "wii", "nintendo wii");
        folder("Sega - Mega Drive - Genesis", "genesis", "megadrive", "mega drive", "sega genesis", "sega mega drive");
        folder("Sega - Master System - Mark III", "sms", "master system", "sega master system");
        folder("Sega - Game Gear", "gg", "game gear", "sega game gear");
        folder("Sega - Saturn", "saturn", "sega saturn");
        folder("Sega - Dreamcast", "dreamcast", "dc", "sega dreamcast");
        folder("Sony - PlayStation", "ps1", "psx", "playstation", "playstation 1", "sony playstation");
        folder("Sony - PlayStation 2", "ps2", "playstation 2", "sony playstation 2");
        folder("Sony - PlayStation Portable", "psp", "playstation portable", "sony psp");
        folder("NEC - PC Engine - TurboGrafx 16", "pce", "pcengine", "pc engine", "turbografx16", "tg16");
        folder("SNK - Neo Geo Pocket", "ngp", "neo geo pocket");
        folder("SNK - Neo Geo Pocket Color", "ngpc", "neo geo pocket color");
        folder("Bandai - WonderSwan", "ws", "wonderswan");
        folder("Bandai - WonderSwan Color", "wsc", "wonderswan color");
        folder("Atari - 2600", "atari2600", "atari 2600");
        folder("Arcade", "arcade", "mame", "fbneo", "final burn neo");

        extensions("Nintendo - Game Boy Advance", "gba");
        extensions("Atari - 2600", "a26");
        extensions("Nintendo - Game Boy Color", "gbc");
        extensions("Nintendo - Game Boy", "gb");
        extensions("Nintendo - Nintendo Entertainment System", "nes");
        extensions("Nintendo - Super Nintendo Entertainment System", "sfc", "smc");
        extensions("Nintendo - Nintendo 64", "n64", "z64", "v64");
        extensions("Nintendo - Nintendo DS", "nds");
        extensions("Nintendo - Nintendo 3DS", "3ds", "cci");
        extensions("Nintendo - Nintendo Entertainment System", "nes", "fds");
        extensions("Nintendo - Nintendo 3DS", "3ds", "cci");
        extensions("Sega - Mega Drive - Genesis", "md", "gen", "smd");
        extensions("Sega - Master System - Mark III", "sms");
        extensions("Sega - Game Gear", "gg");
        extensions("NEC - PC Engine - TurboGrafx 16", "pce", "sgx");
        extensions("SNK - Neo Geo Pocket", "ngp");
        extensions("SNK - Neo Geo Pocket Color", "ngc");
        extensions("Bandai - WonderSwan", "ws");
        extensions("Bandai - WonderSwan Color", "wsc");

        Collections.addAll(AMBIGUOUS,
            "zip", "7z", "chd", "cue", "m3u", "iso", "cso", "pbp",
            "gdi", "cdi", "gcm", "rvz", "wbfs", "wad");
    }

    static String normalize(String value) {
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
    }

    static String folderSystem(String folderName) {
        return FOLDERS.get(normalize(folderName));
    }

    static String systemFor(String filename, String folderSystem) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot + 1 == filename.length())
            return null;
        String ext = filename.substring(dot + 1).toLowerCase(Locale.US);
        String known = EXTENSIONS.get(ext);
        if (known != null)
            return known;
        if (AMBIGUOUS.contains(ext))
            return folderSystem != null ? folderSystem : "Needs Review";
        return null;
    }


    private HRGameLibraryClassifier() {}
}

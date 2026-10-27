/*
 * HR Retro — curated, explicit ARM64 Libretro core suggestions.
 * Core packages are NOT bundled. The user must approve each batch.
 * Core licenses differ from RetroArch and require separate review.
 *
 * Copyright (C) 2026 HR Retro contributors. GPL-3.0-or-later.
 */
package com.retroarch.browser.retroactivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HRCoreCatalog {
    static final class Core {
        final String id;
        final String display;
        final String supportNote;
        Core(String id, String display, String supportNote) {
            this.id = id;
            this.display = display;
            this.supportNote = supportNote;
        }
        String filename() {
            return id + "_libretro_android.so";
        }
        String infoName() {
            return id + "_libretro.info";
        }
    }

    private static final Map<String, Core> BY_SYSTEM = new LinkedHashMap<>();
    private static final Map<String, String> SHORT_NAMES = new LinkedHashMap<>();

    private static void core(String system, String id, String name, String note) {
        BY_SYSTEM.put(system, new Core(id, name, note));
    }

    private static void shortName(String full, String shortLabel) {
        SHORT_NAMES.put(full, shortLabel);
    }

    static {
        core("Nintendo - Game Boy", "gambatte", "Gambatte", "");
        core("Nintendo - Game Boy Color", "gambatte", "Gambatte", "");
        core("Nintendo - Game Boy Advance", "mgba", "mGBA", "");
        core("Nintendo - Nintendo Entertainment System", "nestopia", "Nestopia UE", "");
        core("Nintendo - Super Nintendo Entertainment System", "snes9x", "Snes9x", "");
        core("Nintendo - Nintendo 64", "mupen64plus_next_gles3", "Mupen64Plus-Next", "Uses GLES3.");
        core("Nintendo - Nintendo DS", "melondsds", "melonDS DS", "Some titles need additional BIOS/firmware.");
        core("Sega - Mega Drive - Genesis", "genesis_plus_gx", "Genesis Plus GX", "");
        core("Sega - Master System - Mark III", "genesis_plus_gx", "Genesis Plus GX", "");
        core("Sega - Game Gear", "genesis_plus_gx", "Genesis Plus GX", "");
        core("Sega - Dreamcast", "flycast", "Flycast", "Some titles need BIOS or special content files.");
        core("Sony - PlayStation", "pcsx_rearmed", "PCSX ReARMed", "A legally obtained BIOS may improve compatibility.");
        core("Sony - PlayStation Portable", "ppsspp", "PPSSPP", "May require additional PPSSPP assets.");
        core("NEC - PC Engine - TurboGrafx 16", "mednafen_pce_fast", "Beetle PCE Fast", "");
        core("SNK - Neo Geo Pocket", "mednafen_ngp", "Beetle NeoPop", "");
        core("SNK - Neo Geo Pocket Color", "mednafen_ngp", "Beetle NeoPop", "");
        core("Bandai - WonderSwan", "mednafen_wswan", "Beetle Cygne", "");
        core("Bandai - WonderSwan Color", "mednafen_wswan", "Beetle Cygne", "");
        core("Atari - 2600", "stella", "Stella", "");

        // Unsupported/unverified on this app's Android target intentionally
        // have no auto-download suggestion (e.g. GameCube/Wii/PS2/3DS,
        // Saturn, Arcade). Manual/advanced setup remains available.

        shortName("Nintendo - Game Boy", "Game Boy");
        shortName("Nintendo - Game Boy Color", "Game Boy Color");
        shortName("Nintendo - Game Boy Advance", "Game Boy Advance");
        shortName("Nintendo - Nintendo Entertainment System", "NES");
        shortName("Nintendo - Super Nintendo Entertainment System", "Super Nintendo");
        shortName("Nintendo - Nintendo 64", "Nintendo 64");
        shortName("Nintendo - Nintendo DS", "Nintendo DS");
        shortName("Nintendo - Nintendo 3DS", "Nintendo 3DS");
        shortName("Nintendo - GameCube", "GameCube");
        shortName("Nintendo - Wii", "Wii");
        shortName("Sega - Mega Drive - Genesis", "Genesis / Mega Drive");
        shortName("Sega - Master System - Mark III", "Master System");
        shortName("Sega - Game Gear", "Game Gear");
        shortName("Sega - Saturn", "Saturn");
        shortName("Sega - Dreamcast", "Dreamcast");
        shortName("Sony - PlayStation", "PlayStation");
        shortName("Sony - PlayStation 2", "PlayStation 2");
        shortName("Sony - PlayStation Portable", "PSP");
        shortName("NEC - PC Engine - TurboGrafx 16", "PC Engine / TurboGrafx");
        shortName("SNK - Neo Geo Pocket", "Neo Geo Pocket");
        shortName("SNK - Neo Geo Pocket Color", "Neo Geo Pocket Color");
        shortName("Bandai - WonderSwan", "WonderSwan");
        shortName("Bandai - WonderSwan Color", "WonderSwan Color");
        shortName("Atari - 2600", "Atari 2600");
        shortName("Arcade", "Arcade");
        shortName("Needs Review", "Needs Review");
    }

    static Core forSystem(String system) {
        return BY_SYSTEM.get(system);
    }

    static String shortSystem(String system) {
        String found = SHORT_NAMES.get(system);
        return found != null ? found : system;
    }

    static List<Core> requiredCores(Iterable<String> systems) {
        LinkedHashMap<String, Core> unique = new LinkedHashMap<>();
        for (String system : systems) {
            Core core = forSystem(system);
            if (core != null)
                unique.put(core.id, core);
        }
        return new ArrayList<>(unique.values());
    }

    private HRCoreCatalog() {}
}

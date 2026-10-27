/*
 * HR Retro — curated core catalog checks.
 * Compile alongside HRCoreCatalog.java.
 */
package com.retroarch.browser.retroactivity;

import java.util.Arrays;
import java.util.List;

public final class HRCoreCatalogTest {
    private static int checks = 0;

    private static void eq(String what, String actual, String expected) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        checks++;
    }

    private static void ok(String what, boolean condition) {
        if (!condition)
            throw new AssertionError(what);
        checks++;
    }

    public static void main(String[] args) {
        eq("Game Boy core", HRCoreCatalog.forSystem("Nintendo - Game Boy").id,
                "gambatte");
        eq("Game Boy Color shares core", HRCoreCatalog.forSystem(
                "Nintendo - Game Boy Color").id, "gambatte");
        eq("GBA core", HRCoreCatalog.forSystem(
                "Nintendo - Game Boy Advance").id, "mgba");
        eq("PS1 core", HRCoreCatalog.forSystem(
                "Sony - PlayStation").id, "pcsx_rearmed");
        eq("SNES core", HRCoreCatalog.forSystem(
                "Nintendo - Super Nintendo Entertainment System").id, "snes9x");
        eq("N64 GLES3", HRCoreCatalog.forSystem(
                "Nintendo - Nintendo 64").id, "mupen64plus_next_gles3");
        eq("Nintendo DS core", HRCoreCatalog.forSystem(
                "Nintendo - Nintendo DS").id, "melondsds");
        eq("Dreamcast", HRCoreCatalog.forSystem(
                "Sega - Dreamcast").id, "flycast");
        eq("Unknown left manual", HRCoreCatalog.forSystem("Needs Review") == null ?
                null : "NOT NULL", null);
        eq("Unsupported kept manual", HRCoreCatalog.forSystem(
                "Nintendo - Wii") == null ? null : "NOT NULL", null);
        eq("Readable Game Boy Color", HRCoreCatalog.shortSystem(
                "Nintendo - Game Boy Color"), "Game Boy Color");
        eq("Readable PlayStation", HRCoreCatalog.shortSystem(
                "Sony - PlayStation"), "PlayStation");

        List<HRCoreCatalog.Core> wanted = HRCoreCatalog.requiredCores(Arrays.asList(
            "Nintendo - Game Boy", "Nintendo - Game Boy Color",
            "Nintendo - Game Boy Advance", "Nintendo - Game Boy Advance",
            "Sony - PlayStation", "Nintendo - Wii", "Needs Review"));
        ok("Only three distinct cores", wanted.size() == 3);
        eq("Core filename", wanted.get(0).filename(),
                "gambatte_libretro_android.so");
        eq("Core info filename", wanted.get(0).infoName(),
                "gambatte_libretro.info");
        System.out.println("HR Retro core catalog: " + checks + " checks passed");
    }
}

/*
 * HR Retro host-side classifier regression test.
 * Compile with javac alongside HRGameLibraryClassifier.java.
 * GPL-3.0-or-later. Copyright (C) 2026 HR Retro contributors.
 */
package com.retroarch.browser.retroactivity;

public final class HRClassifierTest {
    private static int assertions = 0;

    private static void equal(String description, String actual, String expected) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        assertions++;
    }

    public static void main(String[] args) {
        equal("GBA by extension", HRGameLibraryClassifier.systemFor(
            "Pokemon Emerald.GBA", null), "Nintendo - Game Boy Advance");
        equal("GB by extension", HRGameLibraryClassifier.systemFor(
            "Tetris.gb", null), "Nintendo - Game Boy");
        equal("GBC by extension", HRGameLibraryClassifier.systemFor(
            "Crystal.GBC", null), "Nintendo - Game Boy Color");
        equal("SNES by extension", HRGameLibraryClassifier.systemFor(
            "Chrono Trigger.sfc", null), "Nintendo - Super Nintendo Entertainment System");
        equal("NES disk format", HRGameLibraryClassifier.systemFor(
            "Zelda.fds", null), "Nintendo - Nintendo Entertainment System");
        equal("N64 by extension", HRGameLibraryClassifier.systemFor(
            "Star Fox.Z64", null), "Nintendo - Nintendo 64");
        equal("DS by extension", HRGameLibraryClassifier.systemFor(
            "Mario Kart.nds", null), "Nintendo - Nintendo DS");
        equal("3DS by extension", HRGameLibraryClassifier.systemFor(
            "Animal Crossing.3ds", null), "Nintendo - Nintendo 3DS");
        equal("Genesis by extension", HRGameLibraryClassifier.systemFor(
            "Sonic The Hedgehog.gen", null), "Sega - Mega Drive - Genesis");
        equal("Atari by extension", HRGameLibraryClassifier.systemFor(
            "Adventure.a26", null), "Atari - 2600");
        equal("PS1 by folder", HRGameLibraryClassifier.folderSystem(
            "ps1"), "Sony - PlayStation");
        equal("PS2 by folder", HRGameLibraryClassifier.folderSystem(
            "PlayStation 2"), "Sony - PlayStation 2");
        equal("PS1 CHD with parent folder", HRGameLibraryClassifier.systemFor(
            "Metal Gear Solid.chd", "Sony - PlayStation"), "Sony - PlayStation");
        equal("Dreamcast GDI with parent folder", HRGameLibraryClassifier.systemFor(
            "Shenmue.gdi", "Sega - Dreamcast"), "Sega - Dreamcast");
        equal("Arcade ZIP with parent folder", HRGameLibraryClassifier.systemFor(
            "sf2.zip", "Arcade"), "Arcade");
        equal("Unsorted ZIP needs review", HRGameLibraryClassifier.systemFor(
            "unknown.zip", null), "Needs Review");
        equal("Unsorted ISO needs review", HRGameLibraryClassifier.systemFor(
            "unknown.iso", null), "Needs Review");
        equal("BIN ignored to avoid CUE duplication", HRGameLibraryClassifier.systemFor(
            "track01.bin", "Sony - PlayStation"), null);
        equal("README ignored", HRGameLibraryClassifier.systemFor(
            "readme.txt", "Nintendo - Game Boy Advance"), null);
        equal("Scripts ignored", HRGameLibraryClassifier.systemFor(
            "installer.exe", "Nintendo - Game Boy Advance"), null);
        equal("Root name normalization", HRGameLibraryClassifier.folderSystem(
            "Nintendo - Game Boy Advance"), "Nintendo - Game Boy Advance");
        equal("Unmapped folder", HRGameLibraryClassifier.folderSystem(
            "Random Games"), null);
        equal("No extension", HRGameLibraryClassifier.systemFor(
            "README", null), null);
        System.out.println("HR Retro classification: " + assertions + " checks passed");
    }
}

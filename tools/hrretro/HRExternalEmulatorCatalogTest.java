package com.retroarch.browser.retroactivity;

import java.util.Arrays;
import java.util.List;

public final class HRExternalEmulatorCatalogTest {
    private static int checks;
    private static void ok(String label, boolean value) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static void eq(String label, String actual, String expected) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        checks++;
    }
    public static void main(String[] args) {
        eq("Azahar package is allowlisted",
            HRExternalEmulatorCatalog.forPackage("org.azahar_emu.azahar").id, "azahar");
        eq("Dolphin package is allowlisted",
            HRExternalEmulatorCatalog.forPackage("org.dolphinemu.dolphinemu").id, "dolphin");
        ok("Unknown package rejected", HRExternalEmulatorCatalog.forPackage("com.example.apk") == null);
        ok("Malformed package rejected", HRExternalEmulatorCatalog.forPackage("org.azahar_emu.azahar.evil") == null);
        ok("Null package rejected", HRExternalEmulatorCatalog.forPackage(null) == null);
        ok("Malformed pending URI rejected", !HRExternalEmulatorCatalog.isValidPendingReview("file:///tmp/game.apk", "org.azahar_emu.azahar"));
        ok("Unknown pending package rejected", !HRExternalEmulatorCatalog.isValidPendingReview("content://sd/document/1", "com.evil.app"));
        ok("Cancelled Android installer keeps review resumable",
            HRExternalEmulatorCatalog.isValidPendingReview("content://sd/document/1", "org.azahar_emu.azahar"));
        List<HRExternalEmulatorCatalog.Entry> planned = HRExternalEmulatorCatalog.forSystems(Arrays.asList(
            "Nintendo - Nintendo 3DS", "Nintendo - Nintendo 3DS", "Nintendo - GameCube", "Needs Review"));
        ok("Repeated systems produce one install plan per app", planned.size() == 2);
        eq("3DS points only to official project release", planned.get(0).sourceUrl,
            "https://github.com/azahar-emu/azahar/releases/latest");
        eq("GameCube points to official Dolphin site", planned.get(1).sourceUrl,
            "https://dolphin-emu.org/download/");
        HRExternalEmulatorCatalog.Entry azahar = HRExternalEmulatorCatalog.forPackage("org.azahar_emu.azahar");
        ok("Official Azahar APK asset and SHA accepted", HRExternalEmulatorCatalog.isOfficialApkAsset(
            azahar, "azahar-android-vanilla-2126.2.apk",
            "https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar-android-vanilla-2126.2.apk",
            "sha256:" + repeat("a", 64)));
        ok("Canonical GitHub capitalization is accepted", HRExternalEmulatorCatalog.isOfficialApkAsset(
            azahar, "azahar-android-vanilla-2126.2.apk",
            "https://github.com/Azahar-Emu/azahar/releases/download/2126.2/azahar-android-vanilla-2126.2.apk",
            "sha256:" + repeat("a", 64)));
        ok("Wrong GitHub owner rejected", !HRExternalEmulatorCatalog.isOfficialApkAsset(
            azahar, "azahar-android-vanilla-2126.2.apk",
            "https://github.com/fake/azahar/releases/download/2126.2/azahar-android-vanilla-2126.2.apk",
            "sha256:" + repeat("a", 64)));
        ok("Missing digest rejected", !HRExternalEmulatorCatalog.isOfficialApkAsset(
            azahar, "azahar-android-vanilla-2126.2.apk",
            "https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar-android-vanilla-2126.2.apk", null));
        ok("Non Android APK asset rejected", !HRExternalEmulatorCatalog.isOfficialApkAsset(
            azahar, "azahar-libretro-android-arm64-v8a.zip",
            "https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar-libretro-android-arm64-v8a.zip",
            "sha256:" + repeat("a", 64)));
        ok("Unsupported Xbox does not get an APK source",
            HRExternalEmulatorCatalog.forSystems(Arrays.asList("Microsoft - Xbox" )).isEmpty());
        System.out.println("HR Retro external emulator catalog: " + checks + " checks passed");
    }
    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}

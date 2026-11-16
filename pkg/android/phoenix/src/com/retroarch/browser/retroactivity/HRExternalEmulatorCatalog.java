/*
 * HR Retro — curated external Android emulator setup references.
 * Package allowlisting is not publisher authentication. Locally selected APK
 * files are presented as unverified and still require Android's installer UI.
 */
package com.retroarch.browser.retroactivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class HRExternalEmulatorCatalog {
    static final class Entry {
        final String id;
        final String name;
        final String packageName;
        final String sourceUrl;
        final String githubOwner;
        final String githubRepo;
        final List<String> systems;

        Entry(String id, String name, String packageName, String sourceUrl,
                String githubOwner, String githubRepo, String... systems) {
            this.id = id;
            this.name = name;
            this.packageName = packageName;
            this.sourceUrl = sourceUrl;
            this.githubOwner = githubOwner;
            this.githubRepo = githubRepo;
            this.systems = Collections.unmodifiableList(Arrays.asList(systems));
        }
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
        new Entry("azahar", "Azahar", "org.azahar_emu.azahar",
            "https://github.com/azahar-emu/azahar/releases/latest",
            "azahar-emu", "azahar",
            "Nintendo - Nintendo 3DS"),
        new Entry("dolphin", "Dolphin", "org.dolphinemu.dolphinemu",
            "https://dolphin-emu.org/download/",
            null, null,
            "Nintendo - GameCube", "Nintendo - Wii")
    ));

    static List<Entry> entries() { return ENTRIES; }

    static List<Entry> forSystems(Iterable<String> systems) {
        ArrayList<Entry> found = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            for (String system : systems) {
                if (entry.systems.contains(system)) {
                    found.add(entry);
                    break;
                }
            }
        }
        return found;
    }

    static Entry forPackage(String packageName) {
        if (packageName == null || packageName.length() > 255) return null;
        for (Entry entry : ENTRIES)
            if (entry.packageName.equals(packageName)) return entry;
        return null;
    }

    static Entry forId(String id) {
        if (id == null) return null;
        for (Entry entry : ENTRIES) if (entry.id.equals(id)) return entry;
        return null;
    }

    static boolean isValidPendingReview(String uri, String packageName) {
        if (forPackage(packageName) == null || uri == null || uri.length() > 4096) return false;
        return uri.startsWith("content://");
    }

    static boolean isOfficialApkAsset(Entry entry, String name, String url, String digest) {
        if (entry == null || !"azahar".equals(entry.id) || name == null || url == null || digest == null)
            return false;
        if (!name.matches("azahar-android-vanilla-(?:[0-9]+\\.)*[0-9]+\\.apk")) return false;
        String prefix = "https://github.com/" + entry.githubOwner + "/" + entry.githubRepo
            + "/releases/download/";
        return url.regionMatches(true, 0, prefix, 0, prefix.length())
            && digest.matches("sha256:[0-9a-fA-F]{64}");
    }

    private HRExternalEmulatorCatalog() {}
}

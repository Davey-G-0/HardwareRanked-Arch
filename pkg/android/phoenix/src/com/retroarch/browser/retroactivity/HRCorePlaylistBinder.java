/*
 * HR Retro app-owned playlist binding.
 * Never writes user-created playlists or game data.
 * Copyright (C) 2026 HR Retro contributors. GPL-3.0-or-later.
 */
package com.retroarch.browser.retroactivity;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class HRCorePlaylistBinder {
    private static final String TAG = "HRCoreBinder";
    private static final long MAX_PLAYLIST_BYTES = 20L * 1024 * 1024;

    static JSONObject readJson(File file) throws Exception {
        if (!file.isFile() || file.length() > MAX_PLAYLIST_BYTES)
            throw new java.io.IOException("Playlist missing or oversized.");
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > MAX_PLAYLIST_BYTES)
                    throw new java.io.IOException("Playlist exceeded size limit.");
                out.write(buf, 0, n);
            }
            return new JSONObject(out.toString("UTF-8"));
        }
    }

    static int bind(Context context, File directory) {
        if (!directory.isDirectory())
            return 0;
        File[] files = directory.listFiles();
        if (files == null)
            return 0;
        int updated = 0;
        for (File file : files) {
            String name = file.getName();
            if (!name.startsWith("HR - ") || !name.endsWith(".lpl"))
                continue;
            String system = name.substring(5, name.length() - 4);
            HRCoreCatalog.Core core = HRCoreCatalog.forSystem(system);
            if (core == null || !HRCoreInstaller.isInstalled(context, core))
                continue;
            try {
                JSONObject json = readJson(file);
                String corePath = HRCoreInstaller.coreFile(context, core).getAbsolutePath();
                boolean changed = !corePath.equals(json.optString("default_core_path"));
                json.put("default_core_path", corePath);
                json.put("default_core_name", core.display);
                JSONArray items = json.optJSONArray("items");
                if (items == null)
                    continue;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null)
                        continue;
                    if (!corePath.equals(item.optString("core_path")))
                        changed = true;
                    item.put("core_path", corePath);
                    item.put("core_name", core.display);
                }
                if (!changed)
                    continue;
                File staging = File.createTempFile("hr-bind-", ".tmp", directory);
                boolean saved = false;
                try {
                    try (FileOutputStream out = new FileOutputStream(staging);
                         OutputStreamWriter writer = new OutputStreamWriter(out,
                             StandardCharsets.UTF_8)) {
                        writer.write(json.toString(2));
                        writer.flush();
                        out.getFD().sync();
                    }
                    if (!staging.renameTo(file))
                        throw new java.io.IOException("Can't update HR playlist");
                    saved = true;
                    updated++;
                } finally {
                    if (!saved)
                        staging.delete();
                }
            } catch (Exception e) {
                Log.w(TAG, "Couldn't bind playlist " + name, e);
            }
        }
        return updated;
    }

    private HRCorePlaylistBinder() {}
}

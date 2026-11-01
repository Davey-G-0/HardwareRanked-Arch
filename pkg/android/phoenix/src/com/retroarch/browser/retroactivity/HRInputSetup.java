/*
 * HR Retro v5 — one-time controller shortcut migration for existing installs.
 * Copyright (C) 2026 HR Retro contributors. GPL-3.0-or-later.
 *
 * New installs already default to Start+Select via config.def.h; older v4
 * installations may have an input_menu_toggle_gamepad_combo value in
 * retroarch.cfg that overrides the newly compiled default.
 *
 * Preserve every other setting and don't create an incomplete config file.
 */
package com.retroarch.browser.retroactivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class HRInputSetup {
    private static final String TAG = "HRInputSetup";
    private static final String PREFS = "hrretro_controls";
    private static final String MIGRATION = "start_select_menu_v5";
    private static final String KEY = "input_menu_toggle_gamepad_combo";
    private static final String VALUE = KEY + " = \"4\"";
    private static final long MAX_CONFIG_BYTES = 2L * 1024L * 1024L;
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^\\s*input_menu_toggle_gamepad_combo\\s*=.*$");

    static void migrateMenuShortcut(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(MIGRATION, false))
            return;

        File external = context.getExternalFilesDir(null);
        File[] candidates = {
            external == null ? null : new File(external, "retroarch.cfg"),
            new File(context.getFilesDir(), "retroarch.cfg")
        };
        File current = null;
        for (File file : candidates)
            if (file != null && file.isFile()) {
                current = file;
                break;
            }
        if (current == null) {
            // Do not synthesize a config just to set a default. Native
            // compilation already defaults to Start + Select.
            return;
        }
        if (current.length() < 1 || current.length() > MAX_CONFIG_BYTES) {
            Log.w(TAG, "Skipping nonstandard RetroArch configuration size.");
            return;
        }

        File tmp = null;
        try {
            ArrayList<String> lines = new ArrayList<>();
            boolean present = false;
            boolean modified = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(current),
                            StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (KEY_PATTERN.matcher(line).matches()) {
                        if (!present) {
                            present = true;
                            if (!VALUE.equals(line)) modified = true;
                            lines.add(VALUE);
                        } else {
                            // Duplicate assignments could override our
                            // shortcut; keep just one authoritative value.
                            modified = true;
                        }
                    } else {
                        lines.add(line);
                    }
                }
            }
            if (!present) {
                modified = true;
                lines.add("# HR Retro: Start + Select opens the in-game menu");
                lines.add(VALUE);
            }
            if (modified) {
                tmp = File.createTempFile("hrretro-input-", ".cfg", current.getParentFile());
                try (FileOutputStream out = new FileOutputStream(tmp);
                     OutputStreamWriter writer =
                         new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                    for (String line : lines) {
                        writer.write(line);
                        writer.write('\n');
                    }
                    writer.flush();
                    out.getFD().sync();
                }
                if (!tmp.renameTo(current))
                    throw new java.io.IOException("Could not update input shortcut.");
            }
            prefs.edit().putBoolean(MIGRATION, true).apply();
        } catch (Exception ex) {
            Log.w(TAG, "Shortcut migration skipped; native default remains in effect.", ex);
        } finally {
            if (tmp != null && tmp.exists() && !tmp.delete())
                Log.w(TAG, "Couldn't remove temporary configuration.");
        }
    }

    private HRInputSetup() {}
}

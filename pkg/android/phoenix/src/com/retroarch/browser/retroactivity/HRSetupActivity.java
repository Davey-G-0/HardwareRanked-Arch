/*
 * HR Retro first-run library setup.
 * Copyright (C) 2026 HR Retro contributors.
 * GPL-3.0-or-later.
 *
 * Uses Android's standard folder picker; no MANAGE_EXTERNAL_STORAGE access,
 * ROM copying, or editing the user's game collection.
 */
package com.retroarch.browser.retroactivity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.retroarch.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HRSetupActivity extends Activity {
    private static final String TAG = "HRRetroSetup";
    private static final String PREFS = "hrretro_library";
    private static final String FINISHED = "setup_finished";
    private static final String LAST_URI = "game_folder_uri";
    private static final String GAME_COUNT = "game_count";
    private static final String MANAGE = "hrretro.manage_library";
    private static final int OPEN_FOLDER = 7401;
    private static final int YELLOW = Color.rgb(255, 217, 15);
    private static final int BACKGROUND = Color.rgb(9, 11, 14);
    private static final int SURFACE = Color.rgb(25, 29, 34);
    private static final int WHITE = Color.rgb(245, 246, 247);
    private static final int MUTED = Color.rgb(185, 192, 199);

    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private SharedPreferences prefs;
    private TextView status;
    private TextView chooseButton;
    private TextView launchButton;
    private TextView skipButton;
    private volatile boolean scanning = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        installManageShortcut();

        Intent incoming = getIntent();
        boolean manage = incoming != null && incoming.getBooleanExtra(MANAGE, false);
        boolean incomingGame = incoming != null &&
                (incoming.hasExtra("ROM") || incoming.hasExtra("LIBRETRO"));
        if ((!manage && prefs.getBoolean(FINISHED, false)) || incomingGame) {
            openRetroArch();
            return;
        }
        drawSetup();
    }

    private int dp(float value) {
        return (int)(getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private GradientDrawable surface(int fill, boolean outline) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(12));
        if (outline)
            drawable.setStroke(dp(1), YELLOW);
        return drawable;
    }

    private TextView text(String message, int size, int color, boolean strong) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, strong ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    private TextView action(String label, boolean primary) {
        TextView view = text(label, 18, primary ? BACKGROUND : WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(18), dp(18), dp(18), dp(18));
        view.setBackground(surface(primary ? YELLOW : SURFACE, !primary));
        view.setFocusable(true);
        view.setClickable(true);
        view.setContentDescription(label);
        if (Build.VERSION.SDK_INT >= 21)
            view.setBackgroundTintList(null);
        return view;
    }

    private void addSpace(LinearLayout root, int height) {
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void drawSetup() {
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(38), dp(24), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        TextView brand = text("HR RETRO", 20, YELLOW, true);
        root.addView(brand);
        addSpace(root, 45);

        TextView headline = text("Your games.\nOne folder.", 38, WHITE, true);
        root.addView(headline);
        addSpace(root, 18);

        TextView sub = text(
            "Choose the folder containing your games. We'll find supported files and organize your library by console automatically.",
            17, MUTED, false);
        root.addView(sub);
        addSpace(root, 28);

        TextView important = text(
            "Your game files stay exactly where they are. HR Retro only builds a library of shortcuts.",
            15, MUTED, false);
        important.setPadding(dp(18), dp(18), dp(18), dp(18));
        important.setBackground(surface(SURFACE, false));
        root.addView(important);
        addSpace(root, 24);

        chooseButton = action("Choose Game Folder", true);
        root.addView(chooseButton, new LinearLayout.LayoutParams(-1, dp(68)));
        chooseButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chooseFolder(); }
        });
        addSpace(root, 14);

        launchButton = action("Start HR Retro", false);
        root.addView(launchButton, new LinearLayout.LayoutParams(-1, dp(62)));
        launchButton.setVisibility(View.GONE);
        launchButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openRetroArch(); }
        });
        addSpace(root, 16);

        status = text("Choose a folder to get started.", 16, MUTED, false);
        status.setTextIsSelectable(false);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(status);

        addSpace(root, 28);
        skipButton = action("Skip Setup", false);
        root.addView(skipButton, new LinearLayout.LayoutParams(-1, dp(56)));
        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (scanning) {
                    cancel.set(true);
                    setStatus("Cancelling scan…", false);
                    return;
                }
                prefs.edit().putBoolean(FINISHED, true).apply();
                openRetroArch();
            }
        });
        addSpace(root, 16);
        TextView tip = text(
            "To import a different folder later, long-press the HR Retro app icon and choose Manage Games (on supported Android launchers).",
            13, MUTED, false);
        tip.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(tip);

        if (prefs.contains(LAST_URI)) {
            chooseButton.setText("Choose Another Folder");
            launchButton.setVisibility(View.VISIBLE);
            int games = prefs.getInt(GAME_COUNT, 0);
            status.setText(games + " games indexed in your previous scan.");
        }
        chooseButton.requestFocus();
    }

    private void setStatus(String message, boolean error) {
        if (status != null) {
            status.setText(message);
            status.setTextColor(error ? Color.rgb(255, 135, 117) : MUTED);
        }
    }

    private void chooseFolder() {
        if (scanning)
            return;
        if (Build.VERSION.SDK_INT < 21) {
            setStatus("Folder selection requires Android 5.0 or later.", true);
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(picker, OPEN_FOLDER);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to launch folder picker", ex);
            setStatus("Android couldn't open the folder picker.", true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_FOLDER || resultCode != RESULT_OK
                || data == null || data.getData() == null)
            return;
        final Uri uri = data.getData();
        try {
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0)
                throw new SecurityException("The selected folder is not readable.");
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ex) {
            Log.e(TAG, "Folder access not persisted", ex);
            setStatus("Android couldn't remember access to that folder. Please choose another.", true);
            return;
        }
        scan(uri);
    }

    /**
     * Follow the existing RetroArch playlist path when one is configured.
     * The upstream native defaults are redirected to getFilesDir()/playlists
     * for brand-new HR Retro installs. We must not silently discard an
     * existing player's custom playlist path.
     */
    private File playlistDirectory() throws IOException {
        File external = getExternalFilesDir(null);
        File[] configCandidates = {
            external == null ? null : new File(external, "retroarch.cfg"),
            new File(getFilesDir(), "retroarch.cfg")
        };
        for (File config : configCandidates) {
            if (config == null || !config.isFile())
                continue;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(config), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("playlist_directory"))
                        continue;
                    int equals = trimmed.indexOf('=');
                    if (equals < 0 || !trimmed.substring(0, equals).trim()
                            .equals("playlist_directory"))
                        continue;
                    String value = trimmed.substring(equals + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"")
                            && value.length() >= 2)
                        value = value.substring(1, value.length() - 1);
                    if (value.isEmpty() || value.equals("default"))
                        break;
                    File selected = new File(value);
                    if (!selected.isAbsolute())
                        break;
                    if (!selected.isDirectory() && !selected.mkdirs())
                        throw new IOException(
                            "The playlist directory in your existing settings isn't writable: " +
                            selected.getAbsolutePath());
                    return selected;
                }
            }
            // Native startup prefers the first existing config; do likewise.
            break;
        }
        File fallback = new File(getFilesDir(), "playlists");
        if (!fallback.isDirectory() && !fallback.mkdirs())
            throw new IOException("Couldn't create the HR Retro library directory.");
        return fallback;
    }

    private void scan(final Uri uri) {
        scanning = true;
        cancel.set(false);
        chooseButton.setEnabled(false);
        chooseButton.setAlpha(0.5f);
        launchButton.setVisibility(View.GONE);
        skipButton.setText("Cancel Scan");
        setStatus("Finding games…", false);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final HRGameLibraryImporter.Result result =
                        HRGameLibraryImporter.importFolder(
                            getContentResolver(),
                            uri,
                            playlistDirectory(),
                            cancel,
                            new HRGameLibraryImporter.Progress() {
                                @Override
                                public void onProgress(final int visited, final int games) {
                                    runOnUiThread(new Runnable() {
                                        @Override public void run() {
                                            setStatus("Checked " + visited +
                                                " files • Found " + games + " games", false);
                                        }
                                    });
                                }
                            });
                    if (cancel.get())
                        throw new InterruptedException("Cancelled");
                    prefs.edit()
                        .putString(LAST_URI, uri.toString())
                        .putInt(GAME_COUNT, result.games)
                        .putBoolean(FINISHED, true)
                        .apply();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            finishScan();
                            setStatus(
                                "Found " + result.games + " games across " +
                                result.systems + " systems" +
                                (result.needsReview > 0
                                    ? " • " + result.needsReview + " need review" : "") +
                                (result.limited ? " • Scan limit reached" : ""),
                                false);
                            chooseButton.setText("Choose Another Folder");
                            launchButton.setVisibility(View.VISIBLE);
                            launchButton.requestFocus();
                        }
                    });
                } catch (final Exception ex) {
                    Log.e(TAG, "Import failed", ex);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            finishScan();
                            if (ex instanceof InterruptedException)
                                setStatus("Scan cancelled. No game files were changed.", false);
                            else
                                setStatus("Couldn't scan this folder: " + ex.getMessage(), true);
                        }
                    });
                }
            }
        }, "HRRetro-library-import").start();
    }

    private void finishScan() {
        scanning = false;
        chooseButton.setEnabled(true);
        chooseButton.setAlpha(1.0f);
        skipButton.setText("Skip Setup");
    }

    private void openRetroArch() {
        Intent target = new Intent(this, RetroActivityFuture.class);
        if (getIntent() != null && getIntent().getExtras() != null)
            target.putExtras(getIntent().getExtras());
        target.removeExtra(MANAGE);
        startActivity(target);
        finish();
    }

    private void installManageShortcut() {
        if (Build.VERSION.SDK_INT < 25)
            return;
        try {
            ShortcutManager manager = getSystemService(ShortcutManager.class);
            if (manager == null)
                return;
            Intent intent = new Intent(this, HRSetupActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra(MANAGE, true);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "hrretro.manage")
                .setShortLabel("Manage Games")
                .setLongLabel("Choose game folder and rescan")
                .setIcon(Icon.createWithResource(this, R.drawable.hr_retro_launcher))
                .setIntent(intent)
                .build();
            manager.setDynamicShortcuts(Arrays.asList(shortcut));
        } catch (Exception ex) {
            Log.w(TAG, "Manage Games shortcut unavailable", ex);
        }
    }

    @Override
    public void onBackPressed() {
        if (scanning) {
            cancel.set(true);
            setStatus("Cancelling scan…", false);
            return;
        }
        super.onBackPressed();
    }
}

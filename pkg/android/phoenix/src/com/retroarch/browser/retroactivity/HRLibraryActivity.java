/*
 * HR Retro — controller-first, full-width Android game library.
 * Launches games through the existing RetroArch native activity using
 * explicit core + SAF game path (no repeated core selection).
 *
 * Copyright (C) 2026 HR Retro contributors. GPL-3.0-or-later.
 */
package com.retroarch.browser.retroactivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

public final class HRLibraryActivity extends Activity {
    private static final String TAG = "HRLibrary";
    private static final String PREFS = "hrretro_library";
    private static final int YELLOW = Color.rgb(255, 217, 15);
    private static final int DARK = Color.rgb(9, 11, 14);
    private static final int SURFACE = Color.rgb(24, 29, 35);
    private static final int TEXT = Color.rgb(245, 246, 247);
    private static final int MUTED = Color.rgb(175, 188, 197);
    private static final int ALL = 0;

    private static final class Game {
        final String system;
        final String label;
        final String path;
        Game(String system, String label, String path) {
            this.system = system;
            this.label = label;
            this.path = path;
        }
    }

    private final List<Game> games = new ArrayList<>();
    private final List<Game> filtered = new ArrayList<>();
    private final List<String> systems = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();
    private final AtomicBoolean installing = new AtomicBoolean(false);
    private SharedPreferences prefs;
    private File playlistDir;
    private int selectedSystem = 0;
    private GameAdapter adapter;
    private ListView gameList;
    private LinearLayout tabs;
    private TextView header;
    private TextView status;
    private TextView coresButton;
    private boolean hasPrompted = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            playlistDir = HRSetupActivity.playlistDirectory(this);
        } catch (Exception ex) {
            Log.e(TAG, "Playlist path unavailable", ex);
            playlistDir = new File(getFilesDir(), "playlists");
        }
        readPlaylists();
        buildScreen();
        showGames();
        // Never block the main library behind a forced download.
        getWindow().getDecorView().post(new Runnable() {
            @Override public void run() {
                if (!isFinishing() && !hasPrompted) {
                    hasPrompted = true;
                    if (!prefs.getBoolean("cores_approval_prompted", false))
                        offerMissingCores();
                }
            }
        });
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }

    private GradientDrawable background(int color, boolean border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(9));
        if (border) d.setStroke(dp(2), YELLOW);
        return d;
    }

    private TextView text(String label, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setSingleLine(false);
        return v;
    }

    private TextView button(String label, boolean selected) {
        TextView v = text(label, 13, selected ? DARK : TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setBackground(background(selected ? YELLOW : SURFACE, !selected));
        v.setFocusable(true);
        v.setClickable(true);
        v.setContentDescription(label);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                if (view != null && !selected)
                    view.setBackground(background(SURFACE, focused));
            }
        });
        return v;
    }

    private void readPlaylists() {
        games.clear();
        systems.clear();
        Set<String> detected = new LinkedHashSet<>();
        File[] files = playlistDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.startsWith("HR - ") || !name.endsWith(".lpl"))
                    continue;
                String system = name.substring(5, name.length() - 4);
                try {
                    JSONObject data = HRCorePlaylistBinder.readJson(file);
                    JSONArray items = data.optJSONArray("items");
                    if (items == null || items.length() == 0)
                        continue;
                    int before = games.size();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        String uri = item.optString("path", "");
                        String label = item.optString("label", "").trim();
                        if (!uri.startsWith("saf://") || label.length() == 0)
                            continue;
                        games.add(new Game(system, label, uri));
                    }
                    if (games.size() > before)
                        detected.add(system);
                } catch (Exception ex) {
                    Log.w(TAG, "Ignoring unreadable HR playlist " + name, ex);
                }
            }
        }
        Collections.sort(games, new Comparator<Game>() {
            @Override public int compare(Game a, Game b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        systems.add("All Games");
        ArrayList<String> ordered = new ArrayList<>(detected);
        Collections.sort(ordered, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                return HRCoreCatalog.shortSystem(a)
                    .compareToIgnoreCase(HRCoreCatalog.shortSystem(b));
            }
        });
        systems.addAll(ordered);
        String last = prefs.getString("last_system", "All Games");
        int position = systems.indexOf(last);
        selectedSystem = position < 0 ? ALL : position;
    }

    private void buildScreen() {
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(9), dp(14), dp(9));
        root.setBackgroundColor(DARK);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(56)));

        header = text("HR RETRO", 23, YELLOW, true);
        top.addView(header, new LinearLayout.LayoutParams(0, -1, 1.0f));
        TextView gamesBtn = button("ADD GAMES", false);
        gamesBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSetup(); }
        });
        top.addView(gamesBtn);
        TextView spacer = new TextView(this);
        top.addView(spacer, new LinearLayout.LayoutParams(dp(7), 1));
        coresButton = button("EMULATORS", false);
        coresButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { offerMissingCores(); }
        });
        top.addView(coresButton);
        TextView spacer2 = new TextView(this);
        top.addView(spacer2, new LinearLayout.LayoutParams(dp(7), 1));
        TextView settings = button("ADVANCED", false);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openRetroArch(null, null); }
        });
        top.addView(settings);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(55)));

        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -1));
        rebuildTabs();

        gameList = new ListView(this);
        gameList.setBackgroundColor(DARK);
        gameList.setCacheColorHint(DARK);
        gameList.setDividerHeight(dp(1));
        gameList.setSelector(background(Color.rgb(48, 52, 58), true));
        gameList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        gameList.setFocusable(true);
        gameList.setItemsCanFocus(false);
        adapter = new GameAdapter();
        gameList.setAdapter(adapter);
        gameList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                                               int position, long id) {
                if (position >= 0 && position < filtered.size())
                    play(filtered.get(position));
            }
        });
        root.addView(gameList, new LinearLayout.LayoutParams(-1, 0, 1.0f));

        status = text("L1/R1  SWITCH SYSTEM  •  A  PLAY  •  B  BACK", 11, MUTED, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(32)));
    }

    private void rebuildTabs() {
        tabs.removeAllViews();
        tabViews.clear();
        for (int index = 0; index < systems.size(); index++) {
            final int tab = index;
            TextView view = button(HRCoreCatalog.shortSystem(systems.get(index)),
                    index == selectedSystem);
            LinearLayout.LayoutParams p =
                    new LinearLayout.LayoutParams(-2, dp(44));
            p.setMargins(0, 0, dp(8), 0);
            tabs.addView(view, p);
            tabViews.add(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectSystem(tab, true); }
            });
        }
    }

    private void selectSystem(int index, boolean focusGames) {
        if (index < 0 || index >= systems.size())
            return;
        selectedSystem = index;
        prefs.edit().putString("last_system", systems.get(index)).apply();
        rebuildTabs();
        showGames();
        if (focusGames && !filtered.isEmpty()) {
            gameList.requestFocus();
            gameList.setSelection(0);
        }
    }

    private void showGames() {
        if (gameList == null || adapter == null)
            return;
        filtered.clear();
        for (Game g : games)
            if (selectedSystem == ALL || systems.get(selectedSystem).equals(g.system))
                filtered.add(g);
        adapter.notifyDataSetChanged();
        int missing = missingCores().size();
        coresButton.setText(missing == 0 ? "EMULATORS ✓" : "GET EMULATORS " + missing);
        header.setText("HR RETRO  " + filtered.size() + " GAMES");
        if (filtered.isEmpty())
            status.setText("NO GAMES YET  •  ADD GAMES TO CHOOSE YOUR FOLDER");
        else
            status.setText("L1/R1  SYSTEMS  •  A  PLAY  •  B  BACK");
        if (!filtered.isEmpty()) {
            gameList.requestFocus();
            gameList.setSelection(0);
        }
    }

    private List<HRCoreCatalog.Core> missingCores() {
        List<HRCoreCatalog.Core> cores = HRCoreCatalog.requiredCores(systems);
        ArrayList<HRCoreCatalog.Core> missing = new ArrayList<>();
        for (HRCoreCatalog.Core c : cores)
            if (!HRCoreInstaller.isInstalled(this, c))
                missing.add(c);
        return missing;
    }

    private void offerMissingCores() {
        if (installing.get())
            return;
        final List<HRCoreCatalog.Core> missing = missingCores();
        if (missing.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Emulators ready")
                .setMessage("All automatically supported systems have their recommended cores installed. Some advanced systems may need separate setup.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }
        prefs.edit().putBoolean("cores_approval_prompted", true).apply();
        StringBuilder message = new StringBuilder();
        message.append("We found games that need ")
            .append(missing.size())
            .append(missing.size() == 1 ? " emulator.\n\n" : " emulators.\n\n");
        for (HRCoreCatalog.Core core : missing) {
            message.append("• ").append(core.display).append("\n");
        }
        message.append("\nDownload from the official Libretro server? An internet connection is required. Core licenses vary; no game files or BIOS will be downloaded.");
        new AlertDialog.Builder(this)
            .setTitle("Ready to play")
            .setMessage(message.toString())
            .setPositiveButton("Install " + missing.size(), (dialog, which) -> installCores(missing))
            .setNegativeButton("Later", null)
            .show();
    }

    private void installCores(final List<HRCoreCatalog.Core> cores) {
        if (!installing.compareAndSet(false, true))
            return;
        coresButton.setEnabled(false);
        status.setText("Preparing emulator downloads…");
        new Thread(new Runnable() {
            @Override public void run() {
                final HRCoreInstaller.Outcome outcome = HRCoreInstaller.install(
                    getApplicationContext(), cores, new HRCoreInstaller.Listener() {
                    @Override public void onStatus(final String progress) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() { status.setText(progress); }
                        });
                    }
                });
                HRCorePlaylistBinder.bind(getApplicationContext(), playlistDir);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        installing.set(false);
                        coresButton.setEnabled(true);
                        showGames();
                        StringBuilder summary = new StringBuilder();
                        summary.append("Installed ").append(outcome.installed)
                            .append(" emulator cores.");
                        if (!outcome.failed.isEmpty()) {
                            summary.append("\n\nSome couldn't be installed:\n");
                            for (String fail : outcome.failed)
                                summary.append("• ").append(fail).append("\n");
                        }
                        if (outcome.infoWarning != null)
                            summary.append("\n").append(outcome.infoWarning);
                        new AlertDialog.Builder(HRLibraryActivity.this)
                            .setTitle(outcome.failed.isEmpty() ? "Emulators ready" :
                                "Setup needs attention")
                            .setMessage(summary.toString())
                            .setPositiveButton("OK", null)
                            .show();
                    }
                });
            }
        }, "HRRetro-install-cores").start();
    }

    private void play(final Game game) {
        HRCoreCatalog.Core core = HRCoreCatalog.forSystem(game.system);
        if (core == null) {
            new AlertDialog.Builder(this)
                .setTitle("Manual setup needed")
                .setMessage("Automatic emulator setup for " +
                    HRCoreCatalog.shortSystem(game.system) +
                    " isn't ready yet. Open Advanced to select a compatible installed core, or choose another game.")
                .setPositiveButton("Advanced", (d, w) -> openRetroArch(null, null))
                .setNegativeButton("Back", null)
                .show();
            return;
        }
        if (!HRCoreInstaller.isInstalled(this, core)) {
            new AlertDialog.Builder(this)
                .setTitle("Install " + core.display + "?")
                .setMessage("This game needs " + core.display +
                    " to play.\n\n" + core.supportNote +
                    "\nDownload it now from Libretro?")
                .setPositiveButton("Install", (d, w) -> {
                    ArrayList<HRCoreCatalog.Core> one = new ArrayList<>();
                    one.add(core);
                    installCores(one);
                })
                .setNegativeButton("Back", null).show();
            return;
        }
        openRetroArch(game.path,
            HRCoreInstaller.coreFile(this, core).getAbsolutePath());
    }

    private void openRetroArch(String game, String corePath) {
        Intent intent = new Intent(this, RetroActivityFuture.class);
        if (game != null && corePath != null) {
            intent.putExtra("ROM", game);
            intent.putExtra("LIBRETRO", corePath);
        }
        startActivity(intent);
    }

    private void openSetup() {
        Intent intent = new Intent(this, HRSetupActivity.class);
        intent.putExtra("hrretro.manage_library", true);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onKeyDown(int key, KeyEvent event) {
        if (key == KeyEvent.KEYCODE_BUTTON_L1) {
            selectSystem((selectedSystem - 1 + systems.size()) % systems.size(), true);
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_R1) {
            selectSystem((selectedSystem + 1) % systems.size(), true);
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_A || key == KeyEvent.KEYCODE_DPAD_CENTER) {
            View current = getCurrentFocus();
            if (current != null && current != gameList && current.isClickable()) {
                current.performClick();
                return true;
            }
            int selected = gameList.getSelectedItemPosition();
            if (selected >= 0 && selected < filtered.size()) {
                play(filtered.get(selected));
                return true;
            }
        }
        if (key == KeyEvent.KEYCODE_BUTTON_B) {
            finish();
            return true;
        }
        return super.onKeyDown(key, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameList != null && !installing.get())
            showGames();
    }

    private final class GameAdapter extends BaseAdapter {
        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView detail;
            if (recycled instanceof LinearLayout) {
                row = (LinearLayout) recycled;
                title = (TextView) row.getChildAt(0);
                detail = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(HRLibraryActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(15), dp(6), dp(12), dp(6));
                title = text("", 18, TEXT, true);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                detail = text("", 11, MUTED, false);
                detail.setSingleLine(true);
                row.addView(title);
                row.addView(detail);
                row.setBackgroundColor(SURFACE);
            }
            Game game = filtered.get(position);
            title.setText(game.label.replace('_', ' '));
            HRCoreCatalog.Core core = HRCoreCatalog.forSystem(game.system);
            boolean installed = core != null && HRCoreInstaller.isInstalled(
                HRLibraryActivity.this, core);
            String systemLabel = HRCoreCatalog.shortSystem(game.system);
            detail.setText(systemLabel + "   •   " +
                (core == null ? "Manual setup" :
                 installed ? "A  Play" : "Install " + core.display));
            return row;
        }
    }
}

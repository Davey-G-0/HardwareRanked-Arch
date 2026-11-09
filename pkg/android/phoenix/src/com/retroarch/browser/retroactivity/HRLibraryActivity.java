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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.app.role.RoleManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
    private static final int YELLOW = Color.rgb(176, 116, 255);
    private static final int TEAL = Color.rgb(104, 227, 210);
    private static final int DARK = Color.rgb(9, 10, 18);
    private static final int SURFACE = Color.rgb(23, 25, 39);
    private static final int TEXT = Color.rgb(246, 244, 255);
    private static final int MUTED = Color.rgb(171, 173, 194);
    private static final int ALL = 0;
    private static final int PICK_ARTWORK = 7412;
    private static final int REQUEST_HOME_ROLE = 7413;

    private static final class Game {
        final String system;
        final String label;
        final String path;
        final boolean androidApp;
        Game(String system, String label, String path) {
            this(system, label, path, false);
        }
        Game(String system, String label, String path, boolean androidApp) {
            this.system = system;
            this.label = label;
            this.path = path;
            this.androidApp = androidApp;
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
    private TextView heroTitle;
    private TextView heroCount;
    private ImageView heroImage;
    private FrameLayout hero;
    private HorizontalScrollView tabScroll;
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
        d.setCornerRadius(dp(14));
        if (border) d.setStroke(dp(2), TEAL);
        return d;
    }

    private TextView text(String label, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setSingleLine(false);
        return v;
    }

    private TextView button(String label, boolean selected) {
        TextView v = text(label, 13, selected ? DARK : TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(15), dp(11), dp(15), dp(11));
        v.setBackground(background(selected ? YELLOW : SURFACE, false));
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
        ArrayList<Game> android = readAndroidGames();
        if (!android.isEmpty()) {
            games.addAll(android);
            systems.add("Android Games");
        }
        // Start at the left edge every time. Selection follows explicit
        // controller navigation, never a stale or end-of-list focus.
        selectedSystem = ALL;
    }

    private ArrayList<Game> readAndroidGames() {
        ArrayList<Game> result = new ArrayList<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> matches = getPackageManager().queryIntentActivities(launcher, 0);
        Set<String> seen = new LinkedHashSet<>();
        for (ResolveInfo match : matches) {
            if (match.activityInfo == null || match.activityInfo.applicationInfo == null)
                continue;
            ApplicationInfo app = match.activityInfo.applicationInfo;
            if (app.packageName.equals(getPackageName()) || !seen.add(app.packageName))
                continue;
            if (Build.VERSION.SDK_INT >= 26 && app.category != ApplicationInfo.CATEGORY_GAME)
                continue;
            CharSequence label = match.loadLabel(getPackageManager());
            if (label != null && label.length() > 0)
                result.add(new Game("Android Games", label.toString(), app.packageName, true));
        }
        Collections.sort(result, new Comparator<Game>() {
            @Override public int compare(Game a, Game b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return result;
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
        TextView settings = button("OPTIONS", false);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showOptions(); }
        });
        top.addView(settings);

        hero = new FrameLayout(this);
        hero.setBackground(background(SURFACE, false));
        hero.setClipToOutline(true);
        root.addView(hero, new LinearLayout.LayoutParams(-1, dp(168)));
        heroImage = new ImageView(this);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setAlpha(.48f);
        hero.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] { Color.rgb(35, 19, 62), Color.argb(60, 10, 10, 20) }));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout heroCopy = new LinearLayout(this);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setGravity(Gravity.BOTTOM);
        heroCopy.setPadding(dp(22), dp(16), dp(18), dp(16));
        FrameLayout.LayoutParams heroCopyParams = new FrameLayout.LayoutParams(-1, -1);
        hero.addView(heroCopy, heroCopyParams);
        TextView eyebrow = text("YOUR COLLECTION", 11, TEAL, true);
        heroCopy.addView(eyebrow);
        heroTitle = text("All Games", 26, TEXT, true);
        heroCopy.addView(heroTitle);
        heroCount = text("Browse by system", 13, TEXT, false);
        heroCopy.addView(heroCount);

        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(false);
        tabScroll.setFocusable(false);
        root.addView(tabScroll, new LinearLayout.LayoutParams(-1, dp(64)));

        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabScroll.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -1));
        rebuildTabs();

        gameList = new ListView(this);
        gameList.setId(View.generateViewId());
        gameList.setBackgroundColor(DARK);
        gameList.setCacheColorHint(DARK);
        gameList.setDividerHeight(dp(1));
        gameList.setSelector(background(Color.rgb(40, 34, 57), true));
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
        gameList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {
                if (position >= 0 && position < filtered.size()) {
                    showGameHelp(filtered.get(position));
                    return true;
                }
                return false;
            }
        });
        root.addView(gameList, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        for (TextView tab : tabViews)
            tab.setNextFocusDownId(gameList.getId());

        status = text("L1/R1  SWITCH SYSTEM  •  A  PLAY  •  B  BACK", 11, MUTED, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(32)));
        getWindow().getDecorView().post(new Runnable() {
            @Override public void run() {
                if (!tabViews.isEmpty())
                    tabViews.get(0).requestFocus();
            }
        });
    }

    private void rebuildTabs() {
        tabs.removeAllViews();
        tabViews.clear();
        for (int index = 0; index < systems.size(); index++) {
            final int tab = index;
            TextView view = button(HRCoreCatalog.shortSystem(systems.get(index)),
                    index == selectedSystem);
            LinearLayout.LayoutParams p =
                    new LinearLayout.LayoutParams(-2, dp(48));
            p.setMargins(0, 0, dp(10), 0);
            tabs.addView(view, p);
            tabViews.add(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectSystem(tab, true); }
            });
        }
        if (gameList != null)
            for (TextView tab : tabViews)
                tab.setNextFocusDownId(gameList.getId());
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
        header.setText("HR RETRO");
        refreshHero();
        if (filtered.isEmpty())
            status.setText("NO GAMES YET  •  ADD GAMES TO CHOOSE YOUR FOLDER");
        else
            status.setText("L1/R1  SYSTEMS  •  A  PLAY  •  B  BACK");
        gameList.setSelection(0);
    }

    private void refreshHero() {
        if (heroTitle == null || systems.isEmpty()) return;
        String system = systems.get(selectedSystem);
        heroTitle.setText(HRCoreCatalog.shortSystem(system));
        heroCount.setText(filtered.size() + (filtered.size() == 1 ? " game" : " games")
            + "  ·  A open  ·  ←/→ systems");
        String artwork = prefs.getString("artwork:" + system, null);
        if (artwork == null) {
            heroImage.setImageDrawable(null);
            hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { Color.rgb(88, 31, 151), Color.rgb(35, 113, 126), DARK }));
        } else {
            try {
                heroImage.setImageURI(Uri.parse(artwork));
            } catch (RuntimeException ex) {
                Log.w(TAG, "Could not load saved system artwork", ex);
                heroImage.setImageDrawable(null);
            }
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
        if (game.androidApp) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(game.path);
            if (launch != null) startActivity(launch);
            else Toast.makeText(this, "This Android game is no longer installed.",
                Toast.LENGTH_SHORT).show();
            return;
        }
        HRCoreCatalog.Core core = HRCoreCatalog.forSystem(game.system);
        if (core == null) {
            showAdvancedSystemOptions(game);
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

    /*
     * Dolphin and Android PS2 emulators are separate native Android apps;
     * a PCSX2 desktop core must never be auto-installed as an ARM64 core.
     * We open the user's selected installed app instead of pretending the
     * RetroArch core downloader can satisfy these systems. Android app
     * installers manage their own storage and legally obtained BIOS.
     */
    private void showAdvancedSystemOptions(final Game game) {
        final String system = game.system;
        final boolean dolphin = "Nintendo - GameCube".equals(system)
                || "Nintendo - Wii".equals(system);
        final boolean ps2 = "Sony - PlayStation 2".equals(system);
        if (!dolphin && !ps2) {
            new AlertDialog.Builder(this)
                .setTitle("Additional setup needed")
                .setMessage("This system needs an emulator you choose manually. " +
                    "The full RetroArch settings are still available. " +
                    "When you quit RetroArch, HR Retro will reopen your game library.")
                .setPositiveButton("Advanced", (d, w) -> openRetroArch(null, null))
                .setNegativeButton("Back", null).show();
            return;
        }
        final String appName = dolphin ? "Dolphin" : "NetherSX2 / AetherSX2";
        final String packageName = dolphin
                ? "org.dolphinemu.dolphinemu" : "xyz.aethersx2.android";
        final Intent installed = getPackageManager().getLaunchIntentForPackage(packageName);
        final String message = (dolphin
                ? "GameCube and Wii games use the separate Dolphin Android app."
                : "PCSX2 is a desktop emulator; PS2 on Android requires a compatible Android app such as NetherSX2, plus your own legally obtained BIOS.")
                + "\n\n" + (installed == null
                    ? appName + " isn't currently detectable. Install and configure it separately, then try again."
                    : "Open " + appName + " to configure its game library and launch this game there.")
                + "\n\nAdvanced opens RetroArch's technical menu, not a compatible automatic core.";
        new AlertDialog.Builder(this)
            .setTitle(HRCoreCatalog.shortSystem(system) + " setup")
            .setMessage(message)
            .setPositiveButton(installed != null ? "Open " + appName : "OK",
                (d, w) -> {
                    if (installed != null)
                        startActivity(installed);
                })
            .setNeutralButton("Advanced", (d, w) -> openRetroArch(null, null))
            .setNegativeButton("Back", null)
            .show();
    }

    private void showGameHelp(final Game game) {
        String system = game.system;
        if ("Nintendo - Nintendo DS".equals(system)) {
            new AlertDialog.Builder(this)
                .setTitle("Nintendo DS controls")
                .setMessage("While playing, press START + SELECT together to open the in-game menu. " +
                    "Select Quick Menu → Core Options to adjust the DS screen layout. " +
                    "For fast-forward, open Settings → Input → Hotkeys and bind a spare button to Fast-Forward (Hold). " +
                    "These controls are still manual in this test build.")
                .setPositiveButton("OK", null).show();
        } else if ("Sega - Dreamcast".equals(system)) {
            new AlertDialog.Builder(this)
                .setTitle("Dreamcast black-screen checks")
                .setMessage("Flycast may need compatible OpenGL/Vulkan video settings or additional firmware depending on the game. " +
                    "Try START + SELECT to open the menu; inspect Quick Menu → Core Options and Video settings. " +
                    "HR Retro does not download BIOS files. A black screen is not yet fixed in this build.")
                .setPositiveButton("Advanced", (d, w) -> openRetroArch(null, null))
                .setNegativeButton("Back", null).show();
        } else if ("Nintendo - GameCube".equals(system)
                || "Nintendo - Wii".equals(system)
                || "Sony - PlayStation 2".equals(system)) {
            showAdvancedSystemOptions(game);
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Game controls")
                .setMessage("Press START + SELECT together during gameplay to show RetroArch's in-game menu. " +
                    "Long-press a game for context-sensitive help. Return from Advanced by exiting RetroArch.")
                .setPositiveButton("OK", null).show();
        }
    }

    private void openRetroArch(String game, String corePath) {
        applyTouchscreenPreference();
        Intent intent = new Intent(this, RetroActivityFuture.class);
        intent.putExtra("HR_RETRO_RETURN_LIBRARY", true);
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

    private void showOptions() {
        final String[] options = {
            "Customize system artwork",
            "Controls",
            "Use HR Retro as Android Home",
            "Advanced RetroArch settings"
        };
        new AlertDialog.Builder(this)
            .setTitle("HR Retro options")
            .setItems(options, (dialog, which) -> {
                if (which == 0) pickArtwork();
                else if (which == 1) showControlsOptions();
                else if (which == 2) requestHomeRole();
                else openRetroArch(null, null);
            }).show();
    }

    private void showControlsOptions() {
        final boolean enabled = prefs.getBoolean("touchscreen_controls", false);
        new AlertDialog.Builder(this)
            .setTitle("Controls")
            .setMessage("Show RetroArch's on-screen controls for games without a built-in controller. Physical controller input stays enabled.")
            .setMultiChoiceItems(new String[] { "Touchscreen controls" },
                new boolean[] { enabled }, (dialog, which, checked) -> {
                    prefs.edit().putBoolean("touchscreen_controls", checked).apply();
                    applyTouchscreenPreference();
                })
            .setPositiveButton("Done", null)
            .show();
    }

    private void pickArtwork() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(pick, PICK_ARTWORK);
        } catch (Exception ex) {
            Toast.makeText(this, "Image picker is unavailable.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestHomeRole() {
        if (Build.VERSION.SDK_INT < 29) {
            new AlertDialog.Builder(this).setTitle("Android Home app")
                .setMessage("This Android version doesn't provide the Home app chooser.")
                .setPositiveButton("OK", null).show();
            return;
        }
        RoleManager roles = (RoleManager) getSystemService(ROLE_SERVICE);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            Toast.makeText(this, "Android Home role isn't available on this device.",
                Toast.LENGTH_SHORT).show();
            return;
        }
        if (roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            Toast.makeText(this, "HR Retro is already the Home app.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME),
            REQUEST_HOME_ROLE);
    }

    private void applyTouchscreenPreference() {
        File external = getExternalFilesDir(null);
        File config = external == null ? null : new File(external, "retroarch.cfg");
        if (config == null || !config.isFile() || config.length() > 2 * 1024 * 1024)
            return;
        final String key = "input_overlay_enable";
        final String value = key + " = \"" +
            (prefs.getBoolean("touchscreen_controls", false) ? "true" : "false") + "\"";
        File tmp = null;
        try {
            ArrayList<String> lines = new ArrayList<>();
            boolean replaced = false;
            java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(config),
                    java.nio.charset.StandardCharsets.UTF_8));
            try {
                for (String line; (line = in.readLine()) != null;) {
                    if (line.trim().startsWith(key + " ") || line.trim().startsWith(key + "=")) {
                        if (!replaced) lines.add(value);
                        replaced = true;
                    } else lines.add(line);
                }
            } finally { in.close(); }
            if (!replaced) lines.add(value);
            tmp = File.createTempFile("hrretro-overlay-", ".cfg", config.getParentFile());
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
            try {
                java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(out,
                    java.nio.charset.StandardCharsets.UTF_8);
                for (String line : lines) { writer.write(line); writer.write('\n'); }
                writer.flush(); out.getFD().sync();
                writer.close();
            } finally { out.close(); }
            if (!tmp.renameTo(config)) throw new java.io.IOException("Could not save overlay setting.");
        } catch (Exception ex) {
            Log.w(TAG, "Couldn't update touchscreen controls setting", ex);
        } finally {
            if (tmp != null && tmp.exists()) tmp.delete();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_ARTWORK && resultCode == RESULT_OK && data != null &&
                data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString("artwork:" + systems.get(selectedSystem),
                    uri.toString()).apply();
                refreshHero();
            } catch (Exception ex) {
                Toast.makeText(this, "Couldn't save that artwork. Choose another image.",
                    Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onKeyDown(int key, KeyEvent event) {
        int focusedTab = tabViews.indexOf(getCurrentFocus());
        if (focusedTab >= 0 && (key == KeyEvent.KEYCODE_DPAD_LEFT ||
                key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int step = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            int next = Math.max(0, Math.min(tabViews.size() - 1, focusedTab + step));
            tabViews.get(next).requestFocus();
            tabScroll.smoothScrollTo(tabViews.get(next).getLeft(), 0);
            return true;
        }
        if (focusedTab >= 0 && key == KeyEvent.KEYCODE_DPAD_DOWN && !filtered.isEmpty()) {
            gameList.setSelection(0);
            gameList.requestFocus();
            return true;
        }
        if (getCurrentFocus() == gameList && key == KeyEvent.KEYCODE_DPAD_UP &&
                !tabViews.isEmpty()) {
            tabViews.get(selectedSystem).requestFocus();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_L1) {
            selectSystem(Math.max(0, selectedSystem - 1), false);
            tabViews.get(selectedSystem).requestFocus();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_R1) {
            selectSystem(Math.min(systems.size() - 1, selectedSystem + 1), false);
            tabViews.get(selectedSystem).requestFocus();
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
            detail.setText(game.androidApp ? "ANDROID GAME   •   A  OPEN" : systemLabel + "   •   " +
                (core == null ? "Manual setup" :
                 installed ? "A  Play" : "Install " + core.display));
            return row;
        }
    }
}

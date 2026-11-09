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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
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
    private static final int PICK_ARTWORK = 7412;
    private static final int REQUEST_HOME_ROLE = 7413;
    private static final int PICK_LOGO = 7414;

    private static final class Game {
        final String system;
        final String label;
        final String path;
        final boolean androidApp;
        final String database;
        final String artwork;
        Game(String system, String label, String path) {
            this(system, label, path, false, "", "");
        }
        Game(String system, String label, String path, boolean androidApp) {
            this(system, label, path, androidApp, "", "");
        }
        Game(String system, String label, String path, boolean androidApp,
                String database, String artwork) {
            this.system = system;
            this.label = label;
            this.path = path;
            this.androidApp = androidApp;
            this.database = database;
            this.artwork = artwork;
        }
    }

    private final List<Game> games = new ArrayList<>();
    private final List<Game> filtered = new ArrayList<>();
    private final List<String> systems = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();
    private final Map<String, File> boxArtCache = new HashMap<>();
    private final AtomicBoolean installing = new AtomicBoolean(false);
    private final AtomicBoolean scrapingArt = new AtomicBoolean(false);
    private SharedPreferences prefs;
    private File playlistDir;
    private File thumbnailDir;
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
    private ImageView heroLogo;
    private FrameLayout hero;
    private HorizontalScrollView tabScroll;
    private LinearLayout homePanel;
    private LinearLayout gamesPanel;
    private TextView gamesTitle;
    private TextView gamesCount;
    private TextView backButton;
    private boolean inGamePanel = false;
    private boolean hasPrompted = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            playlistDir = HRSetupActivity.playlistDirectory(this);
            thumbnailDir = HRSetupActivity.thumbnailDirectory(this);
        } catch (Exception ex) {
            Log.e(TAG, "Playlist path unavailable", ex);
            playlistDir = new File(getFilesDir(), "playlists");
            thumbnailDir = new File(getFilesDir(), "thumbnails");
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
        d.setCornerRadius(dp(7));
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
                        String database = item.optString("db_name", "");
                        String artwork = item.optString("thumbnail",
                            item.optString("boxart", ""));
                        games.add(new Game(system, label, uri, false, database, artwork));
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
        if (systems.isEmpty())
            systems.add("Add Games");
        // The console carousel always opens at its left edge. Games appear
        // only after a system has been opened from this shelf.
        selectedSystem = 0;
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
        hideSystemBars();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundColor(DARK);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(5), dp(18), 0);
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

        homePanel = new LinearLayout(this);
        homePanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(homePanel, new LinearLayout.LayoutParams(-1, 0, 1.0f));

        hero = new FrameLayout(this);
        hero.setId(View.generateViewId());
        hero.setBackground(background(SURFACE, false));
        hero.setClipToOutline(true);
        hero.setFocusable(true);
        hero.setClickable(true);
        hero.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSystemGames(); }
        });
        homePanel.addView(hero, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        heroImage = new ImageView(this);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setAlpha(.84f);
        hero.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { Color.argb(155, 7, 9, 12), Color.argb(12, 7, 9, 12),
                Color.argb(185, 7, 9, 12) }));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout heroCopy = new LinearLayout(this);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setGravity(Gravity.TOP | Gravity.LEFT);
        heroCopy.setPadding(dp(34), dp(34), dp(24), dp(16));
        FrameLayout.LayoutParams heroCopyParams = new FrameLayout.LayoutParams(-1, -1);
        hero.addView(heroCopy, heroCopyParams);
        TextView eyebrow = text("HR RETRO  /  SYSTEM SELECT", 11, YELLOW, true);
        heroCopy.addView(eyebrow);
        heroLogo = new ImageView(this);
        heroLogo.setScaleType(ImageView.ScaleType.FIT_START);
        heroLogo.setAdjustViewBounds(true);
        heroLogo.setVisibility(View.GONE);
        heroCopy.addView(heroLogo, new LinearLayout.LayoutParams(dp(440), dp(118)));
        heroTitle = text("SELECT A SYSTEM", 40, TEXT, true);
        heroTitle.setShadowLayer(dp(5), 0, dp(2), Color.BLACK);
        heroCopy.addView(heroTitle);
        heroCount = text("Browse by system", 13, TEXT, false);
        heroCount.setShadowLayer(dp(3), 0, dp(1), Color.BLACK);
        heroCopy.addView(heroCount);

        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(false);
        tabScroll.setFocusable(false);
        tabScroll.setPadding(dp(18), 0, dp(18), dp(4));

        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabScroll.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -1));
        rebuildTabs();

        gamesPanel = new LinearLayout(this);
        gamesPanel.setOrientation(LinearLayout.VERTICAL);
        gamesPanel.setPadding(dp(4), dp(4), dp(4), 0);
        gamesPanel.setVisibility(View.GONE);
        root.addView(gamesPanel, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        LinearLayout gamePanelHeader = new LinearLayout(this);
        gamePanelHeader.setOrientation(LinearLayout.HORIZONTAL);
        gamePanelHeader.setGravity(Gravity.CENTER_VERTICAL);
        gamesPanel.addView(gamePanelHeader, new LinearLayout.LayoutParams(-1, dp(58)));
        backButton = button("← SYSTEMS", false);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSystemsPanel(); }
        });
        gamePanelHeader.addView(backButton);
        LinearLayout gamePanelLabels = new LinearLayout(this);
        gamePanelLabels.setOrientation(LinearLayout.VERTICAL);
        gamePanelLabels.setPadding(dp(14), 0, 0, 0);
        gamePanelHeader.addView(gamePanelLabels,
            new LinearLayout.LayoutParams(0, -1, 1.0f));
        gamesTitle = text("SYSTEM", 17, YELLOW, true);
        gamePanelLabels.addView(gamesTitle);
        gamesCount = text("0 GAMES", 10, MUTED, false);
        gamePanelLabels.addView(gamesCount);

        gameList = new ListView(this);
        gameList.setId(View.generateViewId());
        gameList.setBackgroundColor(DARK);
        gameList.setCacheColorHint(DARK);
        gameList.setDividerHeight(dp(1));
        gameList.setSelector(background(Color.rgb(39, 42, 45), true));
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
        gamesPanel.addView(gameList, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        wireFocusNavigation();

        status = text("←/→  SYSTEMS  •  A  OPEN  •  OPTIONS  ART / CONTROLS", 10, MUTED, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(24)));
        root.addView(tabScroll, new LinearLayout.LayoutParams(-1, dp(58)));
        getWindow().getDecorView().post(new Runnable() {
            @Override public void run() {
                if (!tabViews.isEmpty())
                    tabViews.get(0).requestFocus();
            }
        });
    }

    private void hideSystemBars() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        getWindow().getDecorView().setSystemUiVisibility(flags);
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
                @Override public void onClick(View v) {
                    selectSystem(tab, false);
                    openSystemGames();
                }
            });
        }
        if (gameList != null)
            wireFocusNavigation();
    }

    private void wireFocusNavigation() {
        if (hero == null || tabViews.isEmpty()) return;
        for (TextView tab : tabViews)
            tab.setNextFocusUpId(hero.getId());
        hero.setNextFocusDownId(tabViews.get(selectedSystem).getId());
        if (backButton != null && gameList != null)
            backButton.setNextFocusDownId(gameList.getId());
    }

    private void selectSystem(int index, boolean focusGames) {
        if (index < 0 || index >= systems.size())
            return;
        selectedSystem = index;
        prefs.edit().putString("last_system", systems.get(index)).apply();
        rebuildTabs();
        showGames();
        if (focusGames) openSystemGames();
    }

    private void showGames() {
        if (gameList == null || adapter == null)
            return;
        filtered.clear();
        for (Game g : games)
            if (systems.get(selectedSystem).equals(g.system))
                filtered.add(g);
        adapter.notifyDataSetChanged();
        int missing = missingCores().size();
        int guided = guidedSystems().size();
        coresButton.setText(missing + guided == 0 ? "EMULATORS ✓" :
            "SETUP " + (missing + guided));
        header.setText("HR RETRO");
        refreshHero();
        if (gamesTitle != null) gamesTitle.setText(HRCoreCatalog.shortSystem(systems.get(selectedSystem)));
        if (gamesCount != null) gamesCount.setText(filtered.size() + " GAMES");
        if (inGamePanel)
            status.setText("A  PLAY  •  B  SYSTEMS  •  HOLD A GAME FOR HELP");
        else
            status.setText("←/→  SYSTEMS  •  A  OPEN  •  OPTIONS  ARTWORK / CONTROLS");
        gameList.setSelection(0);
    }

    private void openSystemGames() {
        if (systems.isEmpty() || "Add Games".equals(systems.get(selectedSystem))) {
            openSetup();
            return;
        }
        inGamePanel = true;
        homePanel.setVisibility(View.GONE);
        gamesPanel.setVisibility(View.VISIBLE);
        showGames();
        gameList.setSelection(0);
        gameList.requestFocus();
        if (filtered.isEmpty())
            status.setText("NO GAMES FOR THIS SYSTEM  •  B  BACK");
    }

    private void showSystemsPanel() {
        inGamePanel = false;
        gamesPanel.setVisibility(View.GONE);
        homePanel.setVisibility(View.VISIBLE);
        status.setText("←/→  SYSTEMS  •  A  OPEN  •  OPTIONS  ARTWORK / CONTROLS");
        if (selectedSystem >= 0 && selectedSystem < tabViews.size())
            tabViews.get(selectedSystem).requestFocus();
    }

    private void refreshHero() {
        if (heroTitle == null || systems.isEmpty()) return;
        String system = systems.get(selectedSystem);
        heroTitle.setText(HRCoreCatalog.shortSystem(system));
        String logo = prefs.getString("logo:" + system, null);
        if (logo == null || logo.isEmpty()) {
            heroLogo.setVisibility(View.GONE);
            heroTitle.setVisibility(View.VISIBLE);
        } else {
            try {
                heroLogo.setImageURI(Uri.parse(logo));
                heroLogo.setVisibility(View.VISIBLE);
                heroTitle.setVisibility(View.GONE);
            } catch (RuntimeException ex) {
                Log.w(TAG, "Could not load saved console logo", ex);
                heroLogo.setVisibility(View.GONE);
                heroTitle.setVisibility(View.VISIBLE);
            }
        }
        int count = 0;
        for (Game game : games) if (system.equals(game.system)) count++;
        heroCount.setText(count + (count == 1 ? " GAME" : " GAMES")
            + "  ·  A  OPEN SYSTEM");
        String artwork = prefs.getString("artwork:" + system, null);
        if (artwork == null) {
            Bitmap cover = null;
            for (Game game : games) {
                if (system.equals(game.system)) {
                    cover = decodeBoxArt(findBoxArt(game));
                    if (cover != null) break;
                }
            }
            heroImage.setImageBitmap(cover == null ? pixelSystemArtwork(system) : cover);
        } else {
            try {
                heroImage.setImageURI(Uri.parse(artwork));
            } catch (RuntimeException ex) {
                Log.w(TAG, "Could not load saved system artwork", ex);
                heroImage.setImageDrawable(null);
            }
        }
    }

    private Bitmap pixelSystemArtwork(String system) {
        Bitmap bitmap = Bitmap.createBitmap(640, 280, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        canvas.drawColor(DARK);
        paint.setColor(SURFACE);
        canvas.drawRect(0, 0, 640, 280, paint);
        int seed = system.hashCode();
        paint.setColor(Color.rgb(42, 47, 51));
        for (int i = 0; i < 45; i++) {
            seed = seed * 1103515245 + 12345;
            int x = Math.abs(seed % 620);
            seed = seed * 1103515245 + 12345;
            int y = Math.abs(seed % 260);
            int size = 5 + Math.abs(seed % 18);
            canvas.drawRect(x, y, x + size, y + size, paint);
        }
        paint.setColor(YELLOW);
        canvas.drawRect(458, 42, 574, 158, paint);
        paint.setColor(DARK);
        canvas.drawRect(470, 54, 562, 146, paint);
        paint.setColor(YELLOW);
        canvas.drawRect(486, 70, 546, 130, paint);
        paint.setColor(TEXT);
        canvas.drawRect(498, 82, 510, 94, paint);
        canvas.drawRect(522, 82, 534, 94, paint);
        canvas.drawRect(510, 106, 522, 118, paint);
        paint.setColor(YELLOW);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setFakeBoldText(true);
        paint.setTextSize(11);
        canvas.drawText("HR RETRO  /  PLATFORM", 34, 74, paint);
        String title = HRCoreCatalog.shortSystem(system).toUpperCase(Locale.US);
        paint.setTextSize(34);
        while (paint.measureText(title) > 400 && paint.getTextSize() > 18)
            paint.setTextSize(paint.getTextSize() - 2);
        canvas.drawText(title, 34, 142, paint);
        paint.setColor(TEXT);
        paint.setTextSize(13);
        paint.setFakeBoldText(false);
        canvas.drawText("A  OPEN LIBRARY     OPTIONS  CUSTOMIZE ART", 34, 188, paint);
        return bitmap;
    }

    private String normalizedArtName(String name) {
        return name.toLowerCase(Locale.US).replaceAll("\\.[^.]+$", "")
            .replaceAll("[^a-z0-9]", "");
    }

    private File findBoxArt(Game game) {
        if (boxArtCache.containsKey(game.path))
            return boxArtCache.get(game.path);
        if (game.artwork != null && game.artwork.length() > 0) {
            File supplied = game.artwork.startsWith("file:")
                ? new File(Uri.parse(game.artwork).getPath()) : new File(game.artwork);
            if (supplied.isFile()) {
                boxArtCache.put(game.path, supplied);
                return supplied;
            }
        }
        File root = thumbnailDir;
        if (root == null || !root.isDirectory()) {
            boxArtCache.put(game.path, null);
            return null;
        }
        ArrayList<String> databases = new ArrayList<>();
        if (game.database != null && !game.database.isEmpty())
            databases.add(game.database.replaceFirst("\\.lpl$", ""));
        if (!databases.contains(game.system)) databases.add(game.system);
        String visiblePlaylist = "HR - " + game.system;
        if (!databases.contains(visiblePlaylist)) databases.add(visiblePlaylist);
        String wanted = normalizedArtName(game.label);
        for (String database : databases) {
            File directory = new File(new File(root, database), "Named_Boxarts");
            File[] covers = directory.listFiles();
            if (covers == null) continue;
            for (File cover : covers) {
                String name = cover.getName();
                if (cover.isFile() && normalizedArtName(name).equals(wanted)) {
                    boxArtCache.put(game.path, cover);
                    return cover;
                }
            }
        }
        boxArtCache.put(game.path, null);
        return null;
    }

    private Bitmap decodeBoxArt(File file) {
        if (file == null || !file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, Math.min(bounds.outWidth / 160, bounds.outHeight / 200));
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private Bitmap pixelGameArtwork(Game game) {
        Bitmap bitmap = Bitmap.createBitmap(128, 168, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        int shade = (game.label.hashCode() & 0x7fffffff) % 35;
        canvas.drawColor(Color.rgb(22 + shade, 25 + shade, 29 + shade));
        paint.setColor(YELLOW);
        canvas.drawRect(13, 16, 115, 22, paint);
        paint.setColor(TEXT);
        canvas.drawRect(32, 47, 96, 111, paint);
        paint.setColor(SURFACE);
        canvas.drawRect(40, 55, 88, 103, paint);
        paint.setColor(YELLOW);
        canvas.drawRect(52, 68, 76, 92, paint);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setFakeBoldText(true);
        paint.setTextSize(10);
        String title = game.label.replace('_', ' ').toUpperCase(Locale.US);
        String line = title.length() > 18 ? title.substring(0, 18) : title;
        float width = paint.measureText(line);
        canvas.drawText(line, Math.max(5, (128 - width) / 2), 143, paint);
        paint.setTextSize(8);
        String platform = HRCoreCatalog.shortSystem(game.system).toUpperCase(Locale.US);
        canvas.drawText(platform.length() > 18 ? platform.substring(0, 18) : platform,
            8, 158, paint);
        return bitmap;
    }

    private List<HRCoreCatalog.Core> missingCores() {
        List<HRCoreCatalog.Core> cores = HRCoreCatalog.requiredCores(systems);
        ArrayList<HRCoreCatalog.Core> missing = new ArrayList<>();
        for (HRCoreCatalog.Core c : cores)
            if (!HRCoreInstaller.isInstalled(this, c))
                missing.add(c);
        return missing;
    }

    private List<String> guidedSystems() {
        ArrayList<String> result = new ArrayList<>();
        for (String system : systems) {
            if ("Android Games".equals(system) || "Add Games".equals(system)) continue;
            if (HRCoreCatalog.forSystem(system) == null) result.add(system);
        }
        return result;
    }

    private void offerMissingCores() {
        if (installing.get())
            return;
        final List<HRCoreCatalog.Core> missing = missingCores();
        final List<String> guides = guidedSystems();
        if (missing.isEmpty() && guides.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Emulators ready")
                .setMessage("Every scanned system has its recommended RetroArch core installed.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }
        StringBuilder message = new StringBuilder();
        if (!missing.isEmpty()) {
            prefs.edit().putBoolean("cores_approval_prompted", true).apply();
            message.append("RetroArch cores to install:\n");
            for (HRCoreCatalog.Core core : missing)
                message.append("• ").append(core.display).append("\n");
        }
        if (!guides.isEmpty()) {
            message.append("\nSystems that use a separate Android app:\n");
            for (String system : guides)
                message.append("• ").append(HRCoreCatalog.shortSystem(system)).append("\n");
        }
        if (!missing.isEmpty())
            message.append("\nCore downloads come from Libretro. No games or BIOS are included.");
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
            .setTitle("System setup")
            .setMessage(message.toString());
        if (!missing.isEmpty())
            dialog.setPositiveButton("Install cores", (d, w) -> installCores(missing));
        if (!guides.isEmpty())
            dialog.setNeutralButton("System guides", (d, w) -> showSystemGuidePicker());
        dialog.setNegativeButton("Later", null).show();
    }

    private void showSystemGuidePicker() {
        final List<String> guides = guidedSystems();
        if (guides.isEmpty()) return;
        String[] names = new String[guides.size()];
        for (int i = 0; i < guides.size(); i++)
            names[i] = HRCoreCatalog.shortSystem(guides.get(i));
        new AlertDialog.Builder(this).setTitle("Choose a system")
            .setItems(names, (dialog, which) -> {
                String system = guides.get(which);
                showAdvancedSystemOptions(new Game(system, "", ""));
            }).setNegativeButton("Back", null).show();
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
        final boolean threeDs = "Nintendo - Nintendo 3DS".equals(system);
        final boolean xbox = system.toLowerCase(Locale.US).contains("xbox");
        final boolean dolphin = "Nintendo - GameCube".equals(system)
                || "Nintendo - Wii".equals(system);
        final boolean ps2 = "Sony - PlayStation 2".equals(system);
        if (threeDs) {
            final String packageName = "org.azahar_emu.azahar";
            final Intent installed = getPackageManager().getLaunchIntentForPackage(packageName);
            String message = "HR Retro found Nintendo 3DS games. Azahar publishes an Android build from its official releases.\n\n" +
                (installed == null
                    ? "Open the official release page, download the Android Vanilla APK, then tap it in Downloads and approve Android's installer. Open Azahar once and grant access to your 3DS folder."
                    : "Azahar is installed. Open it and select your 3DS folder. HR Retro does not yet pass individual 3DS files directly to that app.");
            new AlertDialog.Builder(this)
                .setTitle("Nintendo 3DS setup")
                .setMessage(message)
                .setPositiveButton(installed == null ? "Official APKs" : "Open Azahar",
                    (d, w) -> {
                        if (installed != null) startActivity(installed);
                        else openUrl("https://github.com/azahar-emu/azahar/releases/latest");
                    })
                .setNeutralButton("Advanced", (d, w) -> openRetroArch(null, null))
                .setNegativeButton("Back", null).show();
            return;
        }
        if (xbox) {
            new AlertDialog.Builder(this)
                .setTitle("Xbox setup")
                .setMessage("HR Retro found Xbox games, but the official xemu project currently publishes desktop builds, not an Android APK. I won't point you at an unverified APK. Keep these games in the library while we add a vetted Android emulator guide if a supported option becomes available.")
                .setPositiveButton("Official xemu project",
                    (d, w) -> openUrl("https://xemu.app/"))
                .setNegativeButton("Back", null).show();
            return;
        }
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
            .setPositiveButton(installed != null ? "Open " + appName :
                    dolphin ? "Dolphin downloads" : "OK",
                (d, w) -> {
                    if (installed != null)
                        startActivity(installed);
                    else if (dolphin)
                        openUrl("https://dolphin-emu.org/download/");
                })
            .setNeutralButton("Advanced", (d, w) -> openRetroArch(null, null))
            .setNegativeButton("Back", null)
            .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ex) {
            Toast.makeText(this, "No browser is available to open that project page.",
                Toast.LENGTH_SHORT).show();
        }
    }

    private void showGameHelp(final Game game) {
        if (game.androidApp) {
            new AlertDialog.Builder(this).setTitle("Android game")
                .setMessage("This opens the installed Android game directly.")
                .setPositiveButton("OK", null).show();
            return;
        }
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
        } else if (HRCoreCatalog.forSystem(system) == null) {
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showSystemsPanel();
    }

    private void showOptions() {
        final String[] options = {
            "Scrape box art now",
            "Customize system artwork",
            "Controls",
            "Use HR Retro as Android Home",
            "Advanced RetroArch settings"
        };
        new AlertDialog.Builder(this)
            .setTitle("HR Retro options")
            .setItems(options, (dialog, which) -> {
                if (which == 0) scrapeExistingBoxArt();
                else if (which == 1) showArtworkOptions();
                else if (which == 2) showControlsOptions();
                else if (which == 3) requestHomeRole();
                else openRetroArch(null, null);
            }).show();
    }

    private void showArtworkOptions() {
        final String[] choices = {
            "Choose background image",
            "Use a game's box art as background",
            "Choose console logo PNG",
            "Reset to automatic art"
        };
        new AlertDialog.Builder(this).setTitle("Customize " +
            HRCoreCatalog.shortSystem(systems.get(selectedSystem)))
            .setItems(choices, (dialog, which) -> {
                if (which == 0) pickArtwork();
                else if (which == 1) chooseGameCover();
                else if (which == 2) pickLogo();
                else {
                    String system = systems.get(selectedSystem);
                    prefs.edit().remove("artwork:" + system)
                        .remove("logo:" + system).apply();
                    refreshHero();
                }
            }).show();
    }

    private void chooseGameCover() {
        final ArrayList<Game> choices = new ArrayList<>();
        final ArrayList<String> labels = new ArrayList<>();
        String system = systems.get(selectedSystem);
        for (Game game : games) {
            if (system.equals(game.system) && findBoxArt(game) != null) {
                choices.add(game);
                labels.add(game.label.replace('_', ' '));
            }
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, "Scrape or add game artwork first.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Choose a box-art background")
            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                File cover = findBoxArt(choices.get(which));
                if (cover != null) {
                    prefs.edit().putString("artwork:" + system,
                        Uri.fromFile(cover).toString()).apply();
                    refreshHero();
                }
            }).setNegativeButton("Cancel", null).show();
    }

    private void scrapeExistingBoxArt() {
        if (!scrapingArt.compareAndSet(false, true)) return;
        status.setText("LOOKING UP BOX ART…");
        new Thread(() -> {
            try {
                HRThumbnailScraper.Result result = HRThumbnailScraper.scrape(
                    HRLibraryActivity.this, playlistDir, new AtomicBoolean(false),
                    (completed, total, downloaded) -> {
                        if (completed % 20 == 0 || completed == total) {
                            runOnUiThread(() -> status.setText(
                                "BOX ART  " + completed + " / " + total +
                                "  •  DOWNLOADED " + downloaded));
                        }
                    });
                runOnUiThread(() -> {
                    scrapingArt.set(false);
                    boxArtCache.clear();
                    showGames();
                    status.setText("BOX ART READY  •  " + result.downloaded +
                        " NEW  •  " + result.alreadyPresent + " CACHED  •  " +
                        result.missing + " NOT FOUND");
                });
            } catch (Exception ex) {
                Log.w(TAG, "Box art scan failed", ex);
                runOnUiThread(() -> {
                    scrapingArt.set(false);
                    status.setText("BOX ART LOOKUP FAILED  •  CHECK YOUR CONNECTION");
                });
            }
        }, "HRRetro-box-art").start();
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
        pickImage(PICK_ARTWORK);
    }

    private void pickLogo() {
        pickImage(PICK_LOGO);
    }

    private void pickImage(int requestCode) {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(pick, requestCode);
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
        // Until the player changes this hidden option, preserve whatever
        // overlay behavior their existing RetroArch configuration uses.
        if (!prefs.contains("touchscreen_controls"))
            return;
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
        if ((requestCode == PICK_ARTWORK || requestCode == PICK_LOGO) &&
                resultCode == RESULT_OK && data != null &&
                data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String key = requestCode == PICK_LOGO ? "logo:" : "artwork:";
                prefs.edit().putString(key + systems.get(selectedSystem),
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
        if (!inGamePanel && focusedTab >= 0 && (key == KeyEvent.KEYCODE_DPAD_LEFT ||
                key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int step = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            int next = Math.max(0, Math.min(tabViews.size() - 1, focusedTab + step));
            selectSystem(next, false);
            tabViews.get(next).requestFocus();
            tabScroll.smoothScrollTo(tabViews.get(next).getLeft(), 0);
            return true;
        }
        if (!inGamePanel && focusedTab >= 0 && key == KeyEvent.KEYCODE_DPAD_UP) {
            hero.requestFocus();
            return true;
        }
        if (!inGamePanel && getCurrentFocus() == hero && key == KeyEvent.KEYCODE_DPAD_DOWN &&
                !tabViews.isEmpty()) {
            tabViews.get(selectedSystem).requestFocus();
            return true;
        }
        if (inGamePanel && getCurrentFocus() == gameList && key == KeyEvent.KEYCODE_DPAD_UP) {
            backButton.requestFocus();
            return true;
        }
        if (inGamePanel && getCurrentFocus() == backButton && key == KeyEvent.KEYCODE_DPAD_DOWN) {
            gameList.setSelection(0);
            gameList.requestFocus();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_L1) {
            if (inGamePanel) { showSystemsPanel(); return true; }
            selectSystem(Math.max(0, selectedSystem - 1), false);
            tabViews.get(selectedSystem).requestFocus();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_R1) {
            if (inGamePanel) { showSystemsPanel(); return true; }
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
            if (inGamePanel) showSystemsPanel();
            else finish();
            return true;
        }
        return super.onKeyDown(key, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (gameList != null && !installing.get())
            showGames();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private final class GameAdapter extends BaseAdapter {
        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout row;
            ImageView cover;
            TextView title;
            TextView detail;
            if (recycled instanceof LinearLayout) {
                row = (LinearLayout) recycled;
                cover = (ImageView) row.getChildAt(0);
                LinearLayout labels = (LinearLayout) row.getChildAt(1);
                title = (TextView) labels.getChildAt(0);
                detail = (TextView) labels.getChildAt(1);
            } else {
                row = new LinearLayout(HRLibraryActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(7), dp(12), dp(7));
                cover = new ImageView(HRLibraryActivity.this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams coverParams =
                    new LinearLayout.LayoutParams(dp(66), dp(92));
                coverParams.setMargins(0, 0, dp(14), 0);
                row.addView(cover, coverParams);
                LinearLayout labels = new LinearLayout(HRLibraryActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(labels, new LinearLayout.LayoutParams(0, -1, 1.0f));
                title = text("", 18, TEXT, true);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                detail = text("", 10, MUTED, false);
                detail.setSingleLine(true);
                labels.addView(title);
                labels.addView(detail);
                row.setBackground(background(SURFACE, false));
            }
            Game game = filtered.get(position);
            if (game.androidApp) {
                try {
                    Drawable icon = getPackageManager().getApplicationIcon(game.path);
                    cover.setImageDrawable(icon);
                } catch (PackageManager.NameNotFoundException ex) {
                    cover.setImageBitmap(pixelGameArtwork(game));
                }
            } else if (game.artwork != null && game.artwork.startsWith("content://")) {
                cover.setImageURI(Uri.parse(game.artwork));
            } else {
                Bitmap art = decodeBoxArt(findBoxArt(game));
                cover.setImageBitmap(art == null ? pixelGameArtwork(game) : art);
            }
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

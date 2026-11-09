package com.retroarch.browser.retroactivity;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional, on-device box-art fetcher using RetroArch's official thumbnail host. */
final class HRThumbnailScraper {
    private static final String TAG = "HRThumbnailScraper";
    private static final String BASE = "https://thumbnails.libretro.com/";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;
    private static final int MAX_INDEX_BYTES = 16 * 1024 * 1024;
    private static final Pattern PNG_LINK = Pattern.compile(
        "href=\"([^\"]+\\.png)\"", Pattern.CASE_INSENSITIVE);

    interface Progress {
        void onProgress(int completed, int total, int downloaded);
    }

    static final class Result {
        final int downloaded;
        final int alreadyPresent;
        final int missing;

        Result(int downloaded, int alreadyPresent, int missing) {
            this.downloaded = downloaded;
            this.alreadyPresent = alreadyPresent;
            this.missing = missing;
        }
    }

    private static final class Entry {
        final String system;
        final String playlist;
        final String label;
        Entry(String system, String playlist, String label) {
            this.system = system;
            this.playlist = playlist;
            this.label = label;
        }
    }

    static Result scrape(Context context, File playlistDir, AtomicBoolean cancelled,
                         Progress progress) throws Exception {
        File[] playlists = playlistDir == null ? null : playlistDir.listFiles();
        ArrayList<Entry> entries = new ArrayList<>();
        if (playlists != null) {
            for (File playlist : playlists) {
                String fileName = playlist.getName();
                if (!playlist.isFile() || !fileName.startsWith("HR - ") ||
                        !fileName.endsWith(".lpl"))
                    continue;
                JSONObject data = HRCorePlaylistBinder.readJson(playlist);
                JSONArray items = data.optJSONArray("items");
                if (items == null) continue;
                String playlistSystem = fileName.substring(5, fileName.length() - 4);
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String label = item.optString("label", "").trim();
                    String database = item.optString("db_name", playlistSystem + ".lpl");
                    String system = database.replaceFirst("(?i)\\.lpl$", "");
                    if (label.length() > 0 && !"Needs Review".equals(system))
                        entries.add(new Entry(system, "HR - " + playlistSystem, label));
                }
            }
        }

        if (entries.isEmpty()) return new Result(0, 0, 0);
        File thumbnailRoot = HRSetupActivity.thumbnailDirectory(context);
        if (!canReachThumbnailHost())
            return new Result(0, 0, entries.size());
        int downloaded = 0;
        int present = 0;
        int completed = 0;
        Map<String, Map<String, List<String>>> indexes = new HashMap<>();
        for (Entry entry : entries) {
            if (cancelled != null && cancelled.get())
                throw new InterruptedException("Box-art scan cancelled.");
            File boxarts = new File(new File(thumbnailRoot, entry.playlist), "Named_Boxarts");
            if (!boxarts.isDirectory() && !boxarts.mkdirs()) {
                completed++;
                if (progress != null) progress.onProgress(completed, entries.size(), downloaded);
                continue;
            }
            File target = new File(boxarts, safeFilename(entry.label) + ".png");
            if (target.isFile() && target.length() > 0) {
                present++;
            } else {
                if (!indexes.containsKey(entry.system))
                    indexes.put(entry.system, fetchIndex(entry.system, cancelled));
                String match = findMatch(entry.label, indexes.get(entry.system));
                if (match != null && downloadOne(entry, match, target, cancelled))
                    downloaded++;
            }
            completed++;
            if (progress != null) progress.onProgress(completed, entries.size(), downloaded);
        }
        return new Result(downloaded, present, entries.size() - downloaded - present);
    }

    private static boolean canReachThumbnailHost() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.connect();
            return connection.getResponseCode() >= 0;
        } catch (Exception ex) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Index each console's official directory once, then match ROM names to actual assets. */
    private static Map<String, List<String>> fetchIndex(String system,
                                                         AtomicBoolean cancelled) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE + Uri.encode(system) +
                "/Named_Boxarts/").openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "HR-Retro-Android");
            connection.setRequestProperty("Accept", "text/html");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                return null;
            StringBuilder html = new StringBuilder();
            int total = 0;
            try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[16 * 1024];
                for (int count; (count = in.read(buffer)) != -1;) {
                    if (cancelled != null && cancelled.get())
                        throw new InterruptedException("Box-art scan cancelled.");
                    total += count;
                    if (total > MAX_INDEX_BYTES)
                        throw new java.io.IOException("Thumbnail index exceeded size limit.");
                    html.append(new String(buffer, 0, count, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            Map<String, List<String>> index = new HashMap<>();
            Matcher matcher = PNG_LINK.matcher(html);
            while (matcher.find()) {
                String name = Uri.decode(matcher.group(1).replace("&amp;", "&"));
                int slash = name.lastIndexOf('/');
                if (slash >= 0) name = name.substring(slash + 1);
                addIndexName(index, normalize(name.replaceFirst("(?i)\\.png$", "")), name);
                String base = withoutReleaseTags(name.replaceFirst("(?i)\\.png$", ""));
                addIndexName(index, "base:" + normalize(base), name);
            }
            return index;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            android.util.Log.d(TAG, "Could not read thumbnail list for " + system, ex);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void addIndexName(Map<String, List<String>> index,
                                     String key, String filename) {
        List<String> names = index.get(key);
        if (names == null) {
            names = new ArrayList<>();
            index.put(key, names);
        }
        names.add(filename);
    }

    private static String findMatch(String label, Map<String, List<String>> index) {
        if (index == null || index.isEmpty()) return null;
        List<String> candidates = index.get(normalize(label));
        if (candidates == null || candidates.isEmpty())
            candidates = index.get("base:" + normalize(withoutReleaseTags(label)));
        if (candidates == null || candidates.isEmpty()) return null;
        ArrayList<String> ordered = new ArrayList<>(candidates);
        final String wanted = label.toLowerCase(Locale.US);
        Collections.sort(ordered, (a, b) -> scoreRegion(b, wanted) - scoreRegion(a, wanted));
        return ordered.get(0);
    }

    private static int scoreRegion(String filename, String wanted) {
        String candidate = filename.toLowerCase(Locale.US);
        if (wanted.contains("(japan") && candidate.contains("(japan")) return 100;
        if ((wanted.contains("(usa") || wanted.contains("(us,")) &&
                candidate.contains("(usa")) return 95;
        if (wanted.contains("(europe") && candidate.contains("(europe")) return 90;
        if (candidate.contains("(usa, europe")) return 80;
        if (candidate.contains("(usa")) return 70;
        if (candidate.contains("(europe")) return 60;
        if (candidate.contains("(world")) return 50;
        return 10;
    }

    private static String withoutReleaseTags(String name) {
        return name.replaceAll("\\s*\\([^)]*\\)", "")
            .replaceAll("\\s*\\[[^]]*\\]", "").trim();
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static boolean downloadOne(Entry entry, String remoteFilename,
                                       File target, AtomicBoolean cancelled) {
        if (cancelled != null && cancelled.get()) return false;
        HttpURLConnection connection = null;
        File temp = null;
        try {
            String address = BASE + Uri.encode(entry.system) + "/Named_Boxarts/" +
                Uri.encode(remoteFilename);
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "HR-Retro-Android");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return false;
            String type = connection.getContentType();
            if (type != null && !type.toLowerCase(Locale.US).startsWith("image/"))
                return false;
            temp = File.createTempFile("hrretro-art-", ".tmp", target.getParentFile());
            int total = 0;
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16 * 1024];
                for (int count; (count = in.read(buffer)) != -1;) {
                    if (cancelled != null && cancelled.get())
                        throw new InterruptedException("Box-art scan cancelled.");
                    total += count;
                    if (total > MAX_IMAGE_BYTES)
                        throw new java.io.IOException("Thumbnail exceeded size limit.");
                    out.write(buffer, 0, count);
                }
                out.getFD().sync();
            }
            return temp.length() > 0 && temp.renameTo(target);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            android.util.Log.d(TAG, "Thumbnail unavailable for " + entry.label, ex);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
            if (temp != null && temp.exists()) temp.delete();
        }
    }

    private static String safeFilename(String name) {
        return name.replaceAll("[&*/:<>?\\\\|]", "_").trim();
    }

    private HRThumbnailScraper() {}
}

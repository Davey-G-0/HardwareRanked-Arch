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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional, on-device box-art fetcher using RetroArch's official thumbnail host. */
final class HRThumbnailScraper {
    private static final String TAG = "HRThumbnailScraper";
    private static final String BASE = "https://thumbnails.libretro.com/";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;

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
            } else if (downloadOne(entry, target, cancelled)) {
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

    private static boolean downloadOne(Entry entry, File target, AtomicBoolean cancelled) {
        List<String> names = new ArrayList<>();
        names.add(entry.label);
        int tag = entry.label.indexOf(" (");
        if (tag > 0) {
            String shortName = entry.label.substring(0, tag).trim();
            if (!shortName.isEmpty() && !names.contains(shortName)) names.add(shortName);
        }
        for (String name : names) {
            if (cancelled != null && cancelled.get()) return false;
            HttpURLConnection connection = null;
            File temp = null;
            try {
                String fileName = safeFilename(name) + ".png";
                String address = BASE + Uri.encode(entry.system) + "/Named_Boxarts/" +
                    Uri.encode(fileName);
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "HR-Retro-Android");
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) continue;
                String type = connection.getContentType();
                if (type != null && !type.toLowerCase(java.util.Locale.US).startsWith("image/"))
                    continue;
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
                if (temp.length() > 0 && temp.renameTo(target)) return true;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception ex) {
                android.util.Log.d(TAG, "Thumbnail unavailable for " + entry.label, ex);
            } finally {
                if (connection != null) connection.disconnect();
                if (temp != null && temp.exists()) temp.delete();
            }
        }
        return false;
    }

    private static String safeFilename(String name) {
        return name.replaceAll("[&*/:<>?\\\\|]", "_").trim();
    }

    private HRThumbnailScraper() {}
}

/*
 * HR Retro — read-only Android SAF library import.
 * Copyright (C) 2026 HR Retro contributors.
 * License: GPL-3.0-or-later (same terms as the Android RetroArch frontend).
 *
 * The game files are NEVER copied, moved, renamed, modified or deleted.
 * Only app-owned "HR - *.lpl" playlists are written to the existing
 * RetroArch playlist directory.
 */
package com.retroarch.browser.retroactivity;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class HRGameLibraryImporter {
    private static final String TAG = "HRGameLibrary";
    private static final String PREFIX = "HR - ";
    private static final int MAX_DOCUMENTS = 25000;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_PLAYLIST_ENTRIES = 20000;

    private static final String[] COLUMNS = {
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE
    };

    interface Progress {
        void onProgress(int visited, int games);
    }

    static final class Result {
        final int scanned;
        final int games;
        final int needsReview;
        final int systems;
        final boolean limited;

        Result(int scanned, int games, int needsReview, int systems, boolean limited) {
            this.scanned = scanned;
            this.games = games;
            this.needsReview = needsReview;
            this.systems = systems;
            this.limited = limited;
        }
    }

    private static final class Folder {
        final String documentId;
        final String system;
        final int depth;

        Folder(String documentId, String system, int depth) {
            this.documentId = documentId;
            this.system = system;
            this.depth = depth;
        }
    }

    private static final class Game {
        final String name;
        final String documentId;
        Game(String name, String documentId) {
            this.name = name;
            this.documentId = documentId;
        }
    }

    private static final Set<String> IGNORE_FOLDERS = new HashSet<>();
    static {
        Collections.addAll(IGNORE_FOLDERS, "bios", "firmware", "system",
            "saves", "savestates", "savefiles", "screenshots", "thumbnails",
            "assets", "overlays", "shaders", "android", "recyclebin", "trash");
    }

    private static String labelFor(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /**
     * Equivalent of RetroArch's retro_vfs_path_join_saf(documentUri, "").
     * Passing the FULL document Uri allows providers with opaque IDs (not
     * just ExternalStorageProvider's slash-separated document IDs).
     */
    private static String safPath(Uri documentUri) {
        return "saf://" + documentUri.toString()
            .replace("%", "%25")
            .replace("/", "%2F");
    }

    static Result importFolder(ContentResolver resolver, Uri treeUri, File playlistDir,
                               AtomicBoolean cancelled, Progress progress) throws Exception {
        if (treeUri == null || !DocumentsContract.isTreeUri(treeUri))
            throw new IllegalArgumentException("Choose a folder using Android's folder picker.");
        if (playlistDir == null || (!playlistDir.isDirectory() && !playlistDir.mkdirs()))
            throw new java.io.IOException("HR Retro cannot create its playlist folder.");

        LinkedHashMap<String, ArrayList<Game>> groups = new LinkedHashMap<>();
        ArrayDeque<Folder> remaining = new ArrayDeque<>();
        Set<String> visitedDirs = new HashSet<>();
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        remaining.add(new Folder(rootId, null, 0));

        int visited = 0;
        int games = 0;
        boolean limited = false;

        while (!remaining.isEmpty()) {
            if (cancelled.get())
                throw new InterruptedException("Library scan cancelled.");
            Folder folder = remaining.removeFirst();
            if (!visitedDirs.add(folder.documentId))
                continue;
            if (folder.depth > MAX_DEPTH) {
                limited = true;
                continue;
            }
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, folder.documentId);
            try (Cursor cursor = resolver.query(childrenUri, COLUMNS, null, null, null)) {
                if (cursor == null)
                    throw new java.io.IOException("The selected folder is not readable.");
                while (cursor.moveToNext()) {
                    if (cancelled.get())
                        throw new InterruptedException("Library scan cancelled.");
                    if (++visited > MAX_DOCUMENTS) {
                        limited = true;
                        remaining.clear();
                        break;
                    }
                    String documentId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    if (documentId == null || name == null || name.length() == 0)
                        continue;

                    if (Document.MIME_TYPE_DIR.equals(mime)) {
                        String nameKey = HRGameLibraryClassifier.normalize(name);
                        if (IGNORE_FOLDERS.contains(nameKey))
                            continue;
                        String specific = HRGameLibraryClassifier.folderSystem(nameKey);
                        remaining.addLast(new Folder(documentId,
                                specific != null ? specific : folder.system,
                                folder.depth + 1));
                    } else {
                        String system = HRGameLibraryClassifier.systemFor(name, folder.system);
                        if (system != null && games < MAX_PLAYLIST_ENTRIES) {
                            ArrayList<Game> entries = groups.get(system);
                            if (entries == null) {
                                entries = new ArrayList<>();
                                groups.put(system, entries);
                            }
                            entries.add(new Game(name, documentId));
                            games++;
                        } else if (games == MAX_PLAYLIST_ENTRIES) {
                            limited = true;
                        }
                    }
                    if (visited % 150 == 0 && progress != null)
                        progress.onProgress(visited, games);
                }
            }
        }
        if (cancelled.get())
            throw new InterruptedException("Library scan cancelled.");

        // Do not modify game files; write only app-owned playlists.
        // Files with ambiguous extensions are grouped under Needs Review.
        int ambiguous = 0;
        Set<String> generatedFiles = new HashSet<>();
        for (Map.Entry<String, ArrayList<Game>> group : groups.entrySet()) {
            String system = group.getKey();
            if ("Needs Review".equals(system))
                ambiguous = group.getValue().size();
            JSONObject playlist = new JSONObject();
            playlist.put("version", "1.5");
            playlist.put("default_core_path", "DETECT");
            playlist.put("default_core_name", "DETECT");
            playlist.put("label_display_mode", 0);
            playlist.put("right_thumbnail_mode", 0);
            playlist.put("left_thumbnail_mode", 0);
            playlist.put("sort_mode", 0);
            JSONArray items = new JSONArray();
            for (Game game : group.getValue()) {
                JSONObject item = new JSONObject();
                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, game.documentId);
                item.put("path", safPath(documentUri));
                item.put("label", labelFor(game.name));
                item.put("core_path", "DETECT");
                item.put("core_name", "DETECT");
                item.put("crc32", "DETECT");
                item.put("db_name", ("Needs Review".equals(system)
                        ? "HR - Needs Review" : system) + ".lpl");
                items.put(item);
            }
            playlist.put("items", items);
            File target = new File(playlistDir, PREFIX + system + ".lpl");
            generatedFiles.add(target.getName());
            File temp = File.createTempFile("hrretro-", ".tmp", playlistDir);
            boolean committed = false;
            try {
                try (FileOutputStream stream = new FileOutputStream(temp);
                     OutputStreamWriter writer = new OutputStreamWriter(stream,
                            StandardCharsets.UTF_8)) {
                    writer.write(playlist.toString(2));
                    writer.flush();
                    stream.getFD().sync();
                }
                if (!temp.renameTo(target))
                    throw new java.io.IOException("Could not save playlist " + system);
                committed = true;
            } finally {
                if (!committed && temp.exists())
                    temp.delete();
            }
        }
        // A completed rescan replaces *only* files created by HR Retro.
        // Keep user-managed playlists and avoid pruning on truncated scans.
        if (!limited) {
            File[] oldPlaylists = playlistDir.listFiles();
            if (oldPlaylists != null) {
                for (File old : oldPlaylists) {
                    String name = old.getName();
                    if (old.isFile() && name.startsWith(PREFIX)
                            && name.endsWith(".lpl") && !generatedFiles.contains(name)
                            && !old.delete()) {
                        Log.w(TAG, "Could not remove old HR playlist: " + name);
                    }
                }
            }
        }
        if (progress != null)
            progress.onProgress(visited, games);
        Log.i(TAG, "Imported " + games + " games into " + groups.size() + " HR playlists");
        return new Result(visited, games, ambiguous,
                groups.size() - (ambiguous > 0 ? 1 : 0), limited);
    }

    private HRGameLibraryImporter() {}
}

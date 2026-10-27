/*
 * HR Retro — explicitly approved core downloads from official Libretro.
 * No cores, BIOS or ROMs are bundled with HR Retro. Each core has its own
 * upstream license; installation is opt-in and limited to an allowlist.
 *
 * Copyright (C) 2026 HR Retro contributors. GPL-3.0-or-later.
 */
package com.retroarch.browser.retroactivity;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;

final class HRCoreInstaller {
    private static final String TAG = "HRCoreInstaller";
    private static final String HOST = "buildbot.libretro.com";
    private static final String CORE_BASE = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/";
    private static final String INFO_URL = "https://buildbot.libretro.com/assets/frontend/info.zip";
    private static final long MAX_ZIP_BYTES = 64L * 1024 * 1024;
    private static final long MAX_CORE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_INFO_BYTES = 4L * 1024 * 1024;

    interface Listener {
        void onStatus(String status);
    }

    static final class Outcome {
        final int installed;
        final List<String> failed;
        final String infoWarning;
        Outcome(int installed, List<String> failed, String infoWarning) {
            this.installed = installed;
            this.failed = failed;
            this.infoWarning = infoWarning;
        }
    }

    static File coreDir(Context context) {
        return new File(context.getApplicationInfo().dataDir, "cores");
    }

    static File coreFile(Context context, HRCoreCatalog.Core core) {
        return new File(coreDir(context), core.filename());
    }

    static File infoDir(Context context) {
        return new File(context.getApplicationInfo().dataDir, "info");
    }

    static boolean isInstalled(Context context, HRCoreCatalog.Core core) {
        File file = coreFile(context, core);
        return file.isFile() && file.length() > 1024 && validElf(file);
    }

    private static boolean validElf(File file) {
        byte[] prefix = new byte[20];
        try (InputStream in = new FileInputStream(file)) {
            int total = 0;
            while (total < prefix.length) {
                int n = in.read(prefix, total, prefix.length - total);
                if (n < 0) return false;
                total += n;
            }
            // ELF, 64-bit, little-endian, ELF e_machine 183 (AArch64).
            return (prefix[0] & 0xFF) == 0x7f
                && prefix[1] == 'E' && prefix[2] == 'L' && prefix[3] == 'F'
                && prefix[4] == 2 && prefix[5] == 1
                && (prefix[18] & 0xFF) == 183 && (prefix[19] & 0xFF) == 0;
        } catch (IOException ex) {
            return false;
        }
    }

    private static HttpsURLConnection open(String urlString) throws IOException {
        URL url = new URL(urlString);
        for (int attempts = 0; attempts < 3; attempts++) {
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || !HOST.equalsIgnoreCase(url.getHost())
                    || url.getPort() != -1)
                throw new IOException("Core download attempted to leave official Libretro HTTPS.");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setRequestProperty("User-Agent", "HR-Retro-Android-Core-Setup/0.1");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == 307 || status == 308) {
                String redirect = connection.getHeaderField("Location");
                connection.disconnect();
                if (redirect == null)
                    throw new IOException("No redirect URL from Libretro.");
                url = new URL(url, redirect);
                continue;
            }
            if (status != 200) {
                connection.disconnect();
                throw new IOException("Libretro returned HTTP " + status + ".");
            }
            long size = connection.getContentLengthLong();
            if (size > MAX_ZIP_BYTES) {
                connection.disconnect();
                throw new IOException("Archive exceeds maximum supported download size.");
            }
            return connection;
        }
        throw new IOException("Too many redirects downloading the core.");
    }

    private static String basename(String value) {
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return value.substring(slash + 1);
    }

    private static void copyBounded(InputStream in, FileOutputStream out,
                                    long maxBytes) throws IOException {
        byte[] buffer = new byte[32768];
        long size = 0;
        int count;
        while ((count = in.read(buffer)) >= 0) {
            size += count;
            if (size > maxBytes)
                throw new IOException("Core archive exceeded decompression limit.");
            out.write(buffer, 0, count);
        }
        if (size < 1024)
            throw new IOException("Downloaded file was unexpectedly small.");
        out.flush();
        out.getFD().sync();
    }

    private static boolean unzipSingle(String address, String expectedName,
                                       File destination, long sizeLimit) throws IOException {
        File parent = destination.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Could not create core directory.");
        HttpsURLConnection connection = open(address);
        File staging = File.createTempFile("hrretro-core-", ".tmp", parent);
        boolean found = false;
        try {
            try (ZipInputStream zip = new ZipInputStream(
                    new BufferedInputStream(connection.getInputStream()))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (!entry.isDirectory() && name != null
                            && expectedName.equals(basename(name))) {
                        if (found)
                            throw new IOException("Duplicate matching core entry in archive.");
                        try (FileOutputStream output = new FileOutputStream(staging)) {
                            copyBounded(zip, output, sizeLimit);
                        }
                        found = true;
                    }
                    zip.closeEntry();
                }
            }
            if (!found)
                throw new IOException("Official archive did not contain " + expectedName);
            if (destination.getName().endsWith(".so") && !validElf(staging))
                throw new IOException("Downloaded file was not a valid ARM64 emulator core.");
            if (!staging.renameTo(destination))
                throw new IOException("Couldn't install core to app storage.");
            return true;
        } finally {
            connection.disconnect();
            if (staging.exists() && !staging.delete())
                Log.w(TAG, "Could not remove staging file.");
        }
    }

    private static String downloadInfoFiles(Context context,
                                           List<HRCoreCatalog.Core> cores) {
        Set<String> names = new HashSet<>();
        for (HRCoreCatalog.Core core : cores)
            names.add(core.infoName());
        if (names.isEmpty())
            return null;
        File dir = infoDir(context);
        if (!dir.isDirectory() && !dir.mkdirs())
            return "Could not create core metadata directory.";
        HttpsURLConnection connection = null;
        int found = 0;
        try {
            connection = open(INFO_URL);
            try (ZipInputStream zip = new ZipInputStream(
                    new BufferedInputStream(connection.getInputStream()))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName() == null ? "" : basename(entry.getName());
                    if (names.contains(name) && !entry.isDirectory()) {
                        File target = new File(dir, name);
                        File temp = File.createTempFile("hrretro-info-", ".tmp", dir);
                        try {
                            try (FileOutputStream out = new FileOutputStream(temp)) {
                                // .info files are tiny. Use the same safe streaming
                                // path but with a smaller limit per entry.
                                byte[] bytes = new byte[8192];
                                int n;
                                long size = 0;
                                while ((n = zip.read(bytes)) >= 0) {
                                    size += n;
                                    if (size > MAX_INFO_BYTES)
                                        throw new IOException("Core metadata file is too large.");
                                    out.write(bytes, 0, n);
                                }
                                out.flush();
                                out.getFD().sync();
                            }
                            if (!temp.renameTo(target))
                                throw new IOException("Could not install core info.");
                            found++;
                        } finally {
                            if (temp.exists()) temp.delete();
                        }
                    }
                    zip.closeEntry();
                }
            }
            if (found < names.size())
                return "Some core metadata files were not published by Libretro.";
            return null;
        } catch (IOException | SecurityException ex) {
            Log.w(TAG, "Metadata download failed", ex);
            return "Core metadata could not be updated; games may still run.";
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    static Outcome install(Context context, List<HRCoreCatalog.Core> selected,
                           Listener listener) {
        ArrayList<String> failed = new ArrayList<>();
        ArrayList<HRCoreCatalog.Core> ready = new ArrayList<>();
        int installed = 0;
        if (!Build.SUPPORTED_64_BIT_ABIS[0].equals("arm64-v8a")) {
            failed.add("This release only supports ARM64 cores.");
            return new Outcome(0, failed, null);
        }
        for (int index = 0; index < selected.size(); index++) {
            HRCoreCatalog.Core core = selected.get(index);
            if (isInstalled(context, core)) {
                ready.add(core);
                continue;
            }
            if (listener != null)
                listener.onStatus("Installing " + core.display +
                    " (" + (index + 1) + "/" + selected.size() + ")…");
            try {
                String filename = core.filename();
                unzipSingle(CORE_BASE + filename + ".zip",
                    filename, coreFile(context, core), MAX_CORE_BYTES);
                if (!isInstalled(context, core))
                    throw new IOException("Installed core failed integrity checks.");
                ready.add(core);
                installed++;
            } catch (IOException | SecurityException ex) {
                Log.e(TAG, "Failed to install " + core.display, ex);
                failed.add(core.display + ": " + ex.getMessage());
            }
        }
        String infoWarning = null;
        if (!ready.isEmpty()) {
            if (listener != null)
                listener.onStatus("Finishing emulator setup…");
            infoWarning = downloadInfoFiles(context, ready);
        }
        return new Outcome(installed, failed, infoWarning);
    }

    private HRCoreInstaller() {}
}

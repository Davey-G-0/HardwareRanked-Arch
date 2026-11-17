/*
 * HR Retro — download only the curated Azahar Android APK from official
 * GitHub release metadata and verify GitHub's published SHA-256 digest.
 */
package com.retroarch.browser.retroactivity;

import android.content.Context;
import android.content.pm.PackageInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

final class HRExternalApkDownloader {
    private static final String API_URL =
        "https://api.github.com/repos/azahar-emu/azahar/releases/latest";
    private static final long MAX_METADATA_BYTES = 2L * 1024 * 1024;
    private static final long MAX_APK_BYTES = 192L * 1024 * 1024;

    static final class Download {
        final File file;
        final String assetName;
        final String sha256;
        Download(File file, String assetName, String sha256) {
            this.file = file;
            this.assetName = assetName;
            this.sha256 = sha256;
        }
    }

    static Download downloadLatestAzahar(Context context, AtomicBoolean cancelled) throws Exception {
        checkCancelled(cancelled);
        HRExternalEmulatorCatalog.Entry azahar = HRExternalEmulatorCatalog.forPackage(
            "org.azahar_emu.azahar");
        JSONObject release = new JSONObject(readText(API_URL, MAX_METADATA_BYTES, cancelled));
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) throw new IOException("Official release metadata has no APK assets.");
        String assetName = null, assetUrl = null, digest = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", null);
            String url = asset.optString("browser_download_url", null);
            String hash = asset.optString("digest", null);
            if (!HRExternalEmulatorCatalog.isOfficialApkAsset(azahar, name, url, hash)) continue;
            assetName = name;
            assetUrl = url;
            digest = hash.substring("sha256:".length()).toLowerCase(Locale.US);
            break;
        }
        if (assetUrl == null) throw new IOException(
            "No supported Android Vanilla APK with a SHA-256 digest was found in the official release.");

        File directory = new File(context.getCacheDir(), "hrretro-apks");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Couldn't prepare private APK cache.");
        File temporary = File.createTempFile("azahar-", ".apk.part", directory);
        boolean completed = false;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long total = copyOfficialAsset(assetUrl, temporary, sha, cancelled);
            if (total <= 0 || total > MAX_APK_BYTES)
                throw new IOException("Official APK size is outside the accepted limit.");
            String actual = hex(sha.digest());
            if (!actual.equals(digest)) throw new IOException("Downloaded APK SHA-256 doesn't match GitHub release metadata.");
            PackageInfo info = context.getPackageManager().getPackageArchiveInfo(temporary.getAbsolutePath(), 0);
            if (info == null || !azahar.packageName.equals(info.packageName))
                throw new IOException("Official release APK package identity didn't match the catalog.");
            checkCancelled(cancelled);
            completed = true;
            return new Download(temporary, assetName, actual);
        } finally {
            if (!completed) temporary.delete();
        }
    }

    private static String readText(String url, long limit, AtomicBoolean cancelled) throws IOException {
        HttpsURLConnection connection = open(url, true);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new IOException("GitHub release lookup failed with HTTP " + connection.getResponseCode() + ".");
            InputStream in = new BufferedInputStream(connection.getInputStream());
            try {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n; long total = 0;
                while ((n = in.read(buffer)) != -1) {
                    checkCancelled(cancelled);
                    total += n;
                    if (total > limit) throw new IOException("GitHub release metadata exceeded its size limit.");
                    bytes.write(buffer, 0, n);
                }
                checkCancelled(cancelled);
                return new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            } finally { in.close(); }
        } finally { connection.disconnect(); }
    }

    private static long copyOfficialAsset(String firstUrl, File destination,
            MessageDigest digest, AtomicBoolean cancelled) throws IOException {
        URL url = new URL(firstUrl);
        for (int hop = 0; hop <= 5; hop++) {
            checkCancelled(cancelled);
            String host = url.getHost().toLowerCase(Locale.US);
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getPort() != -1
                    || !("github.com".equals(host)
                        || "release-assets.githubusercontent.com".equals(host)
                        || "objects.githubusercontent.com".equals(host)))
                throw new IOException("APK download redirected outside official GitHub release hosts.");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "HR-Retro-Android-Emulator-Setup/1.0");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || hop == 5) throw new IOException("GitHub APK redirect was invalid.");
                    url = new URL(url, location);
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Official APK download failed with HTTP " + status + ".");
                }
                long total = 0;
                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[32768];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        checkCancelled(cancelled);
                        total += n;
                        if (total > MAX_APK_BYTES) throw new IOException("Official APK exceeds the 192 MB download limit.");
                        digest.update(buffer, 0, n);
                        out.write(buffer, 0, n);
                    }
                    out.getFD().sync();
                }
                return total;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many GitHub APK redirects.");
    }

    private static HttpsURLConnection open(String address, boolean api) throws IOException {
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getPort() != -1
                || !(api ? "api.github.com" : "github.com").equalsIgnoreCase(url.getHost()))
            throw new IOException("Release lookup left its official GitHub host.");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "HR-Retro-Android-Emulator-Setup/1.0");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        return connection;
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(String.format(Locale.US, "%02x", b & 0xff));
        return value.toString();
    }

    private static void checkCancelled(AtomicBoolean cancelled) throws java.io.InterruptedIOException {
        if (cancelled.get()) throw new java.io.InterruptedIOException("APK download cancelled.");
    }

    private HRExternalApkDownloader() {}
}

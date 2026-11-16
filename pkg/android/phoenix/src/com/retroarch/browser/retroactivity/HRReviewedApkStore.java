package com.retroarch.browser.retroactivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Private immutable review copies, named by catalog entry and content hash. */
final class HRReviewedApkStore {
    static final class Staged {
        final File file;
        final String sha256;
        Staged(File file, String sha256) { this.file = file; this.sha256 = sha256; }
    }

    static Staged stage(InputStream source, File cacheDirectory, String id,
            String expectedSha256, long maxBytes) throws Exception {
        return stage(source, cacheDirectory, id, expectedSha256, maxBytes, null);
    }

    static Staged stage(InputStream source, File cacheDirectory, String id,
            String expectedSha256, long maxBytes, AtomicBoolean cancelled) throws Exception {
        if (!validId(id)) throw new IOException("Unknown APK review catalog id.");
        if (expectedSha256 != null && !expectedSha256.matches("[0-9a-fA-F]{64}"))
            throw new IOException("Malformed expected SHA-256.");
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs())
            throw new IOException("Couldn't prepare private APK review cache.");
        File temporary = File.createTempFile("hrretro-review-", ".part", cacheDirectory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream in = source; FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (cancelled != null && cancelled.get())
                        throw new java.io.InterruptedIOException("APK review cancelled.");
                    total += count;
                    if (total > maxBytes) throw new IOException("APK exceeds review size limit.");
                    digest.update(buffer, 0, count);
                    out.write(buffer, 0, count);
                }
                out.getFD().sync();
            }
            String hash = hex(digest.digest());
            if (expectedSha256 != null && !hash.equalsIgnoreCase(expectedSha256))
                throw new IOException("APK changed after its official digest was checked.");
            File finalFile = new File(cacheDirectory, "hrretro-" + id + "-" + hash + ".apk");
            if (finalFile.exists()) {
                if (!hash.equals(sha256(finalFile)))
                    throw new IOException("Existing private review copy failed integrity validation.");
                if (!finalFile.setReadOnly()) throw new IOException("Couldn't make reviewed APK read-only.");
                temporary.delete();
            } else if (!temporary.renameTo(finalFile)) {
                throw new IOException("Couldn't finalize private review copy.");
            } else if (!finalFile.setReadOnly()) {
                finalFile.delete();
                throw new IOException("Couldn't make reviewed APK read-only.");
            }
            return new Staged(finalFile, hash);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    static Staged promote(Staged candidate, File cacheDirectory, String id) throws Exception {
        if (!validId(id) || candidate == null || !candidate.file.isFile()
                || !candidate.sha256.equals(sha256(candidate.file)))
            throw new IOException("Candidate APK changed before package review completed.");
        File finalFile = new File(cacheDirectory, "hrretro-" + id + "-" + candidate.sha256 + ".apk");
        if (finalFile.exists()) {
            if (!candidate.sha256.equals(sha256(finalFile)))
                throw new IOException("Existing private review copy failed integrity validation.");
            if (!finalFile.setReadOnly()) throw new IOException("Couldn't make reviewed APK read-only.");
            candidate.file.delete();
        } else if (!candidate.file.renameTo(finalFile)) {
            throw new IOException("Couldn't finalize reviewed APK.");
        } else if (!finalFile.setReadOnly()) {
            finalFile.delete();
            throw new IOException("Couldn't make reviewed APK read-only.");
        }
        return new Staged(finalFile, candidate.sha256);
    }

    static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9-]{1,32}");
    }

    static boolean validStagedName(String name) {
        if (name == null || !name.matches("hrretro-[a-z0-9-]{1,32}-[a-f0-9]{64}\\.apk")) return false;
        String id = name.substring("hrretro-".length(), name.length() - 64 - ".apk".length() - 1);
        return HRExternalEmulatorCatalog.forId(id) != null;
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(String.format(Locale.US, "%02x", b & 0xff));
        return result.toString();
    }

    private HRReviewedApkStore() {}
}

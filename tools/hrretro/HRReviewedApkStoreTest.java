package com.retroarch.browser.retroactivity;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HRReviewedApkStoreTest {
    private static int checks;
    private static void ok(String label, boolean value) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "hrretro-apk-store-test-" + System.nanoTime());
        if (!root.mkdirs()) throw new AssertionError("Couldn't create temporary test directory");
        File source = new File(root, "mutable-source.apk");
        write(source, "reviewed release bytes");
        HRReviewedApkStore.Staged staged = HRReviewedApkStore.stage(
            new java.io.FileInputStream(source), root, "azahar", null, 1024);
        String reviewedHash = staged.sha256;
        write(source, "replacement attacker bytes");
        ok("Changing the selected source cannot change reviewed private copy",
            reviewedHash.equals(HRReviewedApkStore.sha256(staged.file)));
        ok("The staged file is distinct from the mutable source",
            !staged.file.getCanonicalPath().equals(source.getCanonicalPath()));
        ok("Reviewed copy is read-only", !staged.file.canWrite());
        ok("Invalid review ids are rejected", !HRReviewedApkStore.validId("../azahar"));
        ok("Slash and query traversal are rejected", !HRReviewedApkStore.validId("azahar/../../elsewhere"));
        ok("Content-addressed Azahar name is accepted", HRReviewedApkStore.validStagedName(
            "hrretro-azahar-" + repeat("a", 64) + ".apk"));
        ok("Content-addressed name traversal is rejected", !HRReviewedApkStore.validStagedName(
            "hrretro-../" + repeat("a", 64) + ".apk"));
        ok("Uncatalogued provider name is rejected", !HRReviewedApkStore.validStagedName(
            "hrretro-arbitrary-app-" + repeat("a", 64) + ".apk"));
        boolean mismatchRejected = false;
        try {
            HRReviewedApkStore.stage(new ByteArrayInputStream("wrong".getBytes(StandardCharsets.UTF_8)),
                root, "azahar", repeat("0", 64), 1024);
        } catch (java.io.IOException expected) { mismatchRejected = true; }
        ok("Official digest mismatch is rejected", mismatchRejected);
        ok("Digest mismatch leaves no temporary partial copy", count(root, ".part") == 0);

        boolean oversizedRejected = false;
        try {
            HRReviewedApkStore.stage(new ByteArrayInputStream("more than limit".getBytes(StandardCharsets.UTF_8)),
                root, "azahar", null, 4);
        } catch (java.io.IOException expected) { oversizedRejected = true; }
        ok("Oversized copy is rejected", oversizedRejected);
        ok("Oversized copy leaves no partial file", count(root, ".part") == 0);

        AtomicBoolean cancelled = new AtomicBoolean(true);
        boolean cancelledRejected = false;
        try {
            HRReviewedApkStore.stage(new ByteArrayInputStream("cancel this".getBytes(StandardCharsets.UTF_8)),
                root, "azahar", null, 1024, cancelled);
        } catch (java.io.InterruptedIOException expected) { cancelledRejected = true; }
        ok("Cancelled copy stops", cancelledRejected);
        ok("Cancelled copy leaves no partial file", count(root, ".part") == 0);

        AtomicBoolean cancelledAtEof = new AtomicBoolean(false);
        boolean eofRejected = false;
        try {
            HRReviewedApkStore.stage(new ByteArrayInputStream(new byte[0]) {
                @Override public int read(byte[] bytes, int offset, int length) {
                    cancelledAtEof.set(true);
                    return -1;
                }
            }, root, "candidate", null, 1024, cancelledAtEof);
        } catch (java.io.InterruptedIOException expected) { eofRejected = true; }
        ok("Cancellation at EOF cannot publish a staged copy", eofRejected);
        ok("EOF cancellation leaves no partial copy", count(root, ".part") == 0);

        byte[] release = "same reviewed apk bytes".getBytes(StandardCharsets.UTF_8);
        HRReviewedApkStore.Staged candidate = HRReviewedApkStore.stage(
            new ByteArrayInputStream(release), root, "candidate", null, 1024);
        HRReviewedApkStore.Staged concurrent = HRReviewedApkStore.stage(
            new ByteArrayInputStream(release), root, "candidate", null, 1024);
        ok("Concurrent identical reviews own separate candidates",
            !candidate.file.getCanonicalPath().equals(concurrent.file.getCanonicalPath()));
        concurrent.file.delete();
        ok("Cancelling one candidate preserves the other", candidate.file.isFile());
        HRReviewedApkStore.Staged promoted = HRReviewedApkStore.promote(candidate, root, "azahar");
        HRReviewedApkStore.Staged duplicate = HRReviewedApkStore.stage(
            new ByteArrayInputStream(release), root, "candidate", null, 1024);
        HRReviewedApkStore.Staged deduplicated = HRReviewedApkStore.promote(duplicate, root, "azahar");
        ok("Promoted file name is content-addressed", promoted.file.getName().equals(
            "hrretro-azahar-" + promoted.sha256 + ".apk"));
        ok("Duplicate promotion reuses exact staged copy", promoted.file.getCanonicalPath().equals(
            deduplicated.file.getCanonicalPath()));
        ok("Duplicate candidate is removed", !duplicate.file.exists());

        HRReviewedApkStore.Staged again = HRReviewedApkStore.promote(promoted, root, "azahar");
        ok("Repeated promotion preserves the reviewed file", again.file.isFile());
        ok("Repeated promotion preserves integrity", again.sha256.equals(HRReviewedApkStore.sha256(again.file)));

        boolean traversalPromotionRejected = false;
        try { HRReviewedApkStore.promote(promoted, root, "../escape"); }
        catch (java.io.IOException expected) { traversalPromotionRejected = true; }
        ok("Traversal promotion is rejected", traversalPromotionRejected);

        String goodHash = HRReviewedApkStore.sha256(source);
        File corrupt = new File(root, "hrretro-dolphin-" + goodHash + ".apk");
        write(corrupt, "corrupted cached bytes");
        boolean corruptRejected = false;
        try {
            HRReviewedApkStore.stage(new java.io.FileInputStream(source), root, "dolphin", null, 1024);
        } catch (java.io.IOException expected) { corruptRejected = true; }
        ok("Corrupt existing content-addressed copy is rejected", corruptRejected);
        ok("Corrupt existing file isn't mistaken for a verified review copy",
            !goodHash.equals(HRReviewedApkStore.sha256(corrupt)));
        deleteTree(root);
        System.out.println("HR Retro reviewed APK store: " + checks + " checks passed");
    }
    private static void write(File file, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
    private static int count(File directory, String suffix) {
        File[] files = directory.listFiles();
        if (files == null) return 0;
        int result = 0;
        for (File file : files) if (file.getName().endsWith(suffix)) result++;
        return result;
    }
}

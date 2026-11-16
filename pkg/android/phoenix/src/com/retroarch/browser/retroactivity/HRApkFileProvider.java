package com.retroarch.browser.retroactivity;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only, single-file bridge for Android's package installer. */
public final class HRApkFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    static Uri uriFor(android.content.Context context, File file) {
        return Uri.parse("content://" + context.getPackageName() + ".hr-apk/"
            + Uri.encode(file.getName()));
    }

    static File resolve(android.content.Context context, Uri uri) throws FileNotFoundException {
        if (context == null || uri == null || !"content".equals(uri.getScheme())
                || !(context.getPackageName() + ".hr-apk").equals(uri.getAuthority())
                || uri.getPathSegments().size() != 1)
            throw new FileNotFoundException("Invalid APK content URI.");
        String name = uri.getLastPathSegment();
        if (!HRReviewedApkStore.validStagedName(name))
            throw new FileNotFoundException("APK file isn't in the curated cache.");
        String id = name.substring("hrretro-".length(), name.length() - 64 - ".apk".length() - 1);
        if (HRExternalEmulatorCatalog.forId(id) == null)
            throw new FileNotFoundException("APK catalog id isn't allowlisted.");
        File root = new File(context.getCacheDir(), "hrretro-apks");
        File candidate = new File(root, name);
        try {
            if (!candidate.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)
                    || !candidate.isFile())
                throw new FileNotFoundException("APK isn't available in the private cache.");
        } catch (java.io.IOException ex) {
            throw new FileNotFoundException("Couldn't resolve the cached APK.");
        }
        return candidate;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only provider.");
        return ParcelFileDescriptor.open(resolve(getContext(), uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { throw new UnsupportedOperationException(); }
}

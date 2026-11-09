package com.retroarch.browser.retroactivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Relaunches the HR library from the app's default process after native exit. */
public final class HRReturnToLibraryReceiver extends BroadcastReceiver {
    public static final String ACTION_RETURN_TO_LIBRARY =
        "com.hardwareranked.retro.RETURN_TO_LIBRARY";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_RETURN_TO_LIBRARY.equals(intent.getAction()))
            return;
        Intent library = new Intent(context, HRLibraryActivity.class);
        library.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(library);
    }
}

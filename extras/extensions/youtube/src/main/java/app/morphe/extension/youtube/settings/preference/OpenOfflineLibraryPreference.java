/*
 * Copyright 2026 lavinhoque33
 * https://github.com/anddea/revanced-patches/pull/1883
 *
 * Adapted for Morphe:
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import app.morphe.extension.youtube.patches.offline.OfflineScreens;

/**
 * Opens the library of the videos saved with the device downloader.
 */
@SuppressWarnings({"unused", "deprecation"})
public class OpenOfflineLibraryPreference extends Preference {
    {
        setOnPreferenceClickListener(pref -> {
            OfflineScreens.openLibrary(getContext());
            return true;
        });
    }

    public OpenOfflineLibraryPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public OpenOfflineLibraryPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public OpenOfflineLibraryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OpenOfflineLibraryPreference(Context context) {
        super(context);
    }
}

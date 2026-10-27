/*
 * Copyright 2026 lavinhoque33
 * https://github.com/anddea/revanced-patches/pull/1883
 *
 * Adapted for Morphe:
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.offline;

import static java.lang.Boolean.FALSE;
import static app.morphe.extension.shared.settings.Setting.parent;

import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.StringSetting;

/**
 * Settings of the device downloader.
 */
public final class SaveToDeviceSettings {

    public static final BooleanSetting ENABLED =
            new BooleanSetting("morphe_save_to_device", FALSE);

    public static final BooleanSetting REPLACE_DOWNLOADS_PAGE =
            new BooleanSetting("morphe_offline_replace_downloads_page", FALSE, parent(ENABLED));

    public static final StringSetting LIBRARY_SORT =
            new StringSetting("morphe_offline_library_sort", "NEWEST", false, false);

    private SaveToDeviceSettings() {
    }

    /**
     * Loads the settings, so they are known before the settings menu opens.
     */
    static void load() {
    }
}

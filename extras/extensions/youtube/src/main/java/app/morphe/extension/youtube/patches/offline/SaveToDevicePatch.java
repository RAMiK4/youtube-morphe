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

import android.app.Activity;

import app.morphe.extension.shared.Logger;

/**
 * Injection points of the device downloader.
 */
@SuppressWarnings("unused")
public final class SaveToDevicePatch {

    private SaveToDevicePatch() {
    }

    /**
     * Injection point, at the start of the main activity.
     */
    public static void onActivityCreated(Activity activity) {
        SaveToDeviceSettings.load();
    }

    /**
     * @return If download buttons save videos to the device.
     */
    public static boolean isEnabled() {
        return SaveToDeviceSettings.ENABLED.get();
    }

    /**
     * Injection point, before the download of the app and before the download mode of Morphe.
     *
     * @return If the video is downloaded to the device, and the original download must not run.
     */
    public static boolean download(String videoId) {
        try {
            if (!isEnabled() || videoId == null || videoId.isEmpty()) {
                return false;
            }
            OfflineDownloader.download(videoId);
            return true;
        } catch (Exception ex) {
            Logger.printException(() -> "download failure", ex);
            return false;
        }
    }
}

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
import static java.lang.Boolean.TRUE;
import static app.morphe.extension.shared.settings.Setting.parent;

import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.IntegerSetting;
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

    public static final StringSetting ASPECT_MODE =
            new StringSetting("morphe_offline_aspect_mode", "FIT", false, false);

    // Download options.
    public static final BooleanSetting CODEC_H264 =
            new BooleanSetting("morphe_offline_codec_h264", TRUE, parent(ENABLED));

    public static final BooleanSetting CODEC_VP9 =
            new BooleanSetting("morphe_offline_codec_vp9", TRUE, parent(ENABLED));

    public static final BooleanSetting CODEC_AV1 =
            new BooleanSetting("morphe_offline_codec_av1", TRUE, parent(ENABLED));

    public static final BooleanSetting AUDIO_OPUS =
            new BooleanSetting("morphe_offline_audio_opus", TRUE, parent(ENABLED));

    public static final BooleanSetting AUDIO_AAC =
            new BooleanSetting("morphe_offline_audio_aac", TRUE, parent(ENABLED));

    public static final BooleanSetting SHOW_FORMAT_IDS =
            new BooleanSetting("morphe_offline_show_format_ids", FALSE, parent(ENABLED));

    public static final IntegerSetting MIN_HEIGHT =
            new IntegerSetting("morphe_offline_min_height", 144, parent(ENABLED));

    public static final IntegerSetting MAX_HEIGHT =
            new IntegerSetting("morphe_offline_max_height", 4320, parent(ENABLED));

    public static final StringSetting VIDEO_FOLDER =
            new StringSetting("morphe_offline_video_folder", "YouTube", parent(ENABLED));

    public static final StringSetting AUDIO_FOLDER =
            new StringSetting("morphe_offline_audio_folder", "YouTube/Audio", parent(ENABLED));

    public static final StringSetting THUMBNAIL_FOLDER =
            new StringSetting("morphe_offline_thumbnail_folder", "YouTube/Thumbnails", parent(ENABLED));

    public static final BooleanSetting SAVE_THUMBNAIL =
            new BooleanSetting("morphe_offline_save_thumbnail", FALSE, parent(ENABLED));

    private SaveToDeviceSettings() {
    }

    /**
     * Loads the settings, so they are known before the settings menu opens.
     */
    static void load() {
    }
}

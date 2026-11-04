/*
 * Copyright 2026 lavinhoque33
 * https://github.com/lavinhoque33/revanced-patches
 *
 * Adapted for Morphe:
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.offline;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.shared.ui.Dim;

/** Compact picker controls; long language lists scroll in a separate, themed checkbox dialog. */
final class OfflineSubtitlePicker {
    private OfflineSubtitlePicker() {
    }

    static void add(Activity activity, LinearLayout parent, OfflineCaptions.Catalog catalog, int foreground) {
        int padding = Dim.dp(16);
        Switch include = new Switch(activity);
        include.setText(str("morphe_offline_subtitles_include"));
        include.setTextColor(foreground);
        include.setPadding(padding, padding, padding, padding);
        include.setEnabled(!catalog.tracks.isEmpty());
        include.setAlpha(include.isEnabled() ? 1f : 0.4f);
        include.setChecked(catalog.include && include.isEnabled());
        parent.addView(include);
        if (catalog.tracks.isEmpty()) {
            parent.addView(label(activity, str("morphe_offline_subtitles_none"), foreground));
            return;
        }

        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setVisibility(include.isChecked() ? View.VISIBLE : View.GONE);
        parent.addView(options);
        TextView languages = label(activity, "", foreground);
        Runnable updateCount = () -> languages.setText(str("morphe_offline_subtitles_languages",
                catalog.selected.size(), OfflineSubtitle.MAX_TRACKS));
        updateCount.run();
        languages.setOnClickListener(v -> showLanguages(activity, catalog, false, foreground, updateCount));
        options.addView(languages);

        boolean translated = catalog.tracks.stream().anyMatch(track -> !track.translation().isEmpty());
        if (translated) {
            TextView automatic = label(activity, str("morphe_offline_subtitles_translated"), foreground);
            automatic.setOnClickListener(v -> showLanguages(activity, catalog, true, foreground, updateCount));
            options.addView(automatic);
        }
        TextView format = label(activity, "", foreground);
        Runnable updateFormat = () -> format.setText(
                str("morphe_offline_subtitles_format", catalog.format.toUpperCase(Locale.ROOT)));
        updateFormat.run();
        format.setOnClickListener(v -> OfflineUi.showChoices(activity, str("morphe_offline_subtitles_format_title"),
                new String[]{"SRT", "VTT"}, "vtt".equals(catalog.format) ? 1 : 0, foreground, which -> {
                    catalog.format = which == 1 ? "vtt" : "srt";
                    catalog.remember();
                    updateFormat.run();
                }));
        options.addView(format);
        include.setOnCheckedChangeListener((button, checked) -> {
            catalog.include = checked;
            catalog.remember();
            options.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
    }

    private static TextView label(Activity activity, String text, int foreground) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(foreground);
        label.setTextSize(14);
        int padding = Dim.dp(16);
        label.setPadding(padding, Dim.dp(10), padding, Dim.dp(10));
        label.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return label;
    }

    private static void showLanguages(Activity activity, OfflineCaptions.Catalog catalog, boolean translated,
                                      int foreground, Runnable onChanged) {
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.addView(label(activity, str("morphe_offline_subtitles_cap", OfflineSubtitle.MAX_TRACKS), foreground));
        for (OfflineCaptionChoices.Track track : catalog.tracks) {
            if (translated == track.translation().isEmpty()) continue;
            CheckBox box = new CheckBox(activity);
            box.setText(track.name());
            box.setTextColor(foreground);
            box.setButtonTintList(ColorStateList.valueOf(foreground));
            box.setPadding(Dim.dp(16), Dim.dp(8), Dim.dp(16), Dim.dp(8));
            box.setChecked(catalog.selected.contains(track.key()));
            box.setOnClickListener(v -> {
                if (!OfflineSubtitle.select(catalog.selected, track.key(), box.isChecked())) {
                    box.setChecked(false);
                    Utils.showToastShort(str("morphe_offline_subtitles_cap", OfflineSubtitle.MAX_TRACKS));
                } else {
                    catalog.remember();
                    onChanged.run();
                }
            });
            rows.addView(box);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(rows);
        Pair<Dialog, LinearLayout> dialog = CustomDialog.create(activity,
                str(translated ? "morphe_offline_subtitles_translated"
                        : "morphe_offline_subtitles_languages_title"),
                null, null, null, () -> {
                }, null, null, null, false);
        LinearLayout layout = dialog.second;
        layout.addView(scroll, layout.getChildCount() - 1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        dialog.first.show();
    }
}

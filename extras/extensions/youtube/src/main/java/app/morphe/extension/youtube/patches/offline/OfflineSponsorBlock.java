/*
 * Copyright 2026 lavinhoque33
 * https://github.com/lavinhoque33/revanced-patches
 *
 * Adapted for Morphe:
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.sponsorblock.SponsorBlockApi;
import app.morphe.extension.shared.sponsorblock.objects.SegmentCategory;
import app.morphe.extension.shared.sponsorblock.requests.SBRoutes;
import app.morphe.extension.shared.ui.Dim;

/** Download-scoped choices and segment snapshots; never changes the online player's category list. */
final class OfflineSponsorBlock {
    enum Status {OK, NONE, ERROR}

    record Result(Status status, List<OfflineSponsorSegments.Segment> segments, long durationMs) {
    }

    record Selection(boolean include, List<String> categories, @Nullable Result result) {
        static Selection none() {
            return new Selection(false, List.of(), null);
        }
    }

    private static final int TIMEOUT_MS = 5000;

    @Nullable
    private static SponsorBlockApi.SettingsAdapter settings() {
        try {
            return SponsorBlockApi.config().settings();
        } catch (IllegalStateException ex) {
            return null; // SponsorBlock is not set up in this app.
        }
    }

    /** SponsorBlock is turned on in the app settings. */
    static boolean enabled() {
        SponsorBlockApi.SettingsAdapter settings = settings();
        return settings != null && settings.sbEnabled().get();
    }

    static boolean toastOnSkip() {
        SponsorBlockApi.SettingsAdapter settings = settings();
        return settings != null && settings.toastOnSkip().get();
    }

    /** An independent, silent request: no player state, no connection-error toasts. */
    static Result fetch(String videoId) {
        Utils.verifyOffMainThread();
        SponsorBlockApi.SettingsAdapter settings = settings();
        if (settings == null) return new Result(Status.ERROR, List.of(), 0);
        HttpURLConnection connection = null;
        try {
            String categories = URLEncoder.encode(new JSONArray(OfflineSponsorSegments.CATEGORIES).toString(),
                    StandardCharsets.UTF_8.name());
            connection = Requester.getConnectionFromRoute(settings.apiUrl().get(), SBRoutes.GET_SEGMENTS,
                    videoId, categories);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int status = connection.getResponseCode();
            if (status == Requester.HTTP_STATUS_CODE_NOT_FOUND) return new Result(Status.NONE, List.of(), 0);
            if (status != Requester.HTTP_STATUS_CODE_SUCCESS) {
                Logger.printInfo(() -> "Offline SponsorBlock lookup for " + videoId + " HTTP " + status);
                return new Result(Status.ERROR, List.of(), 0);
            }
            JSONArray array = Requester.parseJSONArray(connection);
            long duration = 0;
            for (int index = 0; index < array.length(); index++) {
                JSONObject segment = array.optJSONObject(index);
                if (segment != null) {
                    duration = Math.max(duration, (long) (segment.optDouble("videoDuration", 0) * 1000));
                }
            }
            List<OfflineSponsorSegments.Segment> segments = OfflineSponsorSegments.fromJson(array, true);
            return new Result(segments.isEmpty() ? Status.NONE : Status.OK, segments, duration);
        } catch (Exception ex) {
            Logger.printInfo(() -> "Offline SponsorBlock lookup failed for " + videoId, ex);
            return new Result(Status.ERROR, List.of(), 0);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static void save(OfflineVideo video, Selection selection, Result result) {
        if (!selection.include()) return;
        video.sponsorChecked = result.status() != Status.ERROR;
        video.sponsorDurationMs = result.durationMs();
        video.sponsorCategories = selection.categories();
        video.sponsorSegments = result.segments();
    }

    static String behavior(String key) {
        SegmentCategory category = SegmentCategory.byCategoryKey(key);
        if (category == null) return "ignore";
        try {
            return category.behaviorSetting().get();
        } catch (IllegalStateException ex) {
            return "ignore";
        }
    }

    static final class Picker {
        private final Set<String> categories = new LinkedHashSet<>();
        private boolean include = SaveToDeviceSettings.SPONSORBLOCK_INCLUDE.get();
        private Result result;
        private Activity activity;
        private LinearLayout container;
        private int foreground;
        private final BooleanSupplier active;

        Picker(BooleanSupplier active) {
            this.active = active;
            for (String key : OfflineSponsorSegments.CATEGORIES) {
                if (!"ignore".equals(behavior(key))) categories.add(key);
            }
        }

        void fetch(String videoId) {
            if (!enabled()) return;
            Utils.runOnBackgroundThread(() -> {
                Result fetched = OfflineSponsorBlock.fetch(videoId);
                Utils.runOnMainThread(() -> {
                    if (!active.getAsBoolean()) return;
                    result = fetched;
                    render();
                });
            });
        }

        void add(Activity activity, LinearLayout parent, int foreground) {
            // Only offered while SponsorBlock is turned on, so its category settings apply.
            if (!enabled()) return;
            this.activity = activity;
            this.foreground = foreground;
            container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            parent.addView(container);
            render();
        }

        Selection selection() {
            boolean available = result != null && result.status() == Status.OK;
            return new Selection(include && available, List.copyOf(categories), result);
        }

        private void render() {
            if (container == null || !active.getAsBoolean()) return;
            container.removeAllViews();
            boolean available = result != null && result.status() == Status.OK;
            String summary = result == null ? "morphe_offline_sponsorblock_loading"
                    : switch (result.status()) {
                        case OK -> "morphe_offline_sponsorblock_summary";
                        case NONE -> "morphe_offline_sponsorblock_none";
                        case ERROR -> "morphe_offline_sponsorblock_error";
                    };
            Switch toggle = OfflineUi.toggleRow(activity, container, "sponsorblock",
                    str("morphe_offline_sponsorblock_include"), str(summary), foreground, available, include);
            LinearLayout choices = new LinearLayout(activity);
            choices.setOrientation(LinearLayout.VERTICAL);
            choices.setVisibility(toggle.isChecked() ? View.VISIBLE : View.GONE);
            container.addView(choices);
            for (String key : OfflineSponsorSegments.CATEGORIES) {
                SegmentCategory category = SegmentCategory.byCategoryKey(key);
                if (category == null) continue;
                CheckBox box = new CheckBox(activity);
                box.setText(category.title.toString());
                box.setTextColor(foreground);
                try {
                    box.setButtonTintList(ColorStateList.valueOf(Color.parseColor(category.colorSetting().get())));
                } catch (Exception ex) {
                    Logger.printDebug(() -> "No color for SponsorBlock category " + key, ex);
                }
                box.setPadding(Dim.dp(48), 0, Dim.dp(16), 0);
                box.setChecked(categories.contains(key));
                box.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) categories.add(key);
                    else categories.remove(key);
                });
                choices.addView(box);
            }
            toggle.setOnCheckedChangeListener((button, checked) -> {
                include = checked;
                SaveToDeviceSettings.SPONSORBLOCK_INCLUDE.save(checked);
                choices.setVisibility(checked ? View.VISIBLE : View.GONE);
            });
        }
    }

    private OfflineSponsorBlock() {
    }
}

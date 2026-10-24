package app.morphe.extension.youtube.patches.updates;

import static java.lang.Boolean.TRUE;
import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.LongSetting;
import app.morphe.extension.shared.ui.CustomDialog;

/**
 * Checks a JSON file published with every release and offers the newer build.
 * The URL, the build number and the version text are set while patching.
 */
@SuppressWarnings("unused")
public final class UpdateCheckerPatch {

    public static final BooleanSetting UPDATE_CHECK =
            new BooleanSetting("morphe_update_check", TRUE);

    private static final LongSetting LAST_CHECK =
            new LongSetting("morphe_update_last_check", 0L, false, false);

    private static final LongSetting SKIPPED_BUILD =
            new LongSetting("morphe_update_skipped_build", 0L, false, false);

    private static final long CHECK_INTERVAL_MS = 3 * 60 * 60 * 1000L;
    private static final int TIMEOUT_MS = 10_000;

    private static volatile boolean checking;

    private UpdateCheckerPatch() {
    }

    // Replaced while patching.
    private static String updateUrl() {
        return "";
    }

    // Replaced while patching.
    private static String currentBuild() {
        return "0";
    }

    // Replaced while patching.
    private static String versionInfo() {
        return "";
    }

    public static long currentBuildNumber() {
        try {
            return Long.parseLong(currentBuild().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Summary of the "Check now" preference.
     */
    public static String getVersionSummary() {
        String info = versionInfo();
        return info.isEmpty() ? "" : info;
    }

    /**
     * Injection point. Called when the main activity is created.
     */
    public static void onActivityCreated(Activity activity) {
        try {
            if (!UPDATE_CHECK.get()) return;
            if (System.currentTimeMillis() - LAST_CHECK.get() < CHECK_INTERVAL_MS) return;
            check(activity, false);
        } catch (Exception ex) {
            Logger.printException(() -> "onActivityCreated failure", ex);
        }
    }

    /**
     * Called from the settings.
     */
    public static void checkNow(Activity activity) {
        check(activity, true);
    }

    private static void check(Activity activity, boolean manual) {
        final String url = updateUrl();
        if (url.isEmpty() || checking) return;

        if (!Utils.isNetworkConnected()) {
            if (manual) Utils.showToastShort(str("morphe_update_failed"));
            return;
        }

        checking = true;
        Utils.runOnBackgroundThread(() -> {
            try {
                JSONObject json = fetch(url);
                LAST_CHECK.save(System.currentTimeMillis());

                final long latest = json.getLong("build");
                final long current = currentBuildNumber();
                final boolean skipped = !manual && latest == SKIPPED_BUILD.get();

                if (latest <= current || skipped) {
                    if (manual) Utils.runOnMainThread(() ->
                            Utils.showToastShort(str("morphe_update_latest")));
                    return;
                }

                Utils.runOnMainThread(() -> showUpdateDialog(activity, json, latest));
            } catch (Exception ex) {
                Logger.printInfo(() -> "Update check failed", ex);
                if (manual) Utils.runOnMainThread(() ->
                        Utils.showToastShort(str("morphe_update_failed")));
            } finally {
                checking = false;
            }
        });
    }

    private static JSONObject fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        try {
            final int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + code);
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }
            return new JSONObject(builder.toString());
        } finally {
            connection.disconnect();
        }
    }

    private static void showUpdateDialog(Activity activity, JSONObject json, long latest) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        final String apkUrl = json.optString("apk_url", json.optString("release_url"));
        final String version = str("morphe_update_dialog_version",
                json.optString("yt_version"), json.optString("patches"));
        final String changelog = localizedChangelog(activity, json).trim();
        final String message = changelog.isEmpty() ? version : version + "\n\n" + changelog;

        var dialogPair = CustomDialog.create(
                activity,
                str("morphe_update_dialog_title"),      // Title.
                message,                                 // Message.
                null,                                    // No EditText.
                str("morphe_update_dialog_download"),   // OK button text.
                () -> openUrl(activity, apkUrl),         // OK button action.
                () -> {},                                // Cancel: remind later.
                str("morphe_update_dialog_skip"),       // Neutral button text.
                () -> SKIPPED_BUILD.save(latest),        // Neutral: skip this build.
                true                                     // Dismiss on neutral click.
        );

        Utils.showDialog(activity, dialogPair.first);
    }

    /**
     * Changelog in the app language, then English, then the default changelog (Russian).
     */
    private static String localizedChangelog(Activity activity, JSONObject json) {
        JSONObject changelogs = json.optJSONObject("changelogs");
        if (changelogs != null) {
            String language = activityLanguage(activity);
            // Russian is also the closest choice for these languages.
            if (language.equals("uk") || language.equals("be") || language.equals("kk")) {
                if (!changelogs.has(language)) language = "ru";
            }
            String text = changelogs.optString(language);
            if (text.isEmpty()) text = changelogs.optString("en");
            if (!text.isEmpty()) return text;
        }
        return json.optString("changelog");
    }

    private static String activityLanguage(Activity activity) {
        try {
            return activity.getResources().getConfiguration()
                    .getLocales().get(0).getLanguage();
        } catch (Exception ex) {
            return java.util.Locale.getDefault().getLanguage();
        }
    }

    private static void openUrl(Activity activity, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not open " + url, ex);
        }
    }
}

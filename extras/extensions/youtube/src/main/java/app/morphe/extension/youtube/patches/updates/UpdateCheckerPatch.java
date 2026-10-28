package app.morphe.extension.youtube.patches.updates;

import static java.lang.Boolean.TRUE;
import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

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
import app.morphe.extension.shared.theme.ThemeUtils;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.shared.ui.Dim;

/**
 * Checks a JSON file published with every release and offers the newer build.
 * The URL, the build number and the version text are set while patching.
 */
@SuppressWarnings("unused")
public final class UpdateCheckerPatch {

    public static final BooleanSetting UPDATE_CHECK =
            new BooleanSetting("morphe_update_check", TRUE);

    /** When the update dialog was last shown automatically, and for which build. */
    private static final LongSetting LAST_PROMPT =
            new LongSetting("morphe_update_last_prompt", 0L, false, false);

    private static final LongSetting PROMPTED_BUILD =
            new LongSetting("morphe_update_prompted_build", 0L, false, false);

    private static final LongSetting SKIPPED_BUILD =
            new LongSetting("morphe_update_skipped_build", 0L, false, false);

    /** The same build is offered again automatically only after this much time. */
    private static final long REMIND_INTERVAL_MS = 12 * 60 * 60 * 1000L;
    private static final int TIMEOUT_MS = 10_000;

    private static volatile boolean checking;

    /** Automatic check once per app launch (the activity may be recreated). */
    private static boolean checkedThisLaunch;

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
            if (checkedThisLaunch) return;
            checkedThisLaunch = true;
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
                final long latest = json.getLong("build");
                final long current = currentBuildNumber();
                final long now = System.currentTimeMillis();
                final boolean skipped = !manual && latest == SKIPPED_BUILD.get();
                // Already offered recently: wait before reminding about the same build.
                final boolean remindLater = !manual && latest == PROMPTED_BUILD.get()
                        && now - LAST_PROMPT.get() < REMIND_INTERVAL_MS;

                if (latest <= current || skipped || remindLater) {
                    if (manual) Utils.runOnMainThread(() ->
                            Utils.showToastShort(str("morphe_update_latest")));
                    return;
                }

                if (!manual) {
                    PROMPTED_BUILD.save(latest);
                    LAST_PROMPT.save(now);
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
        final CharSequence message = changelog.isEmpty() ? version : styledChangelog(version, changelog);

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

        // The Cancel button uses the system language; the dialog itself uses the app language.
        final String later = str("morphe_update_dialog_later");
        final String okText = str("morphe_update_dialog_download");
        final String skipText = str("morphe_update_dialog_skip");
        final Button[] buttons = new Button[3]; // Download, Later, Skip.
        forEachButton(dialogPair.second, button -> {
            final String text = String.valueOf(button.getText());
            if (text.equals(okText)) buttons[0] = button;
            else if (text.equals(skipText)) buttons[2] = button;
            else {
                button.setText(later);
                buttons[1] = button;
            }
        });
        stackButtons(activity, buttons);

        Utils.showDialog(activity, dialogPair.first);
    }

    /**
     * Shows the buttons as full width rows in every language (Download, Later, Skip),
     * as the dialog already does when they do not fit in one row.
     */
    private static void stackButtons(Activity activity, Button[] buttons) {
        try {
            for (Button button : buttons) if (button == null) return;
            ViewGroup row = (ViewGroup) buttons[0].getParent();
            ViewGroup container = (ViewGroup) row.getParent();
            container.removeAllViews();
            for (int i = 0; i < buttons.length; i++) {
                ((ViewGroup) buttons[i].getParent()).removeView(buttons[i]);
                buttons[i].setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Dim.dp(36)));
                if (i > 0) {
                    View spacer = new View(activity);
                    spacer.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, Dim.dp(8)));
                    container.addView(spacer);
                }
                container.addView(buttons[i]);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Could not stack the update dialog buttons", ex);
        }
    }

    private static void forEachButton(View view, java.util.function.Consumer<Button> action) {
        if (view instanceof Button) {
            action.accept((Button) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) forEachButton(group.getChildAt(i), action);
        }
    }

    /**
     * Section titles of the changelog are bold with a thin, faint rule under them, like the
     * headings of the release notes on GitHub. Separator lines of older changelogs are dropped.
     */
    private static CharSequence styledChangelog(String version, String changelog) {
        SpannableStringBuilder text = new SpannableStringBuilder(version);
        final int ruleColor = (ThemeUtils.getAppForegroundColor() & 0x00FFFFFF) | 0x33000000;
        // Wrapped lines of an item start under its text, not under the bullet.
        Paint bulletPaint = new Paint();
        bulletPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16,
                Resources.getSystem().getDisplayMetrics()));
        final int bulletIndent = Math.round(bulletPaint.measureText("\u2022 "));
        for (String line : changelog.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.chars().allMatch(c -> c == '\u2500')) continue;
            final boolean heading = !line.startsWith("\u2022");
            // A blank line before every section, a short gap between a rule and its items.
            text.append(heading ? "\n\n" : "\n");
            final int start = text.length();
            text.append(line);
            if (!heading) {
                text.setSpan(new LeadingMarginSpan.Standard(0, bulletIndent), start, text.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                final int end = text.length();
                text.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new RelativeSizeSpan(1.15f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new RuleSpan(ruleColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                final int gap = text.length();
                text.append("\n ");
                text.setSpan(new RelativeSizeSpan(0.4f), gap, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return text;
    }

    /** Draws a thin line along the bottom of a heading line, across the full width. */
    private static final class RuleSpan implements LineBackgroundSpan {
        private final int color;

        RuleSpan(int color) {
            this.color = color;
        }

        @Override
        public void drawBackground(Canvas canvas, Paint paint, int left, int right, int top,
                                   int baseline, int bottom, CharSequence text, int start, int end,
                                   int lineNumber) {
            final int oldColor = paint.getColor();
            final Paint.Style oldStyle = paint.getStyle();
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            final float height = Math.max(1, Dim.dp(1));
            final float y = bottom - height;
            canvas.drawRect(left, y, right, bottom, paint);
            paint.setColor(oldColor);
            paint.setStyle(oldStyle);
        }
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

package app.morphe.extension.youtube.patches.defaults;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.SharedYouTubeSettings;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.youtube.patches.updates.UpdateCheckerPatch;

/**
 * Applies the recommended settings of this build once per setting.
 * <p>
 * A setting is applied only the first time this build (or a later one) sees it.
 * After that, whatever the user selects is kept, also across updates.
 * Settings added to the recommended list later are applied once on the update that adds them.
 */
@SuppressWarnings("unused")
public final class DefaultSettingsPatch {

    private static final String PREFERENCES_NAME = "morphe_extras_defaults";
    private static final String APPLIED_KEYS = "applied_keys";
    private static final String LAST_BUILD = "last_build";
    private static final long FIRST_RUN_RESTART_DELAY_MS = 10_000;

    /**
     * Settings classes that must be loaded, so their settings take part in the import.
     */
    private static final String[] SETTINGS_CLASSES = {
            "app.morphe.extension.shared.settings.BaseSettings",
            "app.morphe.extension.shared.settings.SharedYouTubeSettings",
            "app.morphe.extension.youtube.settings.Settings",
            "app.morphe.extension.youtube.settings.YandexVotSettings",
    };

    private static boolean done;

    private DefaultSettingsPatch() {
    }

    // Replaced while patching.
    private static String defaultsBase64() {
        return "";
    }

    private static String defaults() {
        String encoded = defaultsBase64();
        if (encoded.isEmpty()) return "{}";
        return new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    /**
     * Injection point. Called when the main activity is created.
     */
    public static void onActivityCreated(Activity activity) {
        if (done) return;
        done = true;

        try {
            SharedPreferences preferences =
                    activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            final boolean firstRun = !SharedYouTubeSettings.SETTINGS_INITIALIZED.get();

            final boolean rebootNeeded = applyDefaults(activity, preferences);

            // First start of a new build: a fresh install or an update.
            final long build = UpdateCheckerPatch.currentBuildNumber();
            final boolean newBuild = build > 0 && build != preferences.getLong(LAST_BUILD, -1);
            if (newBuild) preferences.edit().putLong(LAST_BUILD, build).apply();

            if (firstRun) {
                // Morphe restarts the app once its config is loaded on the first run.
                // Restart anyway if that does not happen.
                Utils.runOnMainThreadDelayed(() -> {
                    if (SharedYouTubeSettings.SETTINGS_INITIALIZED.get()) return;
                    SharedYouTubeSettings.SETTINGS_INITIALIZED.save(true);
                    showRestartDialog(currentActivity(activity), str("morphe_restart_first_run"));
                }, FIRST_RUN_RESTART_DELAY_MS);
            } else if (newBuild || rebootNeeded) {
                final String message = newBuild
                        ? str("morphe_extras_restart_updated")
                        : str("morphe_defaults_restart_message");
                Utils.runOnMainThreadDelayed(() -> showRestartDialog(currentActivity(activity), message), 1000);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Could not apply default settings", ex);
        }
    }

    /**
     * @return If a restart is needed for an applied setting.
     */
    private static boolean applyDefaults(Activity activity, SharedPreferences preferences) throws Exception {
        JSONObject defaults = parseSettings(defaults());
        if (defaults.length() == 0) return false;

        Set<String> applied = new HashSet<>(preferences.getStringSet(APPLIED_KEYS, new HashSet<>()));

        JSONObject pending = new JSONObject();
        for (Iterator<String> keys = defaults.keys(); keys.hasNext(); ) {
            String key = keys.next();
            if (!applied.contains(key)) pending.put(key, defaults.get(key));
        }
        if (pending.length() == 0) return false;

        for (String className : SETTINGS_CLASSES) {
            try {
                Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                // Not included in this build.
            }
        }

        // An import resets every setting it does not contain, so the current
        // settings of the user are part of it.
        final String exported = Setting.exportToJson(null);
        final boolean exportHasBraces = exported.trim().startsWith("{");
        JSONObject merged = parseSettings(exported);
        for (Iterator<String> keys = pending.keys(); keys.hasNext(); ) {
            String key = keys.next();
            merged.put(key, pending.get(key));
        }

        // Import the same format the export uses (Morphe writes the entries without braces).
        String importText = merged.toString(2);
        if (!exportHasBraces) {
            importText = importText.substring(importText.indexOf('{') + 1,
                    importText.lastIndexOf('}')).trim();
        }
        final boolean rebootNeeded = Setting.importFromJSON(activity, importText);

        for (Iterator<String> keys = pending.keys(); keys.hasNext(); ) {
            applied.add(keys.next());
        }
        preferences.edit().putStringSet(APPLIED_KEYS, applied).apply();

        Logger.printInfo(() -> "Applied " + pending.length() + " default settings");
        return rebootNeeded;
    }

    /**
     * Parses settings text with or without the enclosing braces.
     */
    private static JSONObject parseSettings(String text) throws Exception {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return new JSONObject();
        if (!trimmed.startsWith("{")) {
            if (trimmed.endsWith(",")) trimmed = trimmed.substring(0, trimmed.length() - 1);
            trimmed = "{" + trimmed + "}";
        }
        return new JSONObject(trimmed);
    }

    private static Activity currentActivity(Activity fallback) {
        Activity current = Utils.getActivity();
        return current != null ? current : fallback;
    }

    private static void showRestartDialog(Activity activity, String message) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        var dialogPair = CustomDialog.create(
                activity,
                str("morphe_settings_restart_title"),   // Title.
                message,                                 // Message.
                null,                                    // No EditText.
                str("morphe_settings_restart"),         // OK button text.
                () -> Utils.restartApp(activity),        // OK button action.
                null,                                    // No Cancel button.
                null,                                    // No Neutral button text.
                null,                                    // No Neutral button action.
                true                                     // Dismiss dialog when onNeutralClick.
        );
        dialogPair.first.setCancelable(false);
        Utils.showDialog(activity, dialogPair.first);
    }
}

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
            JSONObject defaults = new JSONObject(defaults());
            if (defaults.length() == 0) return;

            SharedPreferences preferences =
                    activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            Set<String> applied = new HashSet<>(preferences.getStringSet(APPLIED_KEYS, new HashSet<>()));

            JSONObject pending = new JSONObject();
            for (Iterator<String> keys = defaults.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (!applied.contains(key)) pending.put(key, defaults.get(key));
            }
            if (pending.length() == 0) return;

            for (String className : SETTINGS_CLASSES) {
                try {
                    Class.forName(className);
                } catch (ClassNotFoundException ignored) {
                    // Not included in this build.
                }
            }

            // An import resets every setting it does not contain, so the current
            // settings of the user are part of it.
            JSONObject merged = new JSONObject(Setting.exportToJson(null));
            for (Iterator<String> keys = pending.keys(); keys.hasNext(); ) {
                String key = keys.next();
                merged.put(key, pending.get(key));
            }

            final boolean firstRun = !SharedYouTubeSettings.SETTINGS_INITIALIZED.get();
            final boolean rebootNeeded = Setting.importFromJSON(activity, merged.toString());

            for (Iterator<String> keys = pending.keys(); keys.hasNext(); ) {
                applied.add(keys.next());
            }
            preferences.edit().putStringSet(APPLIED_KEYS, applied).apply();

            Logger.printInfo(() -> "Applied " + pending.length() + " default settings");

            // On the first run Morphe shows its own restart dialog.
            if (rebootNeeded && !firstRun) {
                Utils.runOnMainThreadDelayed(() -> showRestartDialog(activity), 1000);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Could not apply default settings", ex);
        }
    }

    private static void showRestartDialog(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        var dialogPair = CustomDialog.create(
                activity,
                str("morphe_settings_restart_title"),   // Title.
                str("morphe_defaults_restart_message"), // Message.
                null,                                    // No EditText.
                str("morphe_settings_restart"),         // OK button text.
                () -> Utils.restartApp(activity),        // OK button action.
                () -> {},                                // Cancel button action.
                null,                                    // No Neutral button text.
                null,                                    // No Neutral button action.
                true                                     // Dismiss dialog when onNeutralClick.
        );
        Utils.showDialog(activity, dialogPair.first);
    }
}

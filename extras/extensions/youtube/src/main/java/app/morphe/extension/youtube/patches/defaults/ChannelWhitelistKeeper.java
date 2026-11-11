package app.morphe.extension.youtube.patches.defaults;

import android.app.Activity;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.Setting;

/**
 * Keeps the channel whitelists (ads, playback speed, SponsorBlock) when settings are imported.
 * <p>
 * A Morphe settings import resets every setting the imported text does not contain.
 * Preset settings files do not contain the whitelists, so importing one would clear
 * the channels the user whitelisted. The import dialog exports the current settings
 * right before every import, so the whitelists are remembered on export and restored
 * after an import that left them empty.
 */
final class ChannelWhitelistKeeper implements Setting.ImportExportCallback {

    private static final String SETTINGS_CLASS = "app.morphe.extension.youtube.settings.Settings";

    private static final String[] WHITELIST_KEYS = {
            "morphe_ads_channel_whitelist",
            "morphe_playback_speed_channel_whitelist",
            "morphe_sb_channel_whitelist",
    };

    private static boolean registered;

    private final Map<String, String> saved = new HashMap<>();

    private ChannelWhitelistKeeper() {
    }

    static synchronized void register() {
        if (registered) return;
        registered = true;

        try {
            Class.forName(SETTINGS_CLASS);
        } catch (ClassNotFoundException ex) {
            Logger.printInfo(() -> "Settings class not found, whitelists are not kept on import");
            return;
        }

        ChannelWhitelistKeeper keeper = new ChannelWhitelistKeeper();
        keeper.remember();
        Setting.addImportExportCallback(keeper);
    }

    @Nullable
    private static Setting<?> whitelist(String key) {
        Setting<?> setting = Setting.getSettingFromPath(key);
        return setting != null && setting.defaultValue instanceof String ? setting : null;
    }

    private synchronized void remember() {
        saved.clear();
        for (String key : WHITELIST_KEYS) {
            Setting<?> setting = whitelist(key);
            if (setting == null) continue;
            String value = (String) setting.get();
            if (!value.isEmpty()) saved.put(key, value);
        }
    }

    @Override
    public void settingsExported(@Nullable Activity context) {
        remember();
    }

    @Override
    public synchronized void settingsImported(@Nullable Activity context) {
        for (Map.Entry<String, String> entry : saved.entrySet()) {
            Setting<?> setting = whitelist(entry.getKey());
            if (setting == null || !((String) setting.get()).isEmpty()) continue;

            //noinspection unchecked
            ((Setting<String>) setting).save(entry.getValue());
            Logger.printDebug(() -> "Kept channel whitelist on import: " + entry.getKey());
        }
        remember();
    }
}

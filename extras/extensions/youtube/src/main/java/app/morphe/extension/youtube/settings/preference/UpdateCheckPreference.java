package app.morphe.extension.youtube.settings.preference;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.preference.Preference;
import android.util.AttributeSet;

import app.morphe.extension.youtube.patches.updates.UpdateCheckerPatch;

/**
 * "Check now" preference. Shows the installed build and checks for updates on click.
 */
@SuppressWarnings("unused")
public class UpdateCheckPreference extends Preference {

    public UpdateCheckPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public UpdateCheckPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public UpdateCheckPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UpdateCheckPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        String summary = UpdateCheckerPatch.getVersionSummary();
        if (!summary.isEmpty()) setSummary(summary);

        setOnPreferenceClickListener(preference -> {
            Activity activity = findActivity(getContext());
            if (activity != null) UpdateCheckerPatch.checkNow(activity);
            return true;
        });
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity activity) return activity;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}

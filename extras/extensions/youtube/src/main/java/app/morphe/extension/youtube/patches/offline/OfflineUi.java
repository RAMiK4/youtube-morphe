/*
 * Copyright 2026 lavinhoque33
 * https://github.com/anddea/revanced-patches/pull/1889
 *
 * Adapted for Morphe:
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.offline;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.util.Pair;
import android.widget.Switch;

import androidx.annotation.Nullable;

import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.settings.preference.ColorPickerView;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.shared.ui.Dim;

import static app.morphe.extension.shared.StringRef.str;

/**
 * Icons and dialogs shared by the offline library and player, drawn like YouTube's own screens.
 */
final class OfflineUi {
    private OfflineUi() {
    }

    /**
     * @param name Name of a drawable added by the patch, without the {@code morphe_offline_} prefix.
     * @return The drawable tinted with {@code color}.
     */
    static Drawable icon(Context context, String name, int color) {
        Drawable drawable = context.getDrawable(
                ResourceUtils.getIdentifierOrThrow(ResourceType.DRAWABLE, "morphe_offline_" + name));
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(color);
        }
        return drawable;
    }

    /** Icon, wrapping title/summary and a textless switch, shared by download pickers. */
    static Switch toggleRow(Context context, LinearLayout parent, String iconName, String title,
                            String summary, int foreground, boolean enabled, boolean checked) {
        int padding = Dim.dp(16);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(padding, Dim.dp(10), padding, Dim.dp(10));
        row.setAlpha(enabled ? 1f : 0.4f);

        ImageView image = new ImageView(context);
        image.setImageDrawable(icon(context, iconName, foreground));
        int iconSize = Dim.dp(24);
        row.addView(image, new LinearLayout.LayoutParams(iconSize, iconSize));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(padding, 0, padding, 0);
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(16);
        heading.setTextColor(foreground);
        labels.addView(heading);
        if (!summary.isEmpty()) {
            TextView caption = new TextView(context);
            caption.setText(summary);
            caption.setTextSize(13);
            caption.setTextColor(foreground);
            caption.setAlpha(0.7f);
            labels.addView(caption);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(context);
        toggle.setContentDescription(title);
        toggle.setEnabled(enabled);
        toggle.setChecked(enabled && checked);
        row.addView(toggle);
        row.setOnClickListener(view -> {
            if (toggle.isEnabled()) toggle.toggle();
        });
        parent.addView(row);
        return toggle;
    }

    /**
     * Shows the choices as a list with a check mark behind the selected one.
     *
     * @param title Optional dialog title.
     */
    static AlertDialog showChoices(Activity activity, @Nullable String title, String[] labels, int selected,
                                   int foreground, IntConsumer onChosen) {
        return showChoices(activity, title, labels, selected, foreground, onChosen, null);
    }

    static AlertDialog showChoices(Activity activity, @Nullable String title, String[] labels, int selected,
                                   int foreground, IntConsumer onChosen,
                                   @Nullable BiConsumer<TextView, Integer> decorate) {
        final int padding = Dim.dp(24);
        final int checkSize = Dim.dp(24);

        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return labels.length;
            }

            @Override
            public Object getItem(int position) {
                return labels[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Dim.dp(52));
                row.setPadding(padding, 0, padding, 0);

                TextView text = new TextView(activity);
                text.setText(labels[position]);
                text.setTextColor(foreground);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                if (decorate != null) decorate.accept(text, position);
                row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                ImageView check = new ImageView(activity);
                check.setImageDrawable(icon(activity, "check", foreground));
                check.setVisibility(position == selected ? View.VISIBLE : View.INVISIBLE);
                row.addView(check, new LinearLayout.LayoutParams(checkSize, checkSize));
                return row;
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setView(list);
        if (title != null) builder.setTitle(title);
        AlertDialog dialog = builder.create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            onChosen.accept(position);
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(dialogBackground(foreground));
        }
        return dialog;
    }

    /**
     * The RGB color picker of the Morphe settings, without a setting behind it.
     *
     * @param onApplied Called with the opaque color when OK is pressed.
     * @param onDismiss Called when the dialog closes in any way.
     */
    static Dialog showRgbPicker(Context context, String title, int initialColor, int defaultColor,
                                IntConsumer onApplied, Runnable onDismiss) {
        ColorPickerView picker = new ColorPickerView(context);
        picker.setOpacitySliderEnabled(false);
        picker.setColor(initialColor | 0xFF000000);

        Pair<Dialog, LinearLayout> dialog = CustomDialog.create(context, title, null, null, null,
                () -> onApplied.accept(picker.getColor() | 0xFF000000),
                () -> {
                },
                str("morphe_settings_reset_color"),
                () -> picker.setColor(defaultColor | 0xFF000000),
                false);
        LinearLayout layout = dialog.second;
        layout.addView(picker, layout.getChildCount() - 1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.first.setOnDismissListener(ignored -> onDismiss.run());
        dialog.first.setCanceledOnTouchOutside(false);
        dialog.first.show();
        return dialog.first;
    }

    private static Drawable dialogBackground(int foreground) {
        // Dark text on a light dialog and light text on a dark one.
        final boolean darkText = Color.luminance(foreground) < 0.5f;
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(Dim.dp(28));
        shape.setColor(darkText ? 0xFFF1F1F1 : 0xFF282828);
        return shape;
    }
}

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

import static app.morphe.extension.shared.StringRef.str;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.theme.ThemeUtils;
import app.morphe.extension.shared.ui.Dim;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.settings.preference.ToolbarPreferenceFragment;
import android.app.AlertDialog;

/**
 * The offline library: downloads in progress first, then the downloaded videos.
 */
@SuppressLint("SetTextI18n")
final class OfflineLibraryScreen implements OfflineScreens.Screen {
    private static final String[] SORTS = {
            OfflineScreens.SORT_NEWEST, OfflineScreens.SORT_OLDEST,
            OfflineScreens.SORT_TITLE, OfflineScreens.SORT_SIZE
    };

    /** Shared between screen instances, so returning to the library does not decode again. */
    private static final LruCache<String, Bitmap> thumbnails = new LruCache<>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final Set<String> thumbnailsLoading = ConcurrentHashMap.newKeySet();

    private final Activity activity;
    private final int foreground;
    private final int secondary;
    private final Adapter adapter = new Adapter();
    private final TextView emptyView;
    private final SearchView searchView;
    private final MenuItem searchItem;

    private final List<Object> rows = new ArrayList<>();
    private String query = "";
    private boolean destroyed;

    private final Runnable changeListener = this::reload;

    OfflineLibraryScreen(Activity activity) {
        this.activity = activity;
        activity.setTheme(ResourceUtils.getIdentifierOrThrow(ResourceType.STYLE, Utils.isDarkModeEnabled()
                ? "Theme.YouTube.Settings.Dark"
                : "Theme.YouTube.Settings"));
        ToolbarPreferenceFragment.setNavigationBarColor(activity.getWindow());

        foreground = ThemeUtils.getAppForegroundColor();
        secondary = (foreground & 0x00FFFFFF) | 0xAA000000;
        final int background = ThemeUtils.getAppBackgroundColor();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        root.setFitsSystemWindows(true);

        Toolbar toolbar = new Toolbar(activity);
        // Same toolbar color as the Morphe settings.
        toolbar.setBackgroundColor(Utils.isDarkModeEnabled()
                ? ResourceUtils.getColor("yt_black3", Color.BLACK)
                : ResourceUtils.getColor("yt_white1", Color.WHITE));
        toolbar.setNavigationIcon(ToolbarPreferenceFragment.getBackButtonDrawable());
        toolbar.setNavigationContentDescription(str("morphe_settings_navigate_up"));
        toolbar.setNavigationOnClickListener(v -> activity.finish());
        // The Toolbar's own title view sits lower than the back arrow when the theme gives it
        // a title margin or the font has extra padding. Use a plain TextView centered in a toolbar
        // of the standard action bar height.
        TextView toolbarTitle = new TextView(activity);
        toolbarTitle.setText(str("morphe_offline_library_title"));
        toolbarTitle.setTextColor(foreground);
        toolbarTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        toolbarTitle.setSingleLine();
        toolbarTitle.setEllipsize(TextUtils.TruncateAt.END);
        toolbarTitle.setIncludeFontPadding(false);
        toolbarTitle.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(toolbarTitle, new Toolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL));
        TypedValue actionBarSize = new TypedValue();
        final int toolbarHeight = activity.getTheme().resolveAttribute(
                android.R.attr.actionBarSize, actionBarSize, true)
                ? TypedValue.complexToDimensionPixelSize(actionBarSize.data, activity.getResources().getDisplayMetrics())
                : Dim.dp(56);
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toolbarHeight));

        searchView = new SearchView(activity);
        searchView.setQueryHint(str("morphe_offline_library_search"));
        searchView.setMaxWidth(Integer.MAX_VALUE);
        tintSearchView(searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String text) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
                reload();
                return true;
            }
        });

        Menu menu = toolbar.getMenu();
        searchItem = menu.add(str("morphe_offline_library_search"));
        searchItem.setIcon(OfflineUi.icon(activity, "search", foreground));
        searchItem.setActionView(searchView);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                searchView.setQuery("", false);
                return true;
            }
        });

        MenuItem sortItem = menu.add(str("morphe_offline_library_sort"));
        sortItem.setIcon(OfflineUi.icon(activity, "sort", foreground));
        sortItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        sortItem.setOnMenuItemClickListener(clicked -> {
            showSortDialog();
            return true;
        });

        FrameLayout content = new FrameLayout(activity);
        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object row = rows.get(position);
            if (row instanceof OfflineVideo video) {
                OfflineScreens.openPlayer(activity, video.videoId);
            }
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Object row = rows.get(position);
            if (row instanceof OfflineVideo video) {
                showActions(video);
                return true;
            }
            return false;
        });
        content.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = new TextView(activity);
        emptyView.setText(str("morphe_offline_library_empty"));
        emptyView.setTextColor(secondary);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyView.setGravity(Gravity.CENTER);
        final int padding = Dim.dp(32);
        emptyView.setPadding(padding, padding, padding, padding);
        content.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        list.setEmptyView(emptyView);

        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        activity.setContentView(root);

        OfflineIndex.addListener(changeListener);
        OfflineDownloader.addListener(changeListener);
        reload();
        verifyFiles();
    }

    private void showSortDialog() {
        String[] labels = new String[SORTS.length];
        for (int i = 0; i < SORTS.length; i++) {
            labels[i] = str("morphe_offline_library_sort_" + SORTS[i].toLowerCase(Locale.ROOT));
        }
        final String currentSort = SaveToDeviceSettings.LIBRARY_SORT.get();
        int selected = 0;
        for (int i = 0; i < SORTS.length; i++) {
            if (SORTS[i].equals(currentSort)) selected = i;
        }
        OfflineUi.showChoices(activity, str("morphe_offline_library_sort"), labels, selected, foreground, which -> {
            SaveToDeviceSettings.LIBRARY_SORT.save(SORTS[which]);
            reload();
        });
    }

    private void tintSearchView(SearchView view) {
        try {
            int id = activity.getResources().getIdentifier("android:id/search_src_text", null, null);
            TextView text = view.findViewById(id);
            if (text != null) {
                text.setTextColor(foreground);
                text.setHintTextColor(secondary);
            }
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not tint the search view", ex);
        }
    }

    // region data

    private void reload() {
        if (destroyed) return;
        rows.clear();
        for (OfflineDownloader.Task task : OfflineDownloader.getTasks()) {
            if (matches(task.title)) rows.add(task);
        }
        for (OfflineVideo video : OfflineScreens.sorted(OfflineIndex.getAll())) {
            if (matches(video.title) || matches(video.author)) rows.add(video);
        }
        emptyView.setText(query.isEmpty()
                ? str("morphe_offline_library_empty")
                : str("morphe_offline_library_no_results"));
        adapter.notifyDataSetChanged();
    }

    private boolean matches(String text) {
        return query.isEmpty() || text.toLowerCase(Locale.ROOT).contains(query);
    }

    /** Drops entries whose file was deleted outside the app. */
    private void verifyFiles() {
        List<OfflineVideo> videos = OfflineIndex.getAll();
        Utils.runOnBackgroundThread(() -> {
            for (OfflineVideo video : videos) {
                if (!OfflineMediaStore.exists(activity.getApplicationContext(), video.contentUri)) {
                    Logger.printInfo(() -> "File of " + video.videoId + " is gone, removing it from the library");
                    Utils.runOnMainThread(() -> OfflinePlayer.closeIfPlaying(video.videoId));
                    OfflineIndex.remove(video.videoId);
                }
            }
        });
    }

    // endregion

    // region actions

    private void showActions(OfflineVideo video) {
        String[] actions = {
                str("morphe_offline_action_play_background"),
                str("morphe_offline_action_share"),
                str("morphe_offline_action_open_youtube"),
                str("morphe_offline_action_delete"),
        };
        new AlertDialog.Builder(activity)
                .setTitle(video.title)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0 -> {
                            OfflinePlayer.play(video, OfflineScreens.order());
                            Utils.showToastShort(str("morphe_offline_playing_background"));
                        }
                        case 1 -> share(video);
                        case 2 -> openOnYouTube(video);
                        case 3 -> confirmDelete(video);
                        default -> {
                        }
                    }
                })
                .show();
    }

    private void share(OfflineVideo video) {
        Uri uri = Uri.parse(video.contentUri);
        if ("file".equals(uri.getScheme())) {
            // Sharing file uris crashes the receiver on Android 7+, and the app has no FileProvider.
            Utils.showToastLong(str("morphe_offline_share_unsupported"));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(OfflineMediaStore.mimeType(video.displayName)) // WebM downloads
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, video.title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(Intent.createChooser(intent, video.title));
        } catch (Exception ex) {
            Logger.printException(() -> "Could not share " + video.videoId, ex);
        }
    }

    private void openOnYouTube(OfflineVideo video) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/watch?v=" + video.videoId))
                .setPackage(activity.getPackageName());
        try {
            activity.startActivity(intent);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not open " + video.videoId, ex);
        }
    }

    private void confirmDelete(OfflineVideo video) {
        new AlertDialog.Builder(activity)
                .setTitle(str("morphe_offline_delete_title"))
                .setMessage(str("morphe_offline_delete_message", video.title))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    OfflinePlayer.closeIfPlaying(video.videoId);
                    if (OfflineMediaStore.delete(activity, video.contentUri)) {
                        Logger.printInfo(() -> "Deleted " + video.videoId);
                        OfflineIndex.remove(video.videoId);
                    }
                    // Otherwise the system asks for consent, and the next resume drops the entry
                    // if the user agreed.
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // endregion

    // region lifecycle

    @Override
    public boolean onBackPressed() {
        if (searchItem.isActionViewExpanded()) {
            searchItem.collapseActionView();
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        reload();
        verifyFiles();
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        OfflineIndex.removeListener(changeListener);
        OfflineDownloader.removeListener(changeListener);
    }

    // endregion

    // region rows

    static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.US, "%d:%02d", minutes, secs);
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof OfflineDownloader.Task ? 0 : 1;
        }

        @Override
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            Object row = rows.get(position);
            if (row instanceof OfflineDownloader.Task task) {
                TaskRow view = convertView instanceof TaskRow existing ? existing : new TaskRow();
                view.bind(task);
                return view;
            }
            VideoRow view = convertView instanceof VideoRow existing ? existing : new VideoRow();
            view.bind((OfflineVideo) row);
            return view;
        }
    }

    private TextView text(float sizeSp, int color, int maxLines) {
        TextView view = new TextView(activity);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setMaxLines(maxLines);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private final class TaskRow extends LinearLayout {
        private final TextView title = text(15, foreground, 2);
        private final TextView status = text(13, secondary, 1);
        private final ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        private String videoId;

        TaskRow() {
            super(activity);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            final int padding = Dim.dp(16);
            setPadding(padding, padding / 2, padding / 2, padding / 2);

            LinearLayout texts = new LinearLayout(activity);
            texts.setOrientation(VERTICAL);
            texts.addView(title);
            texts.addView(progress, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            texts.addView(status);
            addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageButton cancel = new ImageButton(activity);
            cancel.setImageDrawable(OfflineUi.icon(activity, "close", foreground));
            cancel.setBackgroundColor(Color.TRANSPARENT);
            cancel.setContentDescription(str("morphe_offline_cancel"));
            cancel.setOnClickListener(v -> {
                if (videoId != null) OfflineDownloader.cancel(videoId);
            });
            addView(cancel, new LayoutParams(Dim.dp(48), Dim.dp(48)));
        }

        void bind(OfflineDownloader.Task task) {
            videoId = task.videoId;
            title.setText(task.title);
            final int percent = task.getPercent();
            final boolean determinate = task.getState() == OfflineDownloader.State.DOWNLOADING && percent >= 0;
            progress.setIndeterminate(!determinate);
            if (determinate) progress.setProgress(percent);
            String state = switch (task.getState()) {
                case QUEUED -> str("morphe_offline_download_queued");
                case MERGING -> str("morphe_offline_download_merging");
                case SAVING -> str("morphe_offline_download_saving");
                default -> percent < 0
                        ? str("morphe_offline_download_starting")
                        : str("morphe_offline_download_progress", percent);
            };
            status.setText(task.qualityLabel + " · " + state);
        }
    }

    private final class VideoRow extends LinearLayout {
        private final ImageView thumbnail = new ImageView(activity);
        private final TextView duration = text(12, Color.WHITE, 1);
        private final TextView title = text(15, foreground, 2);
        private final TextView channel = text(13, secondary, 1);
        private final TextView details = text(13, secondary, 1);
        private String videoId;

        VideoRow() {
            super(activity);
            setOrientation(HORIZONTAL);
            final int padding = Dim.dp(12);
            setPadding(Dim.dp(16), padding, Dim.dp(16), padding);

            FrameLayout thumbnailFrame = new FrameLayout(activity);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setClipToOutline(true);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Dim.dp(8));
            shape.setColor(0x33808080);
            thumbnail.setBackground(shape);
            thumbnailFrame.addView(thumbnail, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(Dim.dp(4));
            badge.setColor(0xCC000000);
            duration.setBackground(badge);
            final int badgePadding = Dim.dp(4);
            duration.setPadding(badgePadding, 0, badgePadding, 0);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
            badgeParams.setMargins(badgePadding, badgePadding, badgePadding, badgePadding);
            thumbnailFrame.addView(duration, badgeParams);
            addView(thumbnailFrame, new LayoutParams(Dim.dp(160), Dim.dp(90)));

            LinearLayout texts = new LinearLayout(activity);
            texts.setOrientation(VERTICAL);
            texts.setPadding(padding, 0, 0, 0);
            texts.addView(title);
            texts.addView(channel);
            texts.addView(details);
            addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        void bind(OfflineVideo video) {
            videoId = video.videoId;
            title.setText(video.title);
            channel.setText(video.author);
            channel.setVisibility(video.author.isEmpty() ? GONE : VISIBLE);
            List<String> parts = new ArrayList<>();
            if (!video.qualityLabel.isEmpty()) parts.add(video.qualityLabel);
            if (video.sizeBytes > 0) parts.add(Formatter.formatShortFileSize(activity, video.sizeBytes));
            if (video.lastPositionMs > 0 && video.lengthMs > 0) {
                parts.add(str("morphe_offline_library_resume", formatDuration(video.lastPositionMs)));
            }
            details.setText(TextUtils.join(" · ", parts));
            duration.setText(formatDuration(video.lengthMs));
            duration.setVisibility(video.lengthMs > 0 ? VISIBLE : GONE);
            loadThumbnail(video);
        }

        private void loadThumbnail(OfflineVideo video) {
            Bitmap cached = thumbnails.get(video.videoId);
            thumbnail.setImageBitmap(cached);
            if (cached != null || video.thumbnailPath.isEmpty()
                    || !thumbnailsLoading.add(video.videoId)) {
                return;
            }
            Utils.runOnBackgroundThread(() -> {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeFile(video.thumbnailPath, options);
                thumbnailsLoading.remove(video.videoId);
                if (bitmap == null) return;
                thumbnails.put(video.videoId, bitmap);
                Utils.runOnMainThread(() -> {
                    if (video.videoId.equals(videoId)) thumbnail.setImageBitmap(bitmap);
                });
            });
        }
    }

}

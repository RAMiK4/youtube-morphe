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

final class OfflinePickerLayout {
    private OfflinePickerLayout() {
    }

    static int scrollCap(int screenHeight, int availableHeight, int footerHeight, int padding, int minimum) {
        int remaining = Math.max(0, availableHeight - footerHeight - padding);
        int preferred = Math.max(minimum, (int) (screenHeight * 0.55f));
        return Math.min(preferred, remaining);
    }
}

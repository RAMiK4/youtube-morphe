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

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.youtube.innertube.SaveToDeviceResponse.DownloadCaptionText;
import app.morphe.extension.youtube.innertube.SaveToDeviceResponse.DownloadCaptionTrack;
import app.morphe.extension.youtube.innertube.SaveToDeviceResponse.DownloadCaptionTrackList;
import app.morphe.extension.youtube.innertube.SaveToDeviceResponse.DownloadCaptionTranslationLanguage;

/** Pure metadata-to-picker mapping; native tracks never suppress a distinct translated choice. */
final class OfflineCaptionChoices {
    record Track(String key, String language, String kind, String translation, String name, String url) {
    }

    private OfflineCaptionChoices() {
    }

    static boolean needsMetadata(DownloadCaptionTrackList list) {
        // TV responses can include native tracks but omit the entire translation-language list.
        return list.getCaptionTracksCount() == 0 || list.getTranslationLanguagesCount() == 0;
    }

    static List<Track> from(DownloadCaptionTrackList list) {
        List<Track> result = new ArrayList<>();
        for (DownloadCaptionTrack track : list.getCaptionTracksList()) {
            if (track.getBaseUrl().isEmpty() || track.getLanguageCode().isEmpty()) continue;
            String key = track.getVssId().isEmpty()
                    ? track.getLanguageCode() + ":" + track.getKind() : track.getVssId();
            result.add(new Track(key, track.getLanguageCode(), track.getKind(), "",
                    name(track.getName(), track.getLanguageCode()), track.getBaseUrl()));
        }
        if (result.isEmpty()) return result;
        for (DownloadCaptionTranslationLanguage language : list.getTranslationLanguagesList()) {
            String target = language.getLanguageCode();
            if (target.isEmpty()) continue;
            int sourceIndex = sourceIndex(list, language);
            if (sourceIndex < 0) continue;
            DownloadCaptionTrack source = list.getCaptionTracks(sourceIndex);
            result.add(new Track("translate:" + target, source.getLanguageCode(), "translated", target,
                    name(language.getLanguageName(), target), source.getBaseUrl()));
        }
        return result;
    }

    private static int sourceIndex(DownloadCaptionTrackList list, DownloadCaptionTranslationLanguage language) {
        String target = language.getLanguageCode();
        if (language.getTranslationSourceTrackIndicesCount() > 0) {
            return sourceIndex(list, language.getTranslationSourceTrackIndicesList(), target);
        }
        int preferred = sourceIndex(list, list.getDefaultTranslationSourceTrackIndicesList(), target);
        if (preferred >= 0) return preferred;
        for (int index = 0; index < list.getCaptionTracksCount(); index++) {
            if (eligible(list.getCaptionTracks(index), target)) return index;
        }
        return -1;
    }

    private static int sourceIndex(DownloadCaptionTrackList list, List<Integer> indices, String target) {
        for (int index : indices) {
            if (index >= 0 && index < list.getCaptionTracksCount() && eligible(list.getCaptionTracks(index), target)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean eligible(DownloadCaptionTrack track, String target) {
        return track.getIsTranslatable() && !track.getBaseUrl().isEmpty() && !track.getLanguageCode().isEmpty()
                && !track.getLanguageCode().equals(target);
    }

    private static String name(DownloadCaptionText text, String fallback) {
        StringBuilder name = new StringBuilder();
        for (var run : text.getRunsList()) name.append(run.getText());
        return name.length() == 0 ? fallback : name.toString();
    }
}

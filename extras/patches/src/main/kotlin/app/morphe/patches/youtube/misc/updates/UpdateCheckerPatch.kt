package app.morphe.patches.youtube.misc.updates

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.shared.misc.settings.preference.NonInteractivePreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceCategory
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.shared.YouTubeActivityOnCreateFingerprint

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/updates/UpdateCheckerPatch;"

private const val PREFERENCE_CLASS =
    "app.morphe.extension.youtube.settings.preference.UpdateCheckPreference"

@Suppress("unused")
val updateCheckerPatch = bytecodePatch(
    name = "Update checker",
    description = "Adds an option to check for new builds on app startup.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    val updateUrl by stringOption(
        key = "updateUrl",
        default = "",
        title = "Update URL",
        description = "URL of the JSON file with the latest build. Leave empty to not check.",
    )

    val buildNumber by stringOption(
        key = "buildNumber",
        default = "0",
        title = "Build number",
        description = "Build number of this APK. A newer build has a higher number.",
    ) { it != null && it.matches(Regex("^\\d+$")) }

    val versionInfo by stringOption(
        key = "versionInfo",
        default = "",
        title = "Version text",
        description = "Text shown in the settings for the installed build.",
    )

    execute {
        val url = updateUrl.orEmpty().trim()
        if (url.isEmpty()) return@execute

        PreferenceScreen.MISC.addPreferences(
            PreferenceCategory(
                key = "morphe_update_category",
                titleKey = "morphe_update_category_title",
                sorting = PreferenceScreenPreference.Sorting.UNSORTED,
                preferences = setOf(
                    SwitchPreference("morphe_update_check", summary = true),
                    NonInteractivePreference(
                        key = "morphe_update_check_now",
                        tag = PREFERENCE_CLASS,
                        selectable = true,
                    ),
                ),
            ),
        )

        val extension = mutableClassDefBy(EXTENSION_CLASS)
        fun replace(name: String, value: String) =
            (extension.methods.firstOrNull { it.name == name }
                ?: throw PatchException("Method $name not found")).returnEarly(value)

        replace("updateUrl", url)
        replace("currentBuild", buildNumber!!)
        replace("versionInfo", versionInfo.orEmpty())

        YouTubeActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->onActivityCreated(Landroid/app/Activity;)V",
        )
    }
}

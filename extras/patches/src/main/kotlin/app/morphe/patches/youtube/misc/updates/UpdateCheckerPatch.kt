package app.morphe.patches.youtube.misc.updates

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.shared.misc.settings.preference.NonInteractivePreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.shared.YouTubeActivityOnCreateFingerprint
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/updates/UpdateCheckerPatch;"

private const val PREFERENCE_CLASS =
    "app.morphe.extension.youtube.settings.preference.UpdateCheckPreference"

// Sorted by key in the root menu: right below "About" (00_about).
private const val SCREEN_KEY = "morphe_settings_screen_00_updates"

private val updateCheckerResourcePatch = resourcePatch {
    execute {
        copyResources(
            "updates",
            ResourceGroup("drawable", "$SCREEN_KEY.xml", "${SCREEN_KEY}_bold.xml"),
        )
    }
}

@Suppress("unused")
val updateCheckerPatch = bytecodePatch(
    name = "Update checker",
    description = "Adds an option to check for new builds on app startup.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        updateCheckerResourcePatch,
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

        PreferenceScreen.Screen(
            key = SCREEN_KEY,
            summaryKey = null,
            icon = "@drawable/$SCREEN_KEY",
            iconBold = "@drawable/${SCREEN_KEY}_bold",
            layout = "@layout/preference_with_icon",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
        ).addPreferences(
            SwitchPreference("morphe_update_check", summary = true),
            NonInteractivePreference(
                key = "morphe_update_check_now",
                tag = PREFERENCE_CLASS,
                selectable = true,
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

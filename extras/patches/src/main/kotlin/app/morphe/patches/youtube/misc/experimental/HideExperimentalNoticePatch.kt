package app.morphe.patches.youtube.misc.experimental

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/shared/patches/ExperimentalAppNoticePatch;"

@Suppress("unused")
val hideExperimentalNoticePatch = bytecodePatch(
    name = "Hide experimental app notice",
    description = "Hides the notice shown on first start when the app version is newer than the recommended one.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        // Nothing to hide if this Morphe version has no such notice.
        mutableClassDefByOrNull(EXTENSION_CLASS)?.methods
            ?.firstOrNull { it.name == "experimentalNoticeShouldBeShown" }
            ?.returnEarly(false)
    }
}

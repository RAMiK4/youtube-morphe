/*
 * Copyright 2026 lavinhoque33
 * https://github.com/anddea/revanced-patches/pull/1883
 *
 * Adapted for Morphe:
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.savetodevice

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.misc.settings.preference.NonInteractivePreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.interaction.downloads.downloadsPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.shared.YouTubeActivityOnCreateFingerprint
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import java.util.logging.Logger

private const val EXTENSION_PATCH =
    "Lapp/morphe/extension/youtube/patches/offline/SaveToDevicePatch;"

private const val EXTENSION_OFFLINE_SERVICE_HOOKS =
    "Lapp/morphe/extension/youtube/patches/offline/OfflineServiceHooks;"

private const val EXTENSION_OFFLINE_SCREENS =
    "Lapp/morphe/extension/youtube/patches/offline/OfflineScreens;"

/**
 * Download entry points of Morphe, which the device downloader runs before.
 * The Shorts menu item calls these, if the Morphe version has it.
 */
private const val EXTENSION_DOWNLOADS_PATCH =
    "Lapp/morphe/extension/youtube/patches/DownloadsPatch;"

private const val EXTENSION_ACTIVITY_HOOK =
    "Lapp/morphe/extension/youtube/settings/YouTubeActivityHook;"

private val saveToDeviceResourcePatch = resourcePatch {
    dependsOn(settingsPatch)

    execute {
        PreferenceScreen.PLAYER.addPreferences(
            PreferenceScreenPreference(
                key = "morphe_save_to_device_screen",
                sorting = Sorting.UNSORTED,
                preferences = setOf(
                    SwitchPreference("morphe_save_to_device"),
                    SwitchPreference("morphe_offline_replace_downloads_page"),
                    NonInteractivePreference(
                        key = "morphe_offline_open_library",
                        tag = "app.morphe.extension.youtube.settings.preference.OpenOfflineLibraryPreference",
                        selectable = true
                    )
                )
            )
        )
    }
}

@Suppress("unused")
val saveToDevicePatch = bytecodePatch(
    name = "Save to device",
    description = "Adds an option to download videos as files to the device, " +
        "with a quality picker, a library of the saved videos and an offline player.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        downloadsPatch,
        saveToDeviceResourcePatch
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        // Every hook is optional: a missing match only disables that part and is logged.
        fun hookOptional(name: String, block: () -> Unit) {
            runCatching(block).onFailure {
                Logger.getLogger(this::class.java.name).warning("$name: ${it.message}")
            }
        }

        // Returns early if the call returns true, otherwise continues with the original code.
        // A null register means the first local register, which the original code assigns before it reads it.
        fun MutableMethod.skipIfTrue(index: Int, call: String, scratch: String?, skip: String) {
            val register = scratch ?: run {
                val isStatic = AccessFlags.STATIC.isSet(accessFlags)
                val parameterRegisters = parameterTypes.sumOf { type ->
                    val name = type.toString()
                    if (name == "J" || name == "D") 2 else 1
                } + if (isStatic) 0 else 1
                if (implementation!!.registerCount - parameterRegisters < 1) {
                    throw PatchException("$name has no local register")
                }
                "v0"
            }
            addInstructionsWithLabels(
                index,
                """
                    $call
                    move-result $register
                    if-eqz $register, :original_code
                    $skip
                """,
                ExternalLabel("original_code", getInstruction(index))
            )
        }

        // BackgroundPlayerService is the only stock service with the media playback foreground type.
        // The offline player uses it with its own intent actions.
        hookOptional("BackgroundPlayerService.onCreate") {
            BackgroundPlayerServiceOnCreateFingerprint.method.apply {
                // After the injection in the super call, before the setup of the app.
                val index = indexOfFirstInstructionOrThrow(Opcode.INVOKE_SUPER) + 1
                skipIfTrue(
                    index,
                    "invoke-static { p0 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onPlaybackServiceCreate(Landroid/app/Service;)Z",
                    null,
                    "return-void"
                )
            }
        }
        hookOptional("BackgroundPlayerService.onStartCommand") {
            BackgroundPlayerServiceOnStartCommandFingerprint.method.skipIfTrue(
                0,
                // The flags parameter is not used by the original code, so it is free as a scratch register.
                "invoke-static { p0, p1, p3 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onPlaybackServiceStartCommand(Landroid/app/Service;Landroid/content/Intent;I)Z",
                "p2",
                """
                    const/4 p2, 0x2
                    return p2
                """
            )
        }
        hookOptional("BackgroundPlayerService.onBind") {
            BackgroundPlayerServiceOnBindFingerprint.method.addInstruction(
                0,
                "invoke-static { p0 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onPlaybackServiceBind(Landroid/app/Service;)V"
            )
        }
        hookOptional("BackgroundPlayerService.onTaskRemoved") {
            BackgroundPlayerServiceOnTaskRemovedFingerprint.method.skipIfTrue(
                0,
                "invoke-static { p0 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onPlaybackServiceTaskRemoved(Landroid/app/Service;)Z",
                null,
                "return-void"
            )
        }
        hookOptional("BackgroundPlayerService.onDestroy") {
            BackgroundPlayerServiceOnDestroyFingerprint.let {
                it.method.skipIfTrue(
                    0,
                    "invoke-static { p0 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onPlaybackServiceDestroy(Landroid/app/Service;)Z",
                    null,
                    """
                        invoke-super { p0 }, ${it.classDef.superclass}->onDestroy()V
                        return-void
                    """
                )
            }
        }

        // OfflineKeepAliveService (data sync foreground type) is only used by the in-app downloads.
        // The device downloader uses it to keep the process alive while downloading.
        hookOptional("OfflineKeepAliveService.onCreate") {
            OfflineKeepAliveServiceOnCreateFingerprint.let {
                val serviceType = it.classDef.type
                it.method.apply {
                    // Before the foreground setup, after the field injection.
                    val index = indexOfFirstInstructionOrThrow {
                        val reference = getReference<MethodReference>()
                        opcode == Opcode.INVOKE_DIRECT &&
                            reference?.definingClass == serviceType &&
                            reference.name != "<init>" &&
                            reference.parameterTypes.isEmpty()
                    }
                    skipIfTrue(
                        index,
                        "invoke-static { p0 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onKeepAliveServiceCreate(Landroid/app/Service;)Z",
                        null,
                        "return-void"
                    )
                }
            }
        }
        hookOptional("OfflineKeepAliveService.onStartCommand") {
            OfflineKeepAliveServiceOnStartCommandFingerprint.method.skipIfTrue(
                0,
                "invoke-static { p0, p1, p3 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onKeepAliveServiceStartCommand(Landroid/app/Service;Landroid/content/Intent;I)Z",
                "p2",
                """
                    const/4 p2, 0x2
                    return p2
                """
            )
        }
        hookOptional("OfflineKeepAliveService.onDestroy") {
            val method = OfflineKeepAliveServiceOnDestroyFingerprint.methodOrNull
            if (method != null) {
                method.skipIfTrue(
                    0,
                    "invoke-static { p0 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onKeepAliveServiceDestroy(Landroid/app/Service;)Z",
                    null,
                    """
                        invoke-super { p0 }, ${OfflineKeepAliveServiceOnDestroyFingerprint.classDef.superclass}->onDestroy()V
                        return-void
                    """
                )
            } else {
                // Some versions do not override onDestroy.
                OfflineKeepAliveServiceOnCreateFingerprint.classDef.apply {
                    ImmutableMethod(
                        type,
                        "onDestroy",
                        emptyList(),
                        "V",
                        AccessFlags.PUBLIC.value,
                        null,
                        null,
                        MutableMethodImplementation(1)
                    ).toMutable().apply {
                        addInstructions(
                            0,
                            """
                                invoke-static { p0 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onKeepAliveServiceDestroy(Landroid/app/Service;)Z
                                invoke-super { p0 }, $superclass->onDestroy()V
                                return-void
                            """
                        )
                    }.let(methods::add)
                }
            }
        }
        // Service.onTimeout exists since Android 15, older targets do not override it.
        OfflineKeepAliveServiceOnTimeoutFingerprint.methodOrNull?.let { method ->
            hookOptional("OfflineKeepAliveService.onTimeout") {
                method.skipIfTrue(
                    0,
                    "invoke-static { p0 }, $EXTENSION_OFFLINE_SERVICE_HOOKS->onKeepAliveServiceTimeout(Landroid/app/Service;)Z",
                    null,
                    "return-void"
                )
            }
        }

        // Replace the Downloads page: cancel the navigation to "FEdownloads"
        // and open the saved videos on top of the current page instead.
        hookOptional("Browse endpoint command") {
            SaveToDeviceBrowseEndpointCommandFingerprint.let {
                it.method.apply {
                    val constIndex = it.instructionMatches.first().index
                    val constRegister = getInstruction<OneRegisterInstruction>(constIndex).registerA
                    val equalsIndex = indexOfFirstInstructionOrThrow(constIndex) {
                        opcode == Opcode.INVOKE_VIRTUAL &&
                            getReference<MethodReference>()?.name == "equals"
                    }
                    val equalsInstruction = getInstruction<FiveRegisterInstruction>(equalsIndex)
                    val browseIdRegister = if (equalsInstruction.registerC == constRegister) {
                        equalsInstruction.registerD
                    } else {
                        equalsInstruction.registerC
                    }
                    // The const string overwrites the register next, so it is free here.
                    skipIfTrue(
                        constIndex,
                        "invoke-static/range { v$browseIdRegister .. v$browseIdRegister }, $EXTENSION_OFFLINE_SCREENS->openLibraryInsteadOfBrowse(Ljava/lang/String;)Z",
                        "v$constRegister",
                        "return-void"
                    )
                }
            }
        }

        // The library row of the You tab opens the Downloads page with its own command,
        // which never reaches the browse command handler. The command resolver sees the
        // commands of taps, links and rows, so both forms are replaced there.
        hookOptional("Command resolver") {
            SaveToDeviceResolveCommandFingerprint.method.skipIfTrue(
                0,
                "invoke-static/range { p1 .. p1 }, $EXTENSION_OFFLINE_SCREENS->openLibraryInsteadOfCommand(Ljava/lang/Object;)Z",
                null,
                "return-void"
            )
        }

        // region Entry points.

        // Load the settings at startup.
        YouTubeActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, $EXTENSION_PATCH->onActivityCreated(Landroid/app/Activity;)V"
        )

        // The Download buttons of the app: the action button below the video,
        // the 'Download' item of the feed menus. Runs before the download mode of Morphe.
        SaveToDeviceOfflineVideoEndpointFingerprint.method.skipIfTrue(
            0,
            "invoke-static/range { p3 .. p3 }, $EXTENSION_PATCH->download(Ljava/lang/String;)Z",
            null,
            "return-void"
        )

        // The download item of the Shorts menu of Morphe, which is shown with the download modes
        // that Morphe downloads with. Only in Morphe versions that have it.
        mutableClassDefBy(EXTENSION_DOWNLOADS_PATCH).methods.let { methods ->
            methods.firstOrNull {
                it.name == "isDownloadModeHandledByMorphe" && it.parameterTypes.isEmpty()
            }?.let { method ->
                hookOptional("Shorts download item visibility") {
                    method.skipIfTrue(
                        0,
                        "invoke-static { }, $EXTENSION_PATCH->isEnabled()Z",
                        null,
                        "return v0"
                    )
                }
            }
            methods.firstOrNull {
                it.name == "downloadVideo" && it.parameterTypes == listOf("Ljava/lang/String;")
            }?.let { method ->
                hookOptional("Shorts download item") {
                    method.skipIfTrue(
                        0,
                        "invoke-static { p0 }, $EXTENSION_PATCH->download(Ljava/lang/String;)Z",
                        null,
                        "return v0"
                    )
                }
            }
        }

        // The saved videos and the offline player are shown in the activity of the Morphe settings.
        mutableClassDefBy(EXTENSION_ACTIVITY_HOOK).methods.first {
            it.name == "initialize" && it.parameterTypes == listOf("Landroid/app/Activity;")
        }.skipIfTrue(
            0,
            "invoke-static { p0 }, $EXTENSION_OFFLINE_SCREENS->initialize(Landroid/app/Activity;)Z",
            null,
            "return-void"
        )

        // Back presses of the offline screens. The back press method of the host activity is created
        // by the settings patch and calls handleBackPress(), which returns if the back press was handled.
        // The created method itself is not changed, since its branches do not move with added code.
        mutableClassDefBy(EXTENSION_ACTIVITY_HOOK).methods.let { methods ->
            val withActivity = methods.firstOrNull {
                it.name == "handleBackPress" && it.parameterTypes == listOf("Landroid/app/Activity;")
            }
            if (withActivity != null) {
                withActivity.skipIfTrue(
                    0,
                    "invoke-static { p0 }, $EXTENSION_OFFLINE_SCREENS->onBackPressed(Landroid/app/Activity;)Z",
                    null,
                    "return v0"
                )
            } else {
                // Older Morphe versions do not pass the activity.
                methods.first {
                    it.name == "handleBackPress" && it.parameterTypes.isEmpty()
                }.skipIfTrue(
                    0,
                    "invoke-static { }, $EXTENSION_OFFLINE_SCREENS->onBackPressed()Z",
                    null,
                    "return v0"
                )
            }
        }

        // endregion
    }
}

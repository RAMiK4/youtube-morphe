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

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.anyInstruction
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The handler of the offline video endpoint, which the Download buttons of the app run.
 */
internal object SaveToDeviceOfflineVideoEndpointFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Ljava/util/Map;",
        "L",
        "Ljava/lang/String", // Video ID
        "L",
    ),
    filters = listOf(
        anyInstruction(
            string("Unsupported Offline Video Action: "), // 21.14 and lower
            string("Unsupported Offline Video Action: %s") // 21.15+
        )
    )
)

/**
 * The command resolver, which looks up the handler of every command (taps, links, rows) and calls it.
 */
internal object SaveToDeviceResolveCommandFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(
        anyInstruction(
            string("Unknown command not resolved"), // 20.x
            string("Unknown command not resolved %s") // 21.x
        )
    )
)

/**
 * The command handler of browse endpoints, such as the navigation to the "FEdownloads" page.
 */
internal object SaveToDeviceBrowseEndpointCommandFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(
        string("FEsfv_channel_pivot"),
        string("replace_pane_predicate")
    )
)

/**
 * A lifecycle method of a stock service. The service class names are kept,
 * because the manifest references them.
 */
internal class SaveToDeviceServiceMethodFingerprint(classSuffix: String, methodName: String) : Fingerprint(
    custom = { method, classDef ->
        classDef.type.endsWith(classSuffix) && method.name == methodName
    }
)

private const val BACKGROUND_PLAYER_SERVICE_SUFFIX = "/BackgroundPlayerService;"
private const val OFFLINE_KEEP_ALIVE_SERVICE_SUFFIX = "/OfflineKeepAliveService;"

internal val BackgroundPlayerServiceOnCreateFingerprint =
    SaveToDeviceServiceMethodFingerprint(BACKGROUND_PLAYER_SERVICE_SUFFIX, "onCreate")
internal val BackgroundPlayerServiceOnStartCommandFingerprint =
    SaveToDeviceServiceMethodFingerprint(BACKGROUND_PLAYER_SERVICE_SUFFIX, "onStartCommand")
internal val BackgroundPlayerServiceOnBindFingerprint =
    SaveToDeviceServiceMethodFingerprint(BACKGROUND_PLAYER_SERVICE_SUFFIX, "onBind")
internal val BackgroundPlayerServiceOnTaskRemovedFingerprint =
    SaveToDeviceServiceMethodFingerprint(BACKGROUND_PLAYER_SERVICE_SUFFIX, "onTaskRemoved")
internal val BackgroundPlayerServiceOnDestroyFingerprint =
    SaveToDeviceServiceMethodFingerprint(BACKGROUND_PLAYER_SERVICE_SUFFIX, "onDestroy")

internal val OfflineKeepAliveServiceOnCreateFingerprint =
    SaveToDeviceServiceMethodFingerprint(OFFLINE_KEEP_ALIVE_SERVICE_SUFFIX, "onCreate")
internal val OfflineKeepAliveServiceOnStartCommandFingerprint =
    SaveToDeviceServiceMethodFingerprint(OFFLINE_KEEP_ALIVE_SERVICE_SUFFIX, "onStartCommand")
internal val OfflineKeepAliveServiceOnDestroyFingerprint =
    SaveToDeviceServiceMethodFingerprint(OFFLINE_KEEP_ALIVE_SERVICE_SUFFIX, "onDestroy")
internal val OfflineKeepAliveServiceOnTimeoutFingerprint =
    SaveToDeviceServiceMethodFingerprint(OFFLINE_KEEP_ALIVE_SERVICE_SUFFIX, "onTimeout")

package app.morphe.patches.youtube.layout.theme

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SETTINGS_CLASS = "Lapp/morphe/extension/shared/settings/SharedYouTubeSettings;"
private const val THEME_CLASS = "Lapp/morphe/extension/shared/theme/ThemeColorPatch"
private const val DARK_ENUM = "$THEME_CLASS\$ThemeColorDark;"
private const val LIGHT_ENUM = "$THEME_CLASS\$ThemeColorLight;"
private const val FOREGROUND_AVAILABILITY_CLASS = "$THEME_CLASS\$ThemeColorChangeForegroundAvailability;"

// Defaults of the extension. Nothing is changed for an option left at these values.
private const val DEFAULT_DARK = "PURE_BLACK"
private const val DEFAULT_LIGHT = "WHITE"
private const val DEFAULT_DARK_CUSTOM = "#0F0F0F"
private const val DEFAULT_LIGHT_CUSTOM = "#FFFFFF"

private val darkValues = mapOf(
    "App default" to "APP_DEFAULT",
    "Pure black" to "PURE_BLACK",
    "Material You (Pure black)" to "MATERIAL_YOU_PURE_BLACK",
    "Material You (Neutral)" to "MATERIAL_YOU_NEUTRAL",
    "Material You (Primary)" to "MATERIAL_YOU_PRIMARY",
    "Material You (Secondary)" to "MATERIAL_YOU_SECONDARY",
    "Material You (Tertiary)" to "MATERIAL_YOU_TERTIARY",
    "Catppuccin (Mocha)" to "CATPPUCCIN_MOCHA",
    "Classic YouTube" to "CLASSIC_YOUTUBE",
    "Dark pink" to "DARK_PINK",
    "Dark blue" to "DARK_BLUE",
    "Dark green" to "DARK_GREEN",
    "Dark yellow" to "DARK_YELLOW",
    "Dark orange" to "DARK_ORANGE",
    "Dark red" to "DARK_RED",
    "Custom" to "CUSTOM",
)

private val lightValues = mapOf(
    "App default" to "APP_DEFAULT",
    "White" to "WHITE",
    "Material You (White)" to "MATERIAL_YOU_WHITE",
    "Material You (Neutral)" to "MATERIAL_YOU_NEUTRAL",
    "Material You (Primary)" to "MATERIAL_YOU_PRIMARY",
    "Material You (Secondary)" to "MATERIAL_YOU_SECONDARY",
    "Material You (Tertiary)" to "MATERIAL_YOU_TERTIARY",
    "Catppuccin (Latte)" to "CATPPUCCIN_LATTE",
    "Light pink" to "LIGHT_PINK",
    "Light blue" to "LIGHT_BLUE",
    "Light green" to "LIGHT_GREEN",
    "Light yellow" to "LIGHT_YELLOW",
    "Light orange" to "LIGHT_ORANGE",
    "Light red" to "LIGHT_RED",
    "Custom" to "CUSTOM",
)

private val hexColor = Regex("^#[0-9A-Fa-f]{6}$")

@Suppress("unused")
val themeDefaultsPatch = bytecodePatch(
    name = "Theme defaults",
    description = "Changes the theme colors a new install starts with. " +
        "All colors can still be changed in the app settings. " +
        "Requires the theme colors of the Theme patch to be changed in the app.",
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    dependsOn(themePatch)

    val darkDefault by stringOption(
        key = "darkDefault",
        default = DEFAULT_DARK,
        values = darkValues,
        title = "Default dark theme color",
        description = "The dark theme color selected in the app settings after install.",
        required = true,
    ) { it != null && it in darkValues.values }

    val lightDefault by stringOption(
        key = "lightDefault",
        default = DEFAULT_LIGHT,
        values = lightValues,
        title = "Default light theme color",
        description = "The light theme color selected in the app settings after install.",
        required = true,
    ) { it != null && it in lightValues.values }

    val darkCustomDefault by stringOption(
        key = "darkCustomDefault",
        default = DEFAULT_DARK_CUSTOM,
        title = "Default custom dark color",
        description = "The value of the custom dark theme color, as #RRGGBB.",
        required = true,
    ) { it != null && hexColor.matches(it) }

    val lightCustomDefault by stringOption(
        key = "lightCustomDefault",
        default = DEFAULT_LIGHT_CUSTOM,
        title = "Default custom light color",
        description = "The value of the custom light theme color, as #RRGGBB.",
        required = true,
    ) { it != null && hexColor.matches(it) }

    val changeForegroundDefault by booleanOption(
        key = "changeForegroundDefault",
        default = false,
        title = "Change foreground color by default",
        description = "Draw text and icons with the color of the other theme, " +
            "so the dark theme uses the light color for text and the light theme the dark color.",
        required = true,
    )

    execute {
        val dark = darkDefault!!
        val light = lightDefault!!
        val darkCustom = darkCustomDefault!!.uppercase()
        val lightCustom = lightCustomDefault!!.uppercase()
        val changeForeground = changeForegroundDefault!!

        val nothingChanged = dark == DEFAULT_DARK && light == DEFAULT_LIGHT &&
            darkCustom == DEFAULT_DARK_CUSTOM && lightCustom == DEFAULT_LIGHT_CUSTOM &&
            !changeForeground
        if (nothingChanged) return@execute

        val clinit = mutableClassDefBy(SETTINGS_CLASS).methods.firstOrNull { it.name == "<clinit>" }
            ?: throw PatchException("Static initializer of $SETTINGS_CLASS not found")

        if (dark != DEFAULT_DARK) clinit.replaceEnumDefault("morphe_theme_color_dark", DARK_ENUM, dark)
        if (light != DEFAULT_LIGHT) clinit.replaceEnumDefault("morphe_theme_color_light", LIGHT_ENUM, light)
        if (darkCustom != DEFAULT_DARK_CUSTOM) clinit.replaceStringDefault("morphe_theme_color_dark_custom", darkCustom)
        if (lightCustom != DEFAULT_LIGHT_CUSTOM) clinit.replaceStringDefault("morphe_theme_color_light_custom", lightCustom)
        if (changeForeground) clinit.replaceBooleanDefault("morphe_theme_color_change_foreground", true)

        // The extension only offers the foreground setting once a color was changed from its
        // default. With other defaults the setting has to be available from the start.
        if (dark != DEFAULT_DARK || light != DEFAULT_LIGHT) {
            mutableClassDefBy(FOREGROUND_AVAILABILITY_CLASS).methods
                .first { it.name == "isAvailable" && it.parameterTypes.isEmpty() }
                .returnEarly(true)
        }
    }
}

private fun MutableMethod.indexOfKey(key: String): Int =
    instructions.indexOfFirst {
        it.opcode == Opcode.CONST_STRING &&
            ((it as ReferenceInstruction).reference as StringReference).string == key
    }.takeIf { it >= 0 } ?: throw PatchException("Setting key not found: $key")

private fun MutableMethod.replaceEnumDefault(key: String, enumType: String, value: String) {
    val start = indexOfKey(key)
    val index = (start + 1 until instructions.size).firstOrNull { i ->
        val instruction = getInstruction(i)
        instruction.opcode == Opcode.SGET_OBJECT &&
            ((instruction as ReferenceInstruction).reference as FieldReference).type == enumType
    } ?: throw PatchException("Default value of $key not found")

    val register = getInstruction<OneRegisterInstruction>(index).registerA
    replaceInstruction(index, "sget-object v$register, $enumType->$value:$enumType")
}

private fun MutableMethod.replaceStringDefault(key: String, value: String) {
    val start = indexOfKey(key)
    val index = (start + 1 until instructions.size).firstOrNull { i ->
        getInstruction(i).opcode == Opcode.CONST_STRING
    } ?: throw PatchException("Default value of $key not found")

    val register = getInstruction<OneRegisterInstruction>(index).registerA
    replaceInstruction(index, "const-string v$register, \"$value\"")
}

private fun MutableMethod.replaceBooleanDefault(key: String, value: Boolean) {
    val start = indexOfKey(key)
    for (i in start + 1 until instructions.size) {
        val instruction = getInstruction(i)

        // Boolean.FALSE / Boolean.TRUE
        if (instruction.opcode == Opcode.SGET_OBJECT) {
            val field = (instruction as ReferenceInstruction).reference as FieldReference
            if (field.definingClass == "Ljava/lang/Boolean;") {
                val register = (instruction as OneRegisterInstruction).registerA
                val name = if (value) "TRUE" else "FALSE"
                replaceInstruction(i, "sget-object v$register, Ljava/lang/Boolean;->$name:Ljava/lang/Boolean;")
                return
            }
        }

        // const/4 vX, 0x0 followed by Boolean.valueOf(Z)
        if (instruction.opcode == Opcode.CONST_4 && i + 1 < instructions.size) {
            val next = getInstruction(i + 1)
            val isValueOf = next.opcode == Opcode.INVOKE_STATIC &&
                ((next as ReferenceInstruction).reference as MethodReference).let {
                    it.definingClass == "Ljava/lang/Boolean;" && it.name == "valueOf"
                }
            if (isValueOf && (instruction as NarrowLiteralInstruction).narrowLiteral in 0..1) {
                val register = (instruction as OneRegisterInstruction).registerA
                replaceInstruction(i, "const/4 v$register, ${if (value) "0x1" else "0x0"}")
                return
            }
        }

        // Stop at the next setting.
        if (instruction.opcode == Opcode.NEW_INSTANCE && i > start + 1) break
    }
    throw PatchException("Default value of $key not found")
}

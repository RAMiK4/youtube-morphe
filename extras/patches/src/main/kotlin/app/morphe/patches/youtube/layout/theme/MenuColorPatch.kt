package app.morphe.patches.youtube.layout.theme

import app.morphe.patcher.patch.listDecodableFiles
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml

/** The color of the dark theme the Theme patch selects at runtime, declared by every dark variant. */
private const val THEME_COLOR_DARK = "morphe_theme_color_dark"

/** The color of menus and sheets in the dark theme. */
private const val MENU_COLOR_DARK = "morphe_menu_color_dark"

/** Menus of a pure black theme, which would not stand out from the background in black. */
private const val PURE_BLACK_MENU_COLOR = "#FF0F0F0F"

private val BLACK_VALUES = setOf("@android:color/black", "#000000", "#ff000000")

/** The style of the dark theme and its item of the raised background of menus and sheets. */
private const val DARK_STYLE = "yt.sys.color.baseline.dark"
private const val RAISED_BACKGROUND_ITEM = "yt_sys_color_baseline_raised_background"

/** The app colors of menus and sheets, which the Theme patch gives the theme color. */
private val MENU_COLOR_NAMES = setOf(
    "yt_black1",
    "yt_sys_color_baseline_mobile_dark_default_raised_background",
)

/**
 * Menus and sheets in the theme color, with #0F0F0F for a pure black theme.
 *
 * Since 21.43 the dark theme takes the raised background of menus and sheets (the playback speed,
 * quality and player settings menus, Save to playlist...) from a style item that points to the grey
 * of the app (#212121), which no theme color replaces. The item gets a color of its own here that
 * follows the theme color, so the menus have the background of the app, as in the patch option of the
 * Theme patch. With pure black they are #0F0F0F instead, YouTube's own background.
 */
internal val menuColorPatch = patch {
    dependsOn(themePatch)

    execute {
        // Only with the theme colors of the app settings: a color set while patching replaces everything itself.
        val hasThemeColor = resourceXml("res/values/colors.xml").use { colors ->
            colors.root.children.any { it.attribute("name") == THEME_COLOR_DARK }
        }
        if (!hasThemeColor) return@execute

        resourceXml("res/values/colors.xml").use { colors ->
            colors.root.children.forEach { color ->
                if (color.attribute("name") in MENU_COLOR_NAMES && color.text == "@color/$THEME_COLOR_DARK") {
                    color.text = "@color/$MENU_COLOR_DARK"
                }
            }
            colors.root.appendChild("color") {
                setAttribute("name", MENU_COLOR_DARK)
                text = "@color/$THEME_COLOR_DARK"
            }
        }

        // The variants of the dark theme colors: a pure black one gets the menu color of its own.
        listDecodableFiles("res/values-mcc*/colors.xml").forEach { path ->
            resourceXml(path).use { colors ->
                val themeColor = colors.root.children
                    .firstOrNull { it.attribute("name") == THEME_COLOR_DARK }?.text ?: return@use
                if (themeColor.lowercase() !in BLACK_VALUES) return@use
                colors.root.appendChild("color") {
                    setAttribute("name", MENU_COLOR_DARK)
                    text = PURE_BLACK_MENU_COLOR
                }
            }
        }

        listDecodableFiles("res/values*/styles.xml").forEach { path ->
            resourceXml(path).use { styles ->
                styles.root.children("style")
                    .filter { it.attribute("name") == DARK_STYLE }
                    .forEach { style ->
                        style.children("item")
                            .filter { it.attribute("name") == RAISED_BACKGROUND_ITEM }
                            .forEach { it.text = "@color/$MENU_COLOR_DARK" }
                    }
            }
        }
    }
}

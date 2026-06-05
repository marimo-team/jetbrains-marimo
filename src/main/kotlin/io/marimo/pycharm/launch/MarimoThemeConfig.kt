/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.pycharm.launch

import com.intellij.openapi.application.PathManager
import com.intellij.ui.JBColor
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives the embedded marimo editor's theme through marimo's own config instead of injecting JS.
 *
 * marimo resolves `display.theme` from a `marimo.toml` it finds under `$XDG_CONFIG_HOME/marimo`. We
 * launch the marimo process with `XDG_CONFIG_HOME` pointed at a plugin-owned directory holding a
 * `marimo.toml` whose `theme` is overwritten to match the IDE — but merged over the user's real config
 * so their other marimo settings survive. The theme is resolved explicitly to "light"/"dark" rather
 * than "system" because the embedded JCEF browser's `prefers-color-scheme` does not track the IDE theme.
 */
object MarimoThemeConfig {

    /** Returns the env var name + value to apply to a marimo command so it renders in the IDE theme. */
    fun environment(): Pair<String, String> = "XDG_CONFIG_HOME" to prepareConfigHome().toString()

    private fun prepareConfigHome(): Path {
        val merged = withTheme(readUserConfig(), ideTheme())
        val base = Path.of(PathManager.getConfigPath(), "marimo-theme")
        val target = base.resolve("marimo").resolve("marimo.toml")
        Files.createDirectories(target.parent)
        Files.writeString(target, merged)
        return base
    }

    private fun ideTheme(): String = if (JBColor.isBright()) "light" else "dark"

    private fun readUserConfig(): String? {
        val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.config")
        val path = Path.of(configHome, "marimo", "marimo.toml")
        return if (Files.isRegularFile(path)) Files.readString(path) else null
    }

    /**
     * Returns [existing] marimo.toml text with `[display].theme` set to [theme], preserving every other
     * setting. Inserts the `[display]` table when absent and replaces any prior `theme` within it.
     */
    fun withTheme(existing: String?, theme: String): String {
        val themeLine = "theme = \"$theme\""
        if (existing.isNullOrBlank()) return "[display]\n$themeLine\n"

        val lines = existing.lines().toMutableList()
        var inDisplay = false
        var displayHeaderIndex = -1
        var replaced = false
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inDisplay = trimmed == "[display]"
                if (inDisplay) displayHeaderIndex = i
            } else if (inDisplay && Regex("""^\s*theme\s*=""").containsMatchIn(lines[i])) {
                lines[i] = themeLine
                replaced = true
            }
        }
        if (replaced) return lines.joinToString("\n")
        if (displayHeaderIndex >= 0) {
            lines.add(displayHeaderIndex + 1, themeLine)
            return lines.joinToString("\n")
        }
        val separator = if (existing.endsWith("\n")) "" else "\n"
        return existing + separator + "\n[display]\n$themeLine\n"
    }
}

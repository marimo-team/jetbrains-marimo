/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook

import com.intellij.ui.JBColor

/** The IDE theme used by the marimo launch environment and legacy editor URL. */
object MarimoTheme {
    const val ENVIRONMENT_VARIABLE = "MARIMO_DISPLAY_THEME"

    fun ideTheme(): String = if (JBColor.isBright()) "light" else "dark"
}

package com.github.kirangadhave.marimopycharm.editor

import com.intellij.ui.JBColor

/**
 * Builds the JS that pushes the IDE theme into the embedded marimo page. The probe confirmed marimo's
 * editor reads the `document.body` theme signal; we set all three variants (dataset, class, color-scheme)
 * so we're robust to whichever one the frontend keys off.
 */
object MarimoTheme {
    fun isIdeDark(): Boolean = !JBColor.isBright()

    fun injectionJs(dark: Boolean): String {
        val theme = if (dark) "dark" else "light"
        return """
            (function () {
              var b = document.body;
              if (!b) return;
              b.dataset.theme = '$theme';
              b.classList.toggle('dark', $dark);
              b.classList.toggle('dark-mode', $dark);
              b.style.colorScheme = '$theme';
            })();
        """.trimIndent()
    }
}

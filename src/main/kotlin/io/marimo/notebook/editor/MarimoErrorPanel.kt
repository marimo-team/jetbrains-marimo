/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Swing fallback shown in the notebook tab when marimo can't be displayed. A failed launch has no URL to
 * render, so this offers themeable, actionable buttons instead of a raw browser error page. The panel is
 * presentation only: the editor supplies each action's behaviour through [onAction].
 */
class MarimoErrorPanel(
    model: MarimoErrorModel,
    onAction: (MarimoErrorAction) -> Unit,
) : JPanel(GridBagLayout()) {

    init {
        // Every row shares column 0 with anchor CENTER, and all weights stay 0, so GridBag centers the
        // whole block in the panel and each row on one vertical axis regardless of its width.
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.CENTER
            insets = JBUI.insets(4, 0)
        }

        add(JBLabel(AllIcons.General.Error), gbc)

        gbc.gridy++
        add(JBLabel(html(model.message)), gbc)

        model.detail?.let {
            gbc.gridy++
            add(JBLabel(html(it)).apply { foreground = UIUtil.getContextHelpForeground() }, gbc)
        }

        gbc.gridy++
        add(buttonRow(model.actions, model.sandboxEnabled, onAction), gbc)
    }

    private fun buttonRow(
        actions: List<MarimoErrorAction>,
        sandboxEnabled: Boolean,
        onAction: (MarimoErrorAction) -> Unit,
    ): JPanel =
        JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(8), 0)).apply {
            isOpaque = false
            actions.forEach { action ->
                add(JButton(label(action)).apply {
                    addActionListener { onAction(action) }
                    if (action == MarimoErrorAction.START_IN_SANDBOX && !sandboxEnabled) {
                        isEnabled = false
                        toolTipText = SANDBOX_NEEDS_UV
                    }
                })
            }
        }

    private fun html(text: String): String =
        "<html><div style='text-align:center;width:${JBUI.scale(360)}px'>$text</div></html>"

    private fun label(action: MarimoErrorAction): String =
        when (action) {
            MarimoErrorAction.RETRY -> "Retry"
            MarimoErrorAction.INSTALL -> "Install marimo"
            MarimoErrorAction.START_IN_SANDBOX -> "Start in Sandbox"
            MarimoErrorAction.OPEN_AS_PYTHON -> "Open as Python File"
        }

    companion object {
        const val SANDBOX_NEEDS_UV = "Sandbox mode requires uv — install from astral.sh/uv"
    }
}

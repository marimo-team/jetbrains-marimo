/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridBagLayout
import javax.swing.BoxLayout
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
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        content.add(centered(JBLabel(AllIcons.General.Error)))
        content.add(strut())
        content.add(centered(JBLabel(html(model.message))))

        model.detail?.let {
            content.add(strut())
            content.add(centered(JBLabel(html(it)).apply { foreground = UIUtil.getContextHelpForeground() }))
        }

        content.add(strut())
        content.add(centered(buttonRow(model.actions, model.sandboxEnabled, onAction)))

        add(content)
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

    private fun centered(component: Component): Component =
        component.also { (it as? javax.swing.JComponent)?.alignmentX = Component.CENTER_ALIGNMENT }

    private fun strut() = javax.swing.Box.createVerticalStrut(JBUI.scale(8))

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

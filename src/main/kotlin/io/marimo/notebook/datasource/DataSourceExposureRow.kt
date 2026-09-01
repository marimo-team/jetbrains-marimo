/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import io.marimo.notebook.MarimoBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/** A compact data-source row that does not stretch to fill the tool-window viewport. */
internal class DataSourceExposureRow(
    name: String,
    detail: String,
    exposed: Boolean,
    supported: Boolean,
    primary: Boolean = false,
    showPrimaryAction: Boolean = false,
    onExposureChanged: (Boolean) -> Unit,
    onMakePrimary: () -> Unit,
) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(4, 0, 4, 0)

        val header =
            JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                alignmentX = LEFT_ALIGNMENT
                add(JBLabel(name), BorderLayout.CENTER)
                add(
                    JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                        isOpaque = false
                        if (showPrimaryAction && exposed) {
                            if (primary) {
                                add(
                                    JBLabel(MarimoBundle.message("datasource.panel.default"))
                                        .apply {
                                            foreground = JBColor.GRAY
                                        }
                                )
                            } else {
                                add(
                                    compactButton(
                                        MarimoBundle.message("datasource.panel.make.default"),
                                        onMakePrimary,
                                    )
                                )
                            }
                        }
                        add(
                            compactButton(
                                    MarimoBundle.message(
                                        if (exposed) "datasource.panel.unshare"
                                        else "datasource.panel.share"
                                    )
                                ) {
                                    onExposureChanged(!exposed)
                                }
                                .apply { isEnabled = supported }
                        )
                    },
                    BorderLayout.EAST,
                )
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            }
        val detailLabel =
            JBLabel(detail).apply {
                foreground = JBColor.GRAY
                alignmentX = LEFT_ALIGNMENT
                border = BorderFactory.createEmptyBorder(1, 2, 0, 0)
            }
        add(header)
        add(detailLabel)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    private fun compactButton(text: String, action: () -> Unit): JButton =
        JButton(text).apply {
            margin = Insets(1, 6, 1, 6)
            isFocusable = false
            addActionListener { action() }
        }
}

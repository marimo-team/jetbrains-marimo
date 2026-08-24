/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import io.marimo.notebook.MarimoIcons
import io.marimo.notebook.MarimoLocalhost
import io.marimo.notebook.session.MarimoSessionState
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.SessionSnapshot
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class MarimoSessionsToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.service<NotebookSessionManager>()
        val panel = MarimoSessionsPanel(project, service)
        service.addSessionsListener(panel) { panel.refresh() }
        panel.refresh()
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

private class MarimoSessionsPanel(
    private val project: Project,
    private val service: NotebookSessionManager,
) : JPanel(BorderLayout()), Disposable {
    private val cards =
        JBPanel<JBPanel<*>>().apply {
            layout = GridBagLayout()
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }

    init {
        add(JBScrollPane(cards), BorderLayout.CENTER)
    }

    fun refresh() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val snapshots = service.sessions().sortedBy { it.fileName.lowercase() }
            cards.removeAll()
            if (snapshots.isEmpty()) {
                cards.add(
                    JBLabel("No active marimo sessions").apply {
                        foreground = JBColor.GRAY
                        border = BorderFactory.createEmptyBorder(4, 2, 4, 2)
                    },
                    gridConstraint(0, GridBagConstraints.HORIZONTAL).apply {
                        anchor = GridBagConstraints.NORTHWEST
                    },
                )
            } else {
                snapshots.forEachIndexed { index, snapshot ->
                    cards.add(
                        SessionCard(project, service, snapshot),
                        gridConstraint(index * 2, GridBagConstraints.HORIZONTAL).apply {
                            anchor = GridBagConstraints.NORTHWEST
                        },
                    )
                    if (index != snapshots.lastIndex) {
                        cards.add(
                            separatorRow(),
                            gridConstraint(index * 2 + 1, GridBagConstraints.HORIZONTAL).apply {
                                insets = java.awt.Insets(6, 0, 6, 0)
                            },
                        )
                    }
                }
            }
            cards.add(
                JPanel().apply { isOpaque = false },
                gridConstraint(snapshots.size * 2 + 1, GridBagConstraints.BOTH).apply {
                    weighty = 1.0
                },
            )
            cards.revalidate()
            cards.repaint()
        }
    }

    override fun dispose() = Unit
}

private class SessionCard(
    private val project: Project,
    private val service: NotebookSessionManager,
    snapshot: SessionSnapshot,
) : JPanel(BorderLayout()) {
    init {
        val launch = snapshot.launch
        val url = launch?.let { MarimoLocalhost.rootUrl(it.port) } ?: "Not started yet"
        val canControl = snapshot.state.isLive
        val copyUrlAvailable = launch != null && !launch.tokenAuthEnabled

        border =
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(6, 8, 6, 8),
                BorderFactory.createEmptyBorder(),
            )
        val header =
            JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(JLabel(snapshot.fileName, MarimoIcons.FILE, JLabel.LEFT), BorderLayout.CENTER)
                add(statusBadge(snapshot.state), BorderLayout.EAST)
            }

        val details =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
                add(detailLine("URL", url))
            }

        val actions =
            JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
                add(
                    iconButton(AllIcons.Actions.Forward, "Open notebook").apply {
                        addActionListener {
                            val file =
                                VirtualFileManager.getInstance().findFileByUrl(snapshot.fileUrl)
                                    ?: return@addActionListener
                            openMarimoNotebook(project, file)
                        }
                    }
                )
                add(
                    iconButton(AllIcons.Actions.Copy, "Copy URL").apply {
                        isEnabled = copyUrlAvailable
                        addActionListener {
                            launch?.port?.let {
                                CopyPasteManager.getInstance()
                                    .setContents(StringSelection(MarimoLocalhost.rootUrl(it)))
                            }
                        }
                    }
                )
                add(
                    iconButton(AllIcons.Actions.Restart, "Restart session").apply {
                        isEnabled = canControl
                        addActionListener {
                            val file =
                                VirtualFileManager.getInstance().findFileByUrl(snapshot.fileUrl)
                                    ?: return@addActionListener
                            service.withExistingActionLease(file) { it.restart() }
                        }
                    }
                )
                add(
                    iconButton(AllIcons.Actions.Suspend, "Stop session").apply {
                        isEnabled = canControl
                        addActionListener {
                            val file =
                                VirtualFileManager.getInstance().findFileByUrl(snapshot.fileUrl)
                                    ?: return@addActionListener
                            service.withExistingActionLease(file) { it.stop() }
                        }
                    }
                )
            }

        add(header, BorderLayout.NORTH)
        add(details, BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun statusBadge(state: MarimoSessionState): JLabel {
        val (text, color) =
            when (state) {
                MarimoSessionState.RUNNING -> "RUNNING" to JBColor(0x2E7D32, 0x6FBF73)
                MarimoSessionState.STARTING -> "STARTING" to JBColor(0x1565C0, 0x6EA8FF)
                MarimoSessionState.STOPPING -> "STOPPING" to JBColor(0xEF6C00, 0xFFB366)
                MarimoSessionState.FAILED -> "FAILED" to JBColor(0xC62828, 0xFF7B7B)
                MarimoSessionState.STOPPED -> "STOPPED" to JBColor.GRAY
            }
        return JLabel(text).apply { foreground = color }
    }

    private fun detailLine(label: String, value: String): JLabel =
        JLabel("$label: $value").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }

    private fun iconButton(icon: javax.swing.Icon, tooltip: String): JButton =
        JButton(icon).apply {
            toolTipText = tooltip
            isFocusable = false
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
            preferredSize = Dimension(16, 16)
            val hoverBackground = JBColor(0xEAF2FF, 0x3A4758)
            addHoverListener(hoverBackground)
        }
}

private fun gridConstraint(gridy: Int, fill: Int): GridBagConstraints =
    GridBagConstraints().apply {
        gridx = 0
        this.gridy = gridy
        weightx = 1.0
        this.fill = fill
    }

private fun JButton.addHoverListener(hoverBackground: JBColor) {
    addMouseListener(
        object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                this@addHoverListener.isOpaque = true
                this@addHoverListener.isContentAreaFilled = true
                this@addHoverListener.background = hoverBackground
            }

            override fun mouseExited(e: MouseEvent) {
                this@addHoverListener.isOpaque = false
                this@addHoverListener.isContentAreaFilled = false
            }
        }
    )
}

private fun separatorRow(): JPanel =
    JPanel(BorderLayout()).apply {
        isOpaque = false
        add(
            JPanel().apply {
                background = JBColor(0xE1E4E8, 0x4E5254)
                preferredSize = Dimension(1, 1)
                minimumSize = Dimension(1, 1)
                maximumSize = Dimension(Int.MAX_VALUE, 1)
            },
            BorderLayout.CENTER,
        )
    }

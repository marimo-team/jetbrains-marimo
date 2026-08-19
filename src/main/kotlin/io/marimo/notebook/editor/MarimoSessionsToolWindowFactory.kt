/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.MarimoIcons
import io.marimo.notebook.server.MarimoServerService
import io.marimo.notebook.server.MarimoSessionSnapshot
import io.marimo.notebook.server.MarimoSessionState
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
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.BoxLayout

class MarimoSessionsToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.service<MarimoServerService>()
        val panel = MarimoSessionsPanel(project, service)
        service.addSessionsListener(panel) { panel.refresh() }
        panel.refresh()
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private class MarimoSessionsPanel(
    private val project: Project,
    private val service: MarimoServerService,
) : JPanel(BorderLayout()), Disposable {
    private val cards = JBPanel<JBPanel<*>>().apply {
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
                cards.add(JBLabel("No active marimo sessions").apply {
                    foreground = JBColor.GRAY
                    border = BorderFactory.createEmptyBorder(4, 2, 4, 2)
                }, GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    weightx = 1.0
                    anchor = GridBagConstraints.NORTHWEST
                    fill = GridBagConstraints.HORIZONTAL
                })
            } else {
                snapshots.forEachIndexed { index, snapshot ->
                    cards.add(SessionCard(project, service, snapshot), GridBagConstraints().apply {
                        gridx = 0
                        gridy = index * 2
                        weightx = 1.0
                        anchor = GridBagConstraints.NORTHWEST
                        fill = GridBagConstraints.HORIZONTAL
                        insets = java.awt.Insets(0, 0, 0, 0)
                    })
                    if (index != snapshots.lastIndex) {
                        cards.add(separatorRow(), GridBagConstraints().apply {
                            gridx = 0
                            gridy = index * 2 + 1
                            weightx = 1.0
                            fill = GridBagConstraints.HORIZONTAL
                            insets = java.awt.Insets(6, 0, 6, 0)
                        })
                    }
                }
            }
            cards.add(JPanel().apply { isOpaque = false }, GridBagConstraints().apply {
                gridx = 0
                gridy = snapshots.size * 2 + 1
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            })
            cards.revalidate()
            cards.repaint()
        }
    }

    override fun dispose() = Unit
}

private class SessionCard(
    private val project: Project,
    private val service: MarimoServerService,
    snapshot: MarimoSessionSnapshot,
) : JPanel(BorderLayout()) {
    init {
        val launch = snapshot.launch
        val url = launch?.let { "http://127.0.0.1:${it.port}/" } ?: "Not started yet"
        val canControl = snapshot.state.isLive

        border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(6, 8, 6, 8),
            BorderFactory.createEmptyBorder(),
        )
        val header = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(JLabel(snapshot.fileName, MarimoIcons.FILE, JLabel.LEFT), BorderLayout.CENTER)
            add(statusBadge(snapshot.state), BorderLayout.EAST)
        }

        val details = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
            add(detailLine("URL", url))
        }

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
            add(iconButton(AllIcons.Actions.Forward, "Open notebook").apply {
                addActionListener {
                    val file = VirtualFileManager.getInstance().findFileByUrl(snapshot.fileUrl) ?: return@addActionListener
                    openMarimoNotebook(project, file)
                }
            })
            add(iconButton(AllIcons.Actions.Copy, "Copy URL").apply {
                isEnabled = launch != null
                addActionListener { launch?.port?.let { CopyPasteManager.getInstance().setContents(StringSelection("http://127.0.0.1:$it/")) } }
            })
            add(iconButton(AllIcons.Actions.Restart, "Restart session").apply {
                isEnabled = canControl
                addActionListener {
                    val file = VirtualFileManager.getInstance().findFileByUrl(snapshot.fileUrl) ?: return@addActionListener
                    service.restart(file)
                }
            })
            add(iconButton(AllIcons.Actions.Suspend, "Stop session").apply {
                isEnabled = canControl
                addActionListener { service.stopUrl(snapshot.fileUrl) }
            })
        }

        add(header, BorderLayout.NORTH)
        add(details, BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun statusBadge(state: MarimoSessionState): JLabel {
        val (text, color) = when (state) {
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
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    isOpaque = true
                    isContentAreaFilled = true
                    background = hoverBackground
                }

                override fun mouseExited(e: MouseEvent) {
                    isOpaque = false
                    isContentAreaFilled = false
                }
            })
        }
}

private fun separatorRow(): JPanel =
    JPanel(BorderLayout()).apply {
        isOpaque = false
        add(JPanel().apply {
            background = JBColor(0xE1E4E8, 0x4E5254)
            preferredSize = Dimension(1, 1)
            minimumSize = Dimension(1, 1)
            maximumSize = Dimension(Int.MAX_VALUE, 1)
        }, BorderLayout.CENTER)
    }

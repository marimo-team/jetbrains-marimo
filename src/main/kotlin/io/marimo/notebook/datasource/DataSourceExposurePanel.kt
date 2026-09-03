/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import io.marimo.notebook.MarimoBundle
import io.marimo.notebook.datasource.ide.IdeDataSourceCatalog
import io.marimo.notebook.session.NotebookSessionManager
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/** Notebook-specific data-source sharing controls in the Marimo Sessions tool window. */
class DataSourceExposurePanel(
    private val project: Project,
    private val selectedNotebook: () -> VirtualFile?,
) : JPanel(BorderLayout(0, 8)), Disposable {
    private val store = DataSourceExposureStore.getInstance(project)
    private val list = JPanel()
    private val target = JBLabel()
    private val shareAll = compactButton(MarimoBundle.message("datasource.panel.share.all"))
    private val restartAction = DataSourceRestartAction {
        notebookFile?.let { project.service<NotebookSessionManager>().restart(it) }
    }
    private var candidates: List<CandidateDataSource> = emptyList()
    private var notebookFile: VirtualFile? = null
    private var notebookKey: String? = null
    private var selection: DataSourceExposureSelection? = null
    private val loadState = DataSourceExposurePanelLoadState()

    init {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        list.layout = BoxLayout(list, BoxLayout.Y_AXIS)
        add(header(), BorderLayout.NORTH)
        add(
            JBScrollPane(list).apply {
                border = BorderFactory.createEmptyBorder()
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            },
            BorderLayout.CENTER,
        )
        add(footer(), BorderLayout.SOUTH)
        shareAll.addActionListener { shareAll() }
        project.messageBus
            .connect(this)
            .subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        reload()
                    }
                },
            )
        reload()
    }

    fun reload() {
        notebookFile = selectedNotebook()
        notebookKey = notebookFile?.let { NotebookExposureKey.from(project, it) }
        val load = loadState.begin(notebookKey)
        candidates = emptyList()
        selection = null
        shareAll.isEnabled = false
        restartAction.isVisible = shouldOfferRestartForSelectedNotebook()
        updateTarget()
        if (notebookKey == null) {
            showMessage(MarimoBundle.message("datasource.panel.select.notebook"))
            return
        }
        showMessage(MarimoBundle.message("datasource.panel.loading"))
        AppExecutorUtil.getAppExecutorService().execute {
            val loaded = IdeDataSourceCatalog.candidates(project)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (!loadState.isCurrent(load)) return@invokeLater
                candidates = loaded
                renderCandidates()
            }
        }
    }

    private fun header(): JPanel =
        JPanel(BorderLayout(8, 4)).apply {
            isOpaque = false
            add(target, BorderLayout.CENTER)
            add(
                compactButton(MarimoBundle.message("datasource.panel.refresh")).apply {
                    addActionListener { reload() }
                },
                BorderLayout.EAST,
            )
        }

    private fun footer(): JPanel =
        JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(shareAll)
                },
                BorderLayout.WEST,
            )
            add(restartAction, BorderLayout.CENTER)
        }

    private fun updateTarget() {
        target.text =
            notebookKey?.let { MarimoBundle.message("datasource.panel.target", it) }
                ?: MarimoBundle.message("datasource.panel.no.target")
    }

    private fun renderCandidates() {
        val key = notebookKey ?: return
        list.removeAll()
        if (candidates.isEmpty()) {
            selection = null
            shareAll.isEnabled = false
            showMessage(MarimoBundle.message("datasource.panel.empty"))
            return
        }
        val current =
            DataSourceExposureSelection(candidates, store.exposedIds(key), store.defaultIds(key))
        selection = current
        val familySizes =
            current.items
                .filter { it.candidate.supported && it.candidate.family != null }
                .groupingBy { it.candidate.family }
                .eachCount()
        current.items.forEach { item ->
            val candidate = item.candidate
            val detail =
                candidate.unsupportedReason
                    ?: MarimoBundle.message(
                        if (item.exposed) "datasource.panel.shared.with"
                        else "datasource.panel.not.shared.with",
                        key,
                    )
            list.add(
                DataSourceExposureRow(
                    name = candidate.displayName,
                    detail = detail,
                    exposed = item.exposed,
                    supported = candidate.supported,
                    familyDefault = item.familyDefault,
                    showDefaultAction = (familySizes[candidate.family] ?: 0) > 1,
                    onExposureChanged = { exposed ->
                        current.setExposed(candidate.id, exposed)
                        persist(key, current)
                    },
                    onMakeDefault = {
                        current.setDefault(candidate.id)
                        persist(key, current)
                    },
                )
            )
        }
        shareAll.isEnabled = current.items.any { it.candidate.supported && !it.exposed }
        list.revalidate()
        list.repaint()
    }

    private fun shareAll() {
        val key = notebookKey ?: return
        val current = selection ?: return
        current.shareAll()
        persist(key, current)
    }

    private fun persist(key: String, current: DataSourceExposureSelection) {
        if (store.recordExposures(key, current.entries())) {
            DataSourceStaleness.exposureEdited(project, key)
            restartAction.isVisible = shouldOfferRestartForSelectedNotebook()
        }
        renderCandidates()
    }

    private fun shouldOfferRestartForSelectedNotebook(): Boolean {
        val snapshot =
            notebookFile?.let { project.service<NotebookSessionManager>().peek(it) } ?: return false
        return shouldOfferDataSourceRestart(snapshot.state, snapshot.launchEnvStale)
    }

    private fun showMessage(message: String) {
        list.removeAll()
        list.add(
            JBLabel(message).apply {
                foreground = JBColor.GRAY
                border = BorderFactory.createEmptyBorder(6, 2, 6, 2)
            }
        )
        list.revalidate()
        list.repaint()
    }

    override fun dispose() = Unit

    private fun compactButton(text: String): JButton =
        JButton(text).apply {
            margin = Insets(1, 6, 1, 6)
            isFocusable = false
        }
}

internal data class DataSourceExposurePanelLoad(
    val generation: Long,
    val notebookKey: String?,
)

/** Rejects catalog results from an earlier notebook selection or refresh. */
internal class DataSourceExposurePanelLoadState {
    private var generation = 0L
    private var current = DataSourceExposurePanelLoad(generation, null)

    fun begin(notebookKey: String?): DataSourceExposurePanelLoad {
        current = DataSourceExposurePanelLoad(++generation, notebookKey)
        return current
    }

    fun isCurrent(load: DataSourceExposurePanelLoad): Boolean = load == current
}

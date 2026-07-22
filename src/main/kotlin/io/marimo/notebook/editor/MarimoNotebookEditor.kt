/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.launch.MarimoEnvProbe
import io.marimo.notebook.launch.MarimoInstaller
import io.marimo.notebook.launch.MarimoPresence
import io.marimo.notebook.launch.UvLauncher
import io.marimo.notebook.server.MarimoServerService
import io.marimo.notebook.telemetry.MarimoConsentPrompt
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Component
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.ln

class MarimoNotebookEditor(private val project: Project, private val file: VirtualFile) :
    UserDataHolderBase(), FileEditor {

    private val panel = object : JPanel(BorderLayout()), DataProvider {
        override fun getData(dataId: String): Any? =
            if (CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) file else null
    }
    private val browser = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val server = project.service<MarimoServerService>()
    private val propertyChangeSupport = PropertyChangeSupport(this)

    init {
        browser?.let(::installLoadErrorHandler)
        browser?.let(::installPopupHandler)
        browser?.let(::installEditorFontZoom)
        loadNotebook()
    }

    /**
     * Keep the embedded notebook's zoom in step with the IDE's editor font size, so enlarging the font
     * across editors enlarges the notebook too. CEF resets zoom on navigation, so reapply on every main-frame
     * load as well as whenever the global scheme changes.
     */
    private fun installEditorFontZoom(browser: JBCefBrowser) {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { onEdt { applyEditorFontZoom(browser) } })
        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == true) onEdt { applyEditorFontZoom(browser) }
                }
            },
            browser.cefBrowser,
        )
    }

    /**
     * Map the editor font size onto a CEF zoom level, where the scale factor is `1.2^level`. The platform
     * default font size renders the notebook at its native 100%; larger fonts scale it up proportionally.
     */
    private fun applyEditorFontZoom(browser: JBCefBrowser) {
        val fontSize = EditorColorsManager.getInstance().globalScheme.editorFontSize
        browser.cefBrowser.zoomLevel = ln(fontSize.toDouble() / FontPreferences.DEFAULT_FONT_SIZE) / ln(1.2)
    }

    /**
     * marimo opens a duplicated (or otherwise linked) notebook with `window.open("?file=…", "_blank")`.
     * Left to JCEF's default, that popup becomes a detached OS window that can't be docked as a tab. Catch
     * it here: open notebook deep links as IDE editor tabs and send genuine external links to the system
     * browser, so no stray Chromium window ever appears.
     */
    private fun installPopupHandler(browser: JBCefBrowser) {
        browser.jbCefClient.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    targetUrl: String?,
                    targetFrameName: String?,
                ): Boolean {
                    when (val popup = classifyMarimoPopup(targetUrl)) {
                        null -> return false
                        is MarimoPopup.Notebook -> openNotebookTab(popup.path)
                        is MarimoPopup.External -> BrowserUtil.browse(popup.url)
                    }
                    return true
                }
            },
            browser.cefBrowser,
        )
    }

    /**
     * Resolve a just-created notebook path to a [VirtualFile] and open it as an editor tab. The VFS refresh
     * is synchronous and must run off the EDT; the freshly copied file is picked up as a marimo notebook and
     * rendered in this same editor kind.
     */
    private fun openNotebookTab(path: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val target = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            if (target == null) {
                thisLogger().warn("marimo popup: could not resolve notebook path $path")
                return@executeOnPooledThread
            }
            onEdt { FileEditorManager.getInstance(project).openFile(target, true) }
        }
    }

    /**
     * Catch the case where the server started but its page fails to load (server died after readiness,
     * connection refused, render crash) so the user sees the actionable panel rather than a raw Chromium
     * error page. Only main-frame errors matter; sub-frame failures and user-cancelled loads (ERR_ABORTED,
     * e.g. a Retry that navigates away mid-load) are not editor failures.
     */
    private fun installLoadErrorHandler(browser: JBCefBrowser) {
        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadError(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    errorCode: CefLoadHandler.ErrorCode?,
                    errorText: String?,
                    failedUrl: String?,
                ) {
                    if (frame?.isMain != true) return
                    if (errorCode == null || errorCode == CefLoadHandler.ErrorCode.ERR_NONE) return
                    if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
                    val detail = errorText?.takeIf { it.isNotBlank() } ?: errorCode.name
                    val model = MarimoErrorModel.of(
                        MarimoFailure.EditorLoadFailed(detail), MarimoPresence.Unknown, uvAvailable = false,
                    )
                    onEdt { showContent(MarimoErrorPanel(model, ::onErrorAction)) }
                }
            },
            browser.cefBrowser,
        )
    }

    private fun loadNotebook() {
        showContent(JLabel("Starting marimo…", SwingConstants.CENTER))
        server.urlFor(file).whenComplete { url, err ->
            when {
                err != null -> showServerError(err)
                browser == null -> onEdt {
                    showContent(JLabel("The embedded browser isn't available in this IDE.", SwingConstants.CENTER))
                }
                else -> onEdt {
                    browser.loadURL(url)
                    showContent(browser.component)
                    MarimoConsentPrompt.maybePrompt(project)
                    val launcher = if (server.isSandbox(file)) "uv-sandbox" else "sdk"
                    MarimoTelemetry.getInstance().capture(TelemetryEvent.NotebookOpened(launcher))
                }
            }
        }
    }

    /** Probe off the EDT — detection may run a subprocess — then render the matching error panel. */
    private fun showServerError(err: Throwable?) {
        thisLogger().warn("marimo failed to start for ${file.name}", err)
        ApplicationManager.getApplication().executeOnPooledThread {
            val probe = project.service<MarimoEnvProbe>()
            probe.invalidate()
            val presence = probe.probe(file)
            val uvAvailable = UvLauncher.findUv() != null
            val reason = when {
                presence is MarimoPresence.Unknown -> "no_interpreter"
                presence is MarimoPresence.Missing -> "marimo_missing"
                !uvAvailable -> "uv_missing"
                else -> "other"
            }
            MarimoTelemetry.getInstance().capture(TelemetryEvent.NotebookLaunchFailed(reason))
            MarimoTelemetry.getInstance().captureException(err ?: RuntimeException("marimo failed to start"))
            val model = MarimoErrorModel.of(
                MarimoFailure.ServerNotStarted(err), presence, uvAvailable = uvAvailable,
            )
            onEdt { showContent(MarimoErrorPanel(model, ::onErrorAction)) }
        }
    }

    private fun onErrorAction(action: MarimoErrorAction) {
        when (action) {
            MarimoErrorAction.RETRY -> relaunch()
            MarimoErrorAction.INSTALL -> {
                project.service<MarimoInstaller>().installMarimo(file)
                relaunch()
            }
            MarimoErrorAction.START_IN_SANDBOX -> {
                server.enableSandbox(file)
                MarimoTelemetry.getInstance().capture(TelemetryEvent.SandboxStarted)
                relaunch()
            }
            MarimoErrorAction.OPEN_AS_PYTHON ->
                FileEditorManager.getInstance(project).setSelectedEditor(file, MARIMO_SOURCE_EDITOR_TYPE)
        }
    }

    /** Re-launch this notebook, picking up any launch-mode change (e.g. a newly requested sandbox). */
    fun reload() = relaunch()

    /**
     * The service caches the failed handle by file URL, so a retry that reused it would replay the same
     * failure; release first to force a fresh launch.
     */
    private fun relaunch() {
        server.release(file)
        loadNotebook()
    }

    private fun showContent(component: Component) {
        panel.removeAll()
        addToolbar()
        panel.add(component, BorderLayout.CENTER)
        panel.revalidate()
        panel.repaint()
    }

    private fun onEdt(block: () -> Unit) = ApplicationManager.getApplication().invokeLater(block)

    private fun addToolbar() {
        val row = JPanel(BorderLayout())
        pairToolbar()?.let { row.add(it, BorderLayout.WEST) }
        if (server.isSandbox(file)) row.add(sandboxIndicator(), BorderLayout.EAST)
        if (row.componentCount > 0) panel.add(row, BorderLayout.NORTH)
    }

    private fun pairToolbar(): JComponent? {
        val pairGroup = ActionManager.getInstance().getAction("Marimo.Pair") ?: return null
        val group = DefaultActionGroup(pairGroup)
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MarimoEditorToolbar", group, true)
        toolbar.targetComponent = panel
        return toolbar.component
    }

    /**
     * Read-only badge marking that cells run in marimo's isolated uv sandbox (PEP 723 inline deps)
     * rather than the project interpreter. Not a toggle: leaving sandbox mode can't undo package
     * changes it already made, so the state is surfaced but not reversible from here.
     */
    private fun sandboxIndicator(): JComponent =
        JBLabel("Sandbox", AllIcons.Nodes.Padlock, SwingConstants.LEFT).apply {
            toolTipText = "Running in marimo's isolated uv sandbox (PEP 723 dependencies), not the project interpreter."
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyRight(8)
        }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent? = browser?.component
    override fun getName(): String = "marimo"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun getFile(): VirtualFile = file
    override fun addPropertyChangeListener(listener: PropertyChangeListener) =
        propertyChangeSupport.addPropertyChangeListener(listener)
    override fun removePropertyChangeListener(listener: PropertyChangeListener) =
        propertyChangeSupport.removePropertyChangeListener(listener)
    override fun dispose() {
        browser?.dispose()
        server.release(file)
    }
}

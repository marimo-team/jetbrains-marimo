/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.view

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.marimo.notebook.editor.error.ErrorAction
import io.marimo.notebook.editor.error.ErrorModel
import io.marimo.notebook.editor.error.ErrorPanel
import io.marimo.notebook.editor.error.Failure
import io.marimo.notebook.editor.error.FailureDiagnostics
import io.marimo.notebook.editor.source.MARIMO_SOURCE_EDITOR_TYPE
import io.marimo.notebook.launch.LifecycleStateUpdate
import io.marimo.notebook.launch.MarimoNotebookState
import io.marimo.notebook.launch.StopCause
import io.marimo.notebook.launch.redactAccessTokens
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.PageConfigReader
import io.marimo.notebook.session.environment.MarimoPresence
import io.marimo.notebook.telemetry.MarimoConsentPrompt
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter

/**
 * The long-lived UI and process state for a single open notebook: the JCEF browser, its content
 * panel, and the marimo server it renders. Owned by [NotebookViewRegistry] and keyed by session,
 * not by any one editor tab. Moving a notebook between splits disposes and recreates the
 * [com.intellij.openapi.fileEditor.FileEditor], but the view survives, so the same browser stays
 * connected to the same session.
 */
class NotebookView(private val project: Project, private val file: VirtualFile) :
    NotebookEditorView, Disposable {

    override val panel: JPanel =
        object : JPanel(BorderLayout()), DataProvider {
            override fun getData(dataId: String): Any? =
                if (CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) file else null
        }
    private val browser =
        if (JBCefApp.isSupported() && !ApplicationManager.getApplication().isUnitTestMode)
            JBCefBrowser()
        else null
    private val sessionManager = project.service<NotebookSessionManager>()
    private val lifecycle = sessionManager.lifecycleFor(file)
    private val themeController =
        ThemeController(
            disposable = this,
            onEdt = { block -> onEdt(block = block) },
            onThemeReload = ::reloadForIdeTheme,
        )
    private val popupRouter =
        PopupRouter(
            project = project,
            expectedOrigin = { navigationSnapshot.expectedOrigin },
            onEdt = { block -> onEdt(block = block) },
        )

    /** Disposal can be triggered from any thread, and is checked from the EDT. */
    @Volatile private var disposed = false

    /** Identity of the navigation the browser currently renders, or begins rendering. */
    @Volatile private var navigationSnapshot = NavigationSnapshot(0, null)

    init {
        browser?.let(::installLoadErrorHandler)
        browser?.let(popupRouter::installOn)
        browser?.let(themeController::installOn)
        lifecycle.addListener { onStateChanged(it) }
        loadNotebook()
    }

    override val preferredFocusedComponent: JComponent?
        get() = browser?.component

    private fun reloadForIdeTheme(themedUrl: String) {
        ThreadingAssertions.assertEventDispatchThread()
        navigationSnapshot = navigationSnapshot.withExpectedUrl(themedUrl)
        browser?.loadURL(themedUrl)
    }

    /**
     * Catch the case where the server started but its page fails to load (server died after
     * readiness, connection refused, render crash) so the user sees the actionable panel rather
     * than a raw Chromium error page. Only main-frame errors matter; sub-frame failures and
     * user-cancelled loads (ERR_ABORTED, e.g. a Retry that navigates away mid-load) are not editor
     * failures.
     */
    private fun installLoadErrorHandler(browser: JBCefBrowser) {
        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadError(
                    cefBrowser: org.cef.browser.CefBrowser?,
                    frame: org.cef.browser.CefFrame?,
                    errorCode: CefLoadHandler.ErrorCode?,
                    errorText: String?,
                    failedUrl: String?,
                ) {
                    if (frame?.isMain != true) return
                    if (errorCode == null || errorCode == CefLoadHandler.ErrorCode.ERR_NONE) return
                    if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
                    val navigation = navigationSnapshot
                    val generation = loadErrorGeneration(failedUrl, navigation) ?: return
                    val detail = errorText?.takeIf { it.isNotBlank() } ?: errorCode.name
                    val model =
                        ErrorModel.of(
                            Failure.EditorLoadFailed(detail),
                            MarimoPresence.Unknown,
                            uvAvailable = false,
                        )
                    onEdt(generation) {
                        if (!canRenderNotebookFor(lifecycle.state)) return@onEdt
                        showContent(ErrorPanel(model, ::onErrorAction))
                    }
                }
            },
            browser.cefBrowser,
        )
    }

    /**
     * Theme state is cleared up front so that a look-and-feel change arriving while the server is
     * starting can't reload the previous page. The navigation generation makes every continuation
     * of an older call a no-op: a Restart that begins while an old readiness future or load error
     * is still in flight must own the panel exclusively.
     */
    private fun loadNotebook() {
        ThreadingAssertions.assertEventDispatchThread()
        val navigation = NavigationSnapshot(navigationSnapshot.generation + 1, null)
        navigationSnapshot = navigation
        themeController.reset()
        showContent(JLabel("Starting marimo…", SwingConstants.CENTER))
        sessionManager.urlFor(file).whenComplete { url, err ->
            when {
                navigation.generation != navigationSnapshot.generation -> Unit
                err != null -> showServerError(navigation.generation, err)
                browser == null ->
                    onEdt(navigation.generation) {
                        showContent(
                            JLabel(
                                "The embedded browser isn't available in this IDE.",
                                SwingConstants.CENTER,
                            )
                        )
                    }
                else -> showNotebook(navigation.generation, browser, url)
            }
        }
    }

    /**
     * Ask the server which theme the notebook resolves to before rendering it, so the IDE's theme
     * is only applied when marimo leaves the choice to the host. The request blocks, and completing
     * a ready server URL can happen on the caller's thread, so keep it off the EDT.
     */
    private fun showNotebook(navigation: Long, browser: JBCefBrowser, url: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val resolvedTheme = PageConfigReader.fetchDisplayTheme(url)
            onEdt(navigation) {
                if (!canRenderNotebookFor(lifecycle.state)) return@onEdt
                val themedUrl = themeController.prepareNotebookUrl(resolvedTheme, url)
                navigationSnapshot = NavigationSnapshot(navigation, serverOrigin(url), themedUrl)
                browser.loadURL(themedUrl)
                showContent(browser.component)
                MarimoConsentPrompt.maybePrompt(project)
                val launcher = if (sessionManager.isSandbox(file)) "uv-sandbox" else "sdk"
                MarimoTelemetry.getInstance().capture(TelemetryEvent.NotebookOpened(launcher))
            }
        }
    }

    /**
     * The page can die while the tab stays mounted — marimo's shutdown exits the whole server, and
     * `window.close()` does nothing inside JCEF. Without this the tab keeps showing a notebook that
     * cannot answer, which is indistinguishable from a hang.
     */
    private fun onStateChanged(update: LifecycleStateUpdate) {
        val state = update.state
        val navigation = navigationSnapshot.generation
        when (state) {
            is MarimoNotebookState.Stopped ->
                onEdt(navigation) {
                    if (!lifecycle.isCurrent(update)) return@onEdt
                    themeController.reset()
                    val cause = state.cause
                    if (cause is StopCause.Unexpected) {
                        thisLogger()
                            .warn(
                                "marimo stopped unexpectedly (exit ${cause.exitCode}):\n${redactAccessTokens(cause.outputTail)}"
                            )
                    }
                    val model =
                        ErrorModel.of(
                            Failure.ServerStopped(state.cause),
                            MarimoPresence.Unknown,
                            uvAvailable = false,
                        )
                    showContent(ErrorPanel(model, ::onErrorAction))
                }
            else -> Unit
        }
    }

    /**
     * Probe off the EDT — detection may run a subprocess — then render the matching error panel.
     */
    private fun showServerError(navigation: Long, err: Throwable?) {
        thisLogger().warn("marimo failed to start for ${file.name}", err)
        ApplicationManager.getApplication().executeOnPooledThread {
            val model = FailureDiagnostics.diagnose(project, file, err)
            onEdt(navigation) {
                if (lifecycle.state is MarimoNotebookState.Stopped) return@onEdt
                showContent(ErrorPanel(model, ::onErrorAction))
            }
        }
    }

    private fun onErrorAction(action: ErrorAction) {
        when (action) {
            ErrorAction.RETRY -> relaunch()
            ErrorAction.INSTALL -> {
                FailureDiagnostics.installMarimo(project, file)
                relaunch()
            }
            ErrorAction.START_IN_SANDBOX -> {
                sessionManager.enableSandbox(file)
                relaunch()
            }
            ErrorAction.OPEN_AS_PYTHON ->
                FileEditorManager.getInstance(project)
                    .setSelectedEditor(file, MARIMO_SOURCE_EDITOR_TYPE)
            ErrorAction.CLOSE -> FileEditorManager.getInstance(project).closeFile(file)
        }
    }

    /**
     * Re-launch this notebook, picking up any launch-mode change (e.g. a newly requested sandbox).
     */
    override fun reload() = relaunch()

    /**
     * Connects this retained browser to the server launch already started by the session manager.
     */
    internal fun reconnectAfterSessionRestart() = onEdt { loadNotebook() }

    /**
     * The service caches the failed handle by file URL, so a retry that reused it would replay the
     * same failure; release first to force a fresh launch. The browser is kept and reused —
     * pointing it at the fresh server URL is exactly the restart the user asked for.
     */
    private fun relaunch() {
        sessionManager.release(file)
        loadNotebook()
    }

    private fun showContent(component: Component) {
        panel.removeAll()
        addToolbar()
        panel.add(component, BorderLayout.CENTER)
        panel.revalidate()
        panel.repaint()
    }

    /**
     * Every async continuation lands here. Any of them can be queued while the notebook is open and
     * run after it was closed or after a newer navigation started — both must be dropped.
     */
    private fun onEdt(navigation: Long? = null, block: () -> Unit) =
        ApplicationManager.getApplication().invokeLater {
            if (!disposed && (navigation == null || navigation == navigationSnapshot.generation)) {
                block()
            }
        }

    private fun addToolbar() {
        val row = JPanel(BorderLayout())
        pairToolbar()?.let { row.add(it, BorderLayout.WEST) }
        if (sessionManager.isSandbox(file)) row.add(sandboxIndicator(), BorderLayout.EAST)
        if (row.componentCount > 0) panel.add(row, BorderLayout.NORTH)
    }

    private fun pairToolbar(): JComponent? {
        val pairGroup = ActionManager.getInstance().getAction("Marimo.Pair") ?: return null
        val group = DefaultActionGroup(pairGroup)
        val toolbar =
            ActionManager.getInstance().createActionToolbar("MarimoEditorToolbar", group, true)
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
            toolTipText =
                "Running in marimo's isolated uv sandbox (PEP 723 dependencies), not the project interpreter."
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyRight(8)
        }

    override fun dispose() {
        disposed = true
        browser?.dispose()
    }
}

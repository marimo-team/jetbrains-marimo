/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.marimo.notebook.launch.LifecycleStateUpdate
import io.marimo.notebook.launch.MarimoEnvProbe
import io.marimo.notebook.launch.MarimoInstaller
import io.marimo.notebook.launch.MarimoNotebookState
import io.marimo.notebook.launch.MarimoPresence
import io.marimo.notebook.launch.StopCause
import io.marimo.notebook.launch.UvLauncher
import io.marimo.notebook.launch.redactAccessTokens
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.PageConfigReader
import io.marimo.notebook.telemetry.MarimoConsentPrompt
import io.marimo.notebook.telemetry.MarimoTelemetry
import io.marimo.notebook.telemetry.TelemetryEvent
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.ln
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter

/**
 * The long-lived UI and process state for a single open notebook: the JCEF browser, its content
 * panel, and the marimo server it renders. Owned by [NotebookViewRegistry] and keyed by session,
 * not by any one editor tab. Moving a notebook between splits disposes and recreates the
 * [FileEditor], but the view survives, so the same browser stays connected to the same session.
 */
class MarimoNotebookView(private val project: Project, private val file: VirtualFile) : Disposable {

    val panel: JPanel =
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

    /** Theme state for the loaded page; read and written on the EDT only. */
    private var loadedUrl: String? = null
    private var appliedTheme: String? = null
    private var followsIdeTheme = false

    /** Disposal can be triggered from any thread, and is checked from the EDT. */
    @Volatile private var disposed = false

    /** Identity of the navigation the browser currently renders, or begins rendering. */
    @Volatile private var navigationSnapshot = NavigationSnapshot(0, null)

    init {
        browser?.let(::installLoadErrorHandler)
        browser?.let(::installPopupHandler)
        browser?.let(::installEditorFontZoom)
        browser?.let(::installIdeThemeSync)
        lifecycle.addListener { onStateChanged(it) }
        loadNotebook()
    }

    val preferredFocusedComponent: JComponent?
        get() = browser?.component

    /**
     * Keep the embedded notebook's zoom in step with the IDE's editor font size, so enlarging the
     * font across editors enlarges the notebook too. CEF resets zoom on navigation, so reapply on
     * every main-frame load as well as whenever the global scheme changes.
     */
    private fun installEditorFontZoom(browser: JBCefBrowser) {
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(
                EditorColorsManager.TOPIC,
                EditorColorsListener { onEdt { applyEditorFontZoom(browser) } },
            )
        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    httpStatusCode: Int,
                ) {
                    if (frame?.isMain == true) onEdt { applyEditorFontZoom(browser) }
                }
            },
            browser.cefBrowser,
        )
    }

    /**
     * Map the editor font size onto a CEF zoom level, where the scale factor is `1.2^level`. The
     * platform default font size renders the notebook at its native 100%; larger fonts scale it up
     * proportionally.
     */
    private fun applyEditorFontZoom(browser: JBCefBrowser) {
        val fontSize = EditorColorsManager.getInstance().globalScheme.editorFontSize
        browser.cefBrowser.zoomLevel =
            ln(fontSize.toDouble() / FontPreferences.DEFAULT_FONT_SIZE) / ln(1.2)
    }

    /**
     * Follow the IDE's light/dark theme while it changes. The theme reaches marimo as a query
     * parameter read when the page loads, so applying a new one means reloading the page — done
     * only for a notebook that left the choice to the host, and only when the light/dark answer
     * actually changed (the look and feel also fires for font and colour-scheme edits).
     */
    private fun installIdeThemeSync(browser: JBCefBrowser) {
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(
                LafManagerListener.TOPIC,
                LafManagerListener { onEdt { syncIdeTheme(browser) } },
            )
    }

    private fun syncIdeTheme(browser: JBCefBrowser) {
        val url = loadedUrl ?: return
        if (!followsIdeTheme) return
        val theme = MarimoThemedUrl.ideTheme()
        if (theme == appliedTheme) return
        appliedTheme = theme
        val themedUrl = MarimoThemedUrl.withTheme(url, theme)
        navigationSnapshot = navigationSnapshot.withExpectedUrl(themedUrl)
        browser.loadURL(themedUrl)
    }

    /**
     * marimo opens a duplicated (or otherwise linked) notebook with `window.open("?file=…",
     * "_blank")`. Left to JCEF's default, that popup becomes a detached OS window that can't be
     * docked as a tab. Catch it here: open notebook deep links as IDE editor tabs and send genuine
     * external links to the system browser, so no stray Chromium window ever appears.
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
     * Resolve a just-created notebook path to a [VirtualFile] and open it as an editor tab. The VFS
     * refresh is synchronous and must run off the EDT; the freshly copied file is picked up as a
     * marimo notebook and rendered in this same editor kind.
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
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
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
                        MarimoErrorModel.of(
                            MarimoFailure.EditorLoadFailed(detail),
                            MarimoPresence.Unknown,
                            uvAvailable = false,
                        )
                    onEdt(generation) {
                        if (!canRenderNotebookFor(lifecycle.state)) return@onEdt
                        showContent(MarimoErrorPanel(model, ::onErrorAction))
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
        loadedUrl = null
        appliedTheme = null
        followsIdeTheme = false
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
                followsIdeTheme = MarimoThemedUrl.followsIdeTheme(resolvedTheme)
                appliedTheme = MarimoThemedUrl.ideTheme()
                loadedUrl = url
                val themedUrl = MarimoThemedUrl.of(url, resolvedTheme, appliedTheme!!)
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
            is MarimoNotebookState.Stopping ->
                onEdt(navigation) {
                    if (!lifecycle.isCurrent(update)) return@onEdt
                    showContent(JLabel("Shutting down marimo…", SwingConstants.CENTER))
                }
            is MarimoNotebookState.Stopped ->
                onEdt(navigation) {
                    if (!lifecycle.isCurrent(update)) return@onEdt
                    loadedUrl = null
                    appliedTheme = null
                    followsIdeTheme = false
                    val cause = state.cause
                    if (cause is StopCause.Unexpected) {
                        thisLogger()
                            .warn(
                                "marimo stopped unexpectedly (exit ${cause.exitCode}):\n${redactAccessTokens(cause.outputTail)}"
                            )
                    }
                    val model =
                        MarimoErrorModel.of(
                            MarimoFailure.ServerStopped(state.cause),
                            MarimoPresence.Unknown,
                            uvAvailable = false,
                        )
                    showContent(MarimoErrorPanel(model, ::onErrorAction))
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
            val probe = project.service<MarimoEnvProbe>()
            probe.invalidate()
            val presence = probe.probe(file)
            val uvAvailable = UvLauncher.findUv() != null
            val reason =
                when {
                    presence is MarimoPresence.Unknown -> "no_interpreter"
                    presence is MarimoPresence.Missing -> "marimo_missing"
                    !uvAvailable -> "uv_missing"
                    else -> "other"
                }
            MarimoTelemetry.getInstance().capture(TelemetryEvent.NotebookLaunchFailed(reason))
            MarimoTelemetry.getInstance()
                .captureException(err ?: RuntimeException("marimo failed to start"))
            val model =
                MarimoErrorModel.of(
                    MarimoFailure.ServerNotStarted(err),
                    presence,
                    uvAvailable = uvAvailable,
                )
            onEdt(navigation) {
                if (lifecycle.state is MarimoNotebookState.Stopped) return@onEdt
                showContent(MarimoErrorPanel(model, ::onErrorAction))
            }
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
                sessionManager.enableSandbox(file)
                relaunch()
            }
            MarimoErrorAction.OPEN_AS_PYTHON ->
                FileEditorManager.getInstance(project)
                    .setSelectedEditor(file, MARIMO_SOURCE_EDITOR_TYPE)
            MarimoErrorAction.CLOSE -> FileEditorManager.getInstance(project).closeFile(file)
        }
    }

    /**
     * Re-launch this notebook, picking up any launch-mode change (e.g. a newly requested sandbox).
     */
    fun reload() = relaunch()

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

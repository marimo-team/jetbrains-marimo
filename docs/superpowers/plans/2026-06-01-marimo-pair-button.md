# marimo Pair Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-click "marimo pair" control that opens a PyCharm terminal and automatically starts a chosen AI harness (Claude, Codex, or `pi`) loaded with the marimo-pair prompt for the open notebook.

**Architecture:** A pure `MarimoHarness` enum builds the terminal command by wrapping `marimo pair prompt`. `MarimoServerService` exposes the running URL and the marimo CLI prefix (reusing the launcher that started the server). `MarimoPairLauncher` resolves both, then opens a terminal tab and runs the command. One `MarimoPairAction` drives two UI surfaces: an in-editor toolbar strip (A) and the editor-tab/Tools menu (C).

**Tech Stack:** Kotlin, IntelliJ Platform SDK (PyCharm 2026.1), bundled Terminal plugin, JUnit 4.

---

## File Structure

- `src/main/kotlin/.../pair/MarimoHarness.kt` — enum: id, label, agent flag, command builder (pure).
- `src/main/kotlin/.../pair/MarimoPairLauncher.kt` — resolve URL + CLI prefix, open terminal, run command.
- `src/main/kotlin/.../pair/MarimoPairAction.kt` — `AnAction`: enablement + harness popup.
- `src/main/kotlin/.../launch/MarimoLauncher.kt` — add `marimoCliPrefix` to the interface.
- `src/main/kotlin/.../launch/UvLauncher.kt`, `SdkLauncher.kt` — implement `marimoCliPrefix`.
- `src/main/kotlin/.../server/MarimoServerService.kt` — add `marimoCliPrefixFor(file)`.
- `src/main/kotlin/.../editor/MarimoNotebookEditor.kt` — add toolbar strip (A) + data provider.
- `src/main/resources/META-INF/plugin.xml` — register action (C), depend on terminal plugin.
- `build.gradle.kts` — add `bundledPlugin("org.jetbrains.plugins.terminal")`.
- Tests: `src/test/kotlin/.../pair/MarimoHarnessTest.kt`.

Package root: `com.github.kirangadhave.marimopycharm`.

---

## Task 1: Harness command builder (pure, TDD)

**Files:**
- Create: `src/main/kotlin/com/github/kirangadhave/marimopycharm/pair/MarimoHarness.kt`
- Test: `src/test/kotlin/com/github/kirangadhave/marimopycharm/pair/MarimoHarnessTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.kirangadhave.marimopycharm.pair

import org.junit.Assert.assertEquals
import org.junit.Test

class MarimoHarnessTest {
    private val prefix = listOf("/usr/bin/uv", "run", "--with", "marimo", "marimo")
    private val url = "http://127.0.0.1:2718"

    @Test fun claudeWrapsPairPromptWithClaudeFlag() {
        assertEquals(
            "claude \"\$(/usr/bin/uv run --with marimo marimo pair prompt --url 'http://127.0.0.1:2718' --claude)\"",
            MarimoHarness.CLAUDE.terminalCommand(prefix, url),
        )
    }

    @Test fun codexWrapsPairPromptWithCodexFlag() {
        assertEquals(
            "codex \"\$(/usr/bin/uv run --with marimo marimo pair prompt --url 'http://127.0.0.1:2718' --codex)\"",
            MarimoHarness.CODEX.terminalCommand(prefix, url),
        )
    }

    @Test fun piWrapsPairPromptWithNoAgentFlag() {
        assertEquals(
            "pi \"\$(/usr/bin/uv run --with marimo marimo pair prompt --url 'http://127.0.0.1:2718')\"",
            MarimoHarness.PI.terminalCommand(prefix, url),
        )
    }

    @Test fun sdkPrefixIsHonored() {
        assertEquals(
            "pi \"\$(/opt/py -m marimo pair prompt --url 'http://127.0.0.1:2718')\"",
            MarimoHarness.PI.terminalCommand(listOf("/opt/py", "-m", "marimo"), url),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.github.kirangadhave.marimopycharm.pair.MarimoHarnessTest"`
Expected: FAIL — `MarimoHarness` is unresolved / does not compile.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.github.kirangadhave.marimopycharm.pair

/** An AI coding harness the user can pair with. Mirrors `marimo pair prompt` wrapping. */
enum class MarimoHarness(val id: String, val label: String, private val agentFlag: String?) {
    CLAUDE("claude", "Claude", "--claude"),
    CODEX("codex", "Codex", "--codex"),
    PI("pi", "pi", null);

    /**
     * Terminal command that starts this harness with the marimo-pair prompt.
     * [cliPrefix] are the tokens that invoke the marimo CLI (e.g. uv run ... marimo).
     */
    fun terminalCommand(cliPrefix: List<String>, url: String): String {
        val promptTokens = cliPrefix +
            listOf("pair", "prompt", "--url", "'$url'") +
            (agentFlag?.let { listOf(it) } ?: emptyList())
        return "$id \"\$(${promptTokens.joinToString(" ")})\""
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.github.kirangadhave.marimopycharm.pair.MarimoHarnessTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/kirangadhave/marimopycharm/pair/MarimoHarness.kt \
        src/test/kotlin/com/github/kirangadhave/marimopycharm/pair/MarimoHarnessTest.kt
git commit -m "feat: add marimo pair harness command builder"
```

---

## Task 2: marimo CLI prefix on the launcher abstraction

**Files:**
- Modify: `src/main/kotlin/com/github/kirangadhave/marimopycharm/launch/MarimoLauncher.kt`
- Modify: `src/main/kotlin/com/github/kirangadhave/marimopycharm/launch/UvLauncher.kt`
- Modify: `src/main/kotlin/com/github/kirangadhave/marimopycharm/launch/SdkLauncher.kt`

- [ ] **Step 1: Add `marimoCliPrefix` to the `MarimoLauncher` interface**

In `MarimoLauncher.kt`, add to the `interface MarimoLauncher` block (after `launch`):

```kotlin
    /**
     * Tokens that invoke the marimo CLI for this launcher (e.g.
     * ["uv","run","--with","marimo","marimo"] or ["/path/python","-m","marimo"]).
     * Null if the CLI cannot be resolved on this machine.
     */
    fun marimoCliPrefix(request: LaunchRequest): List<String>?
```

- [ ] **Step 2: Implement it in `UvLauncher`**

In `UvLauncher.kt`, change `findUv()` from `private` to `internal` and add this override (after `launch`):

```kotlin
    override fun marimoCliPrefix(request: LaunchRequest): List<String>? =
        findUv()?.let { listOf(it, "run", "--with", "marimo", "marimo") }
```

- [ ] **Step 3: Implement it in `SdkLauncher`**

In `SdkLauncher.kt`, add this override (after `launch`):

```kotlin
    override fun marimoCliPrefix(request: LaunchRequest): List<String>? =
        SdkPythonResolver.resolvePythonPath(request.project, request.notebook)
            ?.let { listOf(it, "-m", "marimo") }
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/kirangadhave/marimopycharm/launch/MarimoLauncher.kt \
        src/main/kotlin/com/github/kirangadhave/marimopycharm/launch/UvLauncher.kt \
        src/main/kotlin/com/github/kirangadhave/marimopycharm/launch/SdkLauncher.kt
git commit -m "feat: expose marimo CLI prefix from launchers"
```

---

## Task 3: Expose CLI prefix from the server service

**Files:**
- Modify: `src/main/kotlin/com/github/kirangadhave/marimopycharm/server/MarimoServerService.kt`

- [ ] **Step 1: Add `marimoCliPrefixFor`**

In `MarimoServerService.kt`, add after `baseUrlFor`:

```kotlin
    /** marimo CLI prefix for [file], re-resolving the applicable launcher. Null if none applies. */
    fun marimoCliPrefixFor(file: VirtualFile): List<String>? {
        val request = LaunchRequest(project = project, notebook = file, port = 0)
        val launcher = runCatching { registry.resolve(request) }.getOrNull() ?: return null
        return launcher.marimoCliPrefix(request)
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/github/kirangadhave/marimopycharm/server/MarimoServerService.kt
git commit -m "feat: expose marimo CLI prefix from server service"
```

---

## Task 4: Add the bundled Terminal plugin dependency

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Add the bundled plugin in Gradle**

In `build.gradle.kts`, inside the `intellijPlatform { ... }` block, add after `bundledPlugin("PythonCore")`:

```kotlin
        bundledPlugin("org.jetbrains.plugins.terminal")
```

- [ ] **Step 2: Declare the dependency in plugin.xml**

In `plugin.xml`, after `<depends>com.intellij.modules.python</depends>`, add:

```xml
    <depends>org.jetbrains.plugins.terminal</depends>
```

- [ ] **Step 3: Verify it resolves**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (dependency resolves).

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts src/main/resources/META-INF/plugin.xml
git commit -m "chore: depend on bundled terminal plugin"
```

---

## Task 5: Pair launcher — open terminal and run the harness

**Files:**
- Create: `src/main/kotlin/com/github/kirangadhave/marimopycharm/pair/MarimoPairLauncher.kt`

- [ ] **Step 1: Write the launcher**

```kotlin
package com.github.kirangadhave.marimopycharm.pair

import com.github.kirangadhave.marimopycharm.server.MarimoServerService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

object MarimoPairLauncher {

    /** Ensure the server is up, then open a terminal running [harness] with the pair prompt. */
    fun launch(project: Project, file: VirtualFile, harness: MarimoHarness) {
        val server = project.service<MarimoServerService>()
        server.urlFor(file).whenComplete { url, err ->
            ApplicationManager.getApplication().invokeLater {
                if (err != null || url == null) {
                    notify(project, "Could not start marimo: ${err?.message ?: "unknown error"}")
                    return@invokeLater
                }
                val prefix = server.marimoCliPrefixFor(file)
                if (prefix == null) {
                    notify(project, "Could not resolve the marimo CLI (need uv on PATH or marimo in the interpreter).")
                    return@invokeLater
                }
                runInTerminal(project, file, harness.terminalCommand(prefix, url))
            }
        }
    }

    private fun runInTerminal(project: Project, file: VirtualFile, command: String) {
        val workDir = file.parent?.path ?: project.basePath
        try {
            val widget = TerminalToolWindowManager.getInstance(project)
                .createLocalShellWidget(workDir, "marimo pair")
            widget.executeCommand(command)
        } catch (e: Throwable) {
            notify(project, "Could not open a terminal. Run this manually:\n$command")
        }
    }

    private fun notify(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Marimo")
            .createNotification(message, NotificationType.WARNING)
            .notify(project)
    }
}
```

- [ ] **Step 2: Register the notification group in plugin.xml**

In `plugin.xml`, inside `<extensions defaultExtensionNs="com.intellij">`, add:

```xml
        <notificationGroup id="Marimo" displayType="BALLOON"/>
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

If `createLocalShellWidget` / `executeCommand` is reported as deprecated, keep it — it is still functional on PyCharm 2026.1 and is the simplest stable API. Do not switch APIs without a compile error.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/github/kirangadhave/marimopycharm/pair/MarimoPairLauncher.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: run chosen harness in a pycharm terminal"
```

---

## Task 6: Pair action — enablement + harness popup (surface C)

**Files:**
- Create: `src/main/kotlin/com/github/kirangadhave/marimopycharm/pair/MarimoPairAction.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Modify: `src/main/resources/messages/MarimoBundle.properties` (if present; see Step 3)

- [ ] **Step 1: Write the action**

```kotlin
package com.github.kirangadhave.marimopycharm.pair

import com.github.kirangadhave.marimopycharm.detect.MarimoDetector
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile

class MarimoPairAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && MarimoDetector.looksLikeMarimo(file)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        choose(file, project)
    }

    private fun choose(file: VirtualFile, project: com.intellij.openapi.project.Project) {
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(MarimoHarness.entries.toList())
            .setTitle("Pair with…")
            .setItemChosenCallback { harness -> MarimoPairLauncher.launch(project, file, harness) }
            .setRenderer(com.intellij.ui.SimpleListCellRenderer.create("") { it.label })
            .createPopup()
            .showInFocusCenter()
    }
}
```

- [ ] **Step 2: Register the action in plugin.xml (surface C)**

In `plugin.xml`, add a new top-level `<actions>` block (sibling of `<extensions>`):

```xml
    <actions>
        <action id="Marimo.Pair"
                class="com.github.kirangadhave.marimopycharm.pair.MarimoPairAction"
                text="Pair with marimo"
                description="Open a terminal and start an AI harness paired on this marimo notebook"
                icon="AllIcons.Actions.Lightning">
            <add-to-group group-id="EditorTabPopupMenu" anchor="last"/>
            <add-to-group group-id="ToolsMenu" anchor="last"/>
        </action>
    </actions>
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/github/kirangadhave/marimopycharm/pair/MarimoPairAction.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add marimo pair action to tab and tools menus"
```

---

## Task 7: In-editor toolbar strip (surface A)

**Files:**
- Modify: `src/main/kotlin/com/github/kirangadhave/marimopycharm/editor/MarimoNotebookEditor.kt`

- [ ] **Step 1: Make the root panel provide the notebook file as data**

In `MarimoNotebookEditor.kt`, replace the `panel` declaration:

```kotlin
    private val panel = JPanel(BorderLayout())
```

with a panel that supplies `CommonDataKeys.VIRTUAL_FILE` so the shared action's
`update`/`actionPerformed` see this notebook:

```kotlin
    private val panel = object : JPanel(BorderLayout()), DataProvider {
        override fun getData(dataId: String): Any? =
            if (CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) file else null
    }
```

Add imports:

```kotlin
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
```

- [ ] **Step 2: Add the toolbar strip in `init`**

At the very start of the `init { ... }` block (before the existing
`panel.add(JLabel(...))` line), add:

```kotlin
        addPairToolbar()
```

Then add this private method to the class:

```kotlin
    private fun addPairToolbar() {
        val pairAction = ActionManager.getInstance().getAction("Marimo.Pair") ?: return
        val group = DefaultActionGroup(pairAction)
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MarimoEditorToolbar", group, true)
        toolbar.targetComponent = panel
        panel.add(toolbar.component, BorderLayout.NORTH)
    }
```

The existing `init` code adds the loading label / browser to `BorderLayout.CENTER`,
so the toolbar (NORTH) coexists with it. Note the `panel.removeAll()` in the
server callback removes the toolbar too — re-add it after `removeAll()`:

In the `whenComplete` callback, immediately after `panel.removeAll()`, add:

```kotlin
                addPairToolbar()
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/github/kirangadhave/marimopycharm/editor/MarimoNotebookEditor.kt
git commit -m "feat: add pair toolbar strip to the marimo editor"
```

---

## Task 8: Full build + test gate

- [ ] **Step 1: Run the whole test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass (including `MarimoHarnessTest`).

- [ ] **Step 2: Build the plugin**

Run: `./gradlew buildPlugin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit any incidental fixes**

```bash
git add -A
git commit -m "test: green build for marimo pair button"
```

(Skip if nothing changed.)

---

## Manual Verification (runIde)

Run: `./gradlew runIde` (only when the user asks — see project memory: no auto-build/runIde without request).

1. Open the bundled `examples/demo.py` marimo notebook.
2. **Surface A:** confirm a toolbar strip with a "Pair with marimo" (lightning) button sits above the embedded notebook.
3. **Surface C:** right-click the editor tab → confirm "Pair with marimo" appears; also check Tools menu. Both are disabled for non-marimo `.py` files.
4. Click either → a popup lists **Claude / Codex / pi**.
5. Pick **pi** (installed locally) → a terminal tab named "marimo pair" opens and runs:
   `pi "$(<prefix> pair prompt --url 'http://127.0.0.1:<port>')"` — `pi` starts with the prompt as its first message.
6. Repeat for Claude and Codex; confirm the `--claude` / `--codex` flags appear in the executed command.
7. Confirm the agent can reach the notebook (the prompt's URL matches the running embedded server).

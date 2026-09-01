/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.marimo.notebook.MarimoLocalhost
import java.util.concurrent.CompletableFuture

data class LaunchRequest(
    val project: Project,
    val notebook: VirtualFile,
    val port: Int,
    val host: String = MarimoLocalhost.HOST,
    /** Run the notebook in marimo's isolated uv environment (PEP 723 deps); requires uv. */
    val sandbox: Boolean = false,
    /** Absolute path passed to `--token-password-file`, or null for `--no-token`. */
    val tokenPasswordFile: String? = null,
    /** Plugin-built URL with access token when token auth is on; null for plain readiness URL. */
    val authenticatedUrl: String? = null,
    /** Working directory for the server process; the service resolves and records it per launch. */
    val workDir: String? = null,
    /** Extra server environment entries. Values can hold credentials, so never log this map. */
    val extraEnv: Map<String, String> = emptyMap(),
)

/** Owns a spawned marimo process; the lifecycle service drives readiness and disposal. */
interface MarimoServerHandle : Disposable {
    val isAlive: Boolean
    val processHandle: ProcessHandler

    /** Completes with the server URL once it accepts connections. */
    fun awaitReady(): CompletableFuture<String>

    fun onTerminated(listener: (exitCode: Int, outputTail: String) -> Unit)
}

/** Strategy for turning a marimo notebook into a running marimo server. */
interface MarimoLauncher {
    /** Stable id for settings / logging, e.g. "uv". */
    val id: String

    /** Cheap, side-effect-free: can this launcher serve this request on this machine? */
    fun canLaunch(request: LaunchRequest): Boolean

    /** Spawn the server. */
    fun launch(request: LaunchRequest): MarimoServerHandle

    /**
     * Tokens that invoke the marimo CLI for this launcher (e.g.
     * ["uv","run","--with","marimo","marimo"] or ["/path/python","-m","marimo"]). Null if the CLI
     * cannot be resolved on this machine.
     */
    fun marimoCliPrefix(request: LaunchRequest): List<String>?
}

class NoApplicableLauncherException(request: LaunchRequest) :
    RuntimeException("No marimo launcher can handle ${request.notebook.name}")

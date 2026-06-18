/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CompletableFuture

enum class MarimoMode { EDIT, RUN }

data class LaunchRequest(
    val project: Project,
    val notebook: VirtualFile,
    val port: Int,
    val host: String = "127.0.0.1",
    val mode: MarimoMode = MarimoMode.EDIT,
    /** Run the notebook in marimo's isolated uv environment (PEP 723 deps); requires uv. */
    val sandbox: Boolean = false,
)

/** Owns a spawned marimo process; the lifecycle service drives readiness and disposal. */
interface MarimoServerHandle : Disposable {
    val processHandle: ProcessHandler
    /** Completes with the server URL once it accepts connections. */
    fun awaitReady(): CompletableFuture<String>
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
     * ["uv","run","--with","marimo","marimo"] or ["/path/python","-m","marimo"]).
     * Null if the CLI cannot be resolved on this machine.
     */
    fun marimoCliPrefix(request: LaunchRequest): List<String>?
}

class NoApplicableLauncherException(request: LaunchRequest) :
    RuntimeException("No marimo launcher can handle ${request.notebook.name}")

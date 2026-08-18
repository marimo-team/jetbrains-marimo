/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** User-facing session settings, shown on the marimo settings page (Settings → Tools → marimo). */
@Service(Service.Level.APP)
@State(name = "MarimoSessions", storages = [Storage("marimo.xml")])
class MarimoSessionSettings : PersistentStateComponent<MarimoSessionSettings.State> {

    class State {
        /**
         * When true (the default), the plugin generates a per-launch token, passes it to marimo
         * via `--token-password-file`, and builds the authenticated URL itself. Off restores
         * `--no-token` as an escape hatch.
         */
        var tokenAuthEnabled: Boolean = true
    }

    private var current = State()

    override fun getState(): State = current

    override fun loadState(state: State) {
        current = state
    }

    companion object {
        fun getInstance(): MarimoSessionSettings =
            ApplicationManager.getApplication().getService(MarimoSessionSettings::class.java)
    }
}

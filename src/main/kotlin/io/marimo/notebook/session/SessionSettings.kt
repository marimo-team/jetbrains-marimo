/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** User-facing session settings, shown on the marimo settings page (Settings → Tools → marimo). */
@Service(Service.Level.APP)
@State(name = "MarimoSessions", storages = [Storage("marimo.xml")])
class SessionSettings : PersistentStateComponent<SessionSettings.State> {

    class State {
        /**
         * When true (the default), the plugin generates a per-launch token, passes it to marimo via
         * `--token-password-file`, and builds the authenticated URL itself. Off restores
         * `--no-token` as an escape hatch.
         */
        var tokenAuthEnabled: Boolean = true

        /** Minutes a notebook's server keeps running after its last editor tab closes. */
        var backgroundTtlMinutes: Int = DEFAULT_BACKGROUND_TTL_MINUTES
    }

    private var current = State()

    override fun getState(): State = current

    override fun loadState(state: State) {
        current = state
    }

    /** The TTL in milliseconds, clamped so a hand-edited config file cannot disable the reaper. */
    fun backgroundTtlMillis(): Long =
        current.backgroundTtlMinutes.coerceIn(MIN_BACKGROUND_TTL_MINUTES, 720) * 60_000L

    companion object {
        const val DEFAULT_BACKGROUND_TTL_MINUTES: Int = 30
        const val MIN_BACKGROUND_TTL_MINUTES: Int = 1

        fun getInstance(): SessionSettings =
            ApplicationManager.getApplication().getService(SessionSettings::class.java)
    }
}

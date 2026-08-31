/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.marimo.notebook.MarimoBundle
import io.marimo.notebook.session.SessionSettings
import io.marimo.notebook.session.SessionSettings.Companion.MIN_BACKGROUND_TTL_MINUTES
import io.marimo.notebook.telemetry.Consent
import io.marimo.notebook.telemetry.MarimoTelemetry

/** Consent, token auth, and background TTL must be editable without changing plugin code. */
class MarimoSettingsConfigurable :
    BoundConfigurable(MarimoBundle.message("telemetry.settings.title")) {
    private val telemetry = MarimoTelemetry.getInstance()
    private val sessions = SessionSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox(MarimoBundle.message("telemetry.settings.checkbox"))
                .bindSelected(
                    { telemetry.consent == Consent.ALLOWED },
                    { enabled -> if (enabled) telemetry.allow() else telemetry.revoke() },
                )
        }
        row { comment(MarimoBundle.message("telemetry.settings.note")) }
        row {
            checkBox(MarimoBundle.message("sessions.settings.token.checkbox"))
                .bindSelected(sessions.state::tokenAuthEnabled)
        }
        row { comment(MarimoBundle.message("sessions.settings.token.note")) }
        row(MarimoBundle.message("sessions.settings.ttl.label")) {
            spinner(MIN_BACKGROUND_TTL_MINUTES..720)
                .bindIntValue(sessions.state::backgroundTtlMinutes)
                .validationOnInput { spinner ->
                    if (ttlMinutesValid(spinner)) {
                        null
                    } else {
                        error(MarimoBundle.message("sessions.settings.ttl.validation"))
                    }
                }
        }
        row { comment(MarimoBundle.message("sessions.settings.ttl.note")) }
    }

    private fun ttlMinutesValid(spinner: JBIntSpinner): Boolean {
        val text = (spinner.editor as? javax.swing.JSpinner.NumberEditor)?.textField?.text.orEmpty()
        if (text.isEmpty()) return false
        val minutes = text.toIntOrNull() ?: return false
        return minutes in MIN_BACKGROUND_TTL_MINUTES..720
    }
}

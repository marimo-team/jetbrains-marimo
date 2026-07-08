/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.marimo.notebook.MarimoBundle

class MarimoTelemetryConfigurable : BoundConfigurable(MarimoBundle.message("telemetry.settings.title")) {
    private val telemetry = MarimoTelemetry.getInstance()

    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox(MarimoBundle.message("telemetry.settings.checkbox"))
                .bindSelected(
                    { telemetry.consent == Consent.ALLOWED },
                    { enabled -> if (enabled) telemetry.allow() else telemetry.revoke() },
                )
        }
        row { comment(MarimoBundle.message("telemetry.settings.note")) }
    }
}

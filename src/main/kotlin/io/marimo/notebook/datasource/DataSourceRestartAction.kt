/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.ui.components.ActionLink
import io.marimo.notebook.MarimoBundle
import io.marimo.notebook.session.MarimoSessionState

/** Offers an in-place restart after sharing changes affect a live notebook session. */
internal class DataSourceRestartAction(onRestart: () -> Unit) :
    ActionLink(MarimoBundle.message("datasource.panel.restart")) {
    init {
        isVisible = false
        addActionListener {
            onRestart()
            isVisible = false
        }
    }
}

internal fun shouldOfferDataSourceRestart(state: MarimoSessionState?): Boolean =
    state?.isLive == true

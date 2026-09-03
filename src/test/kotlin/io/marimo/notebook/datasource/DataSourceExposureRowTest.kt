/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import java.awt.Container
import java.awt.Dimension
import javax.swing.JButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourceExposureRowTest {
    @Test
    fun rowKeepsItsContentAtTheTopWhenGivenExtraHeight() {
        val row =
            DataSourceExposureRow(
                name = "Orders",
                detail = "Not shared with marimo.",
                exposed = false,
                supported = true,
                onExposureChanged = {},
                onMakeDefault = {},
            )

        row.size = Dimension(400, 600)
        row.doLayout()

        assertEquals(row.preferredSize.height, row.maximumSize.height)
        assertTrue(row.components[1].y < 100)
    }

    @Test
    fun shareAndUnshareButtonsReportTheRequestedState() {
        val requested = mutableListOf<Boolean>()
        val shared =
            DataSourceExposureRow(
                name = "Orders",
                detail = "Shared with notebooks/orders.py",
                exposed = true,
                supported = true,
                onExposureChanged = requested::add,
                onMakeDefault = {},
            )
        val unshared =
            DataSourceExposureRow(
                name = "Orders",
                detail = "Not shared with notebooks/orders.py",
                exposed = false,
                supported = true,
                onExposureChanged = requested::add,
                onMakeDefault = {},
            )

        button(shared, "Unshare").doClick()
        button(unshared, "Share").doClick()

        assertEquals(listOf(false, true), requested)
    }

    private fun button(container: Container, text: String): JButton {
        container.components.forEach { component ->
            if (component is JButton && component.text == text) return component
            if (component is Container) {
                runCatching {
                    return button(component, text)
                }
            }
        }
        error("Button not found: $text")
    }
}

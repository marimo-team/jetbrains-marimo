/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DataSourceToolWindowTabProviderTest : BasePlatformTestCase() {
    fun testProviderCreatesTheDataSourcesTab() {
        val provider = DataSourceToolWindowTabProvider()

        assertEquals("data-sources", provider.id)
        assertEquals("Data Sources", provider.title)
        val component = provider.createComponent(project) { null }
        assertTrue(component is DataSourceExposurePanel)
        Disposer.dispose(component as DataSourceExposurePanel)
    }
}

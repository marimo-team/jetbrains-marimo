/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StalenessPolicyTest {
    @Test
    fun addedSourcesDoNotStaleRunningServers() {
        assertFalse(StalenessPolicy.marksStale(DataSourceEvent.Added("new"), setOf("pg-1")))
    }

    @Test
    fun removingASourceStalesOnlyNotebooksThatUseItAsTheFamilyDefault() {
        assertTrue(StalenessPolicy.marksStale(DataSourceEvent.Removed("pg-1"), setOf("pg-1")))
        assertFalse(StalenessPolicy.marksStale(DataSourceEvent.Removed("pg-1"), setOf("mysql-1")))
    }

    @Test
    fun editingASourceStalesOnlyNotebooksThatUseItAsTheFamilyDefault() {
        assertTrue(StalenessPolicy.marksStale(DataSourceEvent.Changed("pg-1"), setOf("pg-1")))
        assertFalse(StalenessPolicy.marksStale(DataSourceEvent.Changed("pg-1"), setOf("mysql-1")))
    }

    @Test
    fun anonymousChangesStaleOnlyNotebooksWithSharedDefaults() {
        assertTrue(StalenessPolicy.marksStale(DataSourceEvent.Changed(null), setOf("pg-1")))
        assertFalse(StalenessPolicy.marksStale(DataSourceEvent.Changed(null), emptySet()))
    }

    @Test
    fun exposureEditsAlwaysStaleTheTargetNotebook() {
        assertTrue(StalenessPolicy.marksStale(DataSourceEvent.ExposureEdited, emptySet()))
    }
}

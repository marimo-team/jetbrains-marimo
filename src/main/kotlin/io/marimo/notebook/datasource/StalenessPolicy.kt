/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

sealed interface DataSourceEvent {
    data class Added(val id: String) : DataSourceEvent

    data class Removed(val id: String) : DataSourceEvent

    /** The id is null when Database Tools reports no concrete source. */
    data class Changed(val id: String?) : DataSourceEvent

    data object ExposureEdited : DataSourceEvent
}

/** Decides whether a data-source event invalidates a notebook's launch environment. */
object StalenessPolicy {
    fun marksStale(event: DataSourceEvent, defaultIds: Set<String>): Boolean =
        when (event) {
            is DataSourceEvent.Added -> false
            is DataSourceEvent.Removed -> event.id in defaultIds
            is DataSourceEvent.Changed ->
                if (event.id == null) defaultIds.isNotEmpty() else event.id in defaultIds
            DataSourceEvent.ExposureEdited -> true
        }
}

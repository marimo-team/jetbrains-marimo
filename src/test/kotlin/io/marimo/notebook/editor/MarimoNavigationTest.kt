/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import io.marimo.notebook.launch.MarimoNotebookState
import io.marimo.notebook.launch.StopCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarimoNavigationTest {

    @Test fun originDropsPathQueryAndToken() {
        assertEquals("http://127.0.0.1:2718", serverOrigin("http://127.0.0.1:2718?access_token=abc"))
        assertEquals("http://127.0.0.1:2718", serverOrigin("http://127.0.0.1:2718/some/path?x=1#frag"))
    }

    @Test fun originOfPortlessUrlKeepsHostOnly() {
        assertEquals("http://localhost", serverOrigin("http://localhost/index.html"))
    }

    @Test fun originOfGarbageIsNull() {
        assertEquals(null, serverOrigin(null))
        assertEquals(null, serverOrigin(""))
        assertEquals(null, serverOrigin("not a url"))
        assertEquals(null, serverOrigin("chrome-error://chromewebdata/"))
    }

    /** The retry bug: an error from the old server's port must not repaint the new navigation. */
    @Test fun loadErrorFromAnotherPortIsStale() {
        assertFalse(loadErrorIsCurrent("http://127.0.0.1:1111/", "http://127.0.0.1:2222"))
    }

    @Test fun loadErrorFromTheCurrentServerIsRendered() {
        assertTrue(loadErrorIsCurrent("http://127.0.0.1:2222/?file=x", "http://127.0.0.1:2222"))
    }

    @Test fun loadErrorBeforeAnyNavigationIsStale() {
        assertFalse(loadErrorIsCurrent("http://127.0.0.1:2222/", null))
    }

    @Test fun loadErrorKeepsTheSnapshotGenerationItWasCapturedWith() {
        val first = NavigationSnapshot(
            generation = 1,
            expectedOrigin = "http://127.0.0.1:1111",
            expectedUrl = "http://127.0.0.1:1111/?file=a",
        )
        assertEquals(1L, loadErrorGeneration("http://127.0.0.1:1111/?file=a", first))

        val retry = NavigationSnapshot(2, null)
        assertEquals(null, loadErrorGeneration("http://127.0.0.1:1111/", retry))
    }

    @Test fun loadErrorFromTheCurrentSnapshotUsesItsGeneration() {
        val current = NavigationSnapshot(2, "http://127.0.0.1:2222")
        assertEquals(2L, loadErrorGeneration("http://127.0.0.1:2222/?file=x", current))
    }

    @Test fun loadErrorFromPriorNavigationOnSameOriginIsStaleWhenUrlDiffers() {
        val current = NavigationSnapshot(
            generation = 7,
            expectedOrigin = "http://127.0.0.1:2718",
            expectedUrl = "http://127.0.0.1:2718/?access_token=new",
        )
        assertEquals(
            null,
            loadErrorGeneration("http://127.0.0.1:2718/?access_token=old", current),
        )
    }

    @Test fun stoppingAndStoppedLifecyclesCannotRenderNotebookContent() {
        assertTrue(canRenderNotebookFor(MarimoNotebookState.Running("http://127.0.0.1:2222")))
        assertFalse(canRenderNotebookFor(MarimoNotebookState.Stopping("http://127.0.0.1:2222")))
        assertFalse(canRenderNotebookFor(MarimoNotebookState.Stopped(StopCause.Deliberate)))
    }
}

/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.session.NotebookSessionManager
import io.marimo.notebook.session.NotebookSessionManagerTest.FakeLauncher
import io.marimo.notebook.session.SessionManagerSeams
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DataSourceConsentPromptTest : BasePlatformTestCase() {
    private lateinit var manager: NotebookSessionManager
    private lateinit var seams: SessionManagerSeams

    override fun setUp() {
        super.setUp()
        manager = project.service()
        seams = SessionManagerSeams(manager)
    }

    override fun tearDown() {
        try {
            manager.sessions().forEach { manager.stopUrl(it.fileUrl) }
            seams.restore()
        } finally {
            super.tearDown()
        }
    }

    fun testConsentBodyPluralizesConnections() {
        assertEquals(
            "notebooks/orders.py can use 1 IDE database connection. " +
                "Nothing is shared until you opt in.",
            dataSourceConsentBody("notebooks/orders.py", 1),
        )
        assertEquals(
            "notebooks/orders.py can use 2 IDE database connections. " +
                "Nothing is shared until you opt in.",
            dataSourceConsentBody("notebooks/orders.py", 2),
        )
    }

    fun testNeverMarksAffectedLiveSessionsStaleBeforeClearingExposures() {
        val sdk = FakeLauncher("consent-sdk")
        manager.planner = LaunchPlanner(sdk, FakeLauncher("consent-uv"))
        val notebook =
            Path.of(requireNotNull(project.basePath))
                .resolve("notebooks/consent.py")
                .also {
                    Files.createDirectories(it.parent)
                    Files.writeString(it, "import marimo\n")
                }
                .let {
                    requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it))
                }
        val notebookKey = requireNotNull(NotebookExposureKey.from(project, notebook))
        val store = project.service<DataSourceExposureStore>()
        store.recordExposures(
            notebookKey,
            listOf(
                DataSourceExposureStore.ExposureEntry().apply {
                    dataSourceId = "pg-1"
                    exposed = true
                    familyDefault = true
                }
            ),
        )
        val readyUrl = manager.urlFor(notebook)
        assertTrue(sdk.firstLaunch.await(5, TimeUnit.SECONDS))
        sdk.handles.single().becomeReady()
        readyUrl.get(5, TimeUnit.SECONDS)

        DataSourceConsentPrompt.never(project)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertTrue(requireNotNull(manager.peek(notebook)).launchEnvStale)
        assertTrue(store.neverForThisProject())
        assertTrue(store.notebookPathsWithExposures().isEmpty())
    }
}

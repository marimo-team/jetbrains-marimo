/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LaunchPlanner
import io.marimo.notebook.session.NotebookSessionManagerTest.FakeLauncher
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class NotebookSessionLeaseTest : BasePlatformTestCase() {

    private class ManualTtl : TtlScheduler {
        val pending = CopyOnWriteArrayList<Pair<Long, Runnable>>()

        override fun schedule(delayMillis: Long, task: Runnable): TtlCancellable {
            val entry = delayMillis to task
            pending.add(entry)
            return TtlCancellable { pending.remove(entry) }
        }
    }

    private lateinit var ttl: ManualTtl
    private lateinit var sdk: FakeLauncher
    private lateinit var uv: FakeLauncher
    private val leases = mutableListOf<NotebookSessionLease>()

    private val manager: NotebookSessionManager
        get() = project.service<NotebookSessionManager>()

    override fun setUp() {
        super.setUp()
        sdk = FakeLauncher("fake-sdk")
        uv = FakeLauncher("fake-uv")
        manager.planner = LaunchPlanner(sdk, uv)
        ttl = ManualTtl()
        manager.ttlScheduler = ttl
    }

    override fun tearDown() {
        try {
            leases.forEach(NotebookSessionLease::close)
            manager.sessions().forEach { manager.stopUrl(it.fileUrl) }
        } finally {
            super.tearDown()
        }
    }

    fun testTwoEditorLeasesShareTheSessionAndArmTtlAfterTheLastClose() {
        val file = notebook("two_editor_leases.py")
        val first = acquire(file, LeaseOwner.EDITOR_TAB)
        val second = acquire(file, LeaseOwner.EDITOR_TAB)

        assertEquals(first.sessionId, second.sessionId)
        assertEquals(1, manager.sessions().size)
        assertEquals(2, first.status().attachedTabs)

        first.close()

        assertEquals(1, second.status().attachedTabs)
        assertTrue(ttl.pending.isEmpty())

        second.close()

        assertEquals(0, manager.statusFor(file)!!.attachedTabs)
        assertEquals(1, ttl.pending.size)
        assertNotNull(manager.statusFor(file)!!.expiresAtMillis)
    }

    fun testClosingALeaseTwiceOnlyArmsOneTtl() {
        val file = notebook("double_close_lease.py")
        val lease = acquire(file, LeaseOwner.EDITOR_TAB)

        lease.close()
        lease.close()

        assertEquals(0, manager.statusFor(file)!!.attachedTabs)
        assertEquals(1, ttl.pending.size)
    }

    fun testAcquiringAnEditorLeaseCancelsTheArmedTtl() {
        val file = notebook("reacquire_lease.py")
        val first = acquire(file, LeaseOwner.EDITOR_TAB)
        first.close()
        val staleCallback = ttl.pending.single().second

        val second = acquire(file, LeaseOwner.EDITOR_TAB)
        staleCallback.run()

        assertEquals(first.sessionId, second.sessionId)
        assertTrue(ttl.pending.isEmpty())
        assertNull(manager.statusFor(file)?.expiresAtMillis)
        assertNotNull(manager.statusFor(file))
    }

    fun testExpiredLeaseCannotCreateANewSession() {
        val file = notebook("expired_lease.py")
        val lease = acquire(file, LeaseOwner.EDITOR_TAB)
        lease.close()

        ttl.pending.single().second.run()

        assertNull(manager.statusFor(file))
        assertTrue(lease.readyUrl().isCompletedExceptionally)
        assertNull(manager.statusFor(file))
    }

    fun testPairTerminalKeepsTheSessionAliveAfterEditorCloses() {
        val file = notebook("terminal_lease.py")
        val editor = acquire(file, LeaseOwner.EDITOR_TAB)
        val terminal = acquire(file, LeaseOwner.PAIR_TERMINAL)

        editor.close()

        assertTrue(ttl.pending.isEmpty())

        terminal.close()

        assertEquals(1, ttl.pending.size)
    }

    fun testClosingTheLastPromptLeaseArmsTheTtl() {
        val file = notebook("prompt_lease.py")
        val prompt = acquire(file, LeaseOwner.PAIR_PROMPT)

        prompt.close()

        assertEquals(1, ttl.pending.size)
        assertNotNull(manager.statusFor(file)!!.expiresAtMillis)
    }

    fun testPromptLeaseDoesNotCancelAnArmedTtl() {
        val file = notebook("prompt_ttl_lease.py")
        val editor = acquire(file, LeaseOwner.EDITOR_TAB)
        editor.close()

        val prompt = acquire(file, LeaseOwner.PAIR_PROMPT)
        ttl.pending.single().second.run()

        assertNull(manager.statusFor(file))
        assertTrue(prompt.readyUrl().isCompletedExceptionally)
    }

    fun testExistingLeaseDoesNotCreateASession() {
        val file = notebook("action_lease.py")

        assertNull(manager.leaseIfPresent(file))
        assertNull(manager.peek(file))
    }

    fun testPromptLeaseReportsTheActiveSandboxLauncher() {
        val file = notebook("sandbox_prompt.py")
        sdk.canLaunch = false
        manager.enableSandbox(file)
        val lease = acquire(file, LeaseOwner.PAIR_PROMPT)

        assertNull(lease.launcherInfo())

        val readyUrl = lease.readyUrl()
        assertTrue(
            "sandbox launch did not begin",
            uv.firstLaunch.await(5, TimeUnit.SECONDS),
        )
        uv.handles.single().becomeReady()
        readyUrl.get(5, TimeUnit.SECONDS)

        assertEquals(
            LauncherInfo(listOf("fake-uv", "marimo"), sandbox = true),
            lease.launcherInfo(),
        )
    }

    private fun acquire(file: VirtualFile, owner: LeaseOwner): NotebookSessionLease =
        manager.acquire(file, owner).also(leases::add)

    private fun notebook(name: String): VirtualFile =
        myFixture.addFileToProject(name, "import marimo\n").virtualFile
}

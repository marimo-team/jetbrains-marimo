/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidatesTest {
    private fun facts(
        id: String = "pg-1",
        name: String = "Orders DB",
        url: String? = "jdbc:postgresql://db.internal:5432/orders",
        username: String? = "app",
        authProviderId: String? = null,
    ) = IdeDataSourceFacts(id, name, url, username, authProviderId)

    @Test
    fun userPasswordSourcesAreSupported() {
        for (auth in listOf(null, "user-pass", "no-auth")) {
            val candidate = Candidates.from(facts(authProviderId = auth))
            assertTrue("auth '$auth' must be supported", candidate.supported)
            assertEquals(DbFamily.POSTGRES, candidate.family)
            assertEquals("postgresql", candidate.dialect)
        }
    }

    @Test
    fun otherAuthProvidersAreGreyedOutWithAReason() {
        val candidate = Candidates.from(facts(authProviderId = "ms-sso"))
        assertNotNull(candidate.unsupportedReason)
        assertTrue(candidate.unsupportedReason!!.contains("ms-sso"))
    }

    @Test
    fun unparseableUrlsAreGreyedOutWithAReason() {
        val candidate = Candidates.from(facts(url = "jdbc:oracle:thin:@//db:1521/svc"))
        assertNotNull(candidate.unsupportedReason)
        assertNull(candidate.endpoint)
    }

    @Test
    fun unknownSchemesStayExposableWithoutAFamily() {
        val candidate = Candidates.from(facts(url = "jdbc:snowflake://acme.example.com"))
        assertTrue(candidate.supported)
        assertNull(candidate.family)
        assertEquals("snowflake", candidate.dialect)
    }

    @Test
    fun blankUsernamesBecomeNull() {
        assertNull(Candidates.from(facts(username = " ")).username)
    }

    @Test
    fun postgresWithoutAUsernameExplainsWhyQuickAddIsUnavailable() {
        val candidate = Candidates.from(facts(username = null, authProviderId = "no-auth"))

        assertTrue(candidate.unsupportedReason!!.contains("username"))
    }

    @Test
    fun storedPrimaryWinsOtherwiseFirstByName() {
        val a = Candidates.from(facts(id = "a", name = "Beta"))
        val b = Candidates.from(facts(id = "b", name = "Alpha"))
        assertEquals(setOf("a"), Candidates.effectivePrimaries(listOf(a, b), setOf("a")))
        assertEquals(setOf("b"), Candidates.effectivePrimaries(listOf(a, b), emptySet()))
    }

    @Test
    fun primariesAreIndependentPerFamily() {
        val pg = Candidates.from(facts(id = "pg", name = "PG"))
        val my = Candidates.from(facts(id = "my", name = "My", url = "jdbc:mysql://db/shop"))
        assertEquals(setOf("pg", "my"), Candidates.effectivePrimaries(listOf(pg, my), emptySet()))
    }
}

/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.datasource

import java.util.Locale
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
        val reason = candidate.unsupportedReason
        assertNotNull(reason)
        assertTrue(requireNotNull(reason).contains("ms-sso"))
        assertTrue(reason.contains("no authentication"))
    }

    @Test
    fun unparsableUrlsAreGreyedOutWithAReason() {
        val candidate = Candidates.from(facts(url = "jdbc:oracle:thin:@//db:1521/svc"))
        assertNotNull(candidate.unsupportedReason)
        assertNull(candidate.endpoint)
    }

    @Test
    fun unknownSchemesExplainThatQuickAddIsUnavailable() {
        val candidate = Candidates.from(facts(url = "jdbc:snowflake://acme.example.com"))
        assertTrue(candidate.unsupportedReason!!.contains("Quick Add"))
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
    fun everySupportedFamilyRequiresAUsername() {
        val urls =
            listOf(
                "jdbc:postgresql://db/orders",
                "jdbc:mysql://db/orders",
                "jdbc:trino://db/hive",
            )

        urls.forEach { url ->
            val candidate = Candidates.from(facts(url = url, username = null))
            assertTrue(
                "$url should require a username",
                candidate.unsupportedReason!!.contains("username"),
            )
        }
    }

    @Test
    fun everySupportedFamilyRequiresADatabaseOrCatalog() {
        val cases =
            listOf(
                "jdbc:postgresql://db" to "database",
                "jdbc:mysql://db" to "database",
                "jdbc:trino://db" to "catalog",
            )

        cases.forEach { (url, requiredField) ->
            val candidate = Candidates.from(facts(url = url))
            assertTrue(
                "$url should require a $requiredField",
                candidate.unsupportedReason!!.contains(requiredField),
            )
        }
    }

    @Test
    fun storedDefaultWinsOtherwiseFirstByName() {
        val a = Candidates.from(facts(id = "a", name = "Beta"))
        val b = Candidates.from(facts(id = "b", name = "Alpha"))
        assertEquals(setOf("a"), Candidates.effectiveDefaults(listOf(a, b), setOf("a")))
        assertEquals(setOf("b"), Candidates.effectiveDefaults(listOf(a, b), emptySet()))
    }

    @Test
    fun fallbackOrderingDoesNotDependOnTheDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val i = Candidates.from(facts(id = "i", name = "I"))
            val j = Candidates.from(facts(id = "j", name = "J"))

            assertEquals(setOf("i"), Candidates.effectiveDefaults(listOf(j, i), emptySet()))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun defaultsAreIndependentPerFamily() {
        val pg = Candidates.from(facts(id = "pg", name = "PG"))
        val my = Candidates.from(facts(id = "my", name = "My", url = "jdbc:mysql://db/shop"))
        assertEquals(setOf("pg", "my"), Candidates.effectiveDefaults(listOf(pg, my), emptySet()))
    }
}

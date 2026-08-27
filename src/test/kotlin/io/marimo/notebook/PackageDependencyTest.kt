/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guardrail for the import graph in ARCHITECTURE.md (Package dependencies). Same-package and root
 * types (`MarimoLocalhost`, `MarimoBundle`, `MarimoIcons`) are always allowed.
 */
class PackageDependencyTest {

    @Test
    fun productionSourcesObeyAllowedEdges() {
        assertTrue(
            "expected $MAIN_KOTLIN (run tests from the repo root)",
            Files.isDirectory(MAIN_KOTLIN),
        )
        val violations = mutableListOf<String>()
        Files.walk(MAIN_KOTLIN).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .forEach { file ->
                    violations += forbiddenImportsIn(Files.readString(file), file.toString())
                }
        }
        if (violations.isNotEmpty()) {
            fail(violations.joinToString("\n"))
        }
    }

    @Test
    fun forbiddenImportFailsTheGuardrail() {
        val source =
            """
            package io.marimo.notebook.launch

            import io.marimo.notebook.session.NotebookSessionManager
            """
                .trimIndent()
        val hits = forbiddenImportsIn(source, "injected.kt")
        assertTrue(
            hits.joinToString("\n"),
            hits.any { it.contains("launch") && it.contains("session") },
        )
    }

    companion object {
        private val MAIN_KOTLIN = Path.of("src/main/kotlin")

        private const val PLUGIN_PREFIX = "io.marimo.notebook"

        private const val ROOT = "root"

        private val LAYERS =
            setOf("detect", "launch", "session", "editor", "pair", "settings", "telemetry")

        // Allowed edges. Matches the mermaid diagram in ARCHITECTURE.md (Package dependencies).
        // Key imports values. Empty means no other named layer.
        private val ALLOWED: Map<String, Set<String>> =
            mapOf(
                "detect" to emptySet(),
                "launch" to emptySet(),
                "session" to setOf("launch", "telemetry"),
                "editor" to setOf("session", "launch", "detect", "telemetry"),
                "pair" to setOf("session", "detect", "telemetry"),
                "settings" to setOf("session", "telemetry"),
                "telemetry" to emptySet(),
            )

        private val PACKAGE_LINE = Regex("""^package\s+([\w.]+)""")
        // Prefix match so `import pkg.Type as Alias` and `import pkg.*` still count.
        private val IMPORT_LINE = Regex("""^import\s+([\w.]+)""")

        private fun forbiddenImportsIn(source: String, label: String): List<String> {
            val fromLayer = layerOf(packageName(source) ?: return emptyList()) ?: return emptyList()
            val allowed = ALLOWED[fromLayer] ?: emptySet()
            val hits = mutableListOf<String>()
            source.lineSequence().forEachIndexed { index, raw ->
                val imported =
                    IMPORT_LINE.find(raw.trim())?.groupValues?.get(1) ?: return@forEachIndexed
                val toLayer = layerOf(imported) ?: return@forEachIndexed
                if (toLayer == ROOT || toLayer == fromLayer) return@forEachIndexed
                if (toLayer !in allowed) {
                    hits += "$label:${index + 1}: $fromLayer imports $toLayer ($imported)"
                }
            }
            return hits
        }

        private fun packageName(source: String): String? =
            source.lineSequence().firstNotNullOfOrNull { line ->
                PACKAGE_LINE.find(line.trim())?.groupValues?.get(1)
            }

        private fun layerOf(qualified: String): String? {
            if (!qualified.startsWith(PLUGIN_PREFIX)) return null
            if (qualified == PLUGIN_PREFIX) return ROOT
            val first = qualified.removePrefix("$PLUGIN_PREFIX.").substringBefore('.')
            return when {
                first in LAYERS -> first
                first.first().isUpperCase() -> ROOT
                else -> error("Unknown plugin package: $qualified")
            }
        }
    }
}

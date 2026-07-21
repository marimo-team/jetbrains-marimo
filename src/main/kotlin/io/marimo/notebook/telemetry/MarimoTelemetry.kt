/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.PluginId
import com.posthog.server.PostHog
import com.posthog.server.PostHogConfig
import com.posthog.server.PostHogInterface
import java.util.Properties
import java.util.UUID

enum class Consent { UNSET, ALLOWED, DENIED }

/**
 * The wire transport for usage events. The real implementation talks to PostHog; tests inject a
 * recording fake so no network is touched.
 */
interface PostHogSink {
    fun capture(distinctId: String, event: String, properties: Map<String, Any>)

    fun close()
}

@Service(Service.Level.APP)
@State(name = "MarimoTelemetry", storages = [Storage("marimo-telemetry.xml")])
class MarimoTelemetry : PersistentStateComponent<MarimoTelemetry.PersistedState> {

    data class PersistedState(var consent: Consent = Consent.UNSET, var anonymousId: String = "")

    private var persisted = PersistedState()

    @Volatile private var sink: PostHogSink? = null

    override fun getState(): PersistedState {
        if (persisted.anonymousId.isBlank()) persisted.anonymousId = UUID.randomUUID().toString()
        return persisted
    }

    override fun loadState(state: PersistedState) {
        persisted = state
    }

    fun anonymousId(): String = state.anonymousId

    val consent: Consent get() = persisted.consent

    /** Grants consent, brings up the transport, and records plugin activation. */
    fun allow() {
        persisted.consent = Consent.ALLOWED
        if (sink == null) sink = buildSink()
        capture(TelemetryEvent.PluginActivated(ideName(), ideVersion()))
    }

    /** Declines consent from the unset state; no transport is ever constructed. */
    fun deny() {
        persisted.consent = Consent.DENIED
    }

    /** Withdraws previously-granted consent: flushes and tears the transport down. */
    fun revoke() {
        persisted.consent = Consent.DENIED
        sink?.close()
        sink = null
    }

    /**
     * Sends [event] only when consent is [Consent.ALLOWED]; otherwise a network-free no-op. The
     * PostHog client is built lazily on the first allowed capture and reused thereafter.
     */
    fun capture(event: TelemetryEvent) {
        if (consent != Consent.ALLOWED) return
        val target = sink ?: buildSink().also { sink = it }
        val enriched = event.properties + mapOf(
            "plugin_version" to pluginVersion(),
            "environment" to environment(),
        )
        target.capture(anonymousId(), event.name, enriched)
    }

    private fun buildSink(): PostHogSink = RealPostHogSink()

    private fun pluginVersion(): String =
        PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "unknown"

    private fun environment(): String = ENVIRONMENT

    private fun ideName(): String =
        runCatching { ApplicationNamesInfo.getInstance().fullProductName }.getOrDefault("unknown")

    private fun ideVersion(): String =
        runCatching { ApplicationInfo.getInstance().fullVersion }.getOrDefault("unknown")

    @Suppress("unused")
    fun withSinkForTest(sink: PostHogSink): MarimoTelemetry {
        this.sink = sink
        return this
    }

    @Suppress("unused")
    fun setConsentForTest(consent: Consent) {
        persisted.consent = consent
    }

    private class RealPostHogSink : PostHogSink {
        private val client: PostHogInterface = PostHog.with(PostHogConfig(POSTHOG_API_KEY, POSTHOG_HOST))

        override fun capture(distinctId: String, event: String, properties: Map<String, Any>) {
            client.capture(distinctId = distinctId, event = event, properties = properties)
        }

        override fun close() {
            client.close()
        }
    }

    companion object {
        const val PLUGIN_ID = "io.marimo.notebook"
        const val POSTHOG_HOST = "https://us.i.posthog.com"

        // Public, write-only project key (not a secret) — safe to ship in the plugin.
        const val POSTHOG_API_KEY = "phc_rC8Zgmycm8WEoyb3PU2hxEaXvtYfpofh6hZFiibwisHt"

        // Replaced with the real Sentry DSN before Marketplace submit (Phase C).
        const val SENTRY_DSN = "<PLACEHOLDER_DSN>"

        const val PRIVACY_URL = "https://github.com/marimo-team/jetbrains-marimo/blob/main/PRIVACY.md"

        // Baked in at build time from telemetry.properties; "production" only on the release build.
        // Falls back to "development" if the resource is missing or its token was never filtered.
        private val ENVIRONMENT: String by lazy {
            MarimoTelemetry::class.java.getResourceAsStream("/telemetry.properties")
                ?.use { Properties().apply { load(it) }.getProperty("environment") }
                ?.takeIf { it.isNotBlank() && !it.startsWith("\$") }
                ?: "development"
        }

        fun getInstance(): MarimoTelemetry =
            ApplicationManager.getApplication().getService(MarimoTelemetry::class.java)
    }
}

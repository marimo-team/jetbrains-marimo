/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
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
import io.sentry.Sentry
import io.sentry.protocol.User
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

/**
 * The wire transport for crash reports. The real implementation talks to Sentry; tests inject a
 * recording fake so no network is touched. Foreign exceptions are dropped inside the real transport
 * via [SentryOriginFilter], so callers may hand off any throwable.
 */
interface SentrySink {
    fun captureException(throwable: Throwable)

    /** Opens a Sentry release-health session so crash-free session/user rates can be computed. */
    fun startSession()

    /** Closes the current release-health session, marking it cleanly ended. */
    fun endSession()

    fun close()
}

@Service(Service.Level.APP)
@State(name = "MarimoTelemetry", storages = [Storage("marimo-telemetry.xml")])
class MarimoTelemetry : PersistentStateComponent<MarimoTelemetry.PersistedState>, Disposable {

    data class PersistedState(var consent: Consent = Consent.UNSET, var anonymousId: String = "")

    private var persisted = PersistedState()

    @Volatile private var sink: PostHogSink? = null

    @Volatile private var sentrySink: SentrySink? = null

    @Volatile private var sentrySessionActive = false

    override fun getState(): PersistedState {
        if (persisted.anonymousId.isBlank()) persisted.anonymousId = UUID.randomUUID().toString()
        return persisted
    }

    override fun loadState(state: PersistedState) {
        persisted = state
    }

    fun anonymousId(): String = state.anonymousId

    val consent: Consent get() = persisted.consent

    /** Grants consent, brings up both transports, and records plugin activation. */
    fun allow() {
        persisted.consent = Consent.ALLOWED
        if (sink == null) sink = buildSink()
        startSentrySession()
        capture(TelemetryEvent.PluginActivated(ideName(), ideVersion()))
    }

    /** Declines consent from the unset state; no transport is ever constructed. */
    fun deny() {
        persisted.consent = Consent.DENIED
    }

    /** Withdraws previously-granted consent: ends the crash-free session, flushes, tears both transports down. */
    fun revoke() {
        persisted.consent = Consent.DENIED
        sink?.close()
        sink = null
        endSentrySession()
        sentrySink?.close()
        sentrySink = null
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

    /**
     * Reports [throwable] to Sentry only when consent is [Consent.ALLOWED]; otherwise a network-free
     * no-op. Exceptions that did not originate in plugin code are dropped by the transport's
     * [SentryOriginFilter] `beforeSend` hook, so callers need not pre-filter.
     */
    fun captureException(throwable: Throwable) {
        if (consent != Consent.ALLOWED) return
        val target = startSentrySession() ?: return
        target.captureException(throwable)
    }

    /**
     * Builds the Sentry transport on first use and opens exactly one release-health session for the
     * consented run. Returns the live sink, or null when Sentry is disabled (placeholder DSN).
     */
    private fun startSentrySession(): SentrySink? {
        val target = sentrySink ?: buildSentrySink()?.also { sentrySink = it } ?: return null
        if (!sentrySessionActive) {
            target.startSession()
            sentrySessionActive = true
        }
        return target
    }

    private fun endSentrySession() {
        if (sentrySessionActive) {
            sentrySink?.endSession()
            sentrySessionActive = false
        }
    }

    /** Ends the crash-free session and flushes both transports on IDE shutdown, leaving consent intact. */
    override fun dispose() {
        endSentrySession()
        sink?.close()
        sink = null
        sentrySink?.close()
        sentrySink = null
    }

    private fun buildSink(): PostHogSink = RealPostHogSink()

    // No usable DSN (still the Phase-C placeholder) means no crash reporting; usage events are
    // unaffected. Skipping keeps a placeholder build from crashing on Sentry.init's DSN validation.
    private fun buildSentrySink(): SentrySink? =
        if (SENTRY_DSN.startsWith("<")) null else RealSentrySink()

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

    @Suppress("unused")
    fun withSentrySinkForTest(sink: SentrySink): MarimoTelemetry {
        this.sentrySink = sink
        return this
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

    private inner class RealSentrySink : SentrySink {
        init {
            Sentry.init { options ->
                options.dsn = SENTRY_DSN
                options.release = "jetbrains-marimo@${pluginVersion()}"
                options.environment = environment()
                options.isEnableUncaughtExceptionHandler = false
                // Sessions are driven from the consent lifecycle (allow/revoke/dispose), not the
                // SDK's process hooks, so a session maps to one consented run rather than JVM start.
                options.isEnableAutoSessionTracking = false
                options.setBeforeSend { event, _ ->
                    if (SentryOriginFilter.isMarimoOrigin(event.throwable)) event else null
                }
            }
            Sentry.configureScope { scope ->
                scope.setTag("ide_name", ideName())
                scope.setTag("ide_version", ideVersion())
                scope.setTag("plugin_version", pluginVersion())
                scope.setUser(User().apply { id = anonymousId() })
            }
        }

        override fun captureException(throwable: Throwable) {
            Sentry.captureException(throwable)
        }

        override fun startSession() {
            Sentry.startSession()
        }

        override fun endSession() {
            Sentry.endSession()
        }

        override fun close() {
            Sentry.close()
        }
    }

    companion object {
        const val PLUGIN_ID = "io.marimo.notebook"
        const val POSTHOG_HOST = "https://us.i.posthog.com"

        // Public, write-only project key (not a secret) — safe to ship in the plugin.
        const val POSTHOG_API_KEY = "phc_rC8Zgmycm8WEoyb3PU2hxEaXvtYfpofh6hZFiibwisHt"

        // Public client-side DSN (not a secret) — safe to ship in the plugin.
        const val SENTRY_DSN = "https://db83abbe783accef094828aff85196d6@o4505919839862784.ingest.us.sentry.io/4511707070005248"

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

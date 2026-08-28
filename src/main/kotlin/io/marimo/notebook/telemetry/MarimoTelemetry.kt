/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import io.marimo.notebook.telemetry.transport.liveTelemetryEnabled
import io.marimo.notebook.telemetry.transport.postHogSink
import io.marimo.notebook.telemetry.transport.sentrySink
import java.util.Properties
import java.util.UUID

enum class Consent {
    UNSET,
    ALLOWED,
    DENIED,
}

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
 * sanitizer, so callers may hand off any throwable.
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

    override fun getState(): PersistedState = persisted

    override fun loadState(state: PersistedState) {
        persisted = state
    }

    fun anonymousId(): String = persisted.anonymousId

    val consent: Consent
        get() = persisted.consent

    /** Grants consent, brings up both transports, and records plugin activation. */
    fun allow() {
        MarimoConsentPrompt.expire()
        persisted.consent = Consent.ALLOWED
        ensureStarted()
        capture(TelemetryEvent.PluginActivated(ideName(), ideVersion()))
    }

    /**
     * Declines consent without constructing a transport and closes any transport already active.
     */
    fun deny() {
        transitionToDenied()
    }

    /**
     * Withdraws previously-granted consent: ends the crash-free session, flushes, tears both
     * transports down.
     */
    fun revoke() {
        transitionToDenied()
    }

    private fun transitionToDenied() {
        MarimoConsentPrompt.expire()
        synchronized(this) {
            persisted.consent = Consent.DENIED
            closeTransports()
        }
    }

    /**
     * Sends [event] only when consent is [Consent.ALLOWED]; otherwise a network-free no-op. The
     * PostHog client is built lazily on the first allowed capture and reused thereafter.
     */
    fun capture(event: TelemetryEvent) {
        if (consent != Consent.ALLOWED) return
        ensureStarted()
        val target = sink ?: return
        val distinctId = anonymousId()
        val enriched =
            event.properties +
                mapOf(
                    "plugin_version" to pluginVersion(),
                    "environment" to environment(),
                    "distinct_id" to distinctId,
                )
        target.capture(distinctId, event.name, enriched)
    }

    /**
     * Reports [throwable] to Sentry only when consent is [Consent.ALLOWED]; otherwise a
     * network-free no-op. Exceptions that did not originate in plugin code are dropped by the
     * sanitizer `beforeSend` hook, so callers need not pre-filter.
     */
    fun captureException(throwable: Throwable) {
        if (consent != Consent.ALLOWED) return
        val target = ensureStarted() ?: return
        target.captureException(throwable)
    }

    @Synchronized
    private fun ensureStarted(): SentrySink? {
        if (consent != Consent.ALLOWED) return null
        ensureAnonymousId()
        if (sink == null) sink = buildSink()
        val target = sentrySink ?: buildSentrySink().also { sentrySink = it }
        if (!sentrySessionActive) {
            target.startSession()
            sentrySessionActive = true
        }
        return target
    }

    private fun ensureAnonymousId() {
        if (persisted.anonymousId.isBlank()) {
            persisted.anonymousId = UUID.randomUUID().toString()
        }
    }

    private fun endSentrySession() {
        if (sentrySessionActive) {
            sentrySink?.endSession()
            sentrySessionActive = false
        }
    }

    /**
     * Ends the crash-free session and flushes both transports on IDE shutdown, leaving consent
     * intact.
     */
    override fun dispose() {
        closeTransports()
    }

    @Synchronized
    private fun closeTransports() {
        val postHog = sink
        sink = null
        postHog?.close()

        endSentrySession()
        val sentry = sentrySink
        sentrySink = null
        sentry?.close()
    }

    private fun buildSink(): PostHogSink =
        postHogSink(LIVE_TELEMETRY, POSTHOG_API_KEY, POSTHOG_HOST)

    private fun buildSentrySink(): SentrySink =
        sentrySink(
            live = LIVE_TELEMETRY,
            dsn = SENTRY_DSN,
            release = "$SENTRY_RELEASE_PREFIX@${pluginVersion()}",
            environment = environment(),
            ideName = ideName(),
            ideVersion = ideVersion(),
            pluginVersion = pluginVersion(),
            anonymousId = anonymousId(),
        )

    private fun pluginVersion(): String = PLUGIN_VERSION

    private fun environment(): String = ENVIRONMENT

    private fun ideName(): String = runCatching {
        ApplicationNamesInfo.getInstance().fullProductName
    }
        .getOrDefault("unknown")

    private fun ideVersion(): String = runCatching {
        ApplicationInfo.getInstance().fullVersion
    }
        .getOrDefault("unknown")

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

    companion object {
        private const val SENTRY_RELEASE_PREFIX = "jetbrains-marimo"

        const val POSTHOG_HOST = "https://us.i.posthog.com"

        // Public, write-only project key (not a secret) — safe to ship in the plugin.
        const val POSTHOG_API_KEY = "phc_rC8Zgmycm8WEoyb3PU2hxEaXvtYfpofh6hZFiibwisHt"

        // Public client-side DSN (not a secret) — safe to ship in the plugin.
        const val SENTRY_DSN =
            "https://db83abbe783accef094828aff85196d6@o4505919839862784.ingest.us.sentry.io/4511707070005248"

        const val PRIVACY_URL =
            "https://github.com/marimo-team/jetbrains-marimo/blob/main/PRIVACY.md"

        // Baked in at build time by the generateTelemetryConfig Gradle task.
        private val telemetryConfig: Properties by lazy {
            Properties().apply {
                MarimoTelemetry::class.java.getResourceAsStream("/telemetry.properties")?.use {
                    load(it)
                }
            }
        }

        // "production" only on the release build; falls back to "development" if the resource is
        // missing or its token was never filtered.
        private val ENVIRONMENT: String by lazy {
            telemetryConfig.getProperty("environment")?.takeIf {
                it.isNotBlank() && !it.startsWith("\$")
            } ?: "development"
        }

        private val PLUGIN_VERSION: String by lazy {
            telemetryConfig.getProperty("version")?.takeIf { it.isNotBlank() } ?: "unknown"
        }

        // Written at build time. Production artifacts set live=true; local and CI builds stay
        // false unless -Ptelemetry.live=true.
        private val LIVE_TELEMETRY: Boolean by lazy {
            liveTelemetryEnabled(telemetryConfig.getProperty("live"))
        }

        fun getInstance(): MarimoTelemetry =
            ApplicationManager.getApplication().getService(MarimoTelemetry::class.java)
    }
}

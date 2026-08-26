/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.telemetry.transport

import com.posthog.server.PostHog
import com.posthog.server.PostHogConfig
import com.posthog.server.PostHogInterface
import io.marimo.notebook.telemetry.PostHogSink

/** PostHog client behind the usage-event [PostHogSink] seam. */
internal class PostHogTransport(apiKey: String, host: String) : PostHogSink {
    private val client: PostHogInterface = PostHog.with(PostHogConfig(apiKey, host))

    override fun capture(distinctId: String, event: String, properties: Map<String, Any>) {
        client.capture(distinctId = distinctId, event = event, properties = properties)
    }

    override fun close() {
        client.close()
    }
}

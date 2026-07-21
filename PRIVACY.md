# Privacy Policy — marimo for JetBrains

The marimo plugin can send anonymous usage events and crash reports to help the marimo team
understand how the plugin is used and fix the problems people actually hit.

**Telemetry is opt-in and off by default.** The plugin sends nothing until you explicitly allow it,
and you can turn it back off at any time in **Settings → Tools → marimo**. This choice is
independent of the IDE's global "Data Sharing" setting — turning that on does not turn this on, and
turning this off keeps it off regardless of the IDE setting.

## What identifies you

Nothing that personally identifies you. When you opt in, the plugin generates a **random anonymous
identifier** (a UUID) stored locally in `marimo-telemetry.xml`. It is used to group events from the
same installation and is not derived from your name, email, account, machine name, or the IDE's own
installation id.

## Usage events (PostHog)

Sent to PostHog (US region, `https://us.i.posthog.com`). Usage events are **fully path-free** — they
carry only the fields listed below.

Every event also includes: the anonymous identifier, the plugin version, and a build marker
(`development` or `production`).

| Event | When it is sent | Properties |
|---|---|---|
| `plugin_activated` | You grant consent | `ide_name`, `ide_version` |
| `notebook_opened` | A marimo notebook is opened | `launcher` (which launch path was used) |
| `notebook_launch_failed` | The marimo server fails to start | `reason` (a fixed category, e.g. no interpreter / marimo missing) |
| `marimo_install_result` | After an attempt to install marimo | `success` (true/false) |
| `pair_started` | You start a "Pair with AI" session | `method` (terminal / copy-prompt / AI chat), `harness` (which assistant) |
| `sandbox_started` | A notebook is launched in a uv sandbox | — |

No file paths, no code, no notebook contents, and no SQL are ever sent as usage events. The event
set is fixed in the plugin's source; there is no field that carries free-form text.

## Crash reports (Sentry)

Sent to Sentry only for exceptions that originate in the marimo plugin's own code (an origin filter
drops crashes from the IDE or other plugins). A crash report contains:

- the exception type, message, and cause chain;
- the stack trace;
- the plugin version, IDE name and version, and the anonymous identifier.

**A crash report may include a file path** when a path appears inside an exception message or a
captured process-output tail. Crash reports do **not** include your notebook contents, code, cell
outputs, or SQL, and never your name, email, or account. This is the one place telemetry can carry a
path; usage events never do.

## While telemetry is disabled

Nothing is sent. No usage events and no crash reports leave your machine while the setting is off,
regardless of the IDE's Data Sharing setting.

## Contact

Questions about this policy or the data collected: **contact@marimo.io**.

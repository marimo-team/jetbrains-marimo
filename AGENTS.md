# marimo for PyCharm

A JetBrains plugin that opens [marimo](https://marimo.io) notebooks in PyCharm. It starts a local marimo server and shows the editor in a JCEF tab.

## Common commands

| Command | What it does |
|---|---|
| `./gradlew runIde` | Start a sandboxed IDE with the plugin loaded |
| `./gradlew runIde -Ptelemetry.live=true` | Same, and send opt-in events to PostHog and Sentry |
| `./gradlew test` | Run the test suite |
| `./gradlew spotlessCheck` | Check Kotlin format and license headers |
| `./gradlew spotlessApply` | Format Kotlin and fix license headers |
| `./gradlew detekt` | Run Kotlin static analysis |
| `./gradlew check` | Run tests, Spotless, Detekt, and other Gradle verification |
| `./gradlew buildPlugin` | Build the zip in `build/distributions/` |
| `./gradlew verifyPlugin` | Run the JetBrains Plugin Verifier |
| `uvx pre-commit run --all-files` | Run all repository pre-commit checks |

## Conventions

- Kotlin on the IntelliJ Platform. Gradle wrapper. JDK 21+. When `PATH` has no JDK, set `JAVA_HOME` to a 21+ JDK.
- License header on every `.kt` file. Run `./gradlew spotlessApply` to write it.
- Conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`, `release:`.
- Each PR has one category label. CI groups release notes by that label. Write changelog entries only in a `release:` PR.
- Add tests for behavior changes. `./gradlew check` must pass before a PR.
- Bind the server to `127.0.0.1`. Use the per-launch password file. `--no-token` is a settings opt-out.
- Sandbox starts need `uv`; the plugin checks `PATH` and common install locations.

## Packages

Source is `src/main/kotlin/io/marimo/notebook/`. Tests mirror that tree. Wiring is `src/main/resources/META-INF/plugin.xml`.

- `detect/` — sniff whether a `.py` file is a marimo notebook
- `launch/` — uv or SDK, CLI, process, `NotebookLifecycle`
- `session/` — leases, single-flight start, TTL, tokens
- `editor/` — JCEF editors, Sessions tool window, `EDITOR_TAB` leases
- `pair/` — harness terminal and pair prompt
- `settings/` — preferences UI
- `telemetry/` — consent-gated events

**Architecture** (layers, leases, lifecycle, TTL, generation, plugin.xml, imports, threading): [ARCHITECTURE.md](ARCHITECTURE.md).

**Contributing** (PRs, changelog, releasing): [CONTRIBUTING.md](CONTRIBUTING.md).

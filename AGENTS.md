# marimo for PyCharm — Development Guidelines

A JetBrains plugin that opens and runs [marimo](https://marimo.io) notebooks inside
PyCharm. It launches a local marimo server and renders the marimo editor in an
embedded JCEF browser tab.

This is open source: the public behavior, the code, and the contributor experience are
the product. Favor changes that benefit the project and all of its users and
maintainers, not just the immediate task.

## Toolchain

- **Kotlin** on the IntelliJ Platform, built with **Gradle** (use the wrapper, `./gradlew`).
- Requires **JDK 21+** (the JetBrains Runtime bundled with IntelliJ IDEA works). No JDK on
  `PATH`? Set `JAVA_HOME` to a 21+ JDK before running Gradle.
- The plugin runs marimo on the IDE's configured project interpreter (`<python> -m marimo`), and
  offers to install marimo into it when missing. **uv** must be on `PATH` for the isolated-sandbox
  launch path to be available, and is used by maintainers for the release-prep script.

## Common commands

| Command | What it does |
|---|---|
| `./gradlew runIde` | Launch a sandboxed IDE with the plugin loaded |
| `./gradlew runIde -Ptelemetry.live=true` | Same, but send opt-in events to PostHog/Sentry |
| `./gradlew test` | Run the test suite |
| `./gradlew spotlessCheck` | Check Kotlin formatting and license headers |
| `./gradlew spotlessApply` | Format Kotlin and insert or fix license headers |
| `./gradlew detekt` | Run Kotlin static analysis |
| `./gradlew check` | Run tests, Spotless checks, Detekt, and other Gradle verification tasks |
| `./gradlew buildPlugin` | Build the distributable zip in `build/distributions/` |
| `./gradlew verifyPlugin` | Run the JetBrains Plugin Verifier |
| `uvx pre-commit run --all-files` | Run all repository pre-commit checks |

To test against a local marimo checkout, install it into the sandbox IDE's project interpreter in
editable mode (`pip install -e /path/to/marimo`) and open a notebook.

## Layout

Source lives under `src/main/kotlin/io/marimo/notebook/`:

- `detect/` — decide whether a `.py` file is a marimo notebook.
- `editor/` — the custom `FileEditorProvider` that opens notebooks in the marimo editor.
- `launch/` — launch the marimo server (uv vs. SDK Python), build CLI args, manage the process.
- `session/` — manage notebook sessions, server processes, page configuration, and session settings.
- `pair/` — "Pair with marimo" action that wires an AI harness onto a notebook.

Plugin wiring is in `src/main/resources/META-INF/plugin.xml`. Tests mirror the package
layout under `src/test/kotlin/`.

`scripts/release_changes.py` reports the merged pull requests going into the next release,
grouped by the label each one carries.

## Conventions

- **License header** on every `.kt` file (`/* Copyright $YEAR Marimo. All rights reserved. */`),
  enforced by Spotless via `gradle check`. Run `./gradlew spotlessApply` to add it.
- **Conventional commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`, `release:`).
- **Every PR carries a category label** (`enhancement`, `bug`, `documentation`, `internal`,
  `preview`, `dependencies`, `breaking`, `other`), enforced by CI. Release notes are grouped by it.
  PRs do **not** edit `CHANGELOG.md`.
- Add tests for behavior changes; keep `./gradlew check` green before opening a PR.
- The local marimo server is bound to `127.0.0.1`. Token authentication is enabled by default with
  a per-launch password file; `--no-token` is available only as an explicit settings opt-out. Never
  expose the port.
- **Releasing:** run `uv run scripts/release_changes.py`, bump `version` in `gradle.properties`,
  write the entries under `## [Unreleased]`, run `./gradlew patchChangelog` (it inserts the versioned
  heading — never hand-write one), merge a `release: <version>` PR, then run the **Release**
  workflow by hand — once as a dry run to preview the notes, then for real. The tag and the GitHub
  release are created last, only after the Marketplace accepts the plugin. Full runbook in
  [CONTRIBUTING.md](CONTRIBUTING.md).

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full contributor workflow.

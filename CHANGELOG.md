<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# marimo-pycharm Changelog

## [Unreleased]

## [0.2.0] - 2026-11-12

### Added

- Share IDE data sources with individual notebooks from **Marimo Sessions**. Choose one default per database family and restart sessions when their shared connection settings change.
- Keep notebook sessions independent of editor tabs. Split editors share one session, and closing a tab preserves the session until its idle timeout.

### Changed

- Use the normal marimo settings file instead of the old IDE-specific `marimo-theme` copy. The plugin no longer rewrites theme settings on disk. Settings from the old copy are not migrated.

### Fixed

- Preserve explicit user, project, and notebook themes across IDE restarts. A `system` theme uses the IDE theme. Support `MARIMO_DISPLAY_THEME` while retaining the theme query parameter for older marimo versions.
- Open the embedded notebook editor on PyCharm versions that package JCEF as a separate bundled plugin.
- Detect authenticated notebook startup correctly and restart a stopped session when its notebook opens again.
- Keep **Retry** and **Restart** responsive when callbacks arrive from an earlier launch attempt.
- Save **Source** edits when the marimo tab is selected so the notebook reloads the updated code.
- Keep pairing terminals separate for each notebook and harness. Handle shell arguments safely and stop pending prompt work when the project closes.
- Dispose notebook lifecycle objects when sessions end, including on IDE shutdown, without errors about undisposed objects.
- Reuse the verified plugin archive during release signing and publishing with the current Gradle plugin.

## [0.1.2] - 2026-07-24

### Fixed

- "Pair with marimo" now opens its terminal tab reliably on the first launch, even when the Terminal tool window hasn't been opened yet.

## [0.1.1] - 2026-07-23

### Fixed

- Moving a notebook tab to another editor split no longer restarts marimo; the running notebook and its outputs move with the tab.

## [0.1.0] - 2026-07-22

### Added

- Opt-in telemetry to help improve the plugin: anonymous usage events (PostHog) and crash reports (Sentry). It is off by default and can be toggled at any time in **Settings → Tools → marimo**, independent of the IDE's global Data Sharing setting. See [PRIVACY.md](PRIVACY.md) for exactly what is and isn't sent.

### Changed

- Smoother "Pair with marimo" sessions when attaching an AI harness to a notebook.

### Fixed

- More reliable notebook editor startup: the plugin waits for the marimo server to be ready and cleans up the editor tab more robustly across open, reload, and close.

## [0.0.1]

First public release of marimo for JetBrains IDEs.

### Added

- Open marimo `.py` notebooks in an embedded, reactive editor; non-marimo files keep the normal editor.
- Interactive `mo.ui` widgets, SQL cells, and the variables and dependency panels.
- Runs on your project interpreter, and offers to install marimo when it's missing.
- Clear recovery actions (Retry, Install, Open as Python File) when a notebook can't start.
- "Start marimo in Sandbox" — run a notebook in an isolated uv environment.
- "Pair with marimo" — attach an AI harness to a notebook.
- Open a pairing prompt in JetBrains AI Chat when AI Assistant is available, with a copy-and-paste fallback if its prefill API is unavailable.
- "New → marimo Notebook" template and a file icon for notebook `.py` files.
- "Open as Python File" to view a notebook's raw source.
- Editing a notebook's source in another editor reloads the marimo editor automatically.
- Editor theme synced with the IDE's light/dark theme.

### Fixed

- Duplicating a notebook now opens the copy as an editor tab instead of a detached window; external links from the notebook open in your system browser.
- Wait for the marimo server to accept connections before loading the editor, fixing an intermittent `ERR_CONNECTION_REFUSED` when opening a notebook.

[Unreleased]: https://github.com/marimo-team/jetbrains-marimo/compare/0.2.0...HEAD
[0.2.0]: https://github.com/marimo-team/jetbrains-marimo/compare/0.1.2...0.2.0
[0.1.2]: https://github.com/marimo-team/jetbrains-marimo/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/marimo-team/jetbrains-marimo/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/marimo-team/jetbrains-marimo/compare/0.0.1...0.1.0
[0.0.1]: https://github.com/marimo-team/jetbrains-marimo/commits/0.0.1

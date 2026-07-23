<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# marimo-pycharm Changelog

## [Unreleased]

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
First public preview of marimo for JetBrains IDEs.

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

<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# marimo-pycharm Changelog

## [Unreleased]
### Added
- Open marimo `.py` notebooks in an embedded marimo editor; non-marimo Python files keep the normal editor.
- Run marimo on your configured project interpreter as the single source of truth, with per-interpreter
  detection of whether marimo is installed.
- Offer to install marimo into the configured interpreter when it's missing, then relaunch.
- Actionable error panel (Retry, Install marimo, Open as Python File) covering both failed server
  starts and failed editor loads, instead of a raw error page.
- "Start marimo in Sandbox" action and button that runs a notebook in an isolated uv environment.
- marimo file icon for notebook `.py` files.
- "New → marimo Notebook" action that scaffolds a runnable notebook.
- "Open as Python File" escape hatch to view a notebook's raw source.
- Editor theme synced with the IDE's light/dark theme.
- "Pair with marimo" action to attach an AI harness to a notebook.

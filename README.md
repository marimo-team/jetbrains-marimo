# marimo for PyCharm

Open and run [marimo](https://marimo.io) notebooks directly in PyCharm.

marimo notebooks are stored as plain Python files, and this plugin lets you open them in a full marimo editor without leaving your IDE — reactive cells, interactive UI widgets, package management, and more, all in a dedicated notebook tab.

> **Early preview.** This plugin is an experimental proof of concept. Expect rough edges, and please share feedback.

## Features

- **Open `.py` marimo notebooks as notebooks** — files that use marimo open in an interactive editor instead of a plain text view.
- **Full marimo experience** — reactive execution, `mo.ui` widgets, SQL cells, the variables and dependency panels, and the built-in package manager all work as they do in marimo.
- **Git-friendly** — notebooks stay as regular `.py` files, so diffs and reviews work normally.

## Requirements

- PyCharm 2026.1 or later (Community or Professional), or IntelliJ IDEA with the Python plugin
- A project interpreter with marimo installed — the plugin runs marimo on your configured interpreter,
  and offers to install it for you if it's missing
- [uv](https://docs.astral.sh/uv/) (optional) — only needed to run a notebook in an isolated sandbox

## Getting started

1. Install the plugin.
2. Open a folder containing a marimo notebook.
3. Open the notebook `.py` file — it loads in the marimo editor.
4. Edit and run cells; results update reactively.

A marimo notebook is a Python file that looks roughly like this:

```python
import marimo

app = marimo.App()

@app.cell
def _():
    import marimo as mo
    return (mo,)
```

If a `.py` file isn't a marimo notebook, it opens in the normal Python editor as usual.

## Development

This plugin is built on the [IntelliJ Platform Plugin Template][template]. You'll need a JDK 21+ (IntelliJ IDEA's bundled JetBrains Runtime works fine); Gradle is provided via the wrapper.

Run the plugin in a sandboxed IDE:

```bash
./gradlew runIde
```

This launches a separate PyCharm/IDEA instance with the plugin loaded. Open a marimo `.py` file there to try it out. (In IntelliJ IDEA you can also use the **Run Plugin** run configuration.)

Other useful tasks:

| Task | What it does |
|---|---|
| `./gradlew runIde` | Sandboxed IDE with the plugin loaded |
| `./gradlew test` | Run the test suite |
| `./gradlew buildPlugin` | Build a distributable `.zip` in `build/distributions/` |
| `./gradlew verifyPlugin` | Run the JetBrains Plugin Verifier |

The plugin runs marimo on the sandbox IDE's configured project interpreter. To try a local marimo
checkout, install it into that interpreter in editable mode (`pip install -e /path/to/marimo`) and
open a notebook.

## Feedback

This is an early proof of concept built as a side project. Bug reports and ideas are welcome via the issue tracker. Interest in marimo support for PyCharm is also tracked upstream at [JetBrains PY-78283](https://youtrack.jetbrains.com/issue/PY-78283/Add-UI-support-for-Marimo).

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template

# Contributing to marimo for PyCharm

Thanks for your interest in contributing! This plugin brings the
[marimo](https://marimo.io) notebook experience to PyCharm and other IntelliJ
Platform IDEs. Contributions of all kinds — bug reports, feature ideas, docs,
and code — are welcome.

By participating, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- For bugs, please [open an issue](https://github.com/marimo-team/marimo-pycharm/issues/new/choose)
  with steps to reproduce.
- For new features or larger changes, open an issue (or chat with us on
  [Discord](https://marimo.io/discord)) to discuss the approach before opening a
  PR — it saves everyone time.

## Development setup

You'll need a JDK 21+ (the JetBrains Runtime bundled with IntelliJ IDEA works
fine). Gradle is provided via the wrapper, so no separate install is needed.

Run the plugin in a sandboxed IDE:

```bash
./gradlew runIde
```

This launches a separate PyCharm/IDEA instance with the plugin loaded. Open a
marimo `.py` file there to try it out. (In IntelliJ IDEA you can also use the
**Run Plugin** run configuration.)

By default the plugin runs released marimo via `uvx marimo`. To test against a
local marimo checkout, set `MARIMO_CMD` before launching:

```bash
MARIMO_CMD="uv run --project /path/to/marimo marimo" ./gradlew runIde
```

## Useful Gradle tasks

| Task | What it does |
|---|---|
| `./gradlew runIde` | Sandboxed IDE with the plugin loaded |
| `./gradlew test` | Run the test suite |
| `./gradlew check` | Run tests plus verification checks |
| `./gradlew buildPlugin` | Build a distributable `.zip` in `build/distributions/` |
| `./gradlew verifyPlugin` | Run the JetBrains Plugin Verifier |

## Submitting a pull request

1. Fork the repo and create a branch for your change.
2. Make your change and add tests where it makes sense.
3. Make sure `./gradlew check` passes locally.
4. Write a clear PR description and fill out the PR template. Use
   [conventional commit](https://www.conventionalcommits.org/) style for commit
   messages (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
5. Open the PR against `main`.

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE).

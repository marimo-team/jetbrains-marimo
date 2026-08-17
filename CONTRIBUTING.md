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
   messages (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`,
   `release:`).
5. Apply a category label (`enhancement`, `bug`, `documentation`, `internal`,
   `preview`, `dependencies`, `breaking`, or `other`). CI requires one — release
   notes are grouped by it, so an unlabeled pull request cannot be placed. Some
   labels are applied automatically from the files you changed.
6. Open the PR against `main`.

Do not add an entry to `CHANGELOG.md`. Release notes are written once per
release, from the labels above; see [Releasing](#releasing).

## Releasing

Release notes are generated from merged pull requests, so individual pull
requests never edit `CHANGELOG.md` — they carry a category label instead.
Maintainers prepare a release locally, then run the **Release** workflow by hand
to publish it.

This section is the runbook. Follow it as written, whether you are a person or an
AI assistant.

Requires [uv](https://docs.astral.sh/uv/) and the
[`gh` CLI](https://cli.github.com/).

1. **Review what landed.**

   ```bash
   uv run scripts/release_changes.py
   ```

   The report groups merged pull requests by label into the changelog sections
   they belong in, and lists anything with no destination label under **Needs
   judgment**. Fix the label on the pull request and re-run until that section is
   empty — the label is what the next release reads, so correcting it there is
   worth more than deciding by hand here.

2. **Bump `version`** in `gradle.properties`.

3. **Write the entries** under the `## [Unreleased]` heading in `CHANGELOG.md`,
   grouped `### Added` / `### Changed` / `### Fixed` per the report's sections,
   following [Keep a Changelog](https://keepachangelog.com). If the
   `## [Unreleased]` heading is missing, add it — `patchChangelog` recreates it
   either way, but you need somewhere to write the entries now.

   This is shipped copy, not a summary of the diff: `build.gradle.kts` renders
   the matching section into the plugin's `changeNotes`, which is the "What's
   new" panel on the Marketplace listing. For each entry, read the pull request
   body for intent and describe the change from the user's side — what they can
   now do, or what stopped going wrong. Name the UI surface (menu, tab, setting)
   where it helps. Do not paste commit subjects.

4. **Patch the changelog.**

   ```bash
   ./gradlew patchChangelog
   ```

   This inserts `## [<version>] - <date>` below `## [Unreleased]`, so the entries
   you just wrote end up under the version and an empty `[Unreleased]` is left at
   the top. It also adds the version's compare link and repoints
   `[Unreleased]:` at the new version.

5. **Open a pull request titled `release: <version>`** with the version bump and
   the changelog, and get it merged. This is the review gate for the notes.

6. **Dry-run the release.** In the Actions tab, run the **Release** workflow
   against `main` with **Dry run** left enabled. It builds the plugin and prints
   the exact release notes to the run summary without publishing anything. Read
   them. Nothing is tagged and no approval is needed.

7. **Publish.** Run **Release** again with **Dry run** turned off. The run pauses
   for approval, because the publishing job is gated by the `release`
   environment's protection rules. Approve it, and the same run signs and uploads
   the plugin to the JetBrains Marketplace, then creates the tag, the GitHub
   release, and the attached plugin zip in one final step.

There is no third step and no tag to push: **CI creates the tag**, at the end of
the run that publishes. The approval is a pause inside that run, not a separate
dispatch. Never push a version tag by hand.

Because the tag comes last, a failed publish leaves nothing to clean up — no
orphan tag, no release announcing a version that did not ship. Fix the cause and
run the workflow again. The workflow also refuses to start when
`gradle.properties` names a version that already has a release, which is what
catches a forgotten version bump.

### Do not

- Hand-write a `## [<version>]` heading in `CHANGELOG.md`. Step 4 owns that.
- Push a version tag. Step 7 owns that.
- Edit the notes in the GitHub release UI and expect them to persist.
  `CHANGELOG.md` in the repo is the only source of truth — corrections go through
  a pull request.
- Add changelog entries in feature pull requests. Labels carry the
  categorization; the prose is written once, here.

`CHANGELOG.md` in the repo is the only source of truth for release notes.
Editing the notes in the GitHub release UI does not flow back — corrections go
through a pull request.

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE).

# Contributing to marimo for PyCharm

Thanks for your interest in contributing! This plugin brings the
[marimo](https://marimo.io) notebook experience to PyCharm. Contributions of
all kinds — bug reports, feature ideas, docs, and code — are welcome.

By participating, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- For bugs, please [open an issue](https://github.com/marimo-team/marimo-pycharm/issues/new/choose)
  with steps to reproduce.
- For new features or larger changes, open an issue (or chat with us on
  [Discord](https://marimo.io/discord)) to discuss the approach before opening a
  PR — it saves everyone time.

## Development setup

You'll need a JDK 21+ (the JetBrains Runtime bundled with PyCharm works fine).
Gradle is provided via the wrapper, so no separate install is needed.

Run the plugin in a sandboxed IDE:

```bash
./gradlew runIde
```

This launches a separate PyCharm instance with the plugin loaded. Open a marimo
`.py` file there to try it out.

Local and CI builds use no-op telemetry sinks even if you opt in, so they do not
write to PostHog or Sentry. A production artifact (`-Ptelemetry.env=production`)
uses the real backends. To send from a development build while debugging
telemetry, pass `-Ptelemetry.live=true` (events still carry `environment=development`):

```bash
./gradlew runIde -Ptelemetry.live=true
```

Normal notebook launches use the configured project interpreter and run
`<python> -m marimo`. The **Start marimo in Sandbox** action instead uses uv to
run marimo in an isolated environment.

To test against a local marimo checkout, select the environment you want as the
sandbox IDE's project interpreter, install the checkout into that environment
in editable mode (`pip install -e /path/to/marimo`), and open a notebook.

Configure Git to ignore format-only revisions in blame output:

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

This setting keeps `git blame` focused on changes that affect behavior.

### Pre-commit hooks

Install the Git hook to run fast quality checks before each commit:

```bash
uvx pre-commit install
```

Run all hooks before you open a pull request:

```bash
uvx pre-commit run --all-files
```

CI runs the required checks and remains the authority for pull requests.

See the [common command reference](AGENTS.md#common-commands) for Gradle and
repository verification commands.

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

The workflow refuses to start when `gradle.properties` names a version that
already has a GitHub release, which is what catches a forgotten version bump.
It also queues behind any in-flight Release run and will not cancel that run
(`cancel-in-progress: false`), so a publish that has already reached Marketplace
is not killed mid-tag.

If **Marketplace accepted the upload** and **tagging or the GitHub release
failed**, do not dispatch Release again. A second publish uploads the same
Marketplace version and can fail while leaving the tag still missing.

Resume by creating only the GitHub release from this failed run:

1. Confirm the version on
   [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/index?xmlId=io.marimo.notebook).
2. From the failed Actions run, download the `signed-plugin-archive` artifact
   and the `release-note` artifact.
3. Create the tag, GitHub release, and asset with the same commit the run used:

   ```bash
   gh release create "<version>" \
     --repo marimo-team/jetbrains-marimo \
     --target "<commit sha from the failed run>" \
     --title "<version>" \
     --notes-file release_note.txt \
     <signed zip from signed-plugin-archive>
   ```

If Marketplace did **not** accept the upload, fix the cause and dispatch
Release again with **Dry run** turned off. That case still has no tag to clean
up.

### Do not

- Hand-write a `## [<version>]` heading in `CHANGELOG.md`. Step 4 owns that.
- Push a version tag, except the resume `gh release create` above when
  Marketplace already accepted the version and tagging failed.
- Re-dispatch **Release** with Dry run off after Marketplace has accepted that
  version. Resume tagging from `signed-plugin-archive` instead.
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

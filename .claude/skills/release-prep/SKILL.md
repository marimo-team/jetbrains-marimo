---
name: release-prep
description: Prepare a marimo-for-JetBrains release — generate CHANGELOG.md entries from merged pull requests, bump the version, and publish. Use when cutting a new plugin release.
---

# Release prep

The runbook lives in [CONTRIBUTING.md](../../../CONTRIBUTING.md) under
"Releasing", so that people and any AI assistant follow one set of steps and
there is a single place to keep current.

Read that section and follow it exactly, including its "Do not" list.

Start with:

```bash
uv run scripts/release_changes.py
```

Two points that decide whether the result is any good:

- **Entries under "Needs judgment" mean a pull request has no destination
  label.** Fix the label on the pull request and re-run. Do not silently decide
  where it belongs — the label is what the next release will read.
- **`CHANGELOG.md` is shipped copy,** rendered into the plugin's Marketplace
  "What's new" panel. Describe each change from the user's side. Never paste a
  commit subject.

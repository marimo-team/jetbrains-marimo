# Issue tracker: Linear (internal) + GitHub Issues (external intake)

Work on this repo is tracked in Linear: workspace `marimo`, team `Engineering`, issue key `MO`. The public GitHub repo collects external reports only. GitHub issues sync into Linear automatically, and triage happens in Linear.

## Where to act

- **Internal work**: file directly in Linear, team `Engineering`. Do not open a GitHub issue for internal work.
- **External reports**: arrive as GitHub issues, sync into Linear, and land in the `Triage` status (synced issues carry labels like `From User`).
- **Repo scope**: plugin issues carry the label `repo: jetbrains-marimo`. Add it when you file, filter by it when you list.

## Conventions (Linear MCP tools)

- **Create an issue**: `save_issue` with team `Engineering`, a non-empty title, and the label `repo: jetbrains-marimo`. Draft the issue, show it to the user, and save only after an explicit yes.
- **Read an issue**: `get_issue` with the `MO-<n>` identifier; `list_comments` for the thread.
- **List plugin issues**: `list_issues` with team `Engineering` and label `repo: jetbrains-marimo`, plus a state filter.
- **Comment**: `save_comment`, draft-first as above.
- **Statuses**: `Triage`, `Backlog`, `Todo`, `Up Next`, `In Progress`, `Blocked`, `In Review`, `Done`, `Canceled`, `Duplicate`.

## When a skill says "publish to the issue tracker"

Draft a Linear issue for team `Engineering` with the label `repo: jetbrains-marimo`. Save it after user approval.

## When a skill says "fetch the relevant ticket"

Fetch the Linear issue `MO-<n>` with its comments. If the reference is a GitHub issue number, read the GitHub issue and follow its Linear link, or search Linear for the synced copy.

## GitHub side (external intake only)

- Read external reports with `gh issue view <number> --comments`.
- Reply to reporters with `gh issue comment` only after user approval.
- Do not create GitHub issues for internal work, and do not manage triage state on GitHub. GitHub labels are release-note categories for PRs (see `CONTRIBUTING.md`).
- **PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

## Wayfinding operations

Used by `/wayfinder`. The **map** is a Linear issue; **child** tickets are its sub-issues.

- **Map**: one Linear issue in team `Engineering`, holding the Notes / Decisions-so-far / Fog body.
- **Child ticket**: a sub-issue of the map (`parentId`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: Linear issue relations (`blockedBy`). A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children, drop any with an open blocker or an assignee; first in map order wins.
- **Claim**: assign the issue to yourself, with user approval.
- **Resolve**: comment the answer, set the status to `Done`, then append a context pointer to the map's Decisions-so-far.

# Triage Labels

The skills speak in terms of five canonical triage roles. Triage for this repo happens in Linear (team `Engineering`, key `MO`), not in GitHub labels. Two roles map to Linear statuses, three map to Linear labels.

| Role in mattpocock/skills | In Linear                | Kind   |
| ------------------------- | ------------------------ | ------ |
| `needs-triage`            | status `Triage`          | status |
| `needs-info`              | label `needs discussion` | label  |
| `ready-for-agent`         | label `ready-for-agent`  | label  |
| `ready-for-human`         | label `ready-for-human`  | label  |
| `wontfix`                 | status `Canceled`        | status |

When a skill mentions a role (for example "apply the AFK-ready triage label"), use the corresponding Linear status or label from this table.

Notes:

- The labels `ready-for-agent` and `ready-for-human` do not exist in Linear yet. If a label apply fails, ask a maintainer to create the label in team `Engineering` first.
- A `wontfix` label also exists in Linear (synced from GitHub). The status `Canceled` is the signal; the label is optional.
- Do not manage triage labels on GitHub. External GitHub issues sync into Linear, and triage happens there.

Edit this table when the Linear vocabulary changes.

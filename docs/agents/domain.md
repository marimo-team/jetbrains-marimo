# Domain Docs

How the engineering skills consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`ARCHITECTURE.md`** at the repo root — the current architecture map (layers, leases, lifecycle, threading). This file exists today.
- **`CONTEXT.md`** at the repo root — the domain glossary.
- **`docs/adr/`** — read the ADRs that touch the area you are about to work in.

If `CONTEXT.md` or `docs/adr/` do not exist, **proceed silently**. Do not flag their absence and do not suggest creating them upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved.

## File structure

This is a single-context repo. The tree shows the target layout. The ADR file names are examples, not real files.

```
/
├── ARCHITECTURE.md
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-lease-based-sessions.md   (example)
│   └── 0002-uv-first-launch.md        (example)
└── src/main/kotlin/io/marimo/notebook/
```

If this repo ever splits into multiple bounded contexts, switch to a root `CONTEXT-MAP.md` that points at one `CONTEXT.md` per context.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Do not drift to synonyms that the glossary avoids.

If the concept you need is not in the glossary yet, that is a signal. Either you invent language the project does not use (reconsider), or there is a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface the conflict instead of a silent override:

> _Contradicts ADR-0001 (lease-based sessions), but worth reopening because…_

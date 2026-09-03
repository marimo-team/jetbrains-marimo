# Reuse marimo data-source discovery

marimo 0.23.16 introduced data-source discovery from vendor environment variables. The plugin supplies these variables instead of controlling the marimo frontend or editing notebook files.

## Consequences

- Current marimo versions can offer one Quick Add source for each database family.
- Only the family default enters the notebook process environment.
- Older marimo versions can still read the environment variables directly.

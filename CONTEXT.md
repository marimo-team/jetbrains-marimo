# marimo for PyCharm

This context covers local marimo notebook sessions inside PyCharm. It also covers data-source sharing between PyCharm and these sessions.

## Language

**Notebook session**:
The lifetime of one local marimo server for one notebook.
_Avoid_: server instance, notebook server

**IDE data source**:
A Database Tools definition for a database endpoint and its authentication identity. Its password remains in the IDE credential store until use.
_Avoid_: database configuration, connection profile

**Exposure**:
A decision to share one IDE data source with one notebook. Only the family default enters the notebook process environment.
_Avoid_: synchronization, global sharing

**Vendor variables**:
Standard environment variables, such as `PGHOST`, that marimo data-source discovery recognizes. One family default owns these names in each notebook process.
_Avoid_: ambient variables

**Family default**:
The exposed IDE data source that owns the vendor variables for one database family.
_Avoid_: family primary

**Launch environment**:
The environment map for a new marimo process. This map does not change during the process lifetime.

**Detected data source**:
A marimo suggestion that comes from the launch environment. Quick Add can insert its SQL-engine cell.
_Avoid_: plugin-created cell

**Stale launch environment**:
A live notebook session whose launch environment does not match its current exposures or IDE data sources. A restart repairs this state.
_Avoid_: dirty session, out-of-sync session

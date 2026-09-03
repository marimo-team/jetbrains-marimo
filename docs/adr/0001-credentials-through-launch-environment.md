# Pass credentials through the process environment at launch

Notebook code needs credentials that the IDE stores in PasswordSafe. The plugin adds these credentials to the notebook process environment at launch. It does not use dotenv files, command arguments, a credential broker, or a protocol proxy.

## Consequences

- Sharing is explicit for each notebook.
- A credential or sharing change requires a notebook restart.
- The plugin does not write credentials to project files or logs.
- Notebook code and processes with environment-inspection access can read shared credentials.

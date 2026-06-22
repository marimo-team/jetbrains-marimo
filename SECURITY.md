# Security Policy

This plugin embeds the [marimo](https://marimo.io) editor inside PyCharm by
launching a local marimo server and rendering it in an embedded browser. For
marimo's overall security model, see the
[marimo Security documentation](https://docs.marimo.io/security/).

## Supported Versions

We provide security patches for the latest released version only. We encourage
all users to stay on the latest version.

## Reporting a Vulnerability

To report a security vulnerability, please
[draft an advisory through GitHub](https://github.com/marimo-team/marimo-pycharm/security/advisories/new),
or email the marimo team; security [at] marimo [dot] io.

Please include:
- A description of the vulnerability and its potential impact
- Steps to reproduce or a proof-of-concept
- Any suggested mitigations if known

### What to Expect

- **Acknowledgement**: We will respond within 3 business days to confirm receipt
- **Triage**: We will assess severity and scope within 7 days
- **Patch & disclosure**: We aim to release a fix and publish an advisory
  simultaneously, typically within 90 days of the initial report

We will keep you informed throughout the process and credit you in the advisory
unless you prefer to remain anonymous.

## Known Considerations

The plugin launches the marimo server bound to `127.0.0.1` (localhost) with
authentication disabled (`--no-token`). The server is only reachable from the
local machine. Do not expose this port to other hosts.

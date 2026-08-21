/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

/** Stable identity for one notebook session, even when its file is renamed or moved. */
@JvmInline value class SessionId(val value: Long)

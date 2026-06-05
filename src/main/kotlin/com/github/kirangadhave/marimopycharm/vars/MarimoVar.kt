package com.github.kirangadhave.marimopycharm.vars

import com.google.gson.JsonParser

data class MarimoVar(val name: String, val type: String, val value: String)

object VarsIntrospection {
    /**
     * Read-only: reprs the notebook's cell-defined variables. Runs in the scratchpad's COPY of the kernel
     * globals (marimo's runtime copies self.globals before executing scratchpad code), so it cannot define
     * or clobber notebook state.
     *
     * marimo's runtime graph knows exactly which names the user's cells define, so we intersect globals()
     * with that set — this drops injected builtins (print, input, importlib helpers) that otherwise pollute
     * a raw globals() dump. If the internal API moves in a future marimo, we fall back to a heuristic that
     * skips dunders, builtins, and modules. Either way modules (e.g. `import marimo as mo`) are excluded —
     * they aren't useful to inspect as variables. Emits a single JSON array on stdout.
     */
    val SCRIPT: String = """
        import json as _mo_json
        import builtins as _mo_builtins
        import types as _mo_types
        try:
            from marimo._runtime.context import get_context as _mo_get_context
            _mo_defined = set(_mo_get_context().graph.definitions.keys())
        except Exception:
            _mo_defined = None
        _mo_g = globals()
        _mo_builtin_names = set(dir(_mo_builtins))
        _mo_names = _mo_defined if _mo_defined is not None else set(_mo_g.keys())
        _mo_out = []
        for _mo_k in sorted(_mo_names):
            if _mo_k.startswith("_") or _mo_k not in _mo_g:
                continue
            _mo_v = _mo_g[_mo_k]
            if isinstance(_mo_v, _mo_types.ModuleType):
                continue
            if _mo_defined is None and _mo_k in _mo_builtin_names:
                continue
            try:
                _mo_t = type(_mo_v).__name__
            except Exception:
                _mo_t = "?"
            try:
                _mo_r = repr(_mo_v)
            except Exception:
                _mo_r = "<unrepresentable>"
            if len(_mo_r) > 500:
                _mo_r = _mo_r[:500] + "…"
            _mo_out.append({"name": _mo_k, "type": _mo_t, "value": _mo_r})
        print(_mo_json.dumps(_mo_out))
    """.trimIndent()

    fun parse(stdout: String): List<MarimoVar> {
        val trimmed = stdout.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = runCatching { JsonParser.parseString(trimmed).asJsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
            MarimoVar(
                name = obj.get("name").asString,
                type = obj.get("type").asString,
                value = obj.get("value").asString,
            )
        }
    }
}

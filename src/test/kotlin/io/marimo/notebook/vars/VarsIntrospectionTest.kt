/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.vars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VarsIntrospectionTest {
    @Test fun parsesVarArray() {
        val json = """[{"name":"x","type":"int","value":"41"},{"name":"df","type":"DataFrame","value":"<frame>"}]"""
        val vars = VarsIntrospection.parse(json)
        assertEquals(2, vars.size)
        assertEquals(MarimoVar("x", "int", "41"), vars[0])
        assertEquals("DataFrame", vars[1].type)
    }

    @Test fun blankStdoutYieldsEmptyList() {
        assertTrue(VarsIntrospection.parse("   ").isEmpty())
    }

    @Test fun malformedStdoutYieldsEmptyList() {
        assertTrue(VarsIntrospection.parse("not json").isEmpty())
    }
}

/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarimoPopupTest {
    @Test fun classifiesFileDeepLinkAsNotebook() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/?file=%2FUsers%2Fme%2F_notebook.py")
        assertEquals(MarimoPopup.Notebook("/Users/me/_notebook.py"), popup)
    }

    @Test fun decodesSpacesAndSpecialCharsInPath() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/?file=%2FUsers%2Fme%2Fmy%20copy%20(1).py")
        assertEquals(MarimoPopup.Notebook("/Users/me/my copy (1).py"), popup)
    }

    @Test fun findsFileParamAmongOtherParams() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/?foo=1&file=%2Ftmp%2Fa.py&bar=2")
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), popup)
    }

    @Test fun ignoresQueryFragment() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/?file=%2Ftmp%2Fa.py#cell-3")
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), popup)
    }

    @Test fun classifiesExternalUrlWithoutFileParam() {
        val popup = classifyMarimoPopup("https://docs.marimo.io/guides")
        assertEquals(MarimoPopup.External("https://docs.marimo.io/guides"), popup)
    }

    @Test fun classifiesServerRootWithoutFileParamAsExternal() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/")
        assertEquals(MarimoPopup.External("http://127.0.0.1:5123/"), popup)
    }

    @Test fun ignoresBlankAndAboutBlankTargets() {
        assertNull(classifyMarimoPopup(null))
        assertNull(classifyMarimoPopup(""))
        assertNull(classifyMarimoPopup("   "))
        assertNull(classifyMarimoPopup("about:blank"))
    }

    @Test fun treatsEmptyFileParamAsExternal() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/?file=")
        assertEquals(MarimoPopup.External("http://127.0.0.1:5123/?file="), popup)
    }
}

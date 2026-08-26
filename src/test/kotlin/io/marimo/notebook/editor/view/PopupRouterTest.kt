/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.editor.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PopupRouterTest {
    private val origin = "http://127.0.0.1:5123"

    @Test
    fun classifiesFileDeepLinkAsNotebook() {
        val popup =
            classifyMarimoPopup("http://127.0.0.1:5123/?file=%2FUsers%2Fme%2F_notebook.py", origin)
        assertEquals(MarimoPopup.Notebook("/Users/me/_notebook.py"), popup)
    }

    @Test
    fun decodesSpacesAndSpecialCharsInPath() {
        val popup =
            classifyMarimoPopup(
                "http://127.0.0.1:5123/?file=%2FUsers%2Fme%2Fmy%20copy%20(1).py",
                origin,
            )
        assertEquals(MarimoPopup.Notebook("/Users/me/my copy (1).py"), popup)
    }

    @Test
    fun findsFileParamAmongOtherParams() {
        val popup =
            classifyMarimoPopup("http://127.0.0.1:5123/?foo=1&file=%2Ftmp%2Fa.py&bar=2", origin)
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), popup)
    }

    @Test
    fun ignoresQueryFragment() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/?file=%2Ftmp%2Fa.py#cell-3", origin)
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), popup)
    }

    @Test
    fun doesNotReadFileParamFromFragment() {
        val url = "http://127.0.0.1:5123/#cell?file=%2Ftmp%2Fa.py"
        assertEquals(MarimoPopup.External(url), classifyMarimoPopup(url, origin))
    }

    @Test
    fun classifiesExternalUrlWithoutFileParam() {
        val popup = classifyMarimoPopup("https://docs.marimo.io/guides", origin)
        assertEquals(MarimoPopup.External("https://docs.marimo.io/guides"), popup)
    }

    @Test
    fun classifiesServerRootWithoutFileParamAsExternal() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/", origin)
        assertEquals(MarimoPopup.External("http://127.0.0.1:5123/"), popup)
    }

    @Test
    fun ignoresBlankAndAboutBlankTargets() {
        assertNull(classifyMarimoPopup(null, origin))
        assertNull(classifyMarimoPopup("", origin))
        assertNull(classifyMarimoPopup("   ", origin))
        assertNull(classifyMarimoPopup("about:blank", origin))
    }

    @Test
    fun treatsEmptyFileParamAsExternal() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/?file=", origin)
        assertEquals(MarimoPopup.External("http://127.0.0.1:5123/?file="), popup)
    }

    @Test
    fun classifiesRelativeFileDeepLinkAsNotebook() {
        val popup = classifyMarimoPopup("?file=%2Ftmp%2Fa.py", origin)
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), popup)
    }

    @Test
    fun classifiesLocalhostFileDeepLinkAsNotebook() {
        val localhostOrigin = "http://localhost:5123"
        val popup =
            classifyMarimoPopup("http://localhost:5123/?file=%2Ftmp%2Fa.py", localhostOrigin)
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), popup)
    }

    @Test
    fun classifiesIpv6LoopbackFileDeepLinkAsNotebook() {
        val ipv6Origin = serverOrigin("http://[::1]:5123/")!!
        assertEquals("http://[::1]:5123", ipv6Origin)
        val popup = classifyMarimoPopup("http://[::1]:5123/?file=%2Ftmp%2Fa.py", ipv6Origin)
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), popup)
    }

    @Test
    fun classifiesProtocolRelativeIpv6DeepLinkAsNotebook() {
        val ipv6Origin = "http://[::1]:5123"
        val url = "//[::1]:5123/?file=%2Ftmp%2Fa.py"
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), classifyMarimoPopup(url, ipv6Origin))
    }

    @Test
    fun bareFileParamDoesNotShadowFileWithValue() {
        val popup = classifyMarimoPopup("http://127.0.0.1:5123/?file&file=%2Ftmp%2Fa.py", origin)
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), popup)
    }

    @Test
    fun bareFileParamWithoutValueIsExternal() {
        val url = "http://127.0.0.1:5123/?file"
        assertEquals(MarimoPopup.External(url), classifyMarimoPopup(url, origin))
    }

    @Test
    fun doesNotOpenLocalPathFromExternalHost() {
        val url = "https://evil.example.com/?file=%2Fetc%2Fpasswd"
        assertEquals(MarimoPopup.External(url), classifyMarimoPopup(url, origin))
    }

    @Test
    fun fileDeepLinkOnOtherLoopbackPortIsExternal() {
        val url = "http://127.0.0.1:9999/?file=%2Ftmp%2Fa.py"
        assertEquals(MarimoPopup.External(url), classifyMarimoPopup(url, origin))
    }

    @Test
    fun fileDeepLinkWithWrongSchemeIsExternal() {
        val url = "https://127.0.0.1:5123/?file=%2Ftmp%2Fa.py"
        assertEquals(MarimoPopup.External(url), classifyMarimoPopup(url, origin))
    }

    @Test
    fun relativeFileDeepLinkWithoutActiveOriginIsExternal() {
        val url = "?file=%2Ftmp%2Fa.py"
        assertEquals(MarimoPopup.External(url), classifyMarimoPopup(url, expectedOrigin = null))
    }

    @Test
    fun protocolRelativeCrossOriginFileDeepLinkIsExternal() {
        val url = "//evil.example/?file=%2Ftmp%2Fa.py"
        assertEquals(MarimoPopup.External(url), classifyMarimoPopup(url, origin))
    }

    @Test
    fun protocolRelativeSameOriginFileDeepLinkIsNotebook() {
        val url = "//127.0.0.1:5123/?file=%2Ftmp%2Fa.py"
        assertEquals(MarimoPopup.Notebook("/tmp/a.py"), classifyMarimoPopup(url, origin))
    }
}

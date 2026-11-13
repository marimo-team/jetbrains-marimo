/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.session

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.marimo.notebook.launch.LoopbackHttpServer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class PageConfigFetchTest : BasePlatformTestCase() {
    private val page =
        """<marimo-user-config data-config="{&quot;display&quot;:{&quot;theme&quot;:&quot;dark&quot;}}"></marimo-user-config>"""

    fun testQueryTokenBecomesBearerWithoutTheLoginRedirect() {
        val requests = CopyOnWriteArrayList<List<String>>()
        LoopbackHttpServer { socket ->
            val reader = socket.getInputStream().bufferedReader()
            val headers = generateSequence {
                reader.readLine()?.takeIf { it.isNotEmpty() }
            }
                .toList()
            requests.add(headers)
            val authorized = headers.any {
                it.equals("Authorization: Bearer test+token", ignoreCase = true)
            }
            val response =
                if (authorized) {
                    "HTTP/1.1 200 OK\r\nContent-Length: ${page.length}\r\nConnection: close\r\n\r\n$page"
                } else {
                    "HTTP/1.1 303 See Other\r\nLocation: /auth/login\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                }
            socket.getOutputStream().write(response.toByteArray())
        }
            .use { server ->
                assertEquals(
                    "dark",
                    PageConfigReader.fetchDisplayTheme(
                        "http://127.0.0.1:${server.port}/?file=notebook%20name.py&access_token=test%2Btoken"
                    ),
                )
            }
        assertEquals(1, requests.size)
        assertEquals("GET /?file=notebook%20name.py HTTP/1.1", requests.single().first())
    }

    fun testUnauthenticatedServerNeedsNoAuthorizationHeader() {
        val requests = CopyOnWriteArrayList<List<String>>()
        LoopbackHttpServer { socket ->
            val reader = socket.getInputStream().bufferedReader()
            requests.add(
                generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
            )
            socket
                .getOutputStream()
                .write(
                    "HTTP/1.1 200 OK\r\nContent-Length: ${page.length}\r\nConnection: close\r\n\r\n$page"
                        .toByteArray()
                )
        }
            .use { server ->
                assertEquals(
                    "dark",
                    PageConfigReader.fetchDisplayTheme("http://127.0.0.1:${server.port}/"),
                )
            }
        assertFalse(requests.single().any { it.startsWith("Authorization:", ignoreCase = true) })
    }

    fun testAuthorizationIsNotForwardedToARedirect() {
        val redirectedRequests = AtomicInteger()
        LoopbackHttpServer { socket ->
            redirectedRequests.incrementAndGet()
            socket
                .getOutputStream()
                .write(
                    "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .toByteArray()
                )
        }
            .use { destination ->
                LoopbackHttpServer { socket ->
                    socket
                        .getOutputStream()
                        .write(
                            ("HTTP/1.1 303 See Other\r\nLocation: http://127.0.0.1:${destination.port}/\r\n" +
                                    "Content-Length: ${page.length}\r\nConnection: close\r\n\r\n$page")
                                .toByteArray()
                        )
                }
                    .use { source ->
                        PageConfigReader.fetchDisplayTheme(
                            "http://127.0.0.1:${source.port}/?access_token=test-token"
                        )
                    }
            }
        assertEquals("the config request must not follow redirects", 0, redirectedRequests.get())
    }
}

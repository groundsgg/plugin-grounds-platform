package gg.grounds.platform.whitelist

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WhitelistApiClientTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun client(token: String? = "tok-abc"): WhitelistApiClient =
        WhitelistApiClient(
            forgeUrl = server.url("/").toString().trimEnd('/'),
            projectId = "p-1",
            tokenProvider = { token },
        )

    @Test
    fun `parses forge whitelist response and drops auxiliary fields`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "items": [
                        {
                          "mcUuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
                          "mcUsername": "Notch",
                          "addedAt": "2026-05-05T10:00:00Z",
                          "addedBy": "alice"
                        }
                      ]
                    }
                    """
                        .trimIndent()
                )
        )
        val entries = client().fetch()
        assertEquals(
            listOf(WhitelistEntry("069a79f4-44e9-4726-a5be-fca90e38aaf5", "Notch")),
            entries,
        )
    }

    @Test
    fun `sends the forge token as a Bearer auth header`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody("""{"items":[]}""")
        )
        client(token = "gnds_xxx_yyy").fetch()
        val request = server.takeRequest()
        assertEquals("Bearer gnds_xxx_yyy", request.getHeader("Authorization"))
        assertTrue(request.path?.endsWith("/v1/projects/p-1/whitelist") == true)
    }

    @Test
    fun `throws when forge returns non-2xx`() {
        server.enqueue(MockResponse().setResponseCode(503))
        val ex = assertThrows<RuntimeException> { client().fetch() }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("503"))
    }

    @Test
    fun `throws when token is unset (refuses to call forge unauthenticated)`() {
        val noTokenClient =
            WhitelistApiClient(
                forgeUrl = server.url("/").toString().trimEnd('/'),
                projectId = "p-1",
                tokenProvider = { null },
            )
        assertThrows<IllegalStateException> { noTokenClient.fetch() }
        assertEquals(0, server.requestCount, "fetch should refuse before any HTTP call")
    }
}

package gg.grounds.platform.commands

import java.net.http.HttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlatformCommandClientTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lease sends deployment identity and parses empty command`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"command":null}"""))

        val client = client()
        val lease = client.leaseCommand()

        assertNull(lease)
        val request = server.takeRequest()
        assertEquals(
            "/v1/platform/deployments/survival-paper/commands/lease?projectId=project-1&pushId=push-9&waitMs=25000",
            request.path,
        )
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
    }

    @Test
    fun `lease response parses command payload`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "command": {
                        "id": "command-1",
                        "command": "say hello",
                        "queuedAt": "2026-05-19T10:00:00.000Z",
                        "leaseToken": "lease-1"
                      }
                    }
                    """
                        .trimIndent()
                )
        )

        val lease = client().leaseCommand()

        assertEquals(
            PlatformCommandLease(
                id = "command-1",
                command = "say hello",
                queuedAt = "2026-05-19T10:00:00.000Z",
                leaseToken = "lease-1",
            ),
            lease,
        )
    }

    @Test
    fun `result posts lease token and status`() {
        server.enqueue(MockResponse().setResponseCode(204))

        client()
            .postResult(
                commandId = "command-1",
                result =
                    PlatformCommandResult(
                        leaseToken = "lease-1",
                        status = PlatformCommandStatus.EXECUTED,
                        message = "Command executed",
                    ),
            )

        val request = server.takeRequest()
        assertEquals(
            "/v1/platform/deployments/survival-paper/commands/command-1/result",
            request.path,
        )
        assertEquals(
            """{"leaseToken":"lease-1","status":"executed","message":"Command executed"}""",
            request.body.readUtf8(),
        )
    }

    private fun client(): PlatformCommandClient =
        PlatformCommandClient(
            env =
                PlatformCommandEnv.Enabled(
                    forgeUrl = server.url("/").toString().trimEnd('/'),
                    projectId = "project-1",
                    appName = "survival",
                    deploymentName = "survival-paper",
                    pushId = "push-9",
                    token = "token-1",
                ),
            httpClient = HttpClient.newBuilder().build(),
        )
}

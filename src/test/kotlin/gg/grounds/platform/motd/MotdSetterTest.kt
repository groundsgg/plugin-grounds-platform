package gg.grounds.platform.motd

import org.bukkit.Server
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MotdSetterTest {

    private fun captureMotd(projectName: String, pushId: String? = null): String {
        val server: Server = mock()
        MotdSetter(server).apply(projectName, pushId)
        val captor = argumentCaptor<String>()
        verify(server).setMotd(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `formats project name and short push id on line one with attribution on line two`() {
        val motd = captureMotd("Demo Project", "abc12345-6789-0abc-def0-123456789abc")
        assertEquals("§fDemo Project §8abc12345\n§8powered by Grounds Developer Platform", motd)
    }

    @Test
    fun `omits push-id suffix when pushId is null`() {
        val motd = captureMotd("Demo Project", pushId = null)
        assertEquals("§fDemo Project\n§8powered by Grounds Developer Platform", motd)
    }

    @Test
    fun `omits push-id suffix when pushId is blank after dash-strip`() {
        val motd = captureMotd("Demo Project", pushId = "----")
        assertEquals("§fDemo Project\n§8powered by Grounds Developer Platform", motd)
    }

    @Test
    fun `truncates short pushId without underflow`() {
        val motd = captureMotd("P", pushId = "abc")
        assertEquals("§fP §8abc\n§8powered by Grounds Developer Platform", motd)
    }
}

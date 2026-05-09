package gg.grounds.platform.motd

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Server
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MotdSetterTest {

    private fun captureMotd(projectName: String, pushId: String? = null): Component {
        val server: Server = mock()
        MotdSetter(server).apply(projectName, pushId)
        val captor = argumentCaptor<Component>()
        verify(server).motd(captor.capture())
        return captor.firstValue
    }

    private fun expectedMotd(projectName: String, shortPushId: String? = null): Component {
        val builder = Component.text().append(Component.text(projectName, NamedTextColor.WHITE))
        if (shortPushId != null) {
            builder.append(Component.text(" $shortPushId", NamedTextColor.DARK_GRAY))
        }
        return builder
            .append(Component.newline())
            .append(
                Component.text("powered by Grounds Developer Platform", NamedTextColor.DARK_GRAY)
            )
            .build()
    }

    @Test
    fun `formats project name and short push id on line one with attribution on line two`() {
        val motd = captureMotd("Demo Project", "abc12345-6789-0abc-def0-123456789abc")
        assertEquals(expectedMotd("Demo Project", "abc12345"), motd)
    }

    @Test
    fun `omits push-id suffix when pushId is null`() {
        val motd = captureMotd("Demo Project", pushId = null)
        assertEquals(expectedMotd("Demo Project"), motd)
    }

    @Test
    fun `omits push-id suffix when pushId is blank after dash-strip`() {
        val motd = captureMotd("Demo Project", pushId = "----")
        assertEquals(expectedMotd("Demo Project"), motd)
    }

    @Test
    fun `truncates short pushId without underflow`() {
        val motd = captureMotd("P", pushId = "abc")
        assertEquals(expectedMotd("P", "abc"), motd)
    }
}

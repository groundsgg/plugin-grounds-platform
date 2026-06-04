package gg.grounds.platform.motd

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Server

/**
 * Sets the server MOTD to a project-aware Adventure component at startup:
 * - Line one: `<projectName> <short pushId>`
 * - Line two: `powered by Grounds Developer Platform`
 *
 * Project name in bright white, push-id tail in dim gray so operators can tell at a glance which
 * deployment is running.
 */
class MotdSetter(private val server: Server) {

    fun apply(projectName: String, pushId: String? = null) {
        val shortPushId =
            pushId?.replace("-", "")?.take(SHORT_PUSH_ID_LEN)?.takeIf { it.isNotEmpty() }
        val builder = Component.text().append(Component.text(projectName, NamedTextColor.WHITE))
        if (shortPushId != null) {
            builder.append(Component.text(" $shortPushId", NamedTextColor.DARK_GRAY))
        }
        val motd =
            builder
                .append(Component.newline())
                .append(
                    Component.text(
                        "powered by Grounds Developer Platform",
                        NamedTextColor.DARK_GRAY,
                    )
                )
                .build()
        server.motd(motd)
    }

    companion object {
        /**
         * Push IDs are UUIDs. We strip dashes and keep the first 8 chars to mirror the portal's
         * push-row convention (`p.id.slice(0, 8)` in `pushes-table.tsx`). Long enough to be
         * near-unique within a project, short enough not to dominate the MOTD line.
         */
        private const val SHORT_PUSH_ID_LEN = 8
    }
}

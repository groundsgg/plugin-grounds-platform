package gg.grounds.platform.motd

import org.bukkit.Server

/**
 * Sets the server MOTD to a project-aware string at startup. Format:
 *
 * <Project Name> §8via Grounds
 *
 * Two lines because Paper's MOTD field accepts a single newline; the second line uses §8 (dark
 * grey) to keep the platform attribution subtle. Operators can override post-startup via `/motd` if
 * Paper supports it on their server, or a future per-project MOTD setting.
 */
class MotdSetter(private val server: Server) {

    fun apply(projectName: String) {
        val motd = "$projectName\n§8via Grounds"
        server.setMotd(motd)
    }
}

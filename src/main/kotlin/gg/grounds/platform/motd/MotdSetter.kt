package gg.grounds.platform.motd

import org.bukkit.Server

/**
 * Sets the server MOTD to a project-aware string at startup. Format:
 *
 *   §f<Project Name> §8<short push id>
 *   §8powered by Grounds Developer Platform
 *
 * Two lines because Paper's MOTD field accepts a single newline. Line one shows the project name in
 * bright white with the (optional) push-id tail in dim grey so operators can tell at a glance which
 * deployment is currently running. Line two is a subtle attribution. Color codes use the §-prefix
 * legacy form which Paper renders consistently across vanilla + modded clients.
 */
class MotdSetter(private val server: Server) {

    fun apply(projectName: String, pushId: String? = null) {
        val versionSuffix =
            pushId
                ?.let { it.replace("-", "").take(SHORT_PUSH_ID_LEN) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { " §8$it" }
                ?: ""
        val motd = "§f$projectName$versionSuffix\n§8powered by Grounds Developer Platform"
        server.setMotd(motd)
    }

    companion object {
        /**
         * Push IDs are UUIDs. We strip dashes and keep the first 8 chars to mirror the portal's push-
         * row convention (`p.id.slice(0, 8)` in `pushes-table.tsx`). Long enough to be near-unique
         * within a project, short enough not to dominate the MOTD line.
         */
        private const val SHORT_PUSH_ID_LEN = 8
    }
}

package gg.grounds.platform.whitelist

import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import org.bukkit.Server

/**
 * Applies a forge whitelist snapshot to Paper's local whitelist.json.
 *
 * Strategy: forge is the source of truth. On every successful fetch we recompute the desired set,
 * add anyone who isn't already whitelisted, and remove anyone who isn't in the snapshot. Bukkit's
 * own `whitelist=true` setting is enforced separately on first run via `enableWhitelistIfNeeded`.
 *
 * Logging is deliberately quiet on the steady-state ("snapshot=N unchanged") so the console isn't
 * flooded; mutations (add/remove) print one INFO line each so an operator can grep for "whitelist
 * sync".
 */
class WhitelistSync(private val server: Server, private val logger: Logger) {

    fun enableWhitelistIfNeeded() {
        if (!server.hasWhitelist()) {
            server.setWhitelist(true)
            logger.info("Enabled server whitelist (reason=platform_managed)")
        }
    }

    fun apply(snapshot: List<WhitelistEntry>) {
        val desired: Map<UUID, WhitelistEntry> =
            snapshot
                .mapNotNull { entry ->
                    val uuid = parseUuid(entry.mcUuid) ?: return@mapNotNull null
                    uuid to entry
                }
                .toMap()

        val current = server.whitelistedPlayers.mapNotNull { it.uniqueId }.toSet()

        var added = 0
        var removed = 0

        for ((uuid, entry) in desired) {
            if (uuid !in current) {
                val player = server.getOfflinePlayer(uuid)
                player.isWhitelisted = true
                logger.info("Whitelist add applied (uuid=$uuid, username=${entry.mcUsername})")
                added++
            }
        }

        for (uuid in current) {
            if (uuid !in desired.keys) {
                val player = server.getOfflinePlayer(uuid)
                player.isWhitelisted = false
                logger.info("Whitelist remove applied (uuid=$uuid)")
                removed++
            }
        }

        if (added == 0 && removed == 0) {
            logger.fine("Whitelist snapshot unchanged (size=${desired.size})")
        } else {
            logger.info(
                "Whitelist sync completed (size=${desired.size}, added=$added, removed=$removed)"
            )
        }
    }

    private fun parseUuid(s: String): UUID? =
        try {
            UUID.fromString(s)
        } catch (e: IllegalArgumentException) {
            logger.log(Level.WARNING, "Whitelist entry has malformed UUID (raw=$s); skipping", e)
            null
        }
}

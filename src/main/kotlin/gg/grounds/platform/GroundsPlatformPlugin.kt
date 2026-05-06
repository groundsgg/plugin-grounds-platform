package gg.grounds.platform

import gg.grounds.platform.motd.MotdSetter
import gg.grounds.platform.whitelist.WhitelistApiClient
import gg.grounds.platform.whitelist.WhitelistSync
import java.util.logging.Level
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

/**
 * Entry point. onEnable reads the platform-context env, sets up the MOTD + whitelist sync if
 * everything's wired, and otherwise logs a single warning and stays inert.
 *
 * The plugin is intentionally fault-tolerant: any error during a single sync iteration is logged at
 * WARN and the next tick of the scheduler retries on its own. We never throw out of the scheduled
 * task — Paper would log a stack trace per occurrence and that obscures the actual server output.
 */
class GroundsPlatformPlugin : JavaPlugin() {

    private var pollTask: BukkitTask? = null

    override fun onEnable() {
        val env = readPlatformEnv()
        if (env == null) {
            logger.warning(
                "Started without platform context (reason=missing_env, " +
                    "expected=GROUNDS_PROJECT_ID,GROUNDS_PROJECT_NAME,GROUNDS_FORGE_URL); " +
                    "MOTD + whitelist sync disabled"
            )
            return
        }
        if (readForgeToken() == null) {
            logger.warning(
                "Started without forge token (reason=GROUNDS_TOKEN_unset, " +
                    "projectId=${env.projectId}); whitelist sync disabled"
            )
        }

        MotdSetter(server).apply(env.projectName, env.pushId)
        logger.info(
            "MOTD set from platform context (projectId=${env.projectId}, " +
                "projectName=${env.projectName}, pushId=${env.pushId ?: "n/a"})"
        )

        val sync = WhitelistSync(server, logger)
        sync.enableWhitelistIfNeeded()

        val client =
            WhitelistApiClient(
                forgeUrl = env.forgeUrl,
                projectId = env.projectId,
                tokenProvider = ::readForgeToken,
            )

        // Poll cadence: 30s. Forge's whitelist mutations are explicit
        // operator actions (add/remove via portal/CLI), so a half-minute
        // delay between rotation and pickup is a fine trade for not
        // hammering the API. First tick runs immediately so initial
        // sync doesn't wait 30s.
        pollTask =
            server.scheduler.runTaskTimerAsynchronously(
                this,
                Runnable {
                    try {
                        val snapshot = client.fetch()
                        // Mutations have to run on the main thread —
                        // Bukkit's whitelist API isn't thread-safe.
                        server.scheduler.runTask(this, Runnable { sync.apply(snapshot) })
                    } catch (e: Exception) {
                        logger.log(
                            Level.WARNING,
                            "Whitelist sync failed; will retry on next tick",
                            e,
                        )
                    }
                },
                0L,
                20L * 30, // 30 seconds, 20 ticks/sec
            )

        logger.info(
            "Started platform plugin successfully " +
                "(projectId=${env.projectId}, forgeUrl=${env.forgeUrl}, pollSeconds=30)"
        )
    }

    override fun onDisable() {
        pollTask?.cancel()
        pollTask = null
    }
}

package gg.grounds.platform

import gg.grounds.platform.commands.PlatformCommandExecution
import gg.grounds.platform.commands.PlatformCommandExecutor
import gg.grounds.platform.commands.PlatformCommandLogger
import gg.grounds.platform.commands.PlatformCommandPoller
import gg.grounds.platform.commands.awaitPaperCommandDispatch
import gg.grounds.platform.commands.platformCommandEnv
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
 * WARN, and the next tick of the scheduler retries on its own. We never throw out of the scheduled
 * task — Paper would log a stack trace per occurrence and that obscures the actual server output.
 */
class GroundsPlatformPlugin : JavaPlugin() {

    private var pollTask: BukkitTask? = null
    private var commandPoller: PlatformCommandPoller? = null

    override fun onEnable() {
        val env = readPlatformEnv()
        if (env == null) {
            logger.warning(
                "Platform integration disabled (reason=missing_env, " +
                    "expected=GROUNDS_PROJECT_ID,GROUNDS_PROJECT_NAME,GROUNDS_FORGE_URL," +
                    "GROUNDS_APP_NAME, features=motd_whitelist_sync)"
            )
            return
        }
        val forgeToken = readForgeToken()
        if (forgeToken == null) {
            logger.warning(
                "Whitelist sync disabled (reason=GROUNDS_TOKEN_unset, " +
                    "projectId=${env.projectId}, appName=${env.appName})"
            )
        }

        commandPoller =
            PlatformCommandPoller(
                    env = platformCommandEnv(env, forgeToken),
                    executor = PaperPlatformCommandExecutor(this),
                    logger = PaperPlatformCommandLogger(this),
                )
                .also { it.start() }

        MotdSetter(server).apply(env.projectName, env.pushId)
        logger.info(
            "MOTD updated successfully (projectId=${env.projectId}, " +
                "projectName=${env.projectName}, appName=${env.appName}, " +
                "pushId=${env.pushId ?: "n/a"})"
        )

        val sync = WhitelistSync(server, logger)
        sync.enableWhitelistIfNeeded()

        val client =
            WhitelistApiClient(
                forgeUrl = env.forgeUrl,
                projectId = env.projectId,
                appName = env.appName,
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
                            "Whitelist sync failed (projectId=${env.projectId}, " +
                                "appName=${env.appName}, retry=next_tick)",
                            e,
                        )
                    }
                },
                0L,
                20L * 30, // 30 seconds, 20 ticks/sec
            )

        logger.info(
            "Platform plugin started successfully " +
                "(projectId=${env.projectId}, appName=${env.appName}, " +
                "forgeUrl=${env.forgeUrl}, pollSeconds=30)"
        )
    }

    override fun onDisable() {
        commandPoller?.close()
        commandPoller = null
        pollTask?.cancel()
        pollTask = null
    }

    private class PaperPlatformCommandExecutor(private val plugin: GroundsPlatformPlugin) :
        PlatformCommandExecutor {
        override fun execute(command: String): PlatformCommandExecution {
            val future =
                plugin.server.scheduler.callSyncMethod(plugin) {
                    plugin.server.dispatchCommand(plugin.server.consoleSender, command)
                }
            return awaitPaperCommandDispatch(future)
        }
    }

    private class PaperPlatformCommandLogger(private val plugin: GroundsPlatformPlugin) :
        PlatformCommandLogger {
        override fun warn(message: String, throwable: Throwable?) {
            plugin.logger.log(Level.WARNING, message, throwable)
        }

        override fun info(message: String) {
            plugin.logger.info(message)
        }

        override fun error(message: String, throwable: Throwable?) {
            plugin.logger.log(Level.SEVERE, message, throwable)
        }
    }
}

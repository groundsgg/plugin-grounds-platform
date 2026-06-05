package gg.grounds.platform.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.ResultedEvent.ComponentResult
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.BuildInfo
import gg.grounds.platform.PlatformEnv
import gg.grounds.platform.readForgeToken
import gg.grounds.platform.readPlatformEnv
import gg.grounds.platform.whitelist.WhitelistApiClient
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger

/**
 * Grounds platform integration for Velocity — the proxy-side counterpart of the Paper
 * GroundsPlatform plugin. Does two things, mirroring Paper:
 * - **Whitelist gate.** Velocity has no built-in whitelist, so we enforce it at the proxy: poll
 *   forge's effective-whitelist endpoint into an in-memory UUID set and deny [LoginEvent]s whose
 *   authenticated UUID isn't in it. It fails OPEN until the first successful sync (a forge hiccup
 *   at boot must not lock everyone out), then enforces. With no GROUNDS_TOKEN the gate stays off.
 * - **MOTD.** Set per server-list ping ([ProxyPingEvent]) to the project name + short push id,
 *   matching the Paper MOTD.
 *
 * The @Subscribe methods on the main @Plugin class are auto-registered by Velocity. The HTTP
 * client + env reader come from the shared :common module.
 */
@Plugin(
    id = "grounds-platform",
    name = "Grounds Platform",
    version = BuildInfo.VERSION,
    description = "Grounds platform integration for Velocity (whitelist + MOTD)",
    authors = ["grounds.gg"],
    url = "https://github.com/groundsgg/plugin-grounds-platform",
)
class GroundsPlatformVelocityPlugin
@Inject
constructor(private val proxy: ProxyServer, private val logger: Logger) {

    // null until the first successful whitelist sync → the login gate fails
    // open until then, and stays open forever when whitelisting is disabled.
    private val whitelist = AtomicReference<Set<UUID>?>(null)

    @Volatile private var env: PlatformEnv? = null
    @Volatile private var client: WhitelistApiClient? = null

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        val e = readPlatformEnv()
        if (e == null) {
            logger.warn(
                "Platform integration disabled (reason=missing_env, expected=" +
                    "GROUNDS_PROJECT_ID,GROUNDS_PROJECT_NAME,GROUNDS_FORGE_URL,GROUNDS_APP_NAME)"
            )
            return
        }
        env = e
        logger.info("MOTD enabled (projectName={}, pushId={})", e.projectName, e.pushId ?: "n/a")

        if (readForgeToken() == null) {
            logger.warn(
                "Whitelist gate disabled (reason=GROUNDS_TOKEN_unset, projectId={}, appName={})",
                e.projectId,
                e.appName,
            )
            return
        }
        client =
            WhitelistApiClient(
                forgeUrl = e.forgeUrl,
                projectId = e.projectId,
                appName = e.appName,
                tokenProvider = ::readForgeToken,
            )
        proxy.scheduler
            .buildTask(this, Runnable { pollWhitelistOnce() })
            .repeat(WHITELIST_POLL_SECONDS, TimeUnit.SECONDS)
            .schedule()
        logger.info(
            "Whitelist gate enabled (projectId={}, appName={}, pollSeconds={})",
            e.projectId,
            e.appName,
            WHITELIST_POLL_SECONDS,
        )
    }

    private fun pollWhitelistOnce() {
        val c = client ?: return
        try {
            val uuids =
                c.fetch()
                    .mapNotNull { runCatching { UUID.fromString(it.mcUuid) }.getOrNull() }
                    .toSet()
            whitelist.set(uuids)
            // Routine periodic sync — debug only; a failed fetch logs warn below.
            logger.debug("Whitelist synced (size={})", uuids.size)
        } catch (ex: Exception) {
            logger.warn("Whitelist fetch failed (reason={}); keeping previous snapshot", ex.message)
        }
    }

    @Subscribe
    fun onLogin(event: LoginEvent) {
        val snapshot = whitelist.get() ?: return // not loaded / disabled → fail open
        val uuid = event.player.uniqueId
        if (uuid !in snapshot) {
            event.result =
                ComponentResult.denied(
                    Component.text("You are not whitelisted on this server.", NamedTextColor.RED)
                )
            logger.info(
                "Whitelist denied login (uuid={}, username={})",
                uuid,
                event.player.username,
            )
        }
    }

    @Subscribe
    fun onProxyPing(event: ProxyPingEvent) {
        val e = env ?: return
        val shortPushId =
            e.pushId?.replace("-", "")?.take(SHORT_PUSH_ID_LEN)?.takeIf { it.isNotEmpty() }
        val builder = Component.text().append(Component.text(e.projectName, NamedTextColor.WHITE))
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
        event.ping = event.ping.asBuilder().description(motd).build()
    }

    private companion object {
        const val WHITELIST_POLL_SECONDS = 30L
        const val SHORT_PUSH_ID_LEN = 8
    }
}

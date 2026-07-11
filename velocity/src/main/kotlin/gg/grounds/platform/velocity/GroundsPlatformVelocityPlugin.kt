package gg.grounds.platform.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.ResultedEvent.ComponentResult
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.util.Favicon
import gg.grounds.BuildInfo
import gg.grounds.platform.PlatformEnv
import gg.grounds.platform.commands.PlatformCommandLogger
import gg.grounds.platform.commands.PlatformCommandPoller
import gg.grounds.platform.commands.platformCommandEnv
import gg.grounds.platform.readForgeToken
import gg.grounds.platform.readPlatformEnv
import gg.grounds.platform.velocity.commands.VelocityPlatformCommandExecutor
import gg.grounds.platform.whitelist.WhitelistApiClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger

/**
 * Grounds platform integration for Velocity — the proxy-side counterpart of the Paper
 * GroundsPlatform plugin. Mirrors Paper platform integration where Velocity has equivalent APIs:
 * - **Whitelist gate.** Velocity has no built-in whitelist, so we enforce it at the proxy: poll
 *   forge's effective-whitelist endpoint into an in-memory UUID set and deny [LoginEvent]s whose
 *   authenticated UUID isn't in it. It fails OPEN until the first successful sync (a forge hiccup
 *   at boot must not lock everyone out), then enforces. With no GROUNDS_TOKEN the gate stays off.
 * - **MOTD.** Set per server-list ping ([ProxyPingEvent]) to the project name + short push id,
 *   matching the Paper MOTD.
 * - **Platform commands.** Poll Forge for queued deployment commands and dispatch them through the
 *   Velocity console command source.
 * - **Server icon.** The Grounds logo, fetched once at startup and served with every ping. Unlike
 *   the MOTD this is brand, not project context, so it is applied even when the platform env is
 *   missing — and a failed fetch only costs the icon, never the ping.
 *
 * The @Subscribe methods on the main @Plugin class are auto-registered by Velocity. The HTTP
 * client + env reader come from the shared :common module.
 */
@Plugin(
    id = "grounds-platform",
    name = "Grounds Platform",
    version = BuildInfo.VERSION,
    description = "Grounds platform integration for Velocity (whitelist, MOTD, commands)",
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
    @Volatile private var commandPoller: PlatformCommandPoller? = null
    @Volatile private var favicon: Favicon? = null

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        // Off the init thread: a slow CDN must not hold up proxy startup. Pings
        // that land before it resolves simply carry no icon.
        proxy.scheduler.buildTask(this, Runnable { loadFavicon() }).schedule()

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

        val forgeToken = readForgeToken()
        commandPoller =
            PlatformCommandPoller(
                    env = platformCommandEnv(e, forgeToken),
                    executor = VelocityPlatformCommandExecutor(proxy),
                    logger = VelocityPlatformCommandLogger(logger),
                )
                .also { it.start() }

        if (forgeToken == null) {
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

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        commandPoller?.close()
        commandPoller = null
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

    /**
     * Fetch the Grounds logo and keep it as a [Favicon]. Velocity requires exactly 64x64 PNG; the
     * CDN serves that. Any failure is logged and swallowed — the proxy then simply pings without an
     * icon rather than not pinging at all.
     */
    private fun loadFavicon() {
        try {
            val response =
                HttpClient.newBuilder()
                    .connectTimeout(ICON_TIMEOUT)
                    .build()
                    .send(
                        HttpRequest.newBuilder(URI.create(ICON_URL))
                            .timeout(ICON_TIMEOUT)
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.ofByteArray(),
                    )
            if (response.statusCode() != 200) {
                logger.warn(
                    "Server icon unavailable (url={}, status={}); serving pings without one",
                    ICON_URL,
                    response.statusCode(),
                )
                return
            }
            val base64 = Base64.getEncoder().encodeToString(response.body())
            favicon = Favicon("data:image/png;base64,$base64")
            logger.info("Server icon loaded (url={}, bytes={})", ICON_URL, response.body().size)
        } catch (ex: Exception) {
            logger.warn(
                "Server icon unavailable (url={}, reason={}); serving pings without one",
                ICON_URL,
                ex.message,
            )
        }
    }

    @Subscribe
    fun onProxyPing(event: ProxyPingEvent) {
        var builder = event.ping.asBuilder()

        // Brand, not project context — applied even when the platform env is absent.
        favicon?.let { builder = builder.favicon(it) }

        env?.let { builder = builder.description(motdFor(it)) }

        event.ping = builder.build()
    }

    /** Project name + short push id, then the platform line. Mirrors the Paper MotdSetter. */
    private fun motdFor(e: PlatformEnv): Component {
        val shortPushId =
            e.pushId?.replace("-", "")?.take(SHORT_PUSH_ID_LEN)?.takeIf { it.isNotEmpty() }
        val builder = Component.text().append(Component.text(e.projectName, NamedTextColor.WHITE))
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

    private companion object {
        const val WHITELIST_POLL_SECONDS = 30L
        const val SHORT_PUSH_ID_LEN = 8

        /**
         * Served from the CDN rather than bundled in the jar so the logo can be swapped without a
         * plugin release. Must stay a 64x64 PNG — Velocity rejects anything else.
         */
        const val ICON_URL = "https://cdn.grounds.gg/logo/icon-64x.png"
        val ICON_TIMEOUT: Duration = Duration.ofSeconds(5)
    }

    private class VelocityPlatformCommandLogger(private val logger: Logger) :
        PlatformCommandLogger {
        override fun warn(message: String, throwable: Throwable?) {
            logger.warn(message, throwable)
        }

        override fun info(message: String) {
            logger.info(message)
        }

        override fun error(message: String, throwable: Throwable?) {
            logger.error(message, throwable)
        }
    }
}

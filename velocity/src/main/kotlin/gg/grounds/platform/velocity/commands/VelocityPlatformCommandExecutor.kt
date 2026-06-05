package gg.grounds.platform.velocity.commands

import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.platform.commands.PlatformCommandExecution
import gg.grounds.platform.commands.PlatformCommandExecutor
import gg.grounds.platform.commands.awaitCommandDispatch

class VelocityPlatformCommandExecutor(private val proxy: ProxyServer) : PlatformCommandExecutor {
    override fun execute(command: String): PlatformCommandExecution =
        awaitCommandDispatch(proxy.commandManager.executeAsync(proxy.consoleCommandSource, command))
}

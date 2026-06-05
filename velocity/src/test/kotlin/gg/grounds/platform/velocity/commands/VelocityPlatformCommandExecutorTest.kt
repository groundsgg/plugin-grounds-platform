package gg.grounds.platform.velocity.commands

import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.platform.commands.PlatformCommandExecution
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class VelocityPlatformCommandExecutorTest {
    @Test
    fun `execute dispatches command as console source`() {
        val future = CompletableFuture.completedFuture(true)
        val commandManager = RecordingCommandManager(future)
        val console = proxy<ConsoleCommandSource>()
        val proxy = proxyServer(commandManager.proxy, console)

        val execution = VelocityPlatformCommandExecutor(proxy).execute("server lobby")

        assertEquals(PlatformCommandExecution.executed("Command executed"), execution)
        assertSame(console, commandManager.source)
        assertEquals("server lobby", commandManager.command)
    }

    @Test
    fun `execute returns failed when command is not handled`() {
        val commandManager = RecordingCommandManager(CompletableFuture.completedFuture(false))
        val proxy = proxyServer(commandManager.proxy, proxy<ConsoleCommandSource>())

        val execution = VelocityPlatformCommandExecutor(proxy).execute("unknown")

        assertEquals(PlatformCommandExecution.failed("Command was not handled"), execution)
    }

    @Test
    fun `execute returns failed when command dispatch fails`() {
        val future = CompletableFuture<Boolean>()
        future.completeExceptionally(IllegalStateException("boom"))
        val commandManager = RecordingCommandManager(future)
        val proxy = proxyServer(commandManager.proxy, proxy<ConsoleCommandSource>())

        val execution = VelocityPlatformCommandExecutor(proxy).execute("server lobby")

        assertEquals(
            PlatformCommandExecution.failed(
                "Command execution failed (reason=IllegalStateException)"
            ),
            execution,
        )
    }

    private class RecordingCommandManager(private val result: CompletableFuture<Boolean>) {
        var source: CommandSource? = null
        var command: String? = null

        val proxy: CommandManager = proxy { method, args ->
            when (method.name) {
                "executeAsync" -> {
                    source = args?.get(0) as CommandSource
                    command = args[1] as String
                    result
                }
                else -> defaultValue(method.returnType)
            }
        }
    }
}

private fun proxyServer(
    commandManager: CommandManager,
    console: ConsoleCommandSource,
): ProxyServer = proxy { method, _ ->
    when (method.name) {
        "getCommandManager" -> commandManager
        "getConsoleCommandSource" -> console
        else -> defaultValue(method.returnType)
    }
}

private inline fun <reified T : Any> proxy(
    noinline handler: (java.lang.reflect.Method, Array<Any?>?) -> Any? = { method, _ ->
        defaultValue(method.returnType)
    }
): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        when (method.name) {
            "toString" -> T::class.java.simpleName
            "hashCode" -> System.identityHashCode(method)
            "equals" -> false
            else -> handler(method, args)
        }
    } as T

private fun defaultValue(type: Class<*>): Any? =
    when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0.0f
        java.lang.Void.TYPE -> null
        else -> null
    }

package gg.grounds.platform.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlatformCommandPollerTest {
    @Test
    fun `disabled poller logs one startup warning and does not lease commands`() {
        val client = RecordingService()
        val logger = RecordingLogger()

        val poller =
            PlatformCommandPoller(
                env =
                    PlatformCommandEnv.Disabled(
                        reason = PlatformCommandDisabledReason.MISSING_TOKEN,
                        projectId = "project-1",
                        appName = "survival",
                    ),
                client = client,
                executor = RecordingExecutor(),
                logger = logger,
            )

        poller.pollOnce()
        poller.pollOnce()

        assertEquals(0, client.leaseCalls)
        assertEquals(
            listOf(
                "Platform command polling disabled (reason=missing_token, appName=survival, projectId=project-1, pushId=unavailable)"
            ),
            logger.warns,
        )
    }

    @Test
    fun `enabled poller executes leased command and posts executed result`() {
        val command =
            PlatformCommandLease(
                id = "command-1",
                command = "say hello",
                queuedAt = "2026-05-19T10:00:00.000Z",
                leaseToken = "lease-1",
            )
        val client = RecordingService(command)
        val executor = RecordingExecutor(PlatformCommandExecution.executed("Command executed"))

        val poller =
            PlatformCommandPoller(
                env = enabledEnv(),
                client = client,
                executor = executor,
                logger = RecordingLogger(),
            )

        poller.pollOnce()

        assertEquals(listOf("say hello"), executor.commands)
        assertEquals(
            listOf(
                "command-1" to
                    PlatformCommandResult(
                        leaseToken = "lease-1",
                        status = PlatformCommandStatus.EXECUTED,
                        message = "Command executed",
                    )
            ),
            client.results,
        )
    }

    @Test
    fun `enabled poller posts failed result when executor throws`() {
        val command =
            PlatformCommandLease(
                id = "command-1",
                command = "bad command",
                queuedAt = "2026-05-19T10:00:00.000Z",
                leaseToken = "lease-1",
            )
        val client = RecordingService(command)
        val executor = ThrowingExecutor()

        PlatformCommandPoller(
                env = enabledEnv(),
                client = client,
                executor = executor,
                logger = RecordingLogger(),
            )
            .pollOnce()

        assertEquals(
            PlatformCommandResult(
                leaseToken = "lease-1",
                status = PlatformCommandStatus.FAILED,
                message = "Command execution failed (reason=boom)",
            ),
            client.results.single().second,
        )
    }

    private fun enabledEnv(): PlatformCommandEnv.Enabled =
        PlatformCommandEnv.Enabled(
            forgeUrl = "https://forge.example",
            projectId = "project-1",
            appName = "survival",
            pushId = "push-9",
            token = "token-1",
        )

    private class RecordingService(private val command: PlatformCommandLease? = null) :
        PlatformCommandService {
        var leaseCalls = 0
        val results = mutableListOf<Pair<String, PlatformCommandResult>>()

        override fun leaseCommand(): PlatformCommandLease? {
            leaseCalls += 1
            return command
        }

        override fun postResult(commandId: String, result: PlatformCommandResult) {
            results += commandId to result
        }
    }

    private class RecordingExecutor(
        private val result: PlatformCommandExecution = PlatformCommandExecution.executed("ok")
    ) : PlatformCommandExecutor {
        val commands = mutableListOf<String>()

        override fun execute(command: String): PlatformCommandExecution {
            commands += command
            return result
        }
    }

    private class ThrowingExecutor : PlatformCommandExecutor {
        override fun execute(command: String): PlatformCommandExecution {
            throw IllegalStateException("boom")
        }
    }

    private class RecordingLogger : PlatformCommandLogger {
        val warns = mutableListOf<String>()

        override fun warn(message: String, throwable: Throwable?) {
            warns += message
        }

        override fun info(message: String) = Unit

        override fun error(message: String, throwable: Throwable?) = Unit
    }
}

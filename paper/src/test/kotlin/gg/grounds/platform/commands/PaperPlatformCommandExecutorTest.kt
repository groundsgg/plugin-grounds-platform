package gg.grounds.platform.commands

import java.util.concurrent.CompletableFuture
import java.util.concurrent.FutureTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaperPlatformCommandExecutorTest {
    @Test
    fun `await dispatch returns executed when command is handled`() {
        val execution =
            awaitPaperCommandDispatch(
                CompletableFuture.completedFuture(true),
                timeoutMillis = 1_000,
            )

        assertEquals(PlatformCommandExecution.executed("Command executed"), execution)
    }

    @Test
    fun `await dispatch returns failed when command is not handled`() {
        val execution =
            awaitPaperCommandDispatch(
                CompletableFuture.completedFuture(false),
                timeoutMillis = 1_000,
            )

        assertEquals(PlatformCommandExecution.failed("Command was not handled"), execution)
    }

    @Test
    fun `await dispatch times out and cancels the future`() {
        val future = FutureTask { true }

        val execution = awaitPaperCommandDispatch(future, timeoutMillis = 1)

        assertEquals(PlatformCommandExecution.failed("Command execution timed out"), execution)
        assertTrue(future.isCancelled)
    }
}

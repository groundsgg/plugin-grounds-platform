package gg.grounds.platform.commands

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

const val PAPER_COMMAND_EXECUTION_TIMEOUT_MILLIS: Long = 5_000

internal fun awaitPaperCommandDispatch(
    future: Future<Boolean>,
    timeoutMillis: Long = PAPER_COMMAND_EXECUTION_TIMEOUT_MILLIS,
): PlatformCommandExecution {
    val handled =
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            return PlatformCommandExecution.failed("Command execution interrupted")
        } catch (_: TimeoutException) {
            future.cancel(true)
            return PlatformCommandExecution.failed("Command execution timed out")
        } catch (_: CancellationException) {
            return PlatformCommandExecution.failed("Command execution cancelled")
        } catch (exception: ExecutionException) {
            return PlatformCommandExecution.failed(
                "Command execution failed (reason=${exception.cause.reason()})"
            )
        }

    return if (handled) {
        PlatformCommandExecution.executed("Command executed")
    } else {
        PlatformCommandExecution.failed("Command was not handled")
    }
}

private fun Throwable?.reason(): String = this?.javaClass?.simpleName ?: "unknown"

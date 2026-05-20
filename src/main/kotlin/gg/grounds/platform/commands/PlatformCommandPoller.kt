package gg.grounds.platform.commands

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

fun interface PlatformCommandExecutor {
    fun execute(command: String): PlatformCommandExecution
}

data class PlatformCommandExecution(val status: PlatformCommandStatus, val message: String) {
    companion object {
        fun executed(message: String): PlatformCommandExecution =
            PlatformCommandExecution(PlatformCommandStatus.EXECUTED, message)

        fun failed(message: String): PlatformCommandExecution =
            PlatformCommandExecution(PlatformCommandStatus.FAILED, message)
    }
}

interface PlatformCommandLogger {
    fun warn(message: String, throwable: Throwable? = null)

    fun info(message: String)

    fun error(message: String, throwable: Throwable? = null)
}

class PlatformCommandPoller(
    private val env: PlatformCommandEnv,
    private val client: PlatformCommandService? =
        (env as? PlatformCommandEnv.Enabled)?.let { PlatformCommandClient(it) },
    private val executor: PlatformCommandExecutor,
    private val logger: PlatformCommandLogger,
    private val worker: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "grounds-platform-command-poller").apply { isDaemon = true }
        },
    private val backoffMillis: Long = 5_000,
    private val sleeper: (Long) -> Unit = { millis -> Thread.sleep(millis) },
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val warningLogged = AtomicBoolean(false)
    private var future: Future<*>? = null

    fun start() {
        when (env) {
            is PlatformCommandEnv.Disabled -> logDisabledWarning()
            is PlatformCommandEnv.Enabled -> {
                if (started.compareAndSet(false, true)) {
                    logger.info(
                        "Platform command polling started successfully (appName=${env.appName}, projectId=${env.projectId}, pushId=${env.pushId.logValue()})"
                    )
                    future = worker.submit { pollLoop() }
                }
            }
        }
    }

    fun pollOnce() {
        when (env) {
            is PlatformCommandEnv.Disabled -> logDisabledWarning()
            is PlatformCommandEnv.Enabled -> pollEnabledOnce()
        }
    }

    override fun close() {
        started.set(false)
        future?.cancel(true)
        worker.shutdownNow()
    }

    private fun pollLoop() {
        while (started.get() && !Thread.currentThread().isInterrupted) {
            pollEnabledOnce()
        }
    }

    private fun pollEnabledOnce() {
        val enabledEnv = env as? PlatformCommandEnv.Enabled ?: return
        val service = client ?: return
        val lease =
            try {
                service.leaseCommand()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (exception: Exception) {
                logger.warn(
                    "Failed to lease platform command (appName=${enabledEnv.appName}, projectId=${enabledEnv.projectId}, pushId=${enabledEnv.pushId.logValue()}, reason=${exception.reason()})",
                    exception,
                )
                sleepAfterLeaseFailure()
                return
            }
        lease?.let { commandLease ->
            val execution =
                try {
                    executor.execute(commandLease.command)
                } catch (exception: Exception) {
                    PlatformCommandExecution.failed(
                        "Command execution failed (reason=${exception.reason()})"
                    )
                }
            if (Thread.currentThread().isInterrupted) return

            try {
                service.postResult(
                    commandId = commandLease.id,
                    result =
                        PlatformCommandResult(
                            leaseToken = commandLease.leaseToken,
                            status = execution.status,
                            message = execution.message,
                        ),
                )
                logger.info(
                    "Platform command result posted successfully (appName=${enabledEnv.appName}, projectId=${enabledEnv.projectId}, pushId=${enabledEnv.pushId.logValue()}, commandId=${commandLease.id}, status=${execution.status.wireValue})"
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (exception: Exception) {
                logger.error(
                    "Failed to post platform command result (appName=${enabledEnv.appName}, projectId=${enabledEnv.projectId}, pushId=${enabledEnv.pushId.logValue()}, commandId=${commandLease.id}, reason=${exception.reason()})",
                    exception,
                )
            }
        }
    }

    private fun logDisabledWarning() {
        val disabledEnv = env as? PlatformCommandEnv.Disabled ?: return
        if (warningLogged.compareAndSet(false, true)) {
            logger.warn(
                "Platform command polling disabled (reason=${disabledEnv.reason.logValue}, appName=${disabledEnv.appName.logValue()}, projectId=${disabledEnv.projectId.logValue()}, pushId=${disabledEnv.pushId.logValue()})"
            )
        }
    }

    private fun sleepAfterLeaseFailure() {
        try {
            sleeper(backoffMillis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun String?.logValue(): String = this ?: "unavailable"

    private fun Exception.reason(): String =
        this.message?.replace(' ', '_')?.take(80) ?: this::class.simpleName ?: "unknown"
}

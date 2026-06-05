package gg.grounds.platform.commands

import java.util.concurrent.Future

const val PAPER_COMMAND_EXECUTION_TIMEOUT_MILLIS: Long = COMMAND_EXECUTION_TIMEOUT_MILLIS

internal fun awaitPaperCommandDispatch(
    future: Future<Boolean>,
    timeoutMillis: Long = PAPER_COMMAND_EXECUTION_TIMEOUT_MILLIS,
): PlatformCommandExecution = awaitCommandDispatch(future, timeoutMillis)

package gg.grounds.platform.commands

import gg.grounds.platform.PlatformEnv

sealed class PlatformCommandEnv {
    data class Enabled(
        val forgeUrl: String,
        val projectId: String,
        val appName: String,
        val pushId: String,
        val token: String,
    ) : PlatformCommandEnv() {
        override fun toString(): String =
            "Enabled(forgeUrl=$forgeUrl, projectId=$projectId, appName=$appName, pushId=$pushId, token=<redacted>)"
    }

    data class Disabled(
        val reason: PlatformCommandDisabledReason,
        val projectId: String? = null,
        val appName: String? = null,
        val pushId: String? = null,
    ) : PlatformCommandEnv()
}

enum class PlatformCommandDisabledReason(val logValue: String) {
    MISSING_PUSH_ID("missing_push_id"),
    MISSING_TOKEN("missing_token"),
}

fun platformCommandEnv(platformEnv: PlatformEnv, token: String?): PlatformCommandEnv =
    platformCommandEnvInternal(platformEnv, token)

private fun platformCommandEnvInternal(
    platformEnv: PlatformEnv,
    token: String?,
): PlatformCommandEnv {
    val pushId = platformEnv.pushId
    if (pushId.isNullOrBlank()) {
        return PlatformCommandEnv.Disabled(
            reason = PlatformCommandDisabledReason.MISSING_PUSH_ID,
            projectId = platformEnv.projectId,
            appName = platformEnv.appName,
            pushId = pushId,
        )
    }
    if (token.isNullOrBlank()) {
        return PlatformCommandEnv.Disabled(
            reason = PlatformCommandDisabledReason.MISSING_TOKEN,
            projectId = platformEnv.projectId,
            appName = platformEnv.appName,
            pushId = pushId,
        )
    }
    return PlatformCommandEnv.Enabled(
        forgeUrl = platformEnv.forgeUrl,
        projectId = platformEnv.projectId,
        appName = platformEnv.appName,
        pushId = pushId,
        token = token,
    )
}

package gg.grounds.platform

/**
 * Platform context the in-pod plugin reads at startup. All four fields are populated by
 * grounds-forge's DeployWorker (see `forge/src/deploy/mcWorkloadToken.ts` + the renderer's
 * mcPlatformEnv block) for Minecraft workloads. Non-MC pods and pods that pre-date the
 * workload-token feature won't have these set; the plugin then runs in inert / no-op mode.
 *
 * Token is read separately from a secret-mounted env var rather than from this struct, so we never
 * accidentally include it in a `toString` or log line.
 */
data class PlatformEnv(val projectId: String, val projectName: String, val forgeUrl: String)

interface EnvReader {
    operator fun get(name: String): String?
}

object SystemEnvReader : EnvReader {
    override fun get(name: String): String? = System.getenv(name)
}

/**
 * Returns null when any of the required platform-context env vars are missing — the plugin treats
 * that as "no platform integration" and skips the MOTD substitution + whitelist sync. The user's
 * gameplay isn't affected.
 */
fun readPlatformEnv(reader: EnvReader = SystemEnvReader): PlatformEnv? {
    val projectId = reader["GROUNDS_PROJECT_ID"]?.trim().orEmpty()
    val projectName = reader["GROUNDS_PROJECT_NAME"]?.trim().orEmpty()
    val forgeUrl = reader["GROUNDS_FORGE_URL"]?.trim().orEmpty()
    if (projectId.isEmpty() || projectName.isEmpty() || forgeUrl.isEmpty()) {
        return null
    }
    return PlatformEnv(
        projectId = projectId,
        projectName = projectName,
        forgeUrl = forgeUrl.trimEnd('/'),
    )
}

/**
 * The token comes from a separate K8s Secret-mounted env var; reading it here keeps the value from
 * showing up in `PlatformEnv.toString()` (Kotlin's `data class` copies the field into the
 * auto-generated representation, which is exactly the kind of accident we want to avoid for
 * credentials).
 */
fun readForgeToken(reader: EnvReader = SystemEnvReader): String? =
    reader["GROUNDS_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() }

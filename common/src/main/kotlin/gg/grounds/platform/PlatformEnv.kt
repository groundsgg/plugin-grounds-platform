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
data class PlatformEnv(
    val projectId: String,
    val projectName: String,
    val forgeUrl: String,
    val appName: String,
    val resourceName: String = appName,
    /**
     * Push ID of the deployment that produced this pod (the renderer's `GROUNDS_PUSH_ID` env).
     * Optional — older deployments may not have it. Surfaced in the MOTD as a "version" tag for
     * operator orientation; whitelist sync doesn't depend on it.
     */
    val pushId: String? = null,
)

fun interface EnvReader {
    operator fun get(name: String): String?
}

val SystemEnvReader: EnvReader = EnvReader { name -> System.getenv(name) }

/**
 * Returns null when any of the required platform-context env vars are missing — the plugin treats
 * that as "no platform integration" and skips the MOTD substitution + whitelist sync. The user's
 * gameplay isn't affected.
 */
fun readPlatformEnv(reader: EnvReader = SystemEnvReader): PlatformEnv? {
    val projectId = reader["GROUNDS_PROJECT_ID"]?.trim().orEmpty()
    val projectName = reader["GROUNDS_PROJECT_NAME"]?.trim().orEmpty()
    val forgeUrl = reader["GROUNDS_FORGE_URL"]?.trim().orEmpty()
    val appName = reader["GROUNDS_APP_NAME"]?.trim().orEmpty()
    if (projectId.isEmpty() || projectName.isEmpty() || forgeUrl.isEmpty() || appName.isEmpty()) {
        return null
    }
    val resourceName =
        reader["GROUNDS_RESOURCE_NAME"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: deploymentNameFromPodHostname(reader["HOSTNAME"]?.trim())
            ?: appName
    return PlatformEnv(
        projectId = projectId,
        projectName = projectName,
        forgeUrl = forgeUrl.trimEnd('/'),
        appName = appName,
        resourceName = resourceName,
        pushId = reader["GROUNDS_PUSH_ID"]?.trim()?.takeIf { it.isNotEmpty() },
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

private fun deploymentNameFromPodHostname(hostname: String?): String? {
    if (hostname.isNullOrBlank()) return null
    val match = Regex("^(.+)-[a-z0-9]{9,10}-[a-z0-9]{5}$").matchEntire(hostname) ?: return null
    return match.groupValues[1].takeIf { it.isNotBlank() }
}

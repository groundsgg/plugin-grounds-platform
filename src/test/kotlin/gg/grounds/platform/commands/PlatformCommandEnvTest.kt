package gg.grounds.platform.commands

import gg.grounds.platform.PlatformEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlatformCommandEnvTest {
    @Test
    fun `env is enabled when platform env has push id and token`() {
        val env =
            platformCommandEnv(
                platformEnv =
                    PlatformEnv(
                        projectId = "project-1",
                        projectName = "Project One",
                        forgeUrl = "https://forge.example",
                        appName = "survival",
                        pushId = "push-9",
                    ),
                token = "token-1",
            )

        assertEquals(
            PlatformCommandEnv.Enabled(
                forgeUrl = "https://forge.example",
                projectId = "project-1",
                appName = "survival",
                pushId = "push-9",
                token = "token-1",
            ),
            env,
        )
    }

    @Test
    fun `env is disabled when push id is missing`() {
        val env =
            platformCommandEnv(
                platformEnv =
                    PlatformEnv(
                        projectId = "project-1",
                        projectName = "Project One",
                        forgeUrl = "https://forge.example",
                        appName = "survival",
                    ),
                token = "token-1",
            )
                as PlatformCommandEnv.Disabled

        assertEquals(PlatformCommandDisabledReason.MISSING_PUSH_ID, env.reason)
        assertEquals("project-1", env.projectId)
        assertEquals("survival", env.appName)
        assertNull(env.pushId)
    }

    @Test
    fun `env is disabled when token is missing`() {
        val env =
            platformCommandEnv(
                platformEnv =
                    PlatformEnv(
                        projectId = "project-1",
                        projectName = "Project One",
                        forgeUrl = "https://forge.example",
                        appName = "survival",
                        pushId = "push-9",
                    ),
                token = null,
            )
                as PlatformCommandEnv.Disabled

        assertEquals(PlatformCommandDisabledReason.MISSING_TOKEN, env.reason)
        assertEquals("project-1", env.projectId)
        assertEquals("survival", env.appName)
        assertEquals("push-9", env.pushId)
    }
}

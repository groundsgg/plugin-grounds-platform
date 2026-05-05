package gg.grounds.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlatformEnvTest {

    private fun reader(map: Map<String, String>): EnvReader =
        object : EnvReader {
            override fun get(name: String): String? = map[name]
        }

    @Test
    fun `returns env when all four required vars are present`() {
        val env =
            readPlatformEnv(
                reader(
                    mapOf(
                        "GROUNDS_PROJECT_ID" to "p-1",
                        "GROUNDS_PROJECT_NAME" to "Demo Project",
                        "GROUNDS_FORGE_URL" to "http://forge:8080",
                    )
                )
            )
        assertEquals(PlatformEnv("p-1", "Demo Project", "http://forge:8080"), env)
    }

    @Test
    fun `strips trailing slash from forgeUrl`() {
        val env =
            readPlatformEnv(
                reader(
                    mapOf(
                        "GROUNDS_PROJECT_ID" to "p-1",
                        "GROUNDS_PROJECT_NAME" to "P",
                        "GROUNDS_FORGE_URL" to "http://forge:8080/",
                    )
                )
            )
        assertEquals("http://forge:8080", env?.forgeUrl)
    }

    @Test
    fun `returns null when projectId is missing`() {
        val env =
            readPlatformEnv(
                reader(mapOf("GROUNDS_PROJECT_NAME" to "P", "GROUNDS_FORGE_URL" to "http://forge"))
            )
        assertNull(env)
    }

    @Test
    fun `returns null when any required var is empty after trim`() {
        val env =
            readPlatformEnv(
                reader(
                    mapOf(
                        "GROUNDS_PROJECT_ID" to "   ",
                        "GROUNDS_PROJECT_NAME" to "P",
                        "GROUNDS_FORGE_URL" to "http://forge",
                    )
                )
            )
        assertNull(env)
    }

    @Test
    fun `forge token reader returns null on missing or empty`() {
        assertNull(readForgeToken(reader(emptyMap())))
        assertNull(readForgeToken(reader(mapOf("GROUNDS_TOKEN" to ""))))
        assertNull(readForgeToken(reader(mapOf("GROUNDS_TOKEN" to "   "))))
    }

    @Test
    fun `forge token reader returns trimmed value`() {
        assertEquals(
            "gnds_abc_def",
            readForgeToken(reader(mapOf("GROUNDS_TOKEN" to " gnds_abc_def "))),
        )
    }

    @Test
    fun `data class toString does not include token`() {
        // Defensive: make sure no future change adds the token to PlatformEnv.
        val env = PlatformEnv("p-1", "Demo", "http://forge")
        val s = env.toString()
        // Sanity — required fields are present
        assert(s.contains("p-1"))
        // Negative — no token-shaped value
        assert(!s.contains("gnds_"))
    }
}

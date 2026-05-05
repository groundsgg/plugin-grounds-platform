package gg.grounds.platform.whitelist

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Single forge whitelist row as returned by GET /v1/projects/:id/whitelist. The endpoint also
 * returns `addedAt` and `addedBy`, but the plugin only needs the UUID and current display name —
 * drop the rest at parse time so we don't drag the row shape into the rest of the codebase.
 */
data class WhitelistEntry(val mcUuid: String, val mcUsername: String)

/**
 * Read-only HTTP client for the forge whitelist endpoint. Uses the JDK built-in HttpClient (already
 * on the classpath, no shading required) with a short connect / request timeout — the plugin polls
 * on a 30s cadence so a hung forge shouldn't block the Paper main thread.
 *
 * Auth header is set per request from the GROUNDS_TOKEN env var, never from a stored field, so
 * logging / debugging this client cannot accidentally surface the token.
 */
class WhitelistApiClient(
    private val forgeUrl: String,
    private val projectId: String,
    private val tokenProvider: () -> String?,
    private val httpClient: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) {

    private val adapter: JsonAdapter<ListResponse> =
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(ListResponse::class.java)

    /**
     * Fetches the current whitelist. Throws on any non-2xx — caller decides whether to surface or
     * swallow (the periodic poller swallows + logs at WARN to keep transient forge hiccups from
     * spamming ERROR).
     */
    fun fetch(): List<WhitelistEntry> {
        val token =
            tokenProvider()
                ?: throw IllegalStateException(
                    "GROUNDS_TOKEN env var is not set; whitelist fetch refused"
                )
        val uri = URI.create("$forgeUrl/v1/projects/$projectId/whitelist")
        val request =
            HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() / 100 != 2) {
            throw RuntimeException(
                "forge whitelist fetch failed (status=${response.statusCode()}, projectId=$projectId)"
            )
        }
        val parsed =
            adapter.fromJson(response.body())
                ?: throw RuntimeException(
                    "forge whitelist response did not parse as JSON (projectId=$projectId)"
                )
        return parsed.items.map { WhitelistEntry(mcUuid = it.mcUuid, mcUsername = it.mcUsername) }
    }

    private data class ListResponse(val items: List<RawEntry>) {
        data class RawEntry(val mcUuid: String, val mcUsername: String)
    }

    @Suppress("unused")
    private val typeRef =
        Types.newParameterizedType(List::class.java, ListResponse.RawEntry::class.java)
}

package gg.grounds.platform.commands

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

interface PlatformCommandService {
    fun leaseCommand(): PlatformCommandLease?

    fun postResult(commandId: String, result: PlatformCommandResult)
}

data class PlatformCommandLease(
    val id: String,
    val command: String,
    val queuedAt: String,
    val leaseToken: String,
)

data class PlatformCommandResult(
    val leaseToken: String,
    val status: PlatformCommandStatus,
    val message: String,
)

enum class PlatformCommandStatus(val wireValue: String) {
    EXECUTED("executed"),
    FAILED("failed"),
}

class PlatformCommandClient(
    private val env: PlatformCommandEnv.Enabled,
    private val httpClient: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : PlatformCommandService {
    private val leaseAdapter: JsonAdapter<LeaseResponse> =
        Moshi.Builder().build().adapter(LeaseResponse::class.java)
    private val resultAdapter: JsonAdapter<ResultRequest> =
        Moshi.Builder().build().adapter(ResultRequest::class.java)

    override fun leaseCommand(): PlatformCommandLease? {
        val response =
            httpClient.send(
                requestBuilder(leaseUri()).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        requireSuccessful(response, "lease platform command")
        return leaseAdapter.fromJson(response.body())?.command?.toLease()
    }

    override fun postResult(commandId: String, result: PlatformCommandResult) {
        val response =
            httpClient.send(
                requestBuilder(resultUri(commandId))
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            resultAdapter.toJson(result.toRequest())
                        )
                    )
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        requireSuccessful(response, "post platform command result")
    }

    private fun requestBuilder(uri: URI): HttpRequest.Builder =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(35))
            .header("Authorization", "Bearer ${env.token}")

    private fun leaseUri(): URI =
        endpoint(
            "/v1/platform/deployments/${encode(env.appName)}/commands/lease",
            buildList {
                add("projectId=${encode(env.projectId)}")
                add("pushId=${encode(env.pushId)}")
                add("waitMs=25000")
            },
        )

    private fun resultUri(commandId: String): URI =
        endpoint(
            "/v1/platform/deployments/${encode(env.appName)}/commands/${encode(commandId)}/result",
            emptyList(),
        )

    private fun endpoint(path: String, query: List<String>): URI {
        val queryString = query.takeIf { it.isNotEmpty() }?.joinToString("&")?.let { "?$it" } ?: ""
        return URI.create("${env.forgeUrl.trimEnd('/')}$path$queryString")
    }

    private fun requireSuccessful(response: HttpResponse<String>, action: String) {
        if (response.statusCode() !in 200..299) {
            throw PlatformCommandHttpException(
                "Failed to $action (statusCode=${response.statusCode()})"
            )
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

class PlatformCommandHttpException(message: String) : RuntimeException(message)

@JsonClass(generateAdapter = true) internal data class LeaseResponse(val command: LeaseCommand?)

@JsonClass(generateAdapter = true)
internal data class LeaseCommand(
    val id: String,
    val command: String,
    val queuedAt: String,
    val leaseToken: String,
) {
    fun toLease(): PlatformCommandLease =
        PlatformCommandLease(
            id = id,
            command = command,
            queuedAt = queuedAt,
            leaseToken = leaseToken,
        )
}

@JsonClass(generateAdapter = true)
internal data class ResultRequest(val leaseToken: String, val status: String, val message: String)

private fun PlatformCommandResult.toRequest(): ResultRequest =
    ResultRequest(leaseToken = leaseToken, status = status.wireValue, message = message)

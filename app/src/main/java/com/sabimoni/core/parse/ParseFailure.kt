package com.sabimoni.core.parse

import com.sabimoni.core.ai.MissingApiKeyException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

/**
 * Whether a failed parse is worth trying again. See
 * docs/adr/0017-parse-worker-and-failure-policy.md — the cost of getting this wrong in
 * either direction is real: retrying a permanent failure burns free-tier quota in a
 * loop, and failing a transient one loses the entry until the user notices.
 */
sealed interface ParseFailure {

    val reason: String

    /** Worth retrying unchanged: the network, the rate limiter, or Google's servers. */
    data class Transient(override val reason: String) : ParseFailure

    /** Retrying cannot help. Something has to change first, usually by the user. */
    data class Permanent(override val reason: String) : ParseFailure
}

fun Throwable.toParseFailure(): ParseFailure = when (this) {
    is MissingApiKeyException ->
        ParseFailure.Permanent("No AI key yet — add one in Settings")

    is ClientRequestException -> when (response.status.value) {
        HTTP_TOO_MANY_REQUESTS ->
            ParseFailure.Transient("Free-tier rate limit reached")
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN ->
            ParseFailure.Permanent("AI key was rejected — check it in Settings")
        else ->
            ParseFailure.Permanent("Request rejected (HTTP ${response.status.value})")
    }

    is ServerResponseException ->
        ParseFailure.Transient("Gemini is unavailable (HTTP ${response.status.value})")

    is HttpRequestTimeoutException,
    is ConnectTimeoutException,
    is SocketTimeoutException,
    is UnresolvedAddressException,
    is IOException,
    ->
        ParseFailure.Transient("No connection")

    is SerializationException ->
        ParseFailure.Permanent("Could not read the AI response")

    else ->
        ParseFailure.Permanent(message ?: "Could not be interpreted")
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

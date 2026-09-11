package com.sabimoni.core.ai.gemini

import com.google.common.truth.Truth.assertThat
import com.sabimoni.core.ai.MissingApiKeyException
import com.sabimoni.core.ai.ParseRequest
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.MoneySource
import com.sabimoni.core.parse.ParseFailure
import com.sabimoni.core.parse.toParseFailure
import com.sabimoni.core.security.ApiKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Test
import java.time.LocalDate

class GeminiParsingProviderTest {

    private val today = LocalDate.of(2026, 9, 9)

    private val request = ParseRequest(
        rawText = "took a taxi for 500, gave 5000 to choir",
        categories = listOf("Transport", "Groups"),
        groups = listOf("Choir"),
        today = today,
    )

    @Test
    fun `splits a compound message into one draft per entry`() = runTest {
        val provider = providerReturning(
            envelope(
                """{"amount":500,"direction":"EXPENSE","category":"Transport",""" +
                    """"note":"taxi to work","source":"CASH","date":"2026-09-09"}""",
                """{"amount":5000,"direction":"EXPENSE","category":"Groups",""" +
                    """"note":"gave to choir","group":"Choir","source":"MOMO"}""",
            ),
        )

        val drafts = provider.parse(request).getOrThrow()

        assertThat(drafts).hasSize(2)
        assertThat(drafts[0].amountXaf).isEqualTo(500L)
        assertThat(drafts[0].direction).isEqualTo(Direction.EXPENSE)
        assertThat(drafts[0].category).isEqualTo("Transport")
        assertThat(drafts[0].occurredOn).isEqualTo(today)
        assertThat(drafts[1].amountXaf).isEqualTo(5000L)
        assertThat(drafts[1].matchedGroup).isEqualTo("Choir")
        assertThat(drafts[1].moneySource).isEqualTo(MoneySource.MOMO)
    }

    @Test
    fun `a missing date is left for the caller to default, not guessed here`() = runTest {
        val provider = providerReturning(
            envelope("""{"amount":1500,"direction":"EXPENSE"}"""),
        )

        val drafts = provider.parse(request).getOrThrow()

        assertThat(drafts.single().occurredOn).isNull()
    }

    @Test
    fun `an unparseable date is dropped rather than failing the whole message`() = runTest {
        val provider = providerReturning(
            envelope("""{"amount":1500,"direction":"EXPENSE","date":"last tuesday"}"""),
        )

        assertThat(provider.parse(request).getOrThrow().single().occurredOn).isNull()
    }

    @Test
    fun `a message with nothing to log parses to zero drafts, not an error`() = runTest {
        val provider = providerReturning(envelope())

        assertThat(provider.parse(request).getOrThrow()).isEmpty()
    }

    @Test
    fun `no api key fails permanently before any request is made`() = runTest {
        val provider = GeminiParsingProvider(
            client = clientReturning(HttpStatusCode.OK, envelope()),
            apiKeyStore = FakeApiKeyStore(key = null),
        )

        val error = provider.parse(request).exceptionOrNull()

        assertThat(error).isInstanceOf(MissingApiKeyException::class.java)
        assertThat(error!!.toParseFailure()).isInstanceOf(ParseFailure.Permanent::class.java)
    }

    @Test
    fun `a rejected key is permanent — retrying cannot fix it`() = runTest {
        val provider = providerReturning(body = "unauthorized", status = HttpStatusCode.Unauthorized)

        val failure = provider.parse(request).exceptionOrNull()!!.toParseFailure()

        assertThat(failure).isInstanceOf(ParseFailure.Permanent::class.java)
        assertThat(failure.reason).contains("Settings")
    }

    @Test
    fun `a rate limit is transient — back off rather than burn the daily cap`() = runTest {
        val provider = providerReturning(body = "slow down", status = HttpStatusCode.TooManyRequests)

        assertThat(provider.parse(request).exceptionOrNull()!!.toParseFailure())
            .isInstanceOf(ParseFailure.Transient::class.java)
    }

    @Test
    fun `a server error is transient`() = runTest {
        val provider = providerReturning(body = "boom", status = HttpStatusCode.ServiceUnavailable)

        assertThat(provider.parse(request).exceptionOrNull()!!.toParseFailure())
            .isInstanceOf(ParseFailure.Transient::class.java)
    }

    @Test
    fun `the key travels in a header, never in the url`() = runTest {
        var sentUrl = ""
        var sentKey: String? = null

        val engine = MockEngine { call ->
            sentUrl = call.url.toString()
            sentKey = call.headers["x-goog-api-key"]
            respond(
                envelope(),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        GeminiParsingProvider(httpClient(engine), FakeApiKeyStore("secret-key"))
            .parse(request)

        assertThat(sentKey).isEqualTo("secret-key")
        assertThat(sentUrl).doesNotContain("secret-key")
    }

    // --- helpers ---------------------------------------------------------------

    private fun providerReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = GeminiParsingProvider(
        client = clientReturning(status, body),
        apiKeyStore = FakeApiKeyStore("test-key"),
    )

    private fun clientReturning(status: HttpStatusCode, body: String) = httpClient(
        MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private fun httpClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }

    /** Wraps draft JSON the way Gemini wraps it: a candidate whose part text is the payload. */
    private fun envelope(vararg entries: String): String {
        val payload = """{"entries":[${entries.joinToString(",")}]}"""
        return buildJsonObject {
            putJsonArray("candidates") {
                addJsonObject {
                    putJsonObject("content") {
                        putJsonArray("parts") {
                            addJsonObject { put("text", payload) }
                        }
                    }
                }
            }
        }.toString()
    }
}

private class FakeApiKeyStore(private val key: String?) : ApiKeyStore {
    override fun observeHasKey(): Flow<Boolean> = flowOf(key != null)
    override suspend fun get(): String? = key
    override suspend fun put(key: String) = Unit
    override suspend fun clear() = Unit
}

package com.sabimoni.core.ai.gemini

import com.sabimoni.core.ai.AiParsingProvider
import com.sabimoni.core.ai.MissingApiKeyException
import com.sabimoni.core.ai.ParseRequest
import com.sabimoni.core.ai.TransactionDraft
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.MoneySource
import com.sabimoni.core.security.ApiKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google AI Studio (Gemini) implementation. See
 * docs/adr/0016-gemini-as-primary-ai-provider.md.
 */
@Singleton
class GeminiParsingProvider @Inject constructor(
    private val client: HttpClient,
    private val apiKeyStore: ApiKeyStore,
) : AiParsingProvider {

    override suspend fun parse(request: ParseRequest): Result<List<TransactionDraft>> =
        runCatching {
            val apiKey = apiKeyStore.get() ?: throw MissingApiKeyException()

            val response: GeminiResponse = client.post("$BASE_URL/$MODEL:generateContent") {
                // Header rather than a ?key= query param, so the credential cannot
                // leak through a URL in a log or trace.
                header(API_KEY_HEADER, apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiRequest(
                        systemInstruction = GeminiContent(
                            parts = listOf(GeminiPart(systemPrompt(request))),
                        ),
                        contents = listOf(
                            GeminiContent(
                                role = "user",
                                parts = listOf(GeminiPart(request.rawText)),
                            ),
                        ),
                        generationConfig = GeminiGenerationConfig(
                            responseSchema = DRAFT_RESPONSE_SCHEMA,
                        ),
                    ),
                )
            }.body()

            val content = response.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text
                ?: error("Gemini response contained no candidates")

            json.decodeFromString<DraftEnvelope>(content)
                .entries
                .map { it.toDraft() }
        }

    private fun systemPrompt(request: ParseRequest): String = buildString {
        appendLine(
            "You convert informal Cameroonian personal-finance notes into structured entries.",
        )
        appendLine("Today is ${request.today}. The currency is XAF (CFA franc), which has no decimals.")
        appendLine()
        appendLine("Known categories: ${request.categories.joinToString(", ").ifEmpty { "none" }}")
        appendLine("Known groups: ${request.groups.joinToString(", ").ifEmpty { "none" }}")
        appendLine()
        appendLine("Rules:")
        appendLine("- amount: a positive whole number of XAF. '5k' means 5000, '2.5k' means 2500.")
        appendLine("- direction: EXPENSE for money going out, INCOME for money coming in.")
        appendLine("- category: pick from the known categories above, or null if none fit.")
        appendLine("- group: match a known group name exactly when the text refers to one, else null.")
        appendLine("- source: CASH, MOMO, CARD or UNKNOWN. MoMo alerts are MOMO.")
        appendLine("- date: ISO yyyy-MM-dd. Resolve relative dates against today; default to today.")
        appendLine("- note: a few words of context, e.g. 'taxi to work'.")
        appendLine(
            "- note must NEVER contain the amount or a currency. The amount is its own " +
                "field, and a copy of it in the note contradicts the entry as soon as the " +
                "user corrects it. For 'sent 1600 to my girlfriend' the note is " +
                "'to my girlfriend', not 'sent 1600 to my girlfriend'.",
        )
        appendLine("- Emit one entry per distinct thing that happened; split compound sentences.")
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MODEL = "gemini-3.5-flash-lite"
        const val API_KEY_HEADER = "x-goog-api-key"
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun DraftDto.toDraft(): TransactionDraft = TransactionDraft(
    amountXaf = amount,
    direction = when (direction.uppercase()) {
        "INCOME" -> Direction.INCOME
        else -> Direction.EXPENSE
    },
    category = category?.takeIf { it.isNotBlank() },
    note = note?.takeIf { it.isNotBlank() },
    matchedGroup = group?.takeIf { it.isNotBlank() },
    moneySource = when (source?.uppercase()) {
        "CASH" -> MoneySource.CASH
        "MOMO" -> MoneySource.MOMO
        "CARD" -> MoneySource.CARD
        else -> MoneySource.UNKNOWN
    },
    occurredOn = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
)

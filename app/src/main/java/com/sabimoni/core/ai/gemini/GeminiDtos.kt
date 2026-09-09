package com.sabimoni.core.ai.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig,
)

@Serializable
internal data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null,
)

@Serializable
internal data class GeminiPart(
    val text: String,
)

@Serializable
internal data class GeminiGenerationConfig(
    val temperature: Double = 0.0,
    val responseMimeType: String = "application/json",
    val responseSchema: JsonObject? = null,
)

@Serializable
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

/** The shape the model must return. */
@Serializable
internal data class DraftEnvelope(
    val entries: List<DraftDto> = emptyList(),
)

@Serializable
internal data class DraftDto(
    val amount: Long,
    val direction: String,
    val category: String? = null,
    val note: String? = null,
    val group: String? = null,
    val source: String? = null,
    val date: String? = null,
)

/**
 * Declaring `amount` as INTEGER makes the API reject a decimal amount, which is the
 * exact failure docs/adr/0006-money-as-integer-xaf.md guards against.
 */
internal val DRAFT_RESPONSE_SCHEMA: JsonObject = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
        putJsonObject("entries") {
            put("type", "ARRAY")
            putJsonObject("items") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("amount") { put("type", "INTEGER") }
                    putJsonObject("direction") {
                        put("type", "STRING")
                        putJsonArray("enum") {
                            add("INCOME")
                            add("EXPENSE")
                        }
                    }
                    putJsonObject("category") {
                        put("type", "STRING")
                        put("nullable", true)
                    }
                    putJsonObject("note") {
                        put("type", "STRING")
                        put("nullable", true)
                    }
                    putJsonObject("group") {
                        put("type", "STRING")
                        put("nullable", true)
                    }
                    putJsonObject("source") {
                        put("type", "STRING")
                        putJsonArray("enum") {
                            add("CASH")
                            add("MOMO")
                            add("CARD")
                            add("UNKNOWN")
                        }
                    }
                    putJsonObject("date") { put("type", "STRING") }
                }
                putJsonArray("required") {
                    add("amount")
                    add("direction")
                }
            }
        }
    }
    putJsonArray("required") { add("entries") }
}

package com.sabimoni.core.ai

import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.MoneySource
import java.time.LocalDate

/**
 * The single interface all AI parsing goes through. See
 * docs/adr/0009-ai-parsing-provider-abstraction.md — no vendor concept may appear in
 * these types, or swapping providers stops being a one-line change.
 */
interface AiParsingProvider {
    suspend fun parse(request: ParseRequest): Result<List<TransactionDraft>>
}

/**
 * Everything sent off-device for one parse, and deliberately nothing more: the text
 * plus the names needed to resolve references like "choir" or "yesterday".
 */
data class ParseRequest(
    val rawText: String,
    val categories: List<String>,
    val groups: List<String>,
    val today: LocalDate,
)

data class TransactionDraft(
    val amountXaf: Long,
    val direction: Direction,
    val category: String? = null,
    val note: String? = null,
    val matchedGroup: String? = null,
    val moneySource: MoneySource = MoneySource.UNKNOWN,
    val occurredOn: LocalDate? = null,
)

class MissingApiKeyException : Exception("No AI provider API key has been configured")

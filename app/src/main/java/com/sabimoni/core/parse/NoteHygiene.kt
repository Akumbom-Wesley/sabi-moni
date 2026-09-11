package com.sabimoni.core.parse

/**
 * Separators a model might group digits with: dot, comma, apostrophe, plain space,
 * no-break space, and the narrow no-break space `Money.format` itself emits.
 */
private const val SEPARATORS = ".,'   "

/**
 * A run of digits, possibly grouped. Deliberately greedy and permissive: a token is only
 * removed if it *equals* the amount, so over-matching costs a miss, never a wrong edit.
 */
private val NUMBER = Regex("[0-9][0-9$SEPARATORS]*[0-9]|[0-9]")

/** A currency word left dangling once the number in front of it is gone. */
private val TRAILING_CURRENCY = Regex("^\\s*(FCFA|XAF|CFA|francs?)\\b", RegexOption.IGNORE_CASE)

private val WHITESPACE = Regex("\\s+")

/**
 * Removes the amount from a note that merely restates it.
 *
 * The model is asked not to put the amount in the note, because the amount is already its
 * own field — but a prompt is advice, not a constraint, and the response schema cannot
 * express "no amount in here". So the boundary enforces it, the same way non-positive
 * amounts are filtered rather than trusted (ADR-0017).
 *
 * It matters because the two copies diverge the instant the user corrects one: "sent 1600
 * to my girlfriend", with the amount fixed to 1500, leaves the note asserting a number
 * that is no longer true. Storing the fact once is the only version that cannot go stale.
 *
 * Only applied to model output. A note the *user* typed is left exactly as typed, amount
 * and all — that is their sentence, not a duplicated field.
 *
 * Returns null when nothing but the amount was in there.
 */
fun stripRestatedAmount(note: String?, amountXaf: Long): String? {
    if (note.isNullOrBlank()) return null

    val stripped = buildString {
        var cursor = 0
        for (match in NUMBER.findAll(note)) {
            if (match.range.first < cursor) continue
            val digits = match.value.filterNot { it in SEPARATORS }
            if (digits.toLongOrNull() != amountXaf) continue

            append(note, cursor, match.range.first)
            cursor = match.range.last + 1
            TRAILING_CURRENCY.find(note.substring(cursor))?.let { cursor += it.value.length }
        }
        append(note, cursor, note.length)
    }

    return stripped
        .replace(WHITESPACE, " ")
        .trim()
        .trim(',', ';', ':', '-', '.')
        .trim()
        .takeIf { it.isNotEmpty() }
}

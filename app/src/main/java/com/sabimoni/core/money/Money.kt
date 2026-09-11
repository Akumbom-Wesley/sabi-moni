package com.sabimoni.core.money

/**
 * An amount in whole XAF. See docs/adr/0006-money-as-integer-xaf.md — the CFA franc
 * has no minor unit, so there is deliberately no decimal handling and no division.
 */
@JvmInline
value class Money(val xaf: Long) : Comparable<Money> {

    val isZero: Boolean get() = xaf == 0L
    val isNegative: Boolean get() = xaf < 0L

    operator fun plus(other: Money): Money = Money(xaf + other.xaf)

    operator fun minus(other: Money): Money = Money(xaf - other.xaf)

    operator fun times(factor: Int): Money = Money(xaf * factor)

    operator fun unaryMinus(): Money = Money(-xaf)

    override fun compareTo(other: Money): Int = xaf.compareTo(other.xaf)

    companion object {
        val ZERO = Money(0L)
    }
}

fun Iterable<Money>.sum(): Money = Money(sumOf { it.xaf })

private const val NARROW_NO_BREAK_SPACE = ' '

/** Formats as `12 500 FCFA`, or `12 500` when [withCurrency] is false. */
fun Money.format(withCurrency: Boolean = true): String {
    val grouped = groupThousands(xaf)
    return if (withCurrency) "$grouped FCFA" else grouped
}

private fun groupThousands(value: Long): String {
    val digits = value.toString().removePrefix("-")
    val builder = StringBuilder(digits.length + digits.length / 3)
    digits.forEachIndexed { index, char ->
        if (index > 0 && (digits.length - index) % 3 == 0) {
            builder.append(NARROW_NO_BREAK_SPACE)
        }
        builder.append(char)
    }
    return if (value < 0) "-$builder" else builder.toString()
}

private val THOUSANDS_SEPARATORS = charArrayOf(
    ' ',
    NARROW_NO_BREAK_SPACE,
    '\u00A0',
    ',',
    '.',
    '\'',
)

/**
 * Reads an amount a human typed into a form (FR1.4, FR1.6). XAF has no minor unit
 * (ADR-0006), so a dot or comma can only be a thousands separator — `1.500`, `1,500` and
 * `1 500` are all 1500, and there is no reading under which any of them is one and a half.
 *
 * Returns null for anything that is not a positive whole amount, so a half-typed field
 * disables Save rather than saving a guess.
 */
fun parseMoney(input: String): Money? {
    val digits = input.filterNot { it in THOUSANDS_SEPARATORS }
    if (digits.isEmpty() || !digits.all(Char::isDigit)) return null
    val xaf = digits.toLongOrNull() ?: return null
    return if (xaf > 0L) Money(xaf) else null
}

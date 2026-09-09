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

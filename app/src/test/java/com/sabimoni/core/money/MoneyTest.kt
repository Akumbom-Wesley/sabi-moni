package com.sabimoni.core.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {

    @Test
    fun parseMoney_readsPlainDigits() {
        assertThat(parseMoney("1500")).isEqualTo(Money(1500))
    }

    @Test
    fun parseMoney_treatsDotsCommasAndSpacesAsThousandsSeparators() {
        // XAF has no minor unit (ADR-0006), so none of these can mean one and a half.
        assertThat(parseMoney("1.500")).isEqualTo(Money(1500))
        assertThat(parseMoney("1,500")).isEqualTo(Money(1500))
        assertThat(parseMoney("1 500")).isEqualTo(Money(1500))
        assertThat(parseMoney("12'500")).isEqualTo(Money(12_500))
    }

    @Test
    fun parseMoney_roundTripsWhatFormatProduces() {
        // Guards the pair rather than the separator character: the editor shows a formatted
        // amount and has to be able to read its own output back.
        val formatted = Money(1_234_567).format(withCurrency = false)

        assertThat(parseMoney(formatted)).isEqualTo(Money(1_234_567))
    }

    @Test
    fun parseMoney_rejectsAnythingThatIsNotAPositiveWholeAmount() {
        assertThat(parseMoney("")).isNull()
        assertThat(parseMoney("   ")).isNull()
        assertThat(parseMoney("0")).isNull()
        assertThat(parseMoney("-500")).isNull()
        assertThat(parseMoney("500 FCFA")).isNull()
        assertThat(parseMoney("five hundred")).isNull()
        assertThat(parseMoney("12a5")).isNull()
    }

    @Test
    fun parseMoney_rejectsAnAmountTooLargeForALong() {
        assertThat(parseMoney("9".repeat(25))).isNull()
    }

    @Test
    fun format_groupsThousandsAndKeepsTheMinus() {
        assertThat(Money(12_500).format(withCurrency = false).filter(Char::isDigit))
            .isEqualTo("12500")
        assertThat(Money(-12_500).format()).startsWith("-")
    }
}

package com.sabimoni.core.parse

import com.google.common.truth.Truth.assertThat
import com.sabimoni.core.money.Money
import com.sabimoni.core.money.format
import org.junit.Test

class NoteHygieneTest {

    @Test
    fun stripsTheAmountTheModelRestatedInProse() {
        // The case that prompted ADR-0020: correcting 1600 to 1500 left the note lying.
        assertThat(stripRestatedAmount("sent 1600 to my girlfriend", 1600))
            .isEqualTo("sent to my girlfriend")
    }

    @Test
    fun stripsTheAmountHoweverItWasGrouped() {
        assertThat(stripRestatedAmount("lunch 1,500 at the canteen", 1500))
            .isEqualTo("lunch at the canteen")
        assertThat(stripRestatedAmount("lunch 1 500 at the canteen", 1500))
            .isEqualTo("lunch at the canteen")
        assertThat(stripRestatedAmount("lunch 1.500 at the canteen", 1500))
            .isEqualTo("lunch at the canteen")
    }

    @Test
    fun stripsAnAmountFormattedTheWayTheAppItselfFormatsIt() {
        val asShown = Money(12_500).format(withCurrency = false)

        assertThat(stripRestatedAmount("paid $asShown for the room", 12_500))
            .isEqualTo("paid for the room")
    }

    @Test
    fun takesTheDanglingCurrencyWordWithIt() {
        assertThat(stripRestatedAmount("sent 1600 FCFA to Ma", 1600)).isEqualTo("sent to Ma")
        assertThat(stripRestatedAmount("sent 1600 XAF to Ma", 1600)).isEqualTo("sent to Ma")
        assertThat(stripRestatedAmount("1600 francs for bread", 1600)).isEqualTo("for bread")
    }

    @Test
    fun returnsNullWhenTheNoteWasNothingButTheAmount() {
        assertThat(stripRestatedAmount("1600", 1600)).isNull()
        assertThat(stripRestatedAmount("1600 FCFA", 1600)).isNull()
    }

    @Test
    fun leavesNumbersThatAreNotTheAmountAlone() {
        // A quantity, a bus number, a date — none of them a duplicated field.
        assertThat(stripRestatedAmount("3 loaves of bread", 1600)).isEqualTo("3 loaves of bread")
        assertThat(stripRestatedAmount("taxi 90 to Bonaberi", 1600)).isEqualTo("taxi 90 to Bonaberi")
    }

    @Test
    fun leavesANoteWithNoNumbersUntouched() {
        assertThat(stripRestatedAmount("to my girlfriend", 1600)).isEqualTo("to my girlfriend")
    }

    @Test
    fun stripsEveryOccurrenceWhenTheModelSaysItTwice() {
        assertThat(stripRestatedAmount("1600 sent, 1600 received", 1600))
            .isEqualTo("sent, received")
    }

    @Test
    fun treatsBlankAndNullAlike() {
        assertThat(stripRestatedAmount(null, 1600)).isNull()
        assertThat(stripRestatedAmount("", 1600)).isNull()
        assertThat(stripRestatedAmount("   ", 1600)).isNull()
    }

    @Test
    fun doesNotStripADigitRunThatMerelyContainsTheAmount() {
        // 16000 is not 1600 — a partial match would silently change the meaning.
        assertThat(stripRestatedAmount("sent 16000 to Ma", 1600)).isEqualTo("sent 16000 to Ma")
    }
}

package com.sabimoni.core.parse

import com.google.common.truth.Truth.assertThat
import com.sabimoni.core.ai.MissingApiKeyException
import kotlinx.serialization.SerializationException
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class ParseFailureTest {

    @Test
    fun `no connection is transient`() {
        assertThat(IOException("network is unreachable").toParseFailure())
            .isInstanceOf(ParseFailure.Transient::class.java)
    }

    @Test
    fun `unknown host is transient`() {
        assertThat(UnknownHostException("generativelanguage.googleapis.com").toParseFailure())
            .isInstanceOf(ParseFailure.Transient::class.java)
    }

    @Test
    fun `missing api key is permanent and says where to fix it`() {
        val failure = MissingApiKeyException().toParseFailure()

        assertThat(failure).isInstanceOf(ParseFailure.Permanent::class.java)
        assertThat(failure.reason).contains("Settings")
    }

    @Test
    fun `unreadable response is permanent, so a bad response cannot loop on the free tier`() {
        assertThat(SerializationException("unexpected token").toParseFailure())
            .isInstanceOf(ParseFailure.Permanent::class.java)
    }

    @Test
    fun `an unrecognised failure is permanent rather than retried blindly`() {
        assertThat(IllegalStateException("no candidates").toParseFailure())
            .isInstanceOf(ParseFailure.Permanent::class.java)
    }

    @Test
    fun `a failure with no message still produces a human-readable reason`() {
        val failure = RuntimeException().toParseFailure()

        assertThat(failure.reason).isNotEmpty()
    }
}

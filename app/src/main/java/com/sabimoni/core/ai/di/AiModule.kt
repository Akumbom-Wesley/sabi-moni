package com.sabimoni.core.ai.di

import com.sabimoni.core.ai.AiParsingProvider
import com.sabimoni.core.ai.gemini.GeminiParsingProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HttpModule {

    // No Logging plugin: request bodies carry both the API key header and personal
    // financial text. See docs/adr/0012-ktor-and-kotlinx-serialization.md.
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
        // A parse is one short sentence in and a few JSON objects out. A 60-second
        // request ceiling meant a dead connection took a minute and a half to admit it,
        // all of it spent behind "waiting to be interpreted" — see ADR-0021. 30 seconds
        // is still generous for slow mobile data, which is the normal case here.
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    abstract fun bindAiParsingProvider(impl: GeminiParsingProvider): AiParsingProvider
}

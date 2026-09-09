package com.sabimoni.core.security

import kotlinx.coroutines.flow.Flow

/**
 * Storage for the AI provider API key. See
 * docs/adr/0010-api-key-at-rest-keystore-datastore.md.
 */
interface ApiKeyStore {

    fun observeHasKey(): Flow<Boolean>

    suspend fun get(): String?

    suspend fun put(key: String)

    suspend fun clear()
}

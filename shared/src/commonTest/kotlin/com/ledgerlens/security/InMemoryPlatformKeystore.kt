package com.ledgerlens.security

/**
 * Test-only in-memory implementation of [PlatformKeystore].
 *
 * Used by JVM unit tests where platform keystores are unavailable or undesirable.
 */
class InMemoryPlatformKeystore : PlatformKeystore {
    private var wrappedKek: ByteArray? = null
    private var salt: ByteArray? = null

    override suspend fun storeKek(wrappedKek: ByteArray): Result<Unit> = runCatching {
        this.wrappedKek = wrappedKek.copyOf()
    }

    override suspend fun retrieveKek(): Result<ByteArray?> = runCatching {
        wrappedKek?.copyOf()
    }

    override suspend fun deleteKek(): Result<Unit> = runCatching {
        wrappedKek = null
        salt = null
    }

    override suspend fun storeSalt(salt: ByteArray): Result<Unit> = runCatching {
        this.salt = salt.copyOf()
    }

    override suspend fun retrieveSalt(): Result<ByteArray?> = runCatching {
        salt?.copyOf()
    }

    override fun isHardwareBacked(): Boolean = false
}

package com.ledgerlens.security

/**
 * JVM implementation of createFileEncryption for tests.
 */
actual fun createFileEncryption(): FileEncryption = AesGcmFileEncryption()

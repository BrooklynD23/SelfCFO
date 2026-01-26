package com.ledgerlens.receipts

/**
 * Desktop/JVM implementation of ContactSuggesterFactory.
 *
 * For now, desktop uses a stub implementation (no OS contacts integration).
 */
actual object ContactSuggesterFactory {
    actual fun create(): ContactSuggester = StubContactSuggester()
}

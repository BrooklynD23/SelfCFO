package com.ledgerlens.receipts

actual object ContactSuggesterFactory {
    actual fun create(): ContactSuggester = StubContactSuggester()
}

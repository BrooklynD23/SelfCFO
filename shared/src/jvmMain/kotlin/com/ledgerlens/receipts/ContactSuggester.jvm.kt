package com.ledgerlens.receipts

/**
 * JVM shared module - ContactSuggester architecture note.
 *
 * Do not provide an `actual` ContactSuggesterFactory here.
 *
 * `androidMain` dependsOn `jvmMain`, so any `actual` in `jvmMain` would be visible
 * to Android compilation and conflict with the Android-specific `actual`.
 *
 * The Desktop/JVM `actual` is provided in `desktopMain`.
 * The Android `actual` is provided in `androidMain`.
 */
@Suppress("unused")
private const val MODULE_DOC_MARKER = "ContactSuggester architecture documentation"

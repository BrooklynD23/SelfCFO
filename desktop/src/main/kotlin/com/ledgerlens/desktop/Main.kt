package com.ledgerlens.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ledgerlens.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LedgerLens"
    ) {
        App()
    }
}

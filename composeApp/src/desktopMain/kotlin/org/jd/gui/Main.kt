/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jd.gui.ui.App

/**
 * Main entry point for JD-GUI Desktop application.
 * Uses Compose Desktop for modern, cross-platform UI.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "JD-GUI - Java Decompiler",
        state = rememberWindowState(
            size = DpSize(1200.dp, 800.dp)
        )
    ) {
        App()
    }
}

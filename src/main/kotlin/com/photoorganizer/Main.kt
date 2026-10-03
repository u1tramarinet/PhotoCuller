package com.photoorganizer

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.photoorganizer.services.ProcessManager
import com.photoorganizer.ui.MainApp

fun main() = application {
    // Start Python FastAPI engine subprocess
    ProcessManager.startBackendEngine()

    val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))

    Window(
        onCloseRequest = {
            ProcessManager.stopBackendEngine()
            exitApplication()
        },
        title = "Photo Organizer Windows Application",
        state = windowState
    ) {
        MainApp()
    }
}

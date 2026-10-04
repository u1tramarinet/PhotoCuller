package com.photoorganizer

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.photoorganizer.services.ProcessManager
import com.photoorganizer.ui.MainApp

fun main() = application {
    // Start Python FastAPI engine subprocess
    ProcessManager.startBackendEngine()

    val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
    var showExitDialog by remember { mutableStateOf(false) }

    val handleExit = {
        ProcessManager.stopBackendEngine()
        exitApplication()
    }

    Window(
        onCloseRequest = {
            showExitDialog = true
        },
        title = "Photo Organizer Windows Application",
        state = windowState
    ) {
        MenuBar {
            Menu("ファイル (File)") {
                Item("終了 (Exit)", onClick = { showExitDialog = true })
            }
        }

        MainApp()

        if (showExitDialog) {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text("アプリの終了確認") },
                    text = { Text("写真整理アプリを終了してもよろしいですか？") },
                    confirmButton = {
                        Button(onClick = handleExit) {
                            Text("終了する")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showExitDialog = false }) {
                            Text("キャンセル")
                        }
                    }
                )
            }
        }
    }
}

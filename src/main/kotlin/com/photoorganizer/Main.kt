package com.photoorganizer

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

import com.photoorganizer.services.ApiService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main() = application {
    // Start Python FastAPI engine subprocess
    ProcessManager.startBackendEngine()

    val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
    var showExitDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    val handleExit = {
        ProcessManager.stopBackendEngine()
        exitApplication()
    }

    val handleResetDatabase = {
        GlobalScope.launch {
            ApiService.resetDatabase()
            refreshKey++
        }
    }

    Window(
        onCloseRequest = {
            showExitDialog = true
        },
        title = "フォト管",
        state = windowState
    ) {
        MenuBar {
            Menu("ファイル") {
                Item("データベースのリセット (デバッグ用)", onClick = { showResetDialog = true })
                Item("終了", onClick = { showExitDialog = true })
            }
        }

        key(refreshKey) {
            MainApp()
        }

        if (showResetDialog) {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("データベースのリセット") },
                    text = { Text("スキャン済みのすべてのデータベース情報を消去しますか？（※ローカルの画像ファイル自体は削除されません）") },
                    confirmButton = {
                        Button(onClick = {
                            handleResetDatabase()
                            showResetDialog = false
                        }) {
                            Text("リセット実行")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showResetDialog = false }) {
                            Text("キャンセル")
                        }
                    }
                )
            }
        }

        if (showExitDialog) {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text("アプリの終了確認") },
                    text = { Text("フォト管を終了してもよろしいですか？") },
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

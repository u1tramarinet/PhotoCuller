package com.photoorganizer.services

import java.io.File
import java.util.concurrent.TimeUnit

object ProcessManager {
    private var pythonProcess: Process? = null

    fun startBackendEngine() {
        if (pythonProcess != null && pythonProcess!!.isAlive) {
            return
        }

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val pythonCmds = if (isWindows) listOf("python", "python3", "py") else listOf("python3", "python")

        val backendDir = File("backend")
        var startedProcess: Process? = null

        for (cmd in pythonCmds) {
            try {
                val pb = ProcessBuilder(cmd, "main.py")
                pb.directory(backendDir)
                pb.redirectErrorStream(true)
                startedProcess = pb.start()
                if (startedProcess.isAlive) {
                    pythonProcess = startedProcess
                    break
                }
            } catch (e: Exception) {
                // Try next command
            }
        }

        if (pythonProcess != null) {
            Runtime.getRuntime().addShutdownHook(Thread {
                stopBackendEngine()
            })
        } else {
            println("Failed to start Python backend engine with available commands.")
        }
    }

    fun stopBackendEngine() {
        pythonProcess?.let {
            if (it.isAlive) {
                it.destroy()
                try {
                    if (!it.waitFor(3, TimeUnit.SECONDS)) {
                        it.destroyForcibly()
                    }
                } catch (e: Exception) {
                    it.destroyForcibly()
                }
            }
        }
        pythonProcess = null
    }
}

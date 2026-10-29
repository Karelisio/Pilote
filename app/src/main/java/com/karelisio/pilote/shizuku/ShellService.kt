package com.karelisio.pilote.shizuku

import android.content.Context
import androidx.annotation.Keep
import kotlin.system.exitProcess

/**
 * Runs inside the Shizuku-provided process (shell or root uid). Works with any Shizuku
 * implementation (official Shizuku, Shevery, forks) since it only relies on the Shizuku API.
 */
@Keep
class ShellService() : IShellService.Stub() {

    @Keep
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return "$code\n$output"
    }
}

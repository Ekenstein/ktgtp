package com.github.ekenstein.ktgtp

import java.nio.file.Path
import kotlin.time.Duration

interface KatagoConsole : GtpConsole {
    fun analyze(stopAt: Duration)
}

private class DefaultKatagoConsole(private val gtpConsole: GtpConsole) : KatagoConsole {
    override fun analyze(stopAt: Duration) {
        send(GtpCommand("kata-analyze"), null)
    }

    override fun send(command: GtpCommand, timeout: Duration?): GtpResponse<String> = gtpConsole.send(command, timeout)

    override fun close() {
        gtpConsole.close()
    }
}

fun katago(path: Path, modelPath: Path, block: KatagoConsole.() -> Unit) {
    gtpConsole(path, "gtp", "-model", modelPath.toString()) {
        val katago = DefaultKatagoConsole(this)
        katago.block()
    }
}

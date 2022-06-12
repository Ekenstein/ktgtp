package com.github.ekenstein.ktgtp

import java.io.BufferedReader
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface GtpConsole {
    fun send(command: GtpCommand, timeout: Duration): GtpResponse<String>
}

private class GtpConsoleImpl(val process: Process) : GtpConsole {
    private val stdin = process.inputStream.bufferedReader()
    private val stdout = process.outputStream.bufferedWriter()
    private var stop = false

    private val responses = ConcurrentLinkedQueue<String>()

    private val readerThread = thread(start = true) {
        tailrec fun BufferedReader.read(builder: StringBuilder): String {
            if (stop) {
                return builder.toString()
            }
            return when (val i = read()) {
                -1 -> {
                    builder.toString()
                }
                else -> {
                    val c = i.toChar()
                    if (c in listOf('\n', '\r') && (builder.endsWith('\n') || builder.endsWith('\r'))) {
                        builder.toString()
                    } else {
                        read(builder.append(c))
                    }
                }
            }
        }

        while (!stop) {
            val response = stdin.read(StringBuilder())
            if (response.isNotBlank()) {
                responses.add(response)
            }
        }
    }

    private val lock = Semaphore(1)

    override fun send(command: GtpCommand, timeout: Duration): GtpResponse<String> {
        tailrec fun pollResponse(): String {
            return when (val response = responses.poll()) {
                null -> pollResponse()
                else -> response.trim()
            }
        }

        lock.acquire()
        return try {
            stdout.write(command.encodeToString())
            stdout.flush()
            val response = pollResponse()

            if (response.startsWith("=")) {
                GtpResponse.Success(response.substring(1).trim())
            } else if (response.startsWith("?")) {
                GtpResponse.Failure(response.substring(1).trim())
            } else {
                error("Couldn't recognize the response '$response'")
            }
        } finally {
            lock.release()
        }
    }

    fun stop() {
        send(GtpCommand("quit"), 1.seconds)
        stop = true
        readerThread.interrupt()
        readerThread.join()

        process.destroy()
    }
}

@OptIn(ExperimentalContracts::class)
fun gtpConsole(command: String, vararg args: String, block: GtpConsole.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val process = ProcessBuilder(command, *args)
        .apply { redirectErrorStream(true) }
        .start()
    val console = GtpConsoleImpl(process).apply(block)
    console.stop()
}

@OptIn(ExperimentalContracts::class)
fun gtpConsole(path: Path, vararg args: String, block: GtpConsole.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val process = ProcessBuilder(path.name, *args)
        .apply { redirectErrorStream(true) }
        .directory(path.parent.toFile())
        .start()

    val console = GtpConsoleImpl(process)
    console.block()
    console.stop()
}

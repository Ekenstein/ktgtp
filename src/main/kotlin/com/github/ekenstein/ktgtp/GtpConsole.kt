package com.github.ekenstein.ktgtp

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.name
import kotlin.time.Duration

/**
 * Represents the scope of a gtp engine.
 */
interface GtpConsole : Closeable {
    /**
     * Send the [command] to the gtp engine and returns the response of the gtp engine.
     * Will throw if the gtp engine hasn't responded within the given [timeout] duration.
     *
     * @param command The command to send to the gtp engine.
     * @param timeout The max duration to wait for the gtp engine to respond.
     * @return [GtpResponse] from the engine which can represent either a failure or a success.
     */
    fun send(command: GtpCommand, timeout: Duration): GtpResponse<String>
}

abstract class BaseGtpConsole(
    private val stdin: BufferedReader,
    private val stdout: BufferedWriter
) : GtpConsole {
    private val responses = ConcurrentLinkedQueue<String>()
    private var stop = false

    private tailrec fun BufferedReader.read(builder: StringBuilder): String {
        return if (stop) {
            builder.toString()
        } else {
            when (val i = read()) {
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
    }

    private val readerThread = thread(start = true) {
        while (!stop) {
            val response = stdin.read(StringBuilder())
            if (response.isNotBlank()) {
                responses.add(response)
            }
        }
    }

    private val lock = Semaphore(1)

    override fun send(command: GtpCommand, timeout: Duration): GtpResponse<String> {
        lock.acquire()
        return try {
            sendCommand(command)
        } finally {
            lock.release()
        }
    }

    private fun sendCommand(command: GtpCommand): GtpResponse<String> {
        tailrec fun pollResponse(): String {
            return when (val response = responses.poll()) {
                null -> pollResponse()
                else -> response.trim()
            }
        }

        val serialized = command.encodeToString()
        stdout.write(serialized)
        stdout.flush()
        val response = pollResponse()

        return if (response.startsWith("=")) {
            GtpResponse.Success(response.substring(1).trim())
        } else if (response.startsWith("?")) {
            GtpResponse.Failure(response.substring(1).trim())
        } else {
            error("Couldn't recognize the response '$response'")
        }
    }

    override fun close() {
        if (stop) {
            return
        }

        lock.acquire()
        try {
            if (!stop) {
                sendCommand(GtpCommand("quit"))
                stop = true
                readerThread.interrupt()
                try {
                    readerThread.join()
                } catch (_: InterruptedException) { }
            }
        } finally {
            lock.release()
        }
    }
}

class SocketGtpConsole(
    private val socket: Socket
) : BaseGtpConsole(socket.getInputStream().bufferedReader(), socket.getOutputStream().bufferedWriter()) {
    override fun close() {
        super.close()
        socket.close()
    }
}

class PipedGtpConsole(
    private val process: Process
) : BaseGtpConsole(process.inputStream.bufferedReader(), process.outputStream.bufferedWriter()) {
    override fun close() {
        super.close()
        process.destroy()
    }
}

/**
 * Start a console for a GTP engine. When exiting the given [block], the gtp engine will be terminated.
 * @param command The command to start the gtp engine. E.g. "gnugo"
 * @param args The args to pass to the gtp engine. E.g. "--mode", "gtp"
 * @param block The scope of the gtp engine.
 */
@OptIn(ExperimentalContracts::class)
inline fun gtpConsole(command: String, vararg args: String, block: GtpConsole.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val process = ProcessBuilder(command, *args)
        .apply { redirectErrorStream(true) }
        .start()

    PipedGtpConsole(process).use(block)
}

/**
 * Start a console for a GTP engine. When exiting the given [block], the gtp engine will be terminated.
 * @param path The path to the gtp engine to start. E.g. Path.of("/home/user/gnugo/gnugo")
 * @param args The args to pass to the gtp engine. E.g. "--mode", "gtp"
 * @param block The scope of the gtp engine.
 */
@OptIn(ExperimentalContracts::class)
inline fun gtpConsole(path: Path, vararg args: String, block: GtpConsole.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val process = ProcessBuilder(path.name, *args)
        .apply { redirectErrorStream(true) }
        .directory(path.parent.toFile())
        .start()

    PipedGtpConsole(process).use(block)
}

/**
 * Starts a console for a GTP engine using sockets. When exiting the given [block], the gtp engine will be terminated.
 */
@OptIn(ExperimentalContracts::class)
inline fun gtpConsole(host: String, port: Int, block: GtpConsole.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val client = Socket(host, port)
    SocketGtpConsole(client).use(block)
}

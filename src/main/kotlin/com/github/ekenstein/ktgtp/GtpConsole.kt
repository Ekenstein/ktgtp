package com.github.ekenstein.ktgtp

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.net.Socket
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.DurationUnit

private const val END_OF_STREAM = -1
private val newLines = listOf('\n', '\r')
private val responseRegex = Regex("""^([=?])(\d+)(\s([.\s\S]*))?""")
private const val GROUP_RESPONSE_TYPE = 1
private const val GROUP_RESPONSE_ID = 2
private const val GROUP_RESPONSE_MESSAGE = 4

/**
 * Represents the scope of a gtp engine.
 */
interface GtpConsole : Closeable {
    /**
     * Send the [command] to the gtp engine and returns the response of the gtp engine.
     * Will throw [IllegalStateException] if the gtp engine hasn't responded within the given [timeout] duration.
     *
     * @param command The command to send to the gtp engine.
     * @param timeout The max duration to wait for the gtp engine to respond. If null, wait until response.
     * @return [GtpResponse] from the engine which can represent either a failure or a success.
     * @throws [GtpException.EngineTimedOut] If the engine hasn't responded within the given [timeout] duration.
     * @throws [GtpException.EngineClosed] If trying to send a command when the engine has been closed.
     */
    fun send(command: GtpCommand, timeout: Duration?): GtpResponse<String>
}

abstract class BaseGtpConsole(
    private val stdin: BufferedReader,
    private val stdout: BufferedWriter
) : GtpConsole {
    private val writeLock = Semaphore(1)
    private val nextId = AtomicInteger(0)
    private val responses = ConcurrentHashMap<Int, GtpResponse<String>>()
    private var isClosed = false

    private tailrec fun BufferedReader.read(builder: StringBuilder): String = if (isClosed) {
        builder.toString()
    } else when (val i = read()) {
        END_OF_STREAM -> builder.toString()
        else -> {
            val c = i.toChar()
            if (c in newLines && newLines.any(builder::endsWith)) {
                builder.toString()
            } else {
                read(builder.append(c))
            }
        }
    }

    private fun parseResponse(string: String): Pair<Int, GtpResponse<String>>? {
        val matches = responseRegex.matchEntire(string)
            ?: return null

        val type = matches.groupValues[GROUP_RESPONSE_TYPE]
        val id = matches.groupValues[GROUP_RESPONSE_ID].toInt()
        val message = matches.groups[GROUP_RESPONSE_MESSAGE]?.value?.trim() ?: ""

        val response = when (type) {
            "?" -> GtpResponse.Failure(message)
            "=" -> GtpResponse.Success(message)
            else -> error("Couldn't recognize the response type '$type'")
        }

        return id to response
    }

    private val readerThread = thread(start = true) {
        while (!isClosed) {
            val string = stdin.read(StringBuilder())
            val response = parseResponse(string)
            if (response != null) {
                responses[response.first] = response.second
            }
        }
    }

    private fun <T> lockWriting(block: () -> T): T {
        writeLock.acquire()
        return try {
            block()
        } finally {
            writeLock.release()
        }
    }

    private fun throwIfClosed() {
        if (isClosed) {
            throw GtpException.EngineClosed
        }
    }

    override fun send(command: GtpCommand, timeout: Duration?): GtpResponse<String> = lockWriting {
        throwIfClosed()
        sendCommand(command, timeout)
    }

    private tailrec fun pollResponse(id: Int, stopAt: Instant?): GtpResponse<String> {
        if (readerThread.isAlive) {
            val response = responses[id]

            return response
                ?: if (stopAt != null && Instant.now() >= stopAt) {
                    throw GtpException.EngineTimedOut
                } else {
                    pollResponse(id, stopAt)
                }
        } else {
            error("The reader thread has unexpectedly died")
        }
    }

    private fun Duration.toInstant() = Instant.now().plusMillis(toLong(DurationUnit.MILLISECONDS))

    private fun sendCommand(command: GtpCommand, timeout: Duration?): GtpResponse<String> {
        val id = nextId.getAndIncrement()
        val serialized = command.encodeToString(id)
        stdout.write(serialized)
        stdout.flush()

        val stopAt = timeout?.toInstant()
        return pollResponse(id, stopAt)
    }

    override fun close() {
        if (isClosed) {
            return
        }

        lockWriting {
            if (!isClosed) {
                sendCommand(GtpCommand("quit"), null)
                isClosed = true
                readerThread.interrupt()
                try {
                    readerThread.join()
                } catch (_: InterruptedException) { }

                stdin.close()
                stdout.close()
            }
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

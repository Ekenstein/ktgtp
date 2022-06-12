package com.github.ekenstein.ktgtp

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val defaultTimeout = 1.seconds

fun GtpConsole.listCommands(timeout: Duration = defaultTimeout) = send(GtpCommand("list_commands"), timeout)
    .map { it.split("\n").toSet() }

fun GtpConsole.play(color: GtpValue.Color, move: GtpValue.Vertex, timeout: Duration = defaultTimeout) = send(
    GtpCommand("play", GtpValue.Move(color, move)),
    timeout
).toUnit()

fun GtpConsole.undo(timeout: Duration = defaultTimeout) = send(GtpCommand("undo"), timeout).toUnit()

fun GtpConsole.protocolVersion(timeout: Duration = defaultTimeout) =
    send(GtpCommand("protocol_version"), timeout).map { it.toInt() }

fun GtpConsole.name(timeout: Duration = defaultTimeout) = send(GtpCommand("name"), timeout)

fun GtpConsole.version(timeout: Duration = defaultTimeout) = send(GtpCommand("version"), timeout)

fun GtpConsole.knownCommand(commandName: String, timeout: Duration = defaultTimeout) =
    send(GtpCommand("known_command", GtpValue.String(commandName)), timeout).map {
        it.toBooleanStrict()
    }

fun GtpConsole.boardSize(boardSize: Int, timeout: Duration = defaultTimeout) =
    send(GtpCommand("boardsize", GtpValue.Int(boardSize)), timeout)

fun GtpConsole.komi(komi: Double, timeout: Duration = defaultTimeout) =
    send(GtpCommand("komi", GtpValue.Float(komi)), timeout).toUnit()

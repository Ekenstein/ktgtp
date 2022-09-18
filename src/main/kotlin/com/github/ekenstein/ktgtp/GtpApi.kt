package com.github.ekenstein.ktgtp

import java.nio.file.Path
import kotlin.time.Duration

private val defaultTimeout: Duration? = null
private val lineSeparator = System.getProperty("line.separator")

/**
 * Asks the gtp engine to list the available commands. On success a list of commands will be sent.
 */
fun GtpConsole.listCommands(timeout: Duration? = defaultTimeout) = send(GtpCommand("list_commands"), timeout)
    .map { it.split(lineSeparator).toSet() }

/**
 * The board configuration and the number of captured stones are reset to the state before the last move.
 * The last move is removed from the move history.
 */
fun GtpConsole.undo(timeout: Duration? = defaultTimeout) = send(GtpCommand("undo"), timeout).toUnit()

fun GtpConsole.protocolVersion(timeout: Duration? = defaultTimeout) =
    send(GtpCommand("protocol_version"), timeout).map { it.toInt() }

fun GtpConsole.name(timeout: Duration? = defaultTimeout) = send(GtpCommand("name"), timeout)

fun GtpConsole.version(timeout: Duration? = defaultTimeout) = send(GtpCommand("version"), timeout)

fun GtpConsole.knownCommand(commandName: String, timeout: Duration? = defaultTimeout) =
    send(GtpCommand("known_command", GtpValue.String(commandName)), timeout).map {
        it.toBooleanStrict()
    }

/**
 * Board size and komi are set to the values given in the sgf file. Board configuration, number of captured stones,
 * and move history are found by replaying the game record up to the position before [moveNumber]
 * or until the end if omitted.
 *
 * @param sgf The path to the sgf file.
 * @param moveNumber Optional move number.
 */
fun GtpConsole.loadSgf(sgf: Path, moveNumber: Int? = null, timeout: Duration? = defaultTimeout) =
    send(
        GtpCommand(
            "loadsgf",
            listOfNotNull(
                GtpValue.String(sgf.toAbsolutePath().toString()),
                moveNumber?.let { GtpValue.Int(it) }
            )
        ),
        timeout
    ).toUnit()

/**
 * A stone of the requested color is played at the requested vertex.
 * The number of captured stones is updated if needed and the move is added to the move history.
 */
fun GtpConsole.play(color: GtpValue.Color, move: GtpValue.Vertex, timeout: Duration? = defaultTimeout) = send(
    GtpCommand("play", GtpValue.Move(color, move)),
    timeout
).toUnit()

/**
 * The board is cleared, the number of captured stones is reset to zero for both colors
 * and the move history is reset to empty.
 */
fun GtpConsole.clearBoard(timeout: Duration? = defaultTimeout) = send(
    GtpCommand("clear_board"),
    timeout
).toUnit()

/**
 * The board size is changed. The board configuration, number of captured stones, and move history become arbitrary.
 */
fun GtpConsole.boardSize(boardSize: Int, timeout: Duration? = defaultTimeout) = send(
    GtpCommand("boardsize", GtpValue.Int(boardSize)),
    timeout
).toUnit()

/**
 * Komi is changed.
 */
fun GtpConsole.komi(komi: Double, timeout: Duration? = defaultTimeout) = send(
    GtpCommand("komi", GtpValue.Float(komi)),
    timeout
).toUnit()

/**
 * Handicap stones are placed on the board
 */
fun GtpConsole.fixedHandicap(numberOfStones: Int, timeout: Duration? = defaultTimeout) =
    send(GtpCommand("fixed_handicap", GtpValue.Int(numberOfStones)), timeout).map {
        it.split(" ").map(GtpValue.Vertex::from).toSet()
    }

/**
 * Handicap stones are placed on the board on the vertices the engine prefers.
 */
fun GtpConsole.placeFreeHandicap(numberOfStones: Int, timeout: Duration? = defaultTimeout) =
    send(GtpCommand("place_free_handicap", GtpValue.Int(numberOfStones)), timeout).map {
        it.split(" ").map(GtpValue.Vertex::from).toSet()
    }

/**
 * Handicap stones are placed on the vertices as requested.
 */
fun GtpConsole.setFreeHandicap(stones: Set<GtpValue.Vertex.Point>, timeout: Duration? = defaultTimeout) = send(
    GtpCommand("set_free_handicap", *stones.toTypedArray()),
    timeout
).toUnit()

/**
 * A stone of the requested color is played where the engine chooses. The number of captured stones is updated
 * if needed and the move is added to the move history.
 */
fun GtpConsole.genMove(color: GtpValue.Color, timeout: Duration? = defaultTimeout) =
    send(GtpCommand("genmove", color), timeout).map {
        GeneratedMove.from(it)
    }

/**
 * This command differs from genmove in that it does not play the generated move.
 */
fun GtpConsole.regGenMove(color: GtpValue.Color, timeout: Duration? = defaultTimeout) =
    send(GtpCommand("reg_genmove", color), timeout).map {
        GeneratedMove.from(it)
    }

/**
 * The engine draws the board as it likes.
 */
fun GtpConsole.showBoard(timeout: Duration? = defaultTimeout) = send(GtpCommand("showboard"), timeout)

/**
 * The time settings are changed.
 * @param mainTime Main time measured in seconds
 * @param byoYomiTime Byo yomi time measured in seconds
 * @param byoYomiStones Number of stones per byo yomi period.
 */
fun GtpConsole.timeSettings(mainTime: Int, byoYomiTime: Int, byoYomiStones: Int, timeout: Duration? = defaultTimeout) =
    send(
        GtpCommand(
            "time_settings",
            GtpValue.Int(mainTime),
            GtpValue.Int(byoYomiTime),
            GtpValue.Int(byoYomiStones)
        ),
        timeout
    ).toUnit()

/**
 * While the main time is counting, the number of remaining stones is given as 0.
 * @param color Color for which the information applies
 * @param time Number of seconds remaining
 * @param stones Number of stones remaining
 */
fun GtpConsole.timeLeft(color: GtpValue.Color, time: Int, stones: Int, timeout: Duration? = defaultTimeout) =
    send(
        GtpCommand(
            "time_left",
            color,
            GtpValue.Int(time),
            GtpValue.Int(stones)
        ),
        timeout
    ).toUnit()

fun GtpConsole.finalScore(timeout: Duration? = defaultTimeout) = send(GtpCommand("final_score"), timeout)

enum class StoneStatus { Alive, Seki, Dead }

fun GtpConsole.finalStatusList(status: StoneStatus, timeout: Duration? = defaultTimeout) = send(
    GtpCommand(
        "final_status_list",
        when (status) {
            StoneStatus.Alive -> GtpValue.String("alive")
            StoneStatus.Seki -> GtpValue.String("seki")
            StoneStatus.Dead -> GtpValue.String("dead")
        }
    ),
    timeout
).map { stones ->
    val groups = stones.split(lineSeparator).map { it.split(" ") }
    groups.map { group ->
        group.map(GtpValue.Vertex::from).toSet()
    }
}

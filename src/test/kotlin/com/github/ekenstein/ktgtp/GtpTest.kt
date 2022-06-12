package com.github.ekenstein.ktgtp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.time.Duration.Companion.seconds

class GtpTest {
    private val requiredCommands = listOf(
        "protocol_version",
        "name",
        "version",
        "known_command",
        "list_commands",
        "quit",
        "boardsize",
        "clear_board",
        "komi",
        "play",
        "genmove",
    )

    private fun gnuGo(block: GtpConsole.() -> Unit) {
        gtpConsole("gnugo", "--mode", "gtp") {
            block()
        }
    }

    @Test
    fun `can list known commands`() {
        gnuGo {
            val commands = listCommands().getOrNull().orEmpty()
            assertTrue(commands.containsAll(requiredCommands))
        }
    }

    @Test
    fun `can get name of the engine`() {
        gnuGo {
            val name = name().getOrNull()
            assertEquals("GNU Go", name)
        }
    }

    @Test
    fun `required commands are all known commands`() {
        assertAll(
            requiredCommands.map {
                {
                    gnuGo {
                        val actual = knownCommand(it).getOrNull() ?: false
                        assertTrue(actual, "Expected $it to be a known command")
                    }
                }
            }
        )
    }

    @Test
    fun `can get version of the engine`() {
        gnuGo {
            val version = version()
            assertTrue(version.isSuccess())
        }
    }

    @Test
    fun `can set board size`() {
        gnuGo {
            assertTrue(boardSize(19).isSuccess())
        }
    }

    @Test
    fun `board size must be legal`() {
        gnuGo {
            assertFalse(boardSize(0).isSuccess())
        }
    }

    @Test
    fun `komi can be set`() {
        gnuGo {
            assertTrue(komi(6.5).isSuccess())
        }
    }

    @Test
    fun `response will fail on unknown commands`() {
        gnuGo {
            assertFalse(send(GtpCommand("foobar"), 1.seconds).isSuccess())
        }
    }

    @Test
    fun `can not play outside of the board`() {
        gnuGo {
            boardSize(9)
            assertFalse(play(GtpValue.black, GtpValue.point(10, 10)).isSuccess())
        }
    }

    @Test
    fun `can not place a stone on another stone`() {
        gnuGo {
            play(GtpValue.black, GtpValue.point(3, 3))
            assertFalse(play(GtpValue.white, GtpValue.point(3, 3)).isSuccess())
        }
    }

    @Test
    fun `can undo a move`() {
        gnuGo {
            play(GtpValue.black, GtpValue.point(3, 3))
            undo()
            assertTrue(play(GtpValue.black, GtpValue.point(3, 3)).isSuccess())
        }
    }

    @Test
    fun `can get protocol version`() {
        gnuGo {
            assertTrue(protocolVersion().isSuccess())
        }
    }

    @Test
    fun `can save information outside of the block`() {
        var name: String? = null
        gtpConsole("gnugo", "--mode", "gtp") {
            name = name().getOrNull()
        }

        assertEquals("GNU Go", name)
    }
}

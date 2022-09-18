package com.github.ekenstein.ktgtp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.toPath
import kotlin.test.assertNotNull
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
        gtpConsole("gnugo", "--mode", "gtp", "--seed", "42") {
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
    fun `can play a 9,9 coordinate`() {
        gnuGo {
            assertTrue(play(GtpValue.black, GtpValue.point(9, 9)).isSuccess())
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
    fun `can load sgf`() {
        val path = getResourcePath("/3bn6-gokifu-20220324-Byun_Sangil-Gu_Zihao.sgf")
        gnuGo {
            assertTrue(loadSgf(path).isSuccess())
            val board = showBoard().getOrNull()
            assertNotNull(board)
        }
    }

    @Test
    fun `can place fixed handicap`() {
        gnuGo {
            val vertices = fixedHandicap(2).getOrNull().orEmpty()
            val expectedVertices = setOf(
                GtpValue.point(4, 4),
                GtpValue.point(16, 16)
            )
            assertEquals(expectedVertices, vertices)
        }
    }

    @Test
    fun `can not place fixed handicap if the board is not empty`() {
        gnuGo {
            play(GtpValue.black, GtpValue.point(3, 3))
            assertFalse(fixedHandicap(3).isSuccess())
        }
    }

    @Test
    fun `can not place an invalid number of handicap stones`() {
        gnuGo {
            boardSize(19)
            assertFalse(fixedHandicap(10).isSuccess())
        }
    }

    @Test
    fun `can place free handicap`() {
        gnuGo {
            assertTrue(placeFreeHandicap(3).isSuccess())
        }
    }

    @Test
    fun `can set free handicap`() {
        gnuGo {
            assertTrue(setFreeHandicap(setOf(GtpValue.point(4, 4), GtpValue.point(5, 5))).isSuccess())
            assertFalse(play(GtpValue.white, GtpValue.point(4, 4)).isSuccess())
            assertFalse(play(GtpValue.white, GtpValue.point(5, 5)).isSuccess())
        }
    }

    @Test
    fun `can clear board`() {
        gnuGo {
            assertTrue(clearBoard().isSuccess())
        }
    }

    @Test
    fun `can generate move`() {
        gnuGo {
            assertTrue(genMove(GtpValue.black).isSuccess())
        }
    }

    @Test
    fun `can do regression generation of a move`() {
        gnuGo {
            val move = regGenMove(GtpValue.black).getOrNull()
            assertNotNull(move)
            when (move) {
                is GeneratedMove.Move -> assertTrue(play(GtpValue.black, move.vertex).isSuccess())
                GeneratedMove.Resign -> {}
            }
        }
    }

    @Test
    fun `can show the board`() {
        gnuGo {
            val board = showBoard().getOrNull()
            assertNotNull(board)
            println(board)
        }
    }

    @Test
    fun `can change time settings`() {
        gnuGo {
            assertTrue(timeSettings(300, 30, 5).isSuccess())
        }
    }

    @Test
    fun `engine throws if timeout has been exceeded`() {
        gnuGo {
            assertThrows<GtpException.EngineTimedOut> {
                send(GtpCommand("list_commands"), 0.seconds)
            }
        }
    }

    private fun getResourcePath(path: String) = GtpTest::class.java.getResource(path)
        ?.toURI()
        ?.toPath()
        ?: error("There is no file at $path")
}

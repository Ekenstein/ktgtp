package com.github.ekenstein.ktgtp

data class GtpCommand(
    val command: String,
    val args: List<GtpValue>
) {
    constructor(command: String, vararg args: GtpValue) : this(command, args.toList())
}

/**
 * Represents a generated move.
 */
sealed class GeneratedMove {
    /**
     * The engine resigns.
     */
    object Resign : GeneratedMove()

    /**
     * The engine generated either [GtpValue.Vertex.Point] or [GtpValue.Vertex.Pass].
     */
    data class Move(val vertex: GtpValue.Vertex) : GeneratedMove()

    companion object {
        fun from(string: String) = when (string) {
            "resign" -> Resign
            else -> Move(GtpValue.Vertex.from(string))
        }
    }
}

sealed class GtpValue {
    /**
     * An int is an unsigned integer.
     */
    data class Int(val value: kotlin.Int) : GtpValue() {
        init {
            require(value >= 0) {
                "The integer must be unsigned"
            }
        }
    }
    data class Float(val value: Double) : GtpValue()
    data class String(val value: kotlin.String) : GtpValue() {
        init {
            require(value.none { it.isWhitespace() }) {
                "The string must not contain any white spaces."
            }
        }
    }
    data class Bool(val value: Boolean) : GtpValue()

    sealed class Color : GtpValue() {
        object Black : Color()
        object White : Color()
    }

    sealed class Vertex : GtpValue() {
        object Pass : Vertex()
        data class Point(val x: kotlin.Int, val y: kotlin.Int) : Vertex()

        companion object {
            fun from(string: kotlin.String): Vertex {
                val trimmed = string.trim()
                require(trimmed.isNotBlank()) {
                    "The string can not be converted to a vertex."
                }

                return when (string.trim()) {
                    "pass" -> Pass
                    else -> {
                        val cx = string[0].lowercaseChar()
                        require(cx in 'a'..'z') {
                            "The string '$string' can not be converted to a vertex."
                        }

                        val y = requireNotNull(string.substring(1).toIntOrNull()) {
                            "The string '$string' can not be converted to a vertex."
                        }

                        // 'i' is not part of the board coordinates.
                        val x = if (cx >= 'j') {
                            (cx - 'a')
                        } else {
                            (cx - 'a') + 1
                        }

                        Point(x, y)
                    }
                }
            }
        }
    }

    data class Move(val color: Color, val vertex: Vertex) : GtpValue()

    companion object {
        val black = Color.Black
        val white = Color.White
        val pass = Vertex.Pass
        fun point(x: kotlin.Int, y: kotlin.Int) = Vertex.Point(x, y)
    }
}

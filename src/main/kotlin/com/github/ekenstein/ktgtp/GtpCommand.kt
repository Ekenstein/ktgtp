package com.github.ekenstein.ktgtp

data class GtpCommand(
    val command: String,
    val args: List<GtpValue>
) {
    constructor(command: String, vararg args: GtpValue) : this(command, args.toList())
}

sealed class GtpValue {
    data class Int(val value: kotlin.Int) : GtpValue() {
        init {
            require(value >= 0) {
                "The integer must be unsigned"
            }
        }
    }
    data class Float(val value: Double) : GtpValue()
    data class String(val value: kotlin.String) : GtpValue()
    data class Bool(val value: Boolean) : GtpValue()

    sealed class Color : GtpValue() {
        object Black : Color()
        object White : Color()
    }

    sealed class Vertex : GtpValue() {
        object Pass : Vertex()
        data class Point(val x: kotlin.Int, val y: kotlin.Int) : Vertex() {
            init {
                require(x >= 0 && y >= 0) {
                    "The x and y coordinates must be unsigned"
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

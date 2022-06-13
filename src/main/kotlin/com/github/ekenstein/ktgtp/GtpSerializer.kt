package com.github.ekenstein.ktgtp

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat

fun GtpCommand.encodeToString(): String {
    val args = args.joinToString(" ") { it.encodeToString() }
    return "$command $args\n"
}

private val numberFormatter: NumberFormat = DecimalFormat().apply {
    decimalFormatSymbols = DecimalFormatSymbols().apply {
        decimalSeparator = '.'
    }
    isGroupingUsed = false
}

private fun GtpValue.encodeToString(): String = when (this) {
    is GtpValue.Bool -> value.toString()
    GtpValue.Color.Black -> "black"
    GtpValue.Color.White -> "white"
    is GtpValue.Float -> numberFormatter.format(value)
    is GtpValue.Int -> numberFormatter.format(value)
    is GtpValue.Move -> "${color.encodeToString()} ${vertex.encodeToString()}"
    is GtpValue.String -> value
    GtpValue.Vertex.Pass -> "pass"
    is GtpValue.Vertex.Point -> {
        // 'i' is an invalid coordinate.
        val cx = if (x >= 9) {
            (x + 'a'.code).toChar().uppercase()
        } else {
            ((x - 1) + 'a'.code).toChar().uppercase()
        }
        "$cx$y"
    }
}

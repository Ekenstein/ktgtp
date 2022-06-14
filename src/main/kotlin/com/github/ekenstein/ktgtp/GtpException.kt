package com.github.ekenstein.ktgtp

sealed class GtpException : Exception() {
    object EngineClosed : GtpException() {
        override val message: String = "The GTP engine has been closed"
    }

    object EngineTimedOut : GtpException() {
        override val message: String = "The GTP engine timed out while waiting for a response"
    }
}

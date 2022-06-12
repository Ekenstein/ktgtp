package com.github.ekenstein.ktgtp

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class GtpResponse<out T> {
    data class Failure(val message: String) : GtpResponse<Nothing>()
    data class Success<T>(val value: T) : GtpResponse<T>()
}

fun GtpResponse<*>.toUnit() = map { }

fun <T> GtpResponse<T>.getOrNull() = when (this) {
    is GtpResponse.Failure -> null
    is GtpResponse.Success -> value
}

fun <T, U> GtpResponse<T>.map(transform: (T) -> U): GtpResponse<U> = flatMap {
    GtpResponse.Success(transform(it))
}

fun <T, U> GtpResponse<T>.flatMap(transform: (T) -> GtpResponse<U>) = when (this) {
    is GtpResponse.Failure -> this
    is GtpResponse.Success -> transform(value)
}

@OptIn(ExperimentalContracts::class)
fun <T> GtpResponse<T>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is GtpResponse.Success)
        returns(false) implies (this@isSuccess is GtpResponse.Failure)
    }

    return this is GtpResponse.Success
}

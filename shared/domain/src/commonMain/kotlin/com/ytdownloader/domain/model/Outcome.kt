package com.ytdownloader.domain.model

sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class Failure(val error: DomainError) : Outcome<Nothing>()

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
}

sealed interface DomainError {
    val message: String
    val cause: Throwable?
}

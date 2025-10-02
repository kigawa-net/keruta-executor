package net.kigawa.keruta.executor.domain

sealed interface Result<T, E> {
    val isSuccess: Boolean
    val isFailure: Boolean
    fun getOrElse(defaultValue: T): T
    fun getOrNull(): T?
    fun getErrorOrNull(): E?
    class Success<T, E>(
        val value: T,
    ): Result<T, E> {
        override val isSuccess: Boolean
            get() = true
        override val isFailure: Boolean
            get() = false

        override fun getOrElse(defaultValue: T): T {
            return value
        }

        override fun getOrNull(): T? {
            return value
        }

        override fun getErrorOrNull(): Nothing? {
            return null
        }
    }

    class Failure<T, E>(
        val error: E,
    ): Result<T, E> {
        override val isSuccess: Boolean
            get() = false
        override val isFailure: Boolean
            get() = true

        override fun getOrElse(defaultValue: T): T {
            return defaultValue
        }

        override fun getOrNull(): Nothing? {
            return null
        }

        override fun getErrorOrNull(): E? {
            return error
        }
    }
}

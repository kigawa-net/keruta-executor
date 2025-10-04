package net.kigawa.keruta.executor.domain.err

sealed interface Res<T, E> {
    val isSuccess: Boolean
    val isError: Boolean
    fun getOrElse(defaultValue: T): T
    fun getOrNull(): T?
    fun getErrorOrNull(): E?

    class Success<T, E>(
        val value: T,
    ): Res<T, E> {
        override val isSuccess: Boolean
            get() = true
        override val isError: Boolean
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

    class Error<T, E>(
        val error: E,
    ): Res<T, E> {
        override val isSuccess: Boolean
            get() = false
        override val isError: Boolean
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

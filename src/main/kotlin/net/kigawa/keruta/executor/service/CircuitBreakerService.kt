package net.kigawa.keruta.executor.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Circuit breaker service for handling failures and retries.
 */
@Service
open class CircuitBreakerService {
    private val logger = LoggerFactory.getLogger(CircuitBreakerService::class.java)

    // Circuit breaker state for different operations
    private val circuitStates = ConcurrentHashMap<String, CircuitBreakerState>()

    /**
     * Checks if the circuit breaker is open for a given operation.
     */
    fun isCircuitOpen(operationKey: String): Boolean {
        val state = circuitStates[operationKey]
        if (state != null && state.isOpen()) {
            if (state.shouldAllowTest()) {
                state.halfOpen()
                return false
            }
            return true
        }
        return false
    }

    /**
     * Records a failure for the given operation.
     */
    fun recordFailure(operationKey: String) {
        circuitStates.computeIfAbsent(operationKey) { CircuitBreakerState() }.recordFailure()
    }

    /**
     * Records a success for the given operation.
     */
    fun recordSuccess(operationKey: String) {
        circuitStates[operationKey]?.recordSuccess()
    }

    /**
     * Execute an operation with retry logic and circuit breaker.
     */
    fun <T> executeWithRetry(operationName: String, maxAttempts: Int, operation: () -> T): T {
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                // Check circuit breaker
                if (isCircuitOpen(operationName)) {
                    logger.warn(
                        "Circuit breaker is open for operation: {}, skipping attempt {}",
                        operationName,
                        attempt,
                    )
                    throw lastException ?: RuntimeException("Circuit breaker is open for operation: $operationName")
                }

                val result = operation()
                recordSuccess(operationName)
                return result
            } catch (e: Exception) {
                recordFailure(operationName)
                lastException = e
                logger.warn("Attempt {} failed for operation: {} - {}", attempt, operationName, e.message)

                if (attempt < maxAttempts) {
                    val delayMs = calculateBackoffDelay(attempt)
                    logger.debug("Retrying in {}ms for operation: {}", delayMs, operationName)
                    try {
                        Thread.sleep(delayMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw RuntimeException("Interrupted during retry delay", ie)
                    }
                }
            }
        }

        throw lastException ?: RuntimeException("All attempts failed for operation: $operationName")
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        // Exponential backoff with jitter
        val baseDelay = 1000L * (1L shl attempt) // 1s, 2s, 4s, 8s...
        val jitter = Random.nextLong(0, baseDelay / 2)
        return baseDelay + jitter
    }
}

/**
 * Represents the state of a circuit breaker.
 */
private class CircuitBreakerState {
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var state = State.CLOSED

    private val failureThreshold = 5
    private val timeout = 60000L // 1 minute

    enum class State {
        CLOSED,
        OPEN,
        HALF_OPEN,
    }

    fun isOpen(): Boolean = state == State.OPEN

    fun recordFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        if (failureCount >= failureThreshold) {
            state = State.OPEN
        }
    }

    fun recordSuccess() {
        failureCount = 0
        state = State.CLOSED
    }

    fun shouldAllowTest(): Boolean {
        return state == State.OPEN && (System.currentTimeMillis() - lastFailureTime) > timeout
    }

    fun halfOpen() {
        state = State.HALF_OPEN
    }
}

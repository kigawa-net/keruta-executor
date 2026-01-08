package net.kigawa.keruta.executor.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class CircuitBreakerServiceTest {

    private lateinit var circuitBreakerService: CircuitBreakerService

    @BeforeEach
    fun setUp() {
        circuitBreakerService = CircuitBreakerService()
    }

    @Test
    fun `isCircuitOpen should return false for new operations`() {
        // Given
        val operationKey = "test-operation"

        // When
        val result = circuitBreakerService.isCircuitOpen(operationKey)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isCircuitOpen should return true after failure threshold is reached`() {
        // Given
        val operationKey = "test-operation"

        // When - Record failures up to threshold (5)
        repeat(5) {
            circuitBreakerService.recordFailure(operationKey)
        }

        // Then
        assertTrue(circuitBreakerService.isCircuitOpen(operationKey))
    }

    @Test
    fun `recordSuccess should reset failure count and close circuit`() {
        // Given
        val operationKey = "test-operation"

        // Record failures
        repeat(3) {
            circuitBreakerService.recordFailure(operationKey)
        }

        // When - Record success
        circuitBreakerService.recordSuccess(operationKey)

        // Then
        assertFalse(circuitBreakerService.isCircuitOpen(operationKey))
    }

    @Test
    fun `executeWithRetry should succeed on first attempt`() {
        // Given
        val operationKey = "test-operation"

        // When
        val result = circuitBreakerService.executeWithRetry(operationKey, 3) {
            "success"
        }

        // Then
        assertEquals("success", result)
        assertFalse(circuitBreakerService.isCircuitOpen(operationKey))
    }

    @Test
    fun `executeWithRetry should retry on failure and succeed`() {
        // Given
        val operationKey = "test-operation"
        var attemptCount = 0

        // When
        val result = circuitBreakerService.executeWithRetry(operationKey, 3) {
            attemptCount++
            if (attemptCount < 2) {
                throw RuntimeException("Temporary failure")
            }
            "success"
        }

        // Then
        assertEquals("success", result)
        assertEquals(2, attemptCount)
        assertFalse(circuitBreakerService.isCircuitOpen(operationKey))
    }

    @Test
    fun `executeWithRetry should throw exception after max attempts`() {
        // Given
        val operationKey = "test-operation"

        // When & Then
        val exception = assertThrows(RuntimeException::class.java) {
            circuitBreakerService.executeWithRetry(operationKey, 2) {
                throw RuntimeException("Persistent failure")
            }
        }

        assertEquals("Persistent failure", exception.message)
        assertTrue(circuitBreakerService.isCircuitOpen(operationKey))
    }

    @Test
    fun `executeWithRetry should open circuit after repeated failures across calls`() {
        // Given
        val operationKey = "test-operation"

        // First call - fails once, succeeds on retry
        circuitBreakerService.executeWithRetry(operationKey, 2) {
            throw RuntimeException("Failure")
        }

        // When - Second call fails
        assertThrows(RuntimeException::class.java) {
            circuitBreakerService.executeWithRetry(operationKey, 2) {
                throw RuntimeException("Failure")
            }
        }

        // Then - Circuit should be open
        assertTrue(circuitBreakerService.isCircuitOpen(operationKey))
    }
}
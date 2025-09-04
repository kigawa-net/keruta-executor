package net.kigawa.keruta.executor.service

import io.mockk.every
import io.mockk.mockk
import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate

class CoderTemplateServiceTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var properties: KerutaExecutorProperties
    private lateinit var coderTemplateService: CoderTemplateService

    @BeforeEach
    fun setUp() {
        restTemplate = mockk(relaxed = true)
        properties = mockk(relaxed = true)

        val coderProperties = mockk<net.kigawa.keruta.executor.config.CoderProperties>(relaxed = true)
        every { properties.coder } returns coderProperties
        every { coderProperties.baseUrl } returns "http://coder.example.com"
        every { coderProperties.token } returns "test-token"

        coderTemplateService = CoderTemplateService(restTemplate, properties)
    }

    @Test
    fun `CoderTemplateService should be created successfully`() {
        // Given & When
        // Service is created in setUp()

        // Then
        assertNotNull(coderTemplateService)
    }

    @Test
    fun `getCoderTemplates should return mock templates when API fails`() {
        // Given & When
        val result = coderTemplateService.getCoderTemplates()

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `getCoderTemplate should return template when found in mock data`() {
        // Given & When
        val result = coderTemplateService.getCoderTemplate("ubuntu-basic")

        // Then
        assertNotNull(result)
    }
}

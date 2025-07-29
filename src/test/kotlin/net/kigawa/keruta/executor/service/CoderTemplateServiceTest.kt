package net.kigawa.keruta.executor.service

import io.mockk.*
import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoderTemplateServiceTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var properties: KerutaExecutorProperties
    private lateinit var coderTemplateService: CoderTemplateService
    private lateinit var coderProperties: KerutaExecutorProperties.CoderProperties

    @BeforeEach
    fun setUp() {
        restTemplate = mockk()
        properties = mockk()
        coderProperties = mockk()
        
        every { properties.coder } returns coderProperties
        every { coderProperties.baseUrl } returns "http://coder.example.com"
        every { coderProperties.token } returns "test-token"
        
        coderTemplateService = CoderTemplateService(restTemplate, properties)
    }

    @Test
    fun `getCoderTemplates should return templates from API when successful`() {
        // Given
        val apiResponse = listOf(
            CoderTemplateApiResponse(
                id = "template1",
                name = "template1",
                display_name = "Template 1",
                description = "Test template",
                icon = "/icon/test.svg",
                default_ttl_ms = 3600000L,
                max_ttl_ms = 28800000L,
                min_autostart_interval_ms = 3600000L,
                created_by_name = "admin",
                updated_at = "2023-07-29T10:00:00",
                organization_id = "org1",
                provisioner = "terraform",
                active_version_id = "v1.0.0",
                workspace_count = 5,
                deprecated = false
            )
        )

        every {
            restTemplate.exchange(
                "http://coder.example.com/api/v2/templates",
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                any<ParameterizedTypeReference<List<CoderTemplateApiResponse>>>()
            )
        } returns ResponseEntity.ok(apiResponse)

        // When
        val result = coderTemplateService.getCoderTemplates()

        // Then
        assertEquals(1, result.size)
        val template = result.first()
        assertEquals("template1", template.id)
        assertEquals("template1", template.name)
        assertEquals("Template 1", template.displayName)
        assertEquals("Test template", template.description)
        assertEquals("/icon/test.svg", template.icon)
        assertEquals(3600000L, template.defaultTtlMs)
        assertEquals(28800000L, template.maxTtlMs)
        assertEquals(3600000L, template.minAutostartIntervalMs)
        assertEquals("admin", template.createdByName)
        assertEquals("org1", template.organizationId)
        assertEquals("terraform", template.provisioner)
        assertEquals("v1.0.0", template.activeVersionId)
        assertEquals(5, template.workspaceCount)
        assertEquals(false, template.deprecated)
    }

    @Test
    fun `getCoderTemplates should return mock templates when API fails`() {
        // Given
        every {
            restTemplate.exchange(
                "http://coder.example.com/api/v2/templates",
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                any<ParameterizedTypeReference<List<CoderTemplateApiResponse>>>()
            )
        } throws RestClientException("Connection failed")

        // When
        val result = coderTemplateService.getCoderTemplates()

        // Then
        assertTrue(result.isNotEmpty())
        val ubuntuBasic = result.find { it.id == "ubuntu-basic" }
        assertNotNull(ubuntuBasic)
        assertEquals("Ubuntu Basic", ubuntuBasic.displayName)
        assertEquals("Basic Ubuntu workspace with essential development tools", ubuntuBasic.description)

        val nodejsDev = result.find { it.id == "nodejs-dev" }
        assertNotNull(nodejsDev)
        assertEquals("Node.js Development", nodejsDev.displayName)

        val pythonDataScience = result.find { it.id == "python-datascience" }
        assertNotNull(pythonDataScience)
        assertEquals("Python Data Science", pythonDataScience.displayName)

        val kerutaUbuntu = result.find { it.id == "keruta-ubuntu" }
        assertNotNull(kerutaUbuntu)
        assertEquals("Keruta Ubuntu", kerutaUbuntu.displayName)
    }

    @Test
    fun `getCoderTemplates should handle empty API response`() {
        // Given
        every {
            restTemplate.exchange(
                "http://coder.example.com/api/v2/templates",
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                any<ParameterizedTypeReference<List<CoderTemplateApiResponse>>>()
            )
        } returns ResponseEntity.ok(emptyList())

        // When
        val result = coderTemplateService.getCoderTemplates()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCoderTemplates should handle null API response body`() {
        // Given
        every {
            restTemplate.exchange(
                "http://coder.example.com/api/v2/templates",
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                any<ParameterizedTypeReference<List<CoderTemplateApiResponse>>>()
            )
        } returns ResponseEntity.ok(null)

        // When
        val result = coderTemplateService.getCoderTemplates()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCoderTemplate should return specific template when found`() {
        // Given
        val apiResponse = listOf(
            CoderTemplateApiResponse(
                id = "target-template",
                name = "target-template",
                display_name = "Target Template",
                description = "The template we're looking for",
                icon = "/icon/target.svg",
                default_ttl_ms = 3600000L,
                max_ttl_ms = 28800000L,
                min_autostart_interval_ms = 3600000L,
                created_by_name = "admin",
                updated_at = "2023-07-29T10:00:00",
                organization_id = "org1",
                provisioner = "terraform",
                active_version_id = "v1.0.0",
                workspace_count = 3,
                deprecated = false
            ),
            CoderTemplateApiResponse(
                id = "other-template",
                name = "other-template",
                display_name = "Other Template",
                description = "Another template",
                icon = "/icon/other.svg",
                default_ttl_ms = 3600000L,
                max_ttl_ms = 28800000L,
                min_autostart_interval_ms = 3600000L,
                created_by_name = "admin",
                updated_at = "2023-07-29T10:00:00",
                organization_id = "org1",
                provisioner = "terraform",
                active_version_id = "v1.0.0",
                workspace_count = 1,
                deprecated = false
            )
        )

        every {
            restTemplate.exchange(
                "http://coder.example.com/api/v2/templates",
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                any<ParameterizedTypeReference<List<CoderTemplateApiResponse>>>()
            )
        } returns ResponseEntity.ok(apiResponse)

        // When
        val result = coderTemplateService.getCoderTemplate("target-template")

        // Then
        assertNotNull(result)
        assertEquals("target-template", result.id)
        assertEquals("Target Template", result.displayName)
        assertEquals("The template we're looking for", result.description)
    }

    @Test
    fun `getCoderTemplate should return null when template not found`() {
        // Given
        every {
            restTemplate.exchange(
                "http://coder.example.com/api/v2/templates",
                HttpMethod.GET,
                any<HttpEntity<String>>(),
                any<ParameterizedTypeReference<List<CoderTemplateApiResponse>>>()
            )
        } returns ResponseEntity.ok(emptyList())

        // When
        val result = coderTemplateService.getCoderTemplate("non-existent-template")

        // Then
        assertNull(result)
    }

    @Test
    fun `should create auth headers with token when token is provided`() {
        // Given
        every { coderProperties.token } returns "test-auth-token"

        val capturedEntity = slot<HttpEntity<String>>()
        every {
            restTemplate.exchange(
                "http://coder.example.com/api/v2/templates",
                HttpMethod.GET,
                capture(capturedEntity),
                any<ParameterizedTypeReference<List<CoderTemplateApiResponse>>>()
            )
        } returns ResponseEntity.ok(emptyList())

        // When
        coderTemplateService.getCoderTemplates()

        // Then
        val headers = capturedEntity.captured.headers
        assertEquals("test-auth-token", headers.getFirst("Coder-Session-Token"))
        assertEquals("application/json", headers.getFirst("Accept"))
    }

    @Test
    fun `should create auth headers without token when token is null`() {
        // Given
        every { coderProperties.token } returns null

        val capturedEntity = slot<HttpEntity<String>>()
        every {
            restTemplate.exchange(
                "http://coder.example.com/api/v2/templates",
                HttpMethod.GET,
                capture(capturedEntity),
                any<ParameterizedTypeReference<List<CoderTemplateApiResponse>>>()
            )
        } returns ResponseEntity.ok(emptyList())

        // When
        coderTemplateService.getCoderTemplates()

        // Then
        val headers = capturedEntity.captured.headers
        assertNull(headers.getFirst("Coder-Session-Token"))
        assertEquals("application/json", headers.getFirst("Accept"))
    }

    @Test
    fun `CoderTemplateApiResponse toDto should handle null values gracefully`() {
        // Given
        val apiResponse = CoderTemplateApiResponse(
            id = "test-id",
            name = "test-name",
            display_name = null,
            description = null,
            icon = null,
            default_ttl_ms = null,
            max_ttl_ms = null,
            min_autostart_interval_ms = null,
            created_by_name = null,
            updated_at = null,
            organization_id = null,
            provisioner = null,
            active_version_id = null,
            workspace_count = null,
            deprecated = null
        )

        // When
        val dto = apiResponse.toDto()

        // Then
        assertEquals("test-id", dto.id)
        assertEquals("test-name", dto.name)
        assertEquals("test-name", dto.displayName) // Should fall back to name
        assertEquals("", dto.description) // Should default to empty string
        assertEquals("/icon/default.svg", dto.icon) // Should use default icon
        assertEquals(3600000L, dto.defaultTtlMs) // Should use default TTL
        assertEquals(28800000L, dto.maxTtlMs) // Should use default max TTL
        assertEquals(3600000L, dto.minAutostartIntervalMs) // Should use default interval
        assertEquals("unknown", dto.createdByName) // Should default to "unknown"
        assertEquals("default", dto.organizationId) // Should use default org
        assertEquals("terraform", dto.provisioner) // Should use default provisioner
        assertEquals("unknown", dto.activeVersionId) // Should default to "unknown"
        assertEquals(0, dto.workspaceCount) // Should default to 0
        assertEquals(false, dto.deprecated) // Should default to false
    }
}
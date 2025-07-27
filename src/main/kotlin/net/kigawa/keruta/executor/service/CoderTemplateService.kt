package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

/**
 * Service for communicating with Coder server to fetch templates.
 */
@Service
open class CoderTemplateService(
    private val restTemplate: RestTemplate,
    private val properties: KerutaExecutorProperties
) {
    private val logger = LoggerFactory.getLogger(CoderTemplateService::class.java)

    /**
     * Fetches available Coder templates from the Coder server.
     */
    fun getCoderTemplates(): List<CoderTemplateDto> {
        logger.info("Fetching Coder templates from Coder server: ${properties.coder.baseUrl}")

        try {
            val url = "${properties.coder.baseUrl}/api/v2/templates"
            val headers = createAuthHeaders()
            val entity = HttpEntity<String>(headers)
            val typeReference = object : ParameterizedTypeReference<List<CoderTemplateApiResponse>>() {}
            
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, typeReference)
            val apiTemplates = response.body ?: emptyList()
            
            logger.info("Successfully fetched {} Coder templates", apiTemplates.size)
            return apiTemplates.map { it.toDto() }
        } catch (e: Exception) {
            logger.error("Failed to fetch Coder templates from API, falling back to mock data", e)
            return getMockCoderTemplates()
        }
    }

    /**
     * Fetches a specific Coder template by ID.
     */
    fun getCoderTemplate(id: String): CoderTemplateDto? {
        logger.info("Fetching Coder template: $id")

        return getCoderTemplates().find { it.id == id }
    }

    /**
     * Creates HTTP headers with authentication if token is available.
     */
    private fun createAuthHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        properties.coder.token?.let { token ->
            headers.set("Coder-Session-Token", token)
        }
        headers.set("Accept", "application/json")
        return headers
    }

    /**
     * Returns mock Coder templates for development purposes.
     */
    private fun getMockCoderTemplates(): List<CoderTemplateDto> {
        return listOf(
            CoderTemplateDto(
                id = "ubuntu-basic",
                name = "ubuntu-basic",
                displayName = "Ubuntu Basic",
                description = "Basic Ubuntu workspace with essential development tools",
                icon = "/icon/ubuntu.svg",
                defaultTtlMs = 3600000, // 1 hour
                maxTtlMs = 28800000, // 8 hours
                minAutostartIntervalMs = 3600000, // 1 hour
                createdByName = "admin",
                updatedAt = LocalDateTime.now(),
                organizationId = "default",
                provisioner = "terraform",
                activeVersionId = "v1.0.0",
                workspaceCount = 0,
                deprecated = false
            ),
            CoderTemplateDto(
                id = "nodejs-dev",
                name = "nodejs-dev",
                displayName = "Node.js Development",
                description = "Node.js development environment with VS Code",
                icon = "/icon/nodejs.svg",
                defaultTtlMs = 3600000, // 1 hour
                maxTtlMs = 28800000, // 8 hours
                minAutostartIntervalMs = 3600000, // 1 hour
                createdByName = "admin",
                updatedAt = LocalDateTime.now(),
                organizationId = "default",
                provisioner = "terraform",
                activeVersionId = "v1.0.0",
                workspaceCount = 0,
                deprecated = false
            ),
            CoderTemplateDto(
                id = "python-datascience",
                name = "python-datascience",
                displayName = "Python Data Science",
                description = "Python environment with Jupyter, pandas, and ML libraries",
                icon = "/icon/python.svg",
                defaultTtlMs = 7200000, // 2 hours
                maxTtlMs = 43200000, // 12 hours
                minAutostartIntervalMs = 3600000, // 1 hour
                createdByName = "admin",
                updatedAt = LocalDateTime.now(),
                organizationId = "default",
                provisioner = "terraform",
                activeVersionId = "v1.0.0",
                workspaceCount = 0,
                deprecated = false
            ),
            CoderTemplateDto(
                id = "keruta-ubuntu",
                name = "keruta-ubuntu",
                displayName = "Keruta Ubuntu",
                description = "Ubuntu environment optimized for Keruta development tasks with Claude Code integration",
                icon = "/icon/keruta.svg",
                defaultTtlMs = 3600000, // 1 hour
                maxTtlMs = 28800000, // 8 hours
                minAutostartIntervalMs = 3600000, // 1 hour
                createdByName = "admin",
                updatedAt = LocalDateTime.now(),
                organizationId = "default",
                provisioner = "terraform",
                activeVersionId = "v1.0.0",
                workspaceCount = 0,
                deprecated = false
            )
        )
    }
}

/**
 * DTO for Coder template data.
 */
data class CoderTemplateDto(
    val id: String,
    val name: String,
    val displayName: String,
    val description: String,
    val icon: String,
    val defaultTtlMs: Long,
    val maxTtlMs: Long,
    val minAutostartIntervalMs: Long,
    val createdByName: String,
    val updatedAt: LocalDateTime,
    val organizationId: String,
    val provisioner: String,
    val activeVersionId: String,
    val workspaceCount: Int,
    val deprecated: Boolean = false
)

/**
 * Response from Coder API for template data.
 */
data class CoderTemplateApiResponse(
    val id: String,
    val name: String,
    val display_name: String?,
    val description: String?,
    val icon: String?,
    val default_ttl_ms: Long?,
    val max_ttl_ms: Long?,
    val min_autostart_interval_ms: Long?,
    val created_by_name: String?,
    val updated_at: String?,
    val organization_id: String?,
    val provisioner: String?,
    val active_version_id: String?,
    val workspace_count: Int?,
    val deprecated: Boolean?
) {
    fun toDto(): CoderTemplateDto {
        return CoderTemplateDto(
            id = id,
            name = name,
            displayName = display_name ?: name,
            description = description ?: "",
            icon = icon ?: "/icon/default.svg",
            defaultTtlMs = default_ttl_ms ?: 3600000L,
            maxTtlMs = max_ttl_ms ?: 28800000L,
            minAutostartIntervalMs = min_autostart_interval_ms ?: 3600000L,
            createdByName = created_by_name ?: "unknown",
            updatedAt = updated_at?.let { LocalDateTime.parse(it.replace("Z", "")) } ?: LocalDateTime.now(),
            organizationId = organization_id ?: "default",
            provisioner = provisioner ?: "terraform",
            activeVersionId = active_version_id ?: "unknown",
            workspaceCount = workspace_count ?: 0,
            deprecated = deprecated ?: false
        )
    }
}

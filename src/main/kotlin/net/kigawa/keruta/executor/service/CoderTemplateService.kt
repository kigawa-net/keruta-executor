package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Service for communicating with Coder server to fetch templates.
 */
@Service
open class CoderTemplateService(
    private val restTemplate: RestTemplate,
    private val properties: KerutaExecutorProperties
) {
    private val logger = LoggerFactory.getLogger(CoderTemplateService::class.java)

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var lastRefreshTime: Long = 0L

    /**
     * Fetches available Coder templates from the Coder server.
     */
    fun getCoderTemplates(): List<CoderTemplateDto> {
        logger.info("Fetching Coder templates from Coder server: ${properties.coder.baseUrl}")

        try {
            return tryFetchTemplates()
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401) {
                logger.warn("Authentication failed, attempting to refresh token")
                return tryFetchTemplatesWithRefresh()
            } else {
                logger.error("Failed to fetch Coder templates from API, falling back to mock data", e)
                return getMockCoderTemplates()
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch Coder templates from API, falling back to mock data", e)
            return getMockCoderTemplates()
        }
    }

    /**
     * Attempts to fetch templates with current authentication.
     */
    private fun tryFetchTemplates(): List<CoderTemplateDto> {
        val url = "${properties.coder.baseUrl}/api/v2/templates"
        val headers = createAuthHeaders()
        val entity = HttpEntity<String>(headers)
        val typeReference = object : ParameterizedTypeReference<List<CoderTemplateApiResponse>>() {}

        val response = restTemplate.exchange(url, HttpMethod.GET, entity, typeReference)
        val apiTemplates = response.body ?: emptyList()

        logger.info("Successfully fetched {} Coder templates", apiTemplates.size)
        return apiTemplates.map { it.toDto() }
    }

    /**
     * Attempts to refresh authentication and fetch templates again.
     */
    private fun tryFetchTemplatesWithRefresh(): List<CoderTemplateDto> {
        try {
            if (properties.coder.token == null) {
                logger.error(
                    "No Coder token available for refresh. Check KERUTA_EXECUTOR_CODER_TOKEN env var or K8s secret."
                )
                return getMockCoderTemplates()
            }
            refreshAuthToken()
            return tryFetchTemplates()
        } catch (e: Exception) {
            logger.error(
                "Failed to refresh authentication or fetch templates after refresh, falling back to mock data",
                e
            )
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
        val token = cachedToken ?: properties.coder.token
        token?.let {
            headers.set("Coder-Session-Token", it)
        }
        headers.set("Accept", "application/json")
        return headers
    }

    /**
     * Refreshes the authentication token using the existing token.
     */
    private fun refreshAuthToken() {
        logger.info("Attempting to refresh Coder authentication token")

        try {
            val refreshUrl = "${properties.coder.baseUrl}/api/v2/users/me/tokens"
            val refreshHeaders = HttpHeaders()
            refreshHeaders.set("Content-Type", "application/json")
            refreshHeaders.set("Accept", "application/json")

            // Use existing token for authentication
            val currentToken = cachedToken ?: properties.coder.token
                ?: throw IllegalStateException("No token available for refresh")

            refreshHeaders.set("Coder-Session-Token", currentToken)

            val refreshRequest = mapOf(
                "lifetime" to TimeUnit.DAYS.toSeconds(1) // 1 day lifetime
            )

            val refreshEntity = HttpEntity(refreshRequest, refreshHeaders)
            val refreshResponse = restTemplate.exchange(refreshUrl, HttpMethod.POST, refreshEntity, Map::class.java)

            // Extract new session token from response
            val responseBody = refreshResponse.body as? Map<String, Any>
            val newSessionToken = responseBody?.get("key") as? String
                ?: throw IllegalStateException("No session token received from refresh response")

            cachedToken = newSessionToken
            lastRefreshTime = System.currentTimeMillis()
            logger.info("Successfully refreshed Coder authentication token")
        } catch (e: Exception) {
            logger.error("Failed to refresh Coder authentication token", e)
            throw e
        }
    }

    /**
     * Scheduled task to refresh the authentication token daily.
     */
    @Scheduled(fixedRate = 86400000) // 24 hours in milliseconds
    fun scheduledTokenRefresh() {
        try {
            if (properties.coder.token != null) {
                logger.info("Performing scheduled token refresh")
                refreshAuthToken()
            } else {
                logger.warn(
                    "No Coder token configured - scheduled refresh skipped. " +
                        "Check KERUTA_EXECUTOR_CODER_TOKEN env var or K8s secret."
                )
            }
        } catch (e: Exception) {
            logger.error("Scheduled token refresh failed", e)
        }
    }

    /**
     * Checks if the token needs to be refreshed (older than 23 hours).
     */
    private fun shouldRefreshToken(): Boolean {
        val twentyThreeHours = TimeUnit.HOURS.toMillis(23)
        return System.currentTimeMillis() - lastRefreshTime > twentyThreeHours
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

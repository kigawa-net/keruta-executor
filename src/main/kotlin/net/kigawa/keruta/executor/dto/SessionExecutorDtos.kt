package net.kigawa.keruta.executor.dto

import java.time.LocalDateTime

/**
 * DTO for session data.
 */
data class SessionDto(
    val id: String,
    val name: String,
    val status: String,
    val tags: List<String>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/**
 * DTO for workspace data.
 */
data class WorkspaceDto(
    val id: String,
    val name: String,
    val sessionId: String,
    val status: String,
    val coderWorkspaceId: String?,
    val workspaceUrl: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/**
 * Request for creating a workspace.
 */
data class CreateWorkspaceRequest(
    val name: String,
    val sessionId: String,
    val templateId: String?,
    val automaticUpdates: Boolean,
    val ttlMs: Long,
)

/**
 * Request for updating session status.
 */
data class UpdateSessionStatusRequest(
    val status: String,
)

/**
 * DTO for workspace template data.
 */
data class WorkspaceTemplateDto(
    val id: String,
    val name: String,
    val description: String?,
    val version: String,
    val icon: String?,
    val isDefault: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/**
 * DTO for Coder template data.
 */
data class CoderTemplateDto(
    val id: String,
    val name: String,
    val displayName: String,
    val description: String,
    val icon: String?,
    val default: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
)

/**
 * Request for creating a Coder workspace.
 */
data class CreateCoderWorkspaceRequest(
    val name: String,
    val templateId: String,
    val richParameterValues: List<Map<String, Any>>,
)

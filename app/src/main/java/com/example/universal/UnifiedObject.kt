package com.example.universal

import com.google.gson.JsonObject

/**
 * Unified object representation for the common object layer.
 * Normalizes data from ContactsProvider, CalendarProvider, NotificationListener, etc.
 */
data class UnifiedObject(
    val id: String,
    val type: ObjectType,
    val schemaVersion: Int = 1,
    val payload: JsonObject,
    val provenance: Provenance,
    val permissions: ObjectPermissions = ObjectPermissions(),
    val auditId: String? = null
) {
    fun toJsonObject(): JsonObject {
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("type", type.name)
            addProperty("schemaVersion", schemaVersion)
            add("payload", payload)
            add("provenance", provenance.toJson())
            add("permissions", permissions.toJson())
            auditId?.let { addProperty("auditId", it) }
        }
    }
}

enum class ObjectType {
    Contact,
    Event,
    Notification,
    Note,
    Unknown
}

data class Provenance(
    val source: String,
    val sourceId: String,
    val fetchedAt: Long,
    val lastModifiedAt: Long? = null
) {
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("source", source)
            addProperty("sourceId", sourceId)
            addProperty("fetchedAt", fetchedAt)
            lastModifiedAt?.let { addProperty("lastModifiedAt", it) }
        }
    }
}

data class ObjectPermissions(
    val canRead: Boolean = true,
    val canWrite: Boolean = true,
    val canDelete: Boolean = false
) {
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("canRead", canRead)
            addProperty("canWrite", canWrite)
            addProperty("canDelete", canDelete)
        }
    }
}

package com.example.universal

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory store for UnifiedObjects with audit log and undo support.
 * Schema: (a) type, (b) provenance, (c) permissions, (d) audit log, (e) undo stack.
 */
class SharedObjectStore(private val context: Context) {

    private val gson = Gson()
    private val objects = CopyOnWriteArrayList<UnifiedObject>()
    private val auditLog = CopyOnWriteArrayList<AuditEntry>()
    private val undoStack = ArrayDeque<UndoAction>()
    private val persistFile: File by lazy {
        File(context.filesDir, "shared_objects.json")
    }

    init {
        loadFromDisk()
    }

    fun add(obj: UnifiedObject): String {
        val auditId = UUID.randomUUID().toString()
        val withAudit = obj.copy(auditId = auditId)
        objects.add(withAudit)
        auditLog.add(AuditEntry(auditId, "add", obj.type.name, System.currentTimeMillis()))
        saveToDisk()
        return auditId
    }

    fun addAll(list: List<UnifiedObject>) {
        list.forEach { add(it) }
    }

    fun remove(id: String): Boolean {
        val index = objects.indexOfFirst { it.id == id }
        if (index < 0) return false
        val removed = objects.removeAt(index)
        val auditId = UUID.randomUUID().toString()
        auditLog.add(AuditEntry(auditId, "remove", removed.type.name, System.currentTimeMillis()))
        undoStack.addLast(UndoAction.Remove(removed))
        saveToDisk()
        return true
    }

    fun update(id: String, updated: UnifiedObject): Boolean {
        val index = objects.indexOfFirst { it.id == id }
        if (index < 0) return false
        val old = objects[index]
        objects[index] = updated
        val auditId = UUID.randomUUID().toString()
        auditLog.add(AuditEntry(auditId, "update", updated.type.name, System.currentTimeMillis()))
        undoStack.addLast(UndoAction.Update(old))
        saveToDisk()
        return true
    }

    fun get(id: String): UnifiedObject? = objects.find { it.id == id }

    fun getAll(): List<UnifiedObject> = objects.toList()

    fun getByType(type: ObjectType): List<UnifiedObject> =
        objects.filter { it.type == type }.toList()

    fun getAuditLog(limit: Int = 100): List<AuditEntry> =
        auditLog.takeLast(limit).reversed()

    fun undo(): Boolean {
        val action = undoStack.removeLastOrNull() ?: return false
        when (action) {
            is UndoAction.Remove -> objects.add(action.obj)
            is UndoAction.Update -> {
                val index = objects.indexOfFirst { it.id == action.obj.id }
                if (index >= 0) objects[index] = action.obj
            }
        }
        saveToDisk()
        return true
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    private fun saveToDisk() {
        try {
            val arr = JsonArray()
            objects.forEach { arr.add(it.toJsonObject()) }
            val wrapper = JsonObject().apply {
                add("objects", arr)
                addProperty("auditCount", auditLog.size)
            }
            persistFile.writeText(gson.toJson(wrapper))
        } catch (e: Exception) {
            Log.e(TAG, "saveToDisk error: ${e.message}")
        }
    }

    private fun loadFromDisk() {
        try {
            if (!persistFile.exists()) return
            val wrapper = gson.fromJson(persistFile.readText(), JsonObject::class.java)
            val arr = wrapper.getAsJsonArray("objects") ?: return
            arr.forEach { elem ->
                try {
                    val obj = jsonToUnifiedObject(elem.asJsonObject)
                    objects.add(obj)
                } catch (e: Exception) {
                    Log.e(TAG, "load object error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFromDisk error: ${e.message}")
        }
    }

    private fun jsonToUnifiedObject(json: JsonObject): UnifiedObject {
        val id = json.get("id").asString
        val typeStr = json.get("type").asString
        val type = try {
            ObjectType.valueOf(typeStr)
        } catch (e: Exception) {
            ObjectType.Unknown
        }
        val schemaVersion = json.get("schemaVersion")?.asInt ?: 1
        val payload = json.getAsJsonObject("payload") ?: JsonObject()
        val provJson = json.getAsJsonObject("provenance")
        val provenance = if (provJson != null) Provenance(
            source = provJson.get("source")?.asString ?: "unknown",
            sourceId = provJson.get("sourceId")?.asString ?: id,
            fetchedAt = provJson.get("fetchedAt")?.asLong ?: 0L,
            lastModifiedAt = provJson.get("lastModifiedAt")?.asLong
        ) else Provenance(source = "unknown", sourceId = id, fetchedAt = 0L)
        val permJson = json.getAsJsonObject("permissions")
        val permissions = if (permJson != null) ObjectPermissions(
            canRead = permJson.get("canRead")?.asBoolean ?: true,
            canWrite = permJson.get("canWrite")?.asBoolean ?: true,
            canDelete = permJson.get("canDelete")?.asBoolean ?: false
        ) else ObjectPermissions()
        val auditId = json.get("auditId")?.asString
        return UnifiedObject(id, type, schemaVersion, payload, provenance, permissions, auditId)
    }

    data class AuditEntry(
        val auditId: String,
        val action: String,
        val objectType: String,
        val timestamp: Long
    )

    sealed class UndoAction {
        data class Remove(val obj: UnifiedObject) : UndoAction()
        data class Update(val obj: UnifiedObject) : UndoAction()
    }

    companion object {
        private const val TAG = "SharedObjectStore"
    }
}

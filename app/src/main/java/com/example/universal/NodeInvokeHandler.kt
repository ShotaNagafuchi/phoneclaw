package com.example.universal

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking

/**
 * Handles node.invoke requests from the OpenClaw Gateway.
 * Routes commands to MyAccessibilityService, ObjectProviders (contacts/calendar),
 * and optionally to vision callbacks (magicClicker, magicScraper) provided by MainActivity.
 */
class NodeInvokeHandler(
    private val visionCallbacks: VisionCallbacks?,
    private val context: Context?
) {
    data class InvokeResult(
        val success: Boolean,
        val payload: JsonObject? = null,
        val error: String? = null
    )

    fun handleInvoke(command: String, params: JsonObject): InvokeResult {
        return try {
            when (command) {
                "android.tap" -> handleTap(params)
                "android.swipe" -> handleSwipe(params)
                "android.type" -> handleType(params)
                "android.magicClick" -> handleMagicClick(params)
                "android.magicScraper" -> handleMagicScraper(params)
                "android.getScreenText" -> handleGetScreenText(params)
                "android.contacts.list" -> handleContactsList(params)
                "android.calendar.list" -> handleCalendarList(params)
                "android.contacts.update" -> handleContactsUpdate(params)
                "android.calendar.createEvent" -> handleCalendarCreateEvent(params)
                "android.notifications.list" -> handleNotificationsList(params)
                else -> InvokeResult(false, error = "Unknown command: $command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invoke error for $command: ${e.message}")
            InvokeResult(false, error = e.message ?: "Unknown error")
        }
    }

    private fun handleTap(params: JsonObject): InvokeResult {
        val x = params.get("x")?.asFloat ?: params.get("x")?.asDouble?.toFloat()
            ?: return InvokeResult(false, error = "Missing x")
        val y = params.get("y")?.asFloat ?: params.get("y")?.asDouble?.toFloat()
            ?: return InvokeResult(false, error = "Missing y")

        val service = MyAccessibilityService.instance
        if (service == null) {
            return InvokeResult(false, error = "Accessibility service not available")
        }

        service.simulateClick(x, y)
        return InvokeResult(true, JsonObject().apply {
            addProperty("x", x)
            addProperty("y", y)
        })
    }

    private fun handleSwipe(params: JsonObject): InvokeResult {
        val startX = params.get("startX")?.asFloat ?: params.get("startX")?.asDouble?.toFloat()
            ?: return InvokeResult(false, error = "Missing startX")
        val startY = params.get("startY")?.asFloat ?: params.get("startY")?.asDouble?.toFloat()
            ?: return InvokeResult(false, error = "Missing startY")
        val endX = params.get("endX")?.asFloat ?: params.get("endX")?.asDouble?.toFloat()
            ?: return InvokeResult(false, error = "Missing endX")
        val endY = params.get("endY")?.asFloat ?: params.get("endY")?.asDouble?.toFloat()
            ?: return InvokeResult(false, error = "Missing endY")

        val service = MyAccessibilityService.instance
        if (service == null) {
            return InvokeResult(false, error = "Accessibility service not available")
        }

        service.simulateSwipe(startX, startY, endX, endY)
        return InvokeResult(true, JsonObject().apply {
            addProperty("startX", startX)
            addProperty("startY", startY)
            addProperty("endX", endX)
            addProperty("endY", endY)
        })
    }

    private fun handleType(params: JsonObject): InvokeResult {
        val text = params.get("text")?.asString ?: return InvokeResult(false, error = "Missing text")

        val service = MyAccessibilityService.instance
        if (service == null) {
            return InvokeResult(false, error = "Accessibility service not available")
        }

        service.simulateTypeInFirstEditableField(text)
        return InvokeResult(true, JsonObject().apply {
            addProperty("text", text)
        })
    }

    private fun handleMagicClick(params: JsonObject): InvokeResult {
        val description = params.get("description")?.asString
            ?: return InvokeResult(false, error = "Missing description")

        val callbacks = visionCallbacks
        if (callbacks == null) {
            return InvokeResult(false, error = "Vision callbacks not available (MainActivity required)")
        }

        return runBlocking {
            val success = callbacks.magicClick(description)
            InvokeResult(success, JsonObject().apply {
                addProperty("description", description)
                addProperty("clicked", success)
            })
        }
    }

    private fun handleMagicScraper(params: JsonObject): InvokeResult {
        val question = params.get("question")?.asString ?: params.get("description")?.asString
            ?: return InvokeResult(false, error = "Missing question/description")

        val callbacks = visionCallbacks
        if (callbacks == null) {
            return InvokeResult(false, error = "Vision callbacks not available (MainActivity required)")
        }

        val result = callbacks.magicScrape(question)
        return InvokeResult(true, JsonObject().apply {
            addProperty("result", result)
        })
    }

    private fun handleGetScreenText(params: JsonObject): InvokeResult {
        val service = MyAccessibilityService.instance
        if (service == null) {
            return InvokeResult(false, error = "Accessibility service not available")
        }

        val text = service.getAllTextFromScreen()
        return InvokeResult(true, JsonObject().apply {
            addProperty("text", text)
        })
    }

    private fun handleContactsList(params: JsonObject): InvokeResult {
        val ctx = context ?: return InvokeResult(false, error = "Context not available")
        val limit = params.get("limit")?.asInt ?: 100
        val contacts = ObjectProviders.listContacts(ctx, limit)
        val arr = com.google.gson.JsonArray()
        contacts.forEach { arr.add(it.toJsonObject()) }
        return InvokeResult(true, JsonObject().apply {
            add("contacts", arr)
            addProperty("count", contacts.size)
        })
    }

    private fun handleCalendarList(params: JsonObject): InvokeResult {
        val ctx = context ?: return InvokeResult(false, error = "Context not available")
        val startMs = params.get("startMillis")?.asLong ?: System.currentTimeMillis()
        val endMs = params.get("endMillis")?.asLong
            ?: (startMs + 7 * 24 * 60 * 60 * 1000L)
        val limit = params.get("limit")?.asInt ?: 50
        val events = ObjectProviders.listCalendarEvents(ctx, startMs, endMs, limit)
        val arr = com.google.gson.JsonArray()
        events.forEach { arr.add(it.toJsonObject()) }
        return InvokeResult(true, JsonObject().apply {
            add("events", arr)
            addProperty("count", events.size)
        })
    }

    private fun handleContactsUpdate(params: JsonObject): InvokeResult {
        val ctx = context ?: return InvokeResult(false, error = "Context not available")
        var contactId = params.get("contactId")?.asString ?: return InvokeResult(false, error = "Missing contactId")
        contactId = contactId.removePrefix("contact-")
        val displayName = params.get("displayName")?.asString
        val ok = ObjectProviders.updateContact(ctx, contactId, displayName)
        return InvokeResult(ok, JsonObject().apply {
            addProperty("contactId", contactId)
            addProperty("updated", ok)
        })
    }

    private fun handleCalendarCreateEvent(params: JsonObject): InvokeResult {
        val ctx = context ?: return InvokeResult(false, error = "Context not available")
        val title = params.get("title")?.asString ?: return InvokeResult(false, error = "Missing title")
        val dtStart = params.get("dtStart")?.asLong ?: return InvokeResult(false, error = "Missing dtStart")
        val dtEnd = params.get("dtEnd")?.asLong ?: return InvokeResult(false, error = "Missing dtEnd")
        val description = params.get("description")?.asString
        val eventId = ObjectProviders.createCalendarEvent(ctx, title, dtStart, dtEnd, description)
        return if (eventId != null) {
            InvokeResult(true, JsonObject().apply {
                addProperty("eventId", eventId)
            })
        } else {
            InvokeResult(false, error = "Failed to create calendar event")
        }
    }

    private fun handleNotificationsList(params: JsonObject): InvokeResult {
        val limit = params.get("limit")?.asInt ?: 50
        val notifications = NotificationCaptureService.getRecentNotifications(limit)
        val arr = com.google.gson.JsonArray()
        notifications.forEach { arr.add(it.toJsonObject()) }
        return InvokeResult(true, JsonObject().apply {
            add("notifications", arr)
            addProperty("count", notifications.size)
        })
    }

    /**
     * Callbacks for vision-based commands (magicClicker, magicScraper) that require
     * MainActivity's screenshot + Moondream API.
     */
    interface VisionCallbacks {
        fun magicClick(description: String): Boolean
        fun magicScrape(question: String): String
    }

    companion object {
        private const val TAG = "NodeInvokeHandler"
    }
}

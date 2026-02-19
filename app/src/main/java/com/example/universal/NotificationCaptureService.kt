package com.example.universal

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.JsonObject

/**
 * Captures system notifications and normalizes them to UnifiedObject for the common object layer.
 * User must enable this in Settings > Apps > Special app access > Notification access.
 */
class NotificationCaptureService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val obj = notificationToUnifiedObject(sbn)
            synchronized(notificationBuffer) {
                notificationBuffer.add(0, obj)
                while (notificationBuffer.size > MAX_BUFFER) {
                    notificationBuffer.removeAt(notificationBuffer.size - 1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onNotificationPosted error: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val id = "notification-${sbn.packageName}-${sbn.id}-${sbn.tag ?: ""}"
            synchronized(notificationBuffer) {
                notificationBuffer.removeAll { it.id == id }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onNotificationRemoved error: ${e.message}")
        }
    }

    private fun notificationToUnifiedObject(sbn: StatusBarNotification): UnifiedObject {
        val notif = sbn.notification
        val extras = notif.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val payload = JsonObject().apply {
            addProperty("packageName", sbn.packageName)
            addProperty("title", title)
            addProperty("text", text)
            addProperty("subText", subText)
            addProperty("postTime", sbn.postTime)
            addProperty("id", sbn.id)
            addProperty("tag", sbn.tag)
        }

        val id = "notification-${sbn.packageName}-${sbn.id}-${sbn.tag ?: ""}"
        return UnifiedObject(
            id = id,
            type = ObjectType.Notification,
            payload = payload,
            provenance = Provenance(
                source = "notification",
                sourceId = id,
                fetchedAt = sbn.postTime,
                lastModifiedAt = null
            ),
            permissions = ObjectPermissions(canRead = true, canWrite = false, canDelete = false)
        )
    }

    companion object {
        private const val TAG = "NotificationCapture"
        private const val MAX_BUFFER = 200

        private val notificationBuffer = mutableListOf<UnifiedObject>()

        fun getRecentNotifications(limit: Int = 50): List<UnifiedObject> {
            synchronized(notificationBuffer) {
                return notificationBuffer.take(limit).toList()
            }
        }

        fun clearBuffer() {
            synchronized(notificationBuffer) {
                notificationBuffer.clear()
            }
        }
    }
}

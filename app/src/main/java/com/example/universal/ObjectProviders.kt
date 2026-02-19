package com.example.universal

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.*

/**
 * Reads from ContactsProvider and CalendarProvider, normalizing to UnifiedObject.
 */
object ObjectProviders {

    fun listContacts(context: Context, limit: Int = 100): List<UnifiedObject> {
        val list = mutableListOf<UnifiedObject>()
        try {
            val cr = context.contentResolver
            val uri = ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            cr.query(uri, projection, null, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC")
                ?.use { cursor ->
                    var count = 0
                    while (cursor.moveToNext() && count < limit) {
                        val id = cursor.getLong(0).toString()
                        val name = cursor.getString(1) ?: ""
                        val hasPhone = cursor.getInt(2) == 1

                        val phones = if (hasPhone) getPhonesForContact(cr, id) else emptyList()

                        val payload = JsonObject().apply {
                            addProperty("displayName", name)
                            addProperty("hasPhone", hasPhone)
                            add("phones", JsonArray().apply {
                                phones.forEach { add(it) }
                            })
                        }

                        list.add(
                            UnifiedObject(
                                id = "contact-$id",
                                type = ObjectType.Contact,
                                payload = payload,
                                provenance = Provenance(
                                    source = "contacts",
                                    sourceId = id,
                                    fetchedAt = System.currentTimeMillis()
                                )
                            )
                        )
                        count++
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "listContacts error: ${e.message}")
        }
        return list
    }

    private fun getPhonesForContact(cr: ContentResolver, contactId: String): List<String> {
        val phones = mutableListOf<String>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            cr.query(
                uri,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { phones.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPhonesForContact error: ${e.message}")
        }
        return phones
    }

    fun listCalendarEvents(
        context: Context,
        startMillis: Long = System.currentTimeMillis(),
        endMillis: Long = startMillis + 7 * 24 * 60 * 60 * 1000L,
        limit: Int = 50
    ): List<UnifiedObject> {
        val list = mutableListOf<UnifiedObject>()
        try {
            val cr = context.contentResolver
            val uri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
            cr.query(uri, projection, selection, selectionArgs, CalendarContract.Events.DTSTART + " ASC")
                ?.use { cursor ->
                    var count = 0
                    while (cursor.moveToNext() && count < limit) {
                        val id = cursor.getLong(0).toString()
                        val title = cursor.getString(1) ?: ""
                        val dtStart = cursor.getLong(2)
                        val dtEnd = cursor.getLong(3)
                        val location = cursor.getString(4) ?: ""
                        val description = cursor.getString(5) ?: ""

                        val payload = JsonObject().apply {
                            addProperty("title", title)
                            addProperty("dtStart", dtStart)
                            addProperty("dtEnd", dtEnd)
                            addProperty("location", location)
                            addProperty("description", description)
                        }

                        list.add(
                            UnifiedObject(
                                id = "event-$id",
                                type = ObjectType.Event,
                                payload = payload,
                                provenance = Provenance(
                                    source = "calendar",
                                    sourceId = id,
                                    fetchedAt = System.currentTimeMillis(),
                                    lastModifiedAt = dtEnd
                                )
                            )
                        )
                        count++
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "listCalendarEvents error: ${e.message}")
        }
        return list
    }

    fun updateContact(
        context: Context,
        contactId: String,
        displayName: String? = null
    ): Boolean {
        if (displayName == null) return true
        return try {
            val cr = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
            }
            val updated = cr.update(
                ContactsContract.Data.CONTENT_URI,
                values,
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            )
            updated > 0
        } catch (e: Exception) {
            Log.e(TAG, "updateContact error: ${e.message}")
            false
        }
    }

    fun createCalendarEvent(
        context: Context,
        title: String,
        dtStart: Long,
        dtEnd: Long,
        description: String? = null
    ): String? {
        return try {
            val cr = context.contentResolver
            val calendarId = getDefaultCalendarId(cr) ?: return null
            val values = android.content.ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, dtStart)
                put(CalendarContract.Events.DTEND, dtEnd)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (description != null) put(CalendarContract.Events.DESCRIPTION, description)
            }
            val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment
        } catch (e: Exception) {
            Log.e(TAG, "createCalendarEvent error: ${e.message}")
            null
        }
    }

    private fun getDefaultCalendarId(cr: ContentResolver): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE}=1",
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }

    private const val TAG = "ObjectProviders"
}

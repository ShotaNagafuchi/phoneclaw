package com.example.universal

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/**
 * BuddyWidgetProvider: ホームウィジェットでAIアシスタントの状態を表示
 * 「状態（Listening/Idle）」「次の提案」「ワンタップで実行」をサポート
 */
class BuddyWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val TAG = "BuddyWidgetProvider"
        const val ACTION_REFRESH = "com.example.universal.ACTION_REFRESH"
        const val ACTION_SEARCH = "com.example.universal.ACTION_SEARCH"
        const val ACTION_WIFI = "com.example.universal.ACTION_WIFI"
        const val ACTION_CALENDAR = "com.example.universal.ACTION_CALENDAR"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, BuddyWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            ACTION_SEARCH -> {
                openChromeSearch(context)
            }
            ACTION_WIFI -> {
                openWifiSettings(context)
            }
            ACTION_CALENDAR -> {
                openCalendar(context)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_buddy)

        // 状態を取得
        val buddyService = BuddyService.instance
        val state = if (buddyService != null) {
            context.getString(R.string.buddy_status_running)
        } else {
            context.getString(R.string.buddy_status_idle)
        }
        val suggestionCount = buddyService?.let { 
            val prefs = context.getSharedPreferences("BuddyPrefs", Context.MODE_PRIVATE)
            prefs.getInt("today_suggestions", 0)
        } ?: 0

        // 状態を表示
        views.setTextViewText(R.id.widget_state, state)
        val suggestionText = if (suggestionCount > 0) {
            context.getString(R.string.buddy_widget_suggestions_fmt, suggestionCount)
        } else {
            context.getString(R.string.buddy_widget_waiting)
        }
        views.setTextViewText(R.id.widget_suggestion, suggestionText)

        // クリックアクションを設定
        val refreshIntent = Intent(context, BuddyWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)

        // Chrome検索
        val searchIntent = Intent(context, BuddyWidgetProvider::class.java).apply {
            action = ACTION_SEARCH
        }
        val searchPendingIntent = PendingIntent.getBroadcast(
            context, 1, searchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_search, searchPendingIntent)

        // Wi-Fi確認
        val wifiIntent = Intent(context, BuddyWidgetProvider::class.java).apply {
            action = ACTION_WIFI
        }
        val wifiPendingIntent = PendingIntent.getBroadcast(
            context, 2, wifiIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_wifi, wifiPendingIntent)

        // 予定表示
        val calendarIntent = Intent(context, BuddyWidgetProvider::class.java).apply {
            action = ACTION_CALENDAR
        }
        val calendarPendingIntent = PendingIntent.getBroadcast(
            context, 3, calendarIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_calendar, calendarPendingIntent)

        // メインアクティビティを開く
        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, mainPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun openChromeSearch(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q="))
            intent.setPackage("com.android.chrome")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Chromeがインストールされていない場合はブラウザを開く
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun openWifiSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun openCalendar(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "time"
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open calendar", e)
        }
    }
}

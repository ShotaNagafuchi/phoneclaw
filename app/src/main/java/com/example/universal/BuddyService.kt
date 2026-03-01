package com.example.universal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.universal.edge.EdgeAIManager
import java.util.Calendar

/**
 * BuddyService: Foreground Serviceとして常駐し、AIアシスタントの状態を管理
 * 永続通知で「Buddy稼働中」「今日の提案1件」を表示
 */
class BuddyService : Service() {
    companion object {
        private const val TAG = "BuddyService"
        private const val CHANNEL_ID = "BuddyServiceChannel"
        private const val NOTIFICATION_ID = 1000
        private const val PREFS_NAME = "BuddyPrefs"
        private const val KEY_LAST_UNLOCK_DATE = "last_unlock_date"
        private const val KEY_TODAY_SUGGESTIONS = "today_suggestions"
        
        var instance: BuddyService? = null
            private set
    }

    private lateinit var sharedPreferences: SharedPreferences
    private var suggestionCount = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        updateSuggestionCount()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Edge AI 3層アーキテクチャを初期化
        EdgeAIManager.init(this)

        Log.i(TAG, "BuddyService created and in foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "BuddyService destroyed")
    }

    /**
     * 通知を更新（提案数など）
     */
    fun updateNotification() {
        updateSuggestionCount()
        val notification = buildNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 今日の提案数を更新
     */
    private fun updateSuggestionCount() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDate = sharedPreferences.getInt(KEY_LAST_UNLOCK_DATE, -1)
        
        if (lastDate != today) {
            // 新しい日なのでリセット
            suggestionCount = 1
            sharedPreferences.edit()
                .putInt(KEY_LAST_UNLOCK_DATE, today)
                .putInt(KEY_TODAY_SUGGESTIONS, suggestionCount)
                .apply()
        } else {
            suggestionCount = sharedPreferences.getInt(KEY_TODAY_SUGGESTIONS, 1)
        }
    }

    /**
     * 提案数をインクリメント
     */
    fun incrementSuggestionCount() {
        suggestionCount++
        sharedPreferences.edit()
            .putInt(KEY_TODAY_SUGGESTIONS, suggestionCount)
            .apply()
        updateNotification()
    }

    /**
     * 今日が初回解除かどうかをチェック
     */
    fun isFirstUnlockToday(): Boolean {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDate = sharedPreferences.getInt(KEY_LAST_UNLOCK_DATE, -1)
        return lastDate != today
    }

    /**
     * 深夜かどうかをチェック（22時〜6時）
     */
    fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 6
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (suggestionCount > 0) {
            getString(R.string.buddy_notification_suggestions_fmt, suggestionCount)
        } else {
            getString(R.string.buddy_notification_idle)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.buddy_notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.buddy_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.buddy_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}

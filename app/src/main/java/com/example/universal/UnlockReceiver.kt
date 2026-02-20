package com.example.universal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ロック解除受信機: ACTION_USER_PRESENTとACTION_SCREEN_ONを受信
 * ルールエンジンに基づいてTTSで短い一言を発話
 */
class UnlockReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UnlockReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "Screen unlocked")
                handleUnlock(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen turned on")
                // 補助的なイベントとして記録
            }
        }
    }

    private fun handleUnlock(context: Context) {
        scope.launch {
            try {
                // BuddyServiceが起動していない場合は起動
                val serviceIntent = Intent(context, BuddyService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)

                // 少し待ってからルールを評価
                kotlinx.coroutines.delay(500)

                val ruleEngine = RuleEngine(context)
                val action = ruleEngine.evaluateRules()

                when (action) {
                    is RuleAction.TTS -> {
                        speakText(context, action.message)
                    }
                    is RuleAction.Notification -> {
                        showNotification(context, action.title, action.message)
                    }
                    null -> {
                        Log.d(TAG, "No rule triggered")
                    }
                    else -> {
                        Log.d(TAG, "Unhandled action: $action")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling unlock", e)
            }
        }
    }

    private fun speakText(context: Context, text: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context, object : TextToSpeech.OnInitListener {
            override fun onInit(status: Int) {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    tts!!.language = Locale.JAPANESE
                    tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                    Log.d(TAG, "TTS: $text")
                } else {
                    Log.e(TAG, "TTS initialization failed")
                }
            }
        })
    }

    private fun showNotification(context: Context, title: String, message: String) {
        // BuddyNotificationManagerを使用して通知を表示
        val notificationManager = BuddyNotificationManager(context)
        notificationManager.showRuleNotification(title, message)
    }
}

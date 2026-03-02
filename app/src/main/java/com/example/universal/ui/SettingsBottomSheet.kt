package com.example.universal.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.universal.R
import com.example.universal.MyAccessibilityService
import com.example.universal.edge.EdgeAIManager
import com.example.universal.edge.SoulManager
import com.example.universal.edge.inference.LlmModelManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsBottomSheet : BottomSheetDialogFragment() {

    var onSoulReset: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        // Permissions
        view.findViewById<TextView>(R.id.permAccessibility).text =
            "AccessibilityService: ${if (isAccessibilityEnabled(ctx)) "ON" else "OFF"}"
        view.findViewById<TextView>(R.id.permNotification).text =
            "通知アクセス: ${if (isNotificationAccessEnabled(ctx)) "ON" else "OFF"}"
        view.findViewById<TextView>(R.id.permUsageStats).text =
            "使用状況アクセス: ${if (isUsageStatsEnabled(ctx)) "ON" else "OFF"}"
        view.findViewById<TextView>(R.id.permMicrophone).text =
            "マイク: ${if (isMicrophoneEnabled(ctx)) "ON" else "OFF"}"

        // AI observation
        val service = MyAccessibilityService.instance
        view.findViewById<TextView>(R.id.aiCurrentApp).text =
            "現在のアプリ: ${service?.currentPackageName ?: "不明"}"
        view.findViewById<TextView>(R.id.aiLastEmotion).text =
            "最後の感情: ${service?.lastEmotionLabel ?: "---"}"

        view.findViewById<TextView>(R.id.aiPersonality).text =
            "性格: ${SoulManager.getPersonalitySummary(ctx)}"

        val manager = EdgeAIManager.instance
        view.findViewById<TextView>(R.id.aiEngine).text =
            "エンジン: ${manager?.currentEngineName() ?: "未初期化"}"

        // LLMモデル状態
        val llmStatus = when (LlmModelManager.state) {
            LlmModelManager.ModelState.NOT_DOWNLOADED -> "未DL"
            LlmModelManager.ModelState.DOWNLOADING ->
                "DL中 ${(LlmModelManager.downloadProgress * 100).toInt()}%"
            LlmModelManager.ModelState.READY -> "Gemma 2B 準備完了"
            LlmModelManager.ModelState.ERROR ->
                "エラー: ${LlmModelManager.errorMessage?.take(30) ?: "不明"}"
        }
        val generatorName = manager?.currentGeneratorName() ?: "---"
        view.findViewById<TextView>(R.id.aiEngine).text =
            "エンジン: ${manager?.currentEngineName() ?: "未初期化"} / $generatorName ($llmStatus)"

        // Toggles
        val prefs = ctx.getSharedPreferences("phoneclaw_settings", Context.MODE_PRIVATE)

        view.findViewById<SwitchMaterial>(R.id.toggleOverlay).apply {
            isChecked = prefs.getBoolean("overlay_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("overlay_enabled", checked).apply()
            }
        }

        view.findViewById<SwitchMaterial>(R.id.toggleTTS).apply {
            isChecked = prefs.getBoolean("tts_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("tts_enabled", checked).apply()
            }
        }

        // Reset soul
        view.findViewById<MaterialButton>(R.id.resetSoulButton).setOnClickListener {
            SoulManager.resetToDefault(ctx)
            onSoulReset?.invoke()
            dismiss()
        }
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val myService = ComponentName(ctx, MyAccessibilityService::class.java)
        return enabled.any {
            ComponentName.unflattenFromString(it.id) == myService
        }
    }

    private fun isNotificationAccessEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        return flat?.contains(ctx.packageName) == true
    }

    private fun isUsageStatsEnabled(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isMicrophoneEnabled(ctx: Context): Boolean {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val TAG = "SettingsBottomSheet"
    }
}

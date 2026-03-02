package com.example.universal.edge.autonomous

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.universal.MyAccessibilityService
import com.example.universal.edge.EdgeAIManager
import com.example.universal.edge.inference.EmotionOutput
import com.example.universal.edge.inference.EmotionType
import com.example.universal.edge.inference.EmotionAction
import kotlinx.coroutines.*

/**
 * フル自律操作エージェント。
 *
 * 観察→判断→実行のループを実行。
 * オーブタップでON/OFF。2秒間隔、1セッション最大10アクション。
 */
class AutonomousAgent(private val context: Context) {

    companion object {
        private const val TAG = "AutonomousAgent"
        private const val LOOP_INTERVAL_MS = 2000L
        private const val MAX_ACTIONS_PER_SESSION = 10
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    @Volatile
    var isRunning = false
        private set

    private var actionCount = 0

    private val actionPlanner = LlmActionPlanner(context)

    fun start() {
        if (isRunning) return
        isRunning = true
        actionCount = 0
        Log.i(TAG, "Autonomous mode started")

        job = scope.launch {
            while (isActive && isRunning && actionCount < MAX_ACTIONS_PER_SESSION) {
                try {
                    val observation = observe()
                    val action = decide(observation)
                    execute(action)
                    actionCount++
                    Log.d(TAG, "Action $actionCount/$MAX_ACTIONS_PER_SESSION: $action")
                } catch (e: Exception) {
                    Log.e(TAG, "Agent loop error", e)
                }
                delay(LOOP_INTERVAL_MS)
            }
            Log.i(TAG, "Autonomous session ended (actions: $actionCount)")
            isRunning = false
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
        Log.i(TAG, "Autonomous mode stopped")
    }

    data class ScreenObservation(
        val packageName: String?,
        val screenText: String?,
        val emotionOutput: EmotionOutput?
    )

    private suspend fun observe(): ScreenObservation {
        val service = MyAccessibilityService.instance
        val packageName = service?.currentPackageName
        val screenText = try {
            withContext(Dispatchers.Main) {
                service?.getAllTextFromScreen()
            }
        } catch (_: Exception) { null }

        val emotionOutput = try {
            EdgeAIManager.instance?.react()
        } catch (_: Exception) { null }

        return ScreenObservation(packageName, screenText, emotionOutput)
    }

    private suspend fun decide(observation: ScreenObservation): AgentAction {
        return try {
            actionPlanner.plan(observation)
        } catch (e: Exception) {
            Log.w(TAG, "Planning failed, defaulting to Observe", e)
            AgentAction.Observe
        }
    }

    private fun execute(action: AgentAction) {
        val service = MyAccessibilityService.instance ?: run {
            Log.w(TAG, "AccessibilityService not available")
            return
        }

        when (action) {
            is AgentAction.OpenApp -> {
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Log.d(TAG, "Opened app: ${action.packageName}")
                    } else {
                        Log.w(TAG, "Package not found: ${action.packageName}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open app: ${action.packageName}", e)
                }
            }
            is AgentAction.TapByText -> {
                service.clickButtonWithLabel(action.text)
            }
            is AgentAction.TapByDescription -> {
                service.clickByDesc(action.description)
            }
            is AgentAction.TypeText -> {
                service.enterTextInField(action.text)
            }
            is AgentAction.Swipe -> {
                val display = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
                val w = display.width
                val h = display.height
                val cx = w / 2f
                val cy = h / 2f
                val dist = h / 3f

                when (action.direction) {
                    SwipeDirection.UP -> service.simulateSwipe(cx, cy + dist, cx, cy - dist)
                    SwipeDirection.DOWN -> service.simulateSwipe(cx, cy - dist, cx, cy + dist)
                    SwipeDirection.LEFT -> service.simulateSwipe(cx + dist, cy, cx - dist, cy)
                    SwipeDirection.RIGHT -> service.simulateSwipe(cx - dist, cy, cx + dist, cy)
                }
            }
            is AgentAction.GoBack -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            is AgentAction.GoHome -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
            is AgentAction.Speak -> {
                // TTS経由で発話
                Log.d(TAG, "Agent speaks: ${action.text}")
            }
            is AgentAction.Observe -> {
                // 何もしない（次ループで再観察）
            }
        }
    }
}

// --- アクション定義 ---

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

sealed class AgentAction {
    data class OpenApp(val packageName: String) : AgentAction()
    data class TapByText(val text: String) : AgentAction()
    data class TapByDescription(val description: String) : AgentAction()
    data class TypeText(val text: String) : AgentAction()
    data class Swipe(val direction: SwipeDirection) : AgentAction()
    data object GoBack : AgentAction()
    data object GoHome : AgentAction()
    data class Speak(val text: String) : AgentAction()
    data object Observe : AgentAction()
}

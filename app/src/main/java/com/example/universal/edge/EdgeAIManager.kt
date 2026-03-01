package com.example.universal.edge

import android.content.Context
import android.util.Log
import com.example.universal.edge.data.ContextRepository
import com.example.universal.edge.data.EdgeDatabase
import com.example.universal.edge.inference.EmotionOutput
import com.example.universal.edge.inference.IEmotionEngine
import com.example.universal.edge.inference.RuleBasedEmotionEngine
import com.example.universal.edge.learning.FaceRewardEvaluator
import com.example.universal.edge.learning.IRewardEvaluator
import com.example.universal.edge.learning.LearningOrchestrator
import com.example.universal.edge.learning.MemoryConsolidationWorker
import com.example.universal.edge.learning.ThompsonSamplingBandit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 3層アーキテクチャの統合マネージャー。
 *
 * 外部（BuddyService等）からはこのクラスのみを通じてEdge AIにアクセスする。
 * 内部で3つの層を疎結合に組み立て、ライフサイクルを管理する。
 *
 * 使い方:
 *   EdgeAIManager.init(context)
 *   val output = EdgeAIManager.instance?.react()
 *   EdgeAIManager.instance?.learnFromLastReaction()
 */
class EdgeAIManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // データ層
    private val database = EdgeDatabase.getInstance(context)
    private val repository = ContextRepository(
        database.userProfileDao(),
        database.interactionLogDao()
    )

    // 学習層コンポーネント
    private val bandit = ThompsonSamplingBandit()
    private val rewardEvaluator: IRewardEvaluator = FaceRewardEvaluator(context)
    private val orchestrator = LearningOrchestrator(repository, rewardEvaluator, bandit)

    // 推定層（差し替え可能）
    private var emotionEngine: IEmotionEngine = RuleBasedEmotionEngine(bandit)

    // 最後の推論結果（学習時に参照）
    private var lastOutput: EmotionOutput? = null

    companion object {
        private const val TAG = "EdgeAIManager"

        @Volatile
        var instance: EdgeAIManager? = null
            private set

        fun init(context: Context) {
            if (instance != null) return
            synchronized(this) {
                if (instance == null) {
                    instance = EdgeAIManager(context.applicationContext)
                    instance?.setup()
                    Log.d(TAG, "EdgeAIManager initialized")
                }
            }
        }
    }

    private fun setup() {
        // 報酬評価器を準備
        scope.launch(Dispatchers.IO) {
            try {
                rewardEvaluator.prepare()
            } catch (e: Exception) {
                Log.w(TAG, "RewardEvaluator preparation failed: ${e.message}")
            }
        }

        // 夜間統合ワーカーをスケジュール
        MemoryConsolidationWorker.schedule(context)
    }

    /**
     * 推定エンジンを差し替える（モデルアップグレード時に使用）。
     */
    fun swapEngine(newEngine: IEmotionEngine) {
        emotionEngine = newEngine
        Log.d(TAG, "Engine swapped to: ${newEngine.engineName}")
    }

    /**
     * 現在の文脈に基づいてAIのリアクションを推論する。
     *
     * @return EmotionOutput 推論結果。BuddyServiceが表情表示等に使う
     */
    suspend fun react(): EmotionOutput {
        val context = repository.getCurrentContext()
        val profile = repository.getUserProfile()
        val output = emotionEngine.infer(context, profile)
        lastOutput = output

        Log.d(TAG, "React: ${output.selectedAction.type.name} " +
            "intensity=${output.selectedAction.intensity} " +
            "confidence=${output.confidence} " +
            "engine=${emotionEngine.engineName}")

        return output
    }

    /**
     * 直前のリアクションに対するユーザーの反応を評価し、学習する。
     * react()の後に呼び出す。
     */
    suspend fun learnFromLastReaction() {
        val output = lastOutput ?: run {
            Log.w(TAG, "No previous reaction to learn from")
            return
        }
        orchestrator.learnFromReaction(output)
    }

    /**
     * リアクション → 学習を一括で実行する便利メソッド。
     *
     * @param evaluationDelayMs リアクション後、評価開始までの待機時間
     */
    suspend fun reactAndLearn(evaluationDelayMs: Long = 1000L): EmotionOutput {
        val output = react()

        // バックグラウンドで学習（推論結果は即座に返す）
        scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(evaluationDelayMs)
            orchestrator.learnFromReaction(output)
        }

        return output
    }

    /** 現在のエンジン名を取得 */
    fun currentEngineName(): String = emotionEngine.engineName

    /** エンジンが準備完了か */
    fun isEngineReady(): Boolean = emotionEngine.isReady()
}

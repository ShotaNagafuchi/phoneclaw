# PhoneClaw

PhoneClawは、オンデバイスでワークフローを実行し、アプリに組み込まれたJavaScriptベースのスクリプト言語**ClawScript**を使用して、実行時に自動化ロジックを生成できるAndroid自動化アプリです。

PhoneClawはClaude Bot/Claude Codeにインスパイアされ、すべてのアプリにアクセスできる個人アシスタントとして、Androidフォン用のエージェントループをネイティブに再構築しようとしています。

## デモ

### TikTokへの動画アップロード自動化（音楽付き）

[![Automating Uploading Videos To Tiktok With Songs:](https://img.youtube.com/vi/TRqPFSixaog/0.jpg)](https://www.youtube.com/watch?v=TRqPFSixaog)

### メールから2FAを使用したInstagramアカウント作成の自動化

[![Automating Creating Instagram Accounts](https://img.youtube.com/vi/9zR43vLYCMs/0.jpg)](https://www.youtube.com/watch?v=9zR43vLYCMs)

### CAPTCHAの自動化

[![Automating Captchas:](https://img.youtube.com/vi/aBgbr27fR5M/0.jpg)](https://www.youtube.com/watch?v=aBgbr27fR5M)

## できること

- Accessibilityサービスを使用してAndroid上でマルチステップのアプリワークフローを自動化
- 実行時にスクリプトを生成して、柔軟で適応的な自動化を実現
- ビジョン支援UIターゲティングを使用して、ハードコードされた座標なしでコントロールをクリック
- 画面上のテキストや値を読み取って、分岐、検証、ハンドオフに使用
- cronライクなタイミングで自動化をスケジュールして、繰り返しタスクを実行
- 単一のフロー内でアプリ間（ブラウザ、メール、メディア、メッセージング）でアクションをチェーン
- 異なるデバイスサイズ、レイアウト、言語設定に適応するフローを構築

## セットアップ手順

1. 最も安価なフォンは、米国のWalmartで購入できる$30のMoto G playです。これはデモで使用されているフォンです。
2. Androidで開発者モードを有効にします。デバイスをルート化する必要はありません。
3. Android Studioをダウンロードし、このリポジトリをダウンロードして開き、Build > Generate Bundles or APKs > Generate APKsをクリックします。
4. APKをダウンロードまたはAndroidに転送して、インストールをクリックしてサイドロードします。権限を求められたら許可をクリックします。
5. アプリが開いたら、音声コマンドを使用して「open twitter and click the blue post button every hour」のような簡単な自動化を生成します。
6. エージェントを実行し、スケジュールを設定し、以下のような簡単な言語で編集できるファイルを出力します。

## ClawScript

ClawScriptは、組み込みのJSエンジンを使用してPhoneClaw内で実行され、自動化、スケジューリング、画面理解のためのヘルパー関数を公開します。高速な反復を目的として設計されています：実行時に小さなスクリプトを記述または生成し、すぐに実行し、UIフィードバックに基づいて調整します。

### ClawScript API（コアヘルパー）

- `speakText(text)` — オンデバイスTTSを使用してテキストを読み上げ、状態を確認したり進行状況を提供します。
- `delay(ms)` — 指定されたミリ秒数の間、実行を一時停止します。
- `schedule(task, cronExpression)` — cronライクなスケジュールで実行するタスク文字列を登録します。
- `clearSchedule()` — すべてのスケジュールされたタスクを削除します。
- `magicClicker(description)` — 自然言語の説明でUI要素を見つけてタップします。
- `magicScraper(description)` — 画面上に見えるものについての特定の質問に答えます。
- `sendAgentEmail(to, subject, message)` — 通知やハンドオフのためにデバイスからメールを送信します。
- `safeInt(value, defaultVal)` — フォールバック付きで値を整数に安全にパースします。

### magicClicker

- スクリーンショットとビジョンを使用して、平易な言語で説明されたターゲットを見つけます。
- Accessibilityサービスを通じて最適に一致するUI要素をタップします。
- デバイス間でUIレイアウトがシフトする可能性がある繰り返し可能なフローに最適です。

### magicScraper

- スクリーンショットとビジョンを使用して、画面上に見えるものについての特定の質問に答えます。
- スクリプトでパースしたり分岐したりできる簡潔な文字列を返します。
- OTPコード、ステータスラベル、フィールド値などのテキストを読み取るのに最適です。

### スクリプト例

```js
magicClicker("Create account")
delay(1500)
magicClicker("Email address field")
// ... 独自の入力ヘルパーでテキストを入力
magicClicker("Next")
const otp = magicScraper("The 2FA code shown in the SMS notification")
// ... otpを送信
```

## OpenClaw Gateway経由でのNode.js使用

PhoneClawは、OpenClaw Gatewayに接続して、Node.jsやその他のクライアントからWebSocket経由でデバイス自動化を制御できます。これにより、PCやサーバーからAndroidデバイスをリモートで操作できます。

### OpenClaw Gatewayとは

OpenClaw Gatewayは、PhoneClawをNodeとして接続し、AI駆動のデバイス自動化を可能にするコントロールプレーンです。WebSocketプロトコルを使用して、リモートからデバイスを制御できます。

詳細なプロトコル仕様: https://docs.openclaw.ai/gateway/protocol

### PhoneClawアプリでの接続方法

1. PhoneClawアプリを開きます
2. 「Connect to Gateway」ボタンをタップします
3. Gatewayのホスト名とポートを入力します（例: `localhost:8080` または `gateway.example.com:443`）
4. 必要に応じて認証トークンを入力します
5. 接続が確立されると、「OpenClaw Gateway connected」というステータスが表示されます

### Node.jsからの使用方法

Node.jsからOpenClaw Gateway経由でPhoneClawを制御するには、WebSocketクライアントを使用してGatewayに接続し、`node.invoke`メソッドを使用してコマンドを実行します。

#### 基本的な使用例

```javascript
const WebSocket = require('ws');

// Gatewayに接続
const ws = new WebSocket('ws://localhost:8080');

ws.on('open', () => {
  // Gatewayに接続リクエストを送信
  ws.send(JSON.stringify({
    type: 'req',
    id: 'connect-1',
    method: 'connect',
    params: {
      minProtocol: 3,
      maxProtocol: 3,
      client: {
        id: 'nodejs-client',
        version: '1.0.0',
        platform: 'nodejs',
        mode: 'operator'
      },
      role: 'operator',
      scopes: ['operator.read', 'operator.write'],
      // 必要に応じて認証トークンを追加
      auth: { token: 'YOUR_TOKEN_HERE' }
    }
  }));
});

ws.on('message', (data) => {
  const message = JSON.parse(data);
  
  if (message.type === 'res' && message.ok) {
    console.log('Connected to Gateway');
    
    // PhoneClawノードに対してコマンドを実行
    invokeCommand('android.tap', { x: 500, y: 1000 });
  }
});

function invokeCommand(command, params) {
  ws.send(JSON.stringify({
    type: 'req',
    id: `invoke-${Date.now()}`,
    method: 'node.invoke',
    params: {
      nodeId: 'phoneclaw-XXXX', // PhoneClawデバイスのID
      command: command,
      params: params
    }
  }));
}
```

### 利用可能なコマンド

PhoneClawは以下のコマンドをサポートしています：

#### 基本操作

- **`android.tap`** - 画面の指定座標をタップ
  ```javascript
  { x: 500, y: 1000 }
  ```

- **`android.swipe`** - スワイプジェスチャーを実行
  ```javascript
  { startX: 500, startY: 1000, endX: 500, endY: 500 }
  ```

- **`android.type`** - テキストを入力
  ```javascript
  { text: "Hello, World!" }
  ```

#### ビジョンベース操作

- **`android.magicClick`** - 自然言語の説明でUI要素をクリック
  ```javascript
  { description: "Create account button" }
  ```

- **`android.magicScraper`** - 画面上の情報を読み取る
  ```javascript
  { question: "What is the OTP code shown on screen?" }
  ```

- **`android.getScreenText`** - 画面上のすべてのテキストを取得
  ```javascript
  {}
  ```

#### 連絡先とカレンダー

- **`android.contacts.list`** - 連絡先リストを取得
  ```javascript
  { limit: 100 }
  ```

- **`android.contacts.update`** - 連絡先を更新
  ```javascript
  { contactId: "contact-123", displayName: "New Name" }
  ```

- **`android.calendar.list`** - カレンダーイベントを取得
  ```javascript
  { startMillis: 1234567890, endMillis: 1234567890, limit: 50 }
  ```

- **`android.calendar.createEvent`** - カレンダーイベントを作成
  ```javascript
  { 
    title: "Meeting", 
    dtStart: 1234567890, 
    dtEnd: 1234567890,
    description: "Optional description"
  }
  ```

#### 通知

- **`android.notifications.list`** - 最近の通知を取得
  ```javascript
  { limit: 50 }
  ```

### 完全なNode.js使用例

```javascript
const WebSocket = require('ws');

class PhoneClawClient {
  constructor(gatewayUrl, token = null) {
    this.gatewayUrl = gatewayUrl;
    this.token = token;
    this.ws = null;
    this.nodeId = null;
  }

  async connect() {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.gatewayUrl);
      
      this.ws.on('open', () => {
        this.ws.send(JSON.stringify({
          type: 'req',
          id: 'connect-1',
          method: 'connect',
          params: {
            minProtocol: 3,
            maxProtocol: 3,
            client: {
              id: 'nodejs-client',
              version: '1.0.0',
              platform: 'nodejs',
              mode: 'operator'
            },
            role: 'operator',
            scopes: ['operator.read', 'operator.write'],
            auth: this.token ? { token: this.token } : undefined
          }
        }));
      });

      this.ws.on('message', (data) => {
        const message = JSON.parse(data);
        this.handleMessage(message, resolve, reject);
      });

      this.ws.on('error', reject);
    });
  }

  handleMessage(message, resolve, reject) {
    if (message.type === 'res' && message.ok) {
      if (message.payload?.type === 'hello-ok') {
        console.log('Connected to Gateway');
        resolve();
      }
    } else if (message.type === 'event') {
      // ノードの接続イベントなどを処理
      if (message.event === 'node.connected') {
        this.nodeId = message.payload?.nodeId;
      }
    }
  }

  async invoke(command, params) {
    return new Promise((resolve, reject) => {
      const id = `invoke-${Date.now()}`;
      
      const handler = (data) => {
        const message = JSON.parse(data);
        if (message.id === id) {
          this.ws.removeListener('message', handler);
          if (message.ok) {
            resolve(message.payload);
          } else {
            reject(new Error(message.error));
          }
        }
      };

      this.ws.on('message', handler);

      this.ws.send(JSON.stringify({
        type: 'req',
        id: id,
        method: 'node.invoke',
        params: {
          nodeId: this.nodeId,
          command: command,
          params: params
        }
      }));
    });
  }

  async tap(x, y) {
    return this.invoke('android.tap', { x, y });
  }

  async magicClick(description) {
    return this.invoke('android.magicClick', { description });
  }

  async magicScrape(question) {
    return this.invoke('android.magicScraper', { question });
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
    }
  }
}

// 使用例
async function main() {
  const client = new PhoneClawClient('ws://localhost:8080');
  
  try {
    await client.connect();
    console.log('Connected!');
    
    // 画面をタップ
    await client.tap(500, 1000);
    
    // ビジョンを使用してボタンをクリック
    await client.magicClick('Create account button');
    
    // 画面上の情報を読み取る
    const otp = await client.magicScrape('What is the OTP code?');
    console.log('OTP:', otp);
    
  } catch (error) {
    console.error('Error:', error);
  } finally {
    client.disconnect();
  }
}

main();
```

## 国際化（i18n）

表示言語は**端末のシステム言語**に従い、日本語・英語を切り替えます。

- **英語（デフォルト）**: `res/values/strings.xml`
- **日本語**: `res/values-ja/strings.xml`

### 新しい言語を追加する

1. `res/values-<言語コード>/strings.xml` を追加する（例: 韓国語なら `values-ko`、中国語なら `values-zh`）。
2. `values/strings.xml` にあるキーを同じ名前でコピーし、各文字列を翻訳する。
3. ビルドすると、その言語がシステム言語のときに自動的に使われます。

例: 韓国語を追加する場合

```
app/src/main/res/values-ko/strings.xml
```

## セットアップ

### Moondream認証トークンの設定

Moondream認証トークンをGradleプロパティで提供します（gitから除外されます）：

```properties
# local.properties (プロジェクトルート) または ~/.gradle/gradle.properties
MOONDREAM_AUTH=YOUR_TOKEN_HERE
```

## Edge AI 3層アーキテクチャ

PhoneClawには、オンデバイスで動作する**疎結合・モデル差し替え可能・パラメータ成長型**のAIアーキテクチャが組み込まれています。AIはユーザーとのやり取りを通じてパラメータを更新し、「その人専用の相棒」へと成長します。

### アーキテクチャ概要

```
┌─────────────────────────────────────────┐
│         推定層 (Inference Layer)          │
│  IEmotionEngine インターフェース          │
│  ├─ RuleBasedEngine (v1, LLMなし)        │
│  ├─ AICoreEngine (Gemini Nano) [将来]    │
│  └─ MediaPipeEngine (Gemma 2B) [将来]    │
│  → モデル差し替え: swapEngine() 1行      │
├─────────────────────────────────────────┤
│         学習層 (Learning Layer)           │
│  Thompson Sampling (Contextual Bandit)   │
│  ├─ FaceRewardEvaluator (MediaPipe)      │
│  ├─ LearningOrchestrator                │
│  └─ MemoryConsolidationWorker (夜間統合)  │
├─────────────────────────────────────────┤
│         データ層 (Data Layer)             │
│  Room DB (edge_ai.db)                    │
│  ├─ UserProfile (人格パラメータ α/β)     │
│  ├─ InteractionLog (短期記憶)            │
│  ├─ ContextSnapshot (80次元文脈ベクトル)  │
│  └─ AIDiaryEntry (AI日記)                │
└─────────────────────────────────────────┘
```

### AI日記

AIは夜間充電中に「記憶の統合」を行い、その日のやりとりを**AI視点の日記**として記録します。ユーザーはこの日記を見ることで、AIが何をどう見ているのか・何を学んだのかを確認できます。

日記の例:
```
今日は12回やりとりしました。
あなたが一番喜んでくれたのは「ユーモア」(7回中71%で好反応)でした。
「心配」は空振りが多かったので、少し控えめにしようと思います。

--- 性格の変化 ---
ユーモア ■■■■■■□□□□ 0.42 → 0.58 (↑)
共感　　 ■■■■■□□□□□ 0.50 → 0.52 (↗)
心配　　 ■■■□□□□□□□ 0.45 → 0.35 (↓)

今日はたくさん笑ってくれて嬉しかったです。明日もよろしくね。
```

### 動作の流れ

1. **アプリ起動** → BuddyServiceが `EdgeAIManager.init()` を呼び出し、3層を初期化
2. **日中のやりとり** → ユーザーの操作に応じて推定層が感情リアクションを推論
   - Thompson Samplingが過去の学習パラメータからアクション（共感/ユーモア等）を選択
   - MediaPipe FaceMeshがユーザーの表情（笑顔/しかめ面等）を検知して報酬スコア算出
   - スコアに応じてα/βパラメータを即時更新＋InteractionLogに記録
3. **夜間（充電中）** → MemoryConsolidationWorkerが自動実行
   - 日中の短期記憶（ログ）を集約してプロファイルのα/βをバッチ更新
   - AI日記を生成・保存
   - 生のログを削除（ストレージ節約）
4. **翌日** → 更新されたパラメータに基づいて、よりユーザーに合ったリアクションを生成

### 学習アルゴリズム: Thompson Sampling

- 各感情タイプ（共感/ユーモア/驚き/落ち着き/興奮/心配/励まし/好奇心）に対してBeta分布 Beta(α,β) を維持
- ユーザーが好反応 → α増加（そのアクションが選ばれやすくなる）
- ユーザーが無反応/悪反応 → β増加（そのアクションが選ばれにくくなる）
- 探索と活用が自然にバランスされる（新しいアクションも確率的に試す）

### ワークツリー手順（開発者向け）

#### 1. リポジトリのクローンとブランチ切り替え

```bash
git clone https://github.com/ShotaNagafuchi/phoneclaw.git
cd phoneclaw
git checkout claude/edge-ai-android-architecture-klvmI
```

#### 2. Android Studioで開く

1. Android Studioを起動
2. 「Open」→ `phoneclaw/` フォルダを選択
3. Gradle Syncが完了するまで待つ（Room, WorkManager, MediaPipe等の依存が自動DLされる）

#### 3. ビルド＆実行

```bash
# コマンドラインの場合
./gradlew assembleDebug

# APKの場所
app/build/outputs/apk/debug/app-debug.apk
```

> **versionCode自動インクリメント**: `assembleDebug` / `assembleRelease` を実行するたびに `app/version.properties` 内の `VERSION_CODE` が自動的に+1されます。初回ビルド時にファイルが存在しない場合はversionCode=1で開始します。`version.properties` はローカル専用（`.gitignore`済み）なので、各開発者の環境で独立して管理されます。

#### 4. 端末へのインストール

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> `-r` フラグは既存アプリの上書きインストールを許可します。versionCodeは自動インクリメントされるため、ダウングレードエラーは発生しません。

### Android上での起動手順

1. PhoneClawアプリを開く
2. アクセシビリティサービスの権限を許可（初回のみ）
3. 通知リスナーの権限を許可（初回のみ）
4. **BuddyServiceが自動起動** → 常駐通知「Buddy稼働中」が表示される
5. BuddyService起動時に `EdgeAIManager.init()` が呼ばれ、以下が自動実行:
   - Room DB (edge_ai.db) の初期化
   - MediaPipe FaceLandmarkerモデルのロード
   - MemoryConsolidationWorkerのスケジュール登録（6時間周期、充電+WiFi時のみ）
6. 以降、AIのリアクションは自動的にバックグラウンドで学習が走る

### Edge AIパッケージ構成

```
com.example.universal.edge/
├── EdgeAIManager.kt              ← 統合マネージャー（外部はここだけ使う）
├── data/
│   ├── ContextRepository.kt      ← データ層契約 + 実装
│   ├── EdgeDatabase.kt           ← Room DB定義
│   ├── converter/FloatArrayConverter.kt
│   ├── dao/
│   │   ├── UserProfileDao.kt
│   │   ├── InteractionLogDao.kt
│   │   └── AIDiaryDao.kt
│   └── entity/
│       ├── ContextSnapshot.kt    ← 80次元文脈ベクトル
│       ├── UserProfile.kt        ← Beta分布α/β (人格パラメータ)
│       ├── InteractionLog.kt     ← 短期記憶ログ (~500B/record)
│       └── AIDiaryEntry.kt       ← AI日記
├── inference/
│   ├── IEmotionEngine.kt         ← モデル差し替えインターフェース
│   ├── EmotionOutput.kt          ← EmotionType(8種) + Action
│   ├── OutputParser.kt           ← LLM出力安全弁パーサー
│   └── RuleBasedEmotionEngine.kt ← v1実装 (Bandit + ルール)
└── learning/
    ├── IRewardEvaluator.kt       ← 報酬評価インターフェース
    ├── FaceRewardEvaluator.kt    ← MediaPipe FaceMesh実装
    ├── ThompsonSamplingBandit.kt ← Beta分布サンプリング
    ├── LearningOrchestrator.kt   ← リアルタイム学習サイクル
    ├── MemoryConsolidationWorker.kt ← 夜間パラメータ統合 + AI日記生成
    └── DiaryWriter.kt            ← AI日記テキスト生成
```

### 推定エンジンの差し替え方法（将来）

```kotlin
// Gemini Nano に切り替え
val aiCoreEngine = AICoreEmotionEngine(context)
EdgeAIManager.instance?.swapEngine(aiCoreEngine)

// Gemma 2B に切り替え
val mediaLLMEngine = MediaPipeEmotionEngine(context, "gemma-2b.bin")
EdgeAIManager.instance?.swapEngine(mediaLLMEngine)
```

`IEmotionEngine` インターフェースを実装するだけで、新しいモデルを追加可能です。

## 将来の開発計画

PhoneClawは、より高度な個人アシスタント機能を目指して継続的に開発されています。以下の機能を今後実装予定です：

### インテリジェントなニュース通知

- **最近のニュース通知**: 定期的に最新のニュースを収集し、重要な情報を通知
- **パーソナライズされたニュースフィルタリング**: ユーザーの興味や過去の行動履歴に基づいて、「見るべきニュース」を自動的に選別して通知
- ニュースアプリやRSSフィードから情報を取得し、ClawScriptで自動的に処理

### 行動パターン認識と介入

- **漫画の読みすぎ検知**: アプリの使用状況を監視し、漫画アプリの使用時間が長すぎる場合に「漫画の読みすぎですよ」と音声で注意喚起
- **習慣の追跡**: 各アプリの使用時間を記録し、ユーザーの行動パターンを学習
- **カスタマイズ可能なルール**: ユーザーが設定したルールに基づいて、自動的に介入や通知を実行

### カメラを使った対話型見守り機能

- **筋トレの見守り**: カメラを起動してユーザーの筋トレ動作を監視
- **音声フィードバック**: 「もう少し縦にしてください」などの指示を音声で提供し、フォームを改善
- **リアルタイム動作解析**: カメラ映像をリアルタイムで解析し、適切なフィードバックを提供
- AndroidのカメラAPIを活用し、ビジョンモデルと組み合わせて外界の情報を取得

### スマホ自立移動型ロボット連携

- **Type-C経由の制御**: AndroidデバイスのType-Cポート経由で外部デバイスを制御
- **手足がつくタイプの付属型デバイス**: スマートフォンに接続できる移動型ロボットデバイスのサポート
- **自律移動機能**: ロボットが自律的に移動し、カメラやセンサーを使って環境を認識
- **アプリ経由の制御**: PhoneClawアプリからロボットの動作を制御し、ClawScriptで自動化フローを構築

これらの機能により、PhoneClawは単なる自動化ツールから、ユーザーの日常生活をサポートする真の個人アシスタントへと進化します。

## ライセンス

このプロジェクトのライセンス情報については、リポジトリを確認してください。

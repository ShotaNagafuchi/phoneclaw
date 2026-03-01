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

## 手順まとめ（ビルド・インストール・確認）

**前提**: Android で開発者モードを有効にし、USB で PC と接続。ルート化は不要。音声コマンドや magicClicker を使う場合は API キーが必要（下記「APIキー」参照）。

| やりたいこと | コマンド |
|-------------|----------|
| バージョン確認 | `./gradlew showVersion` |
| デバッグ APK をビルド | `./gradlew assembleDebug` |
| バージョンを上げてからビルド | `./gradlew bumpVersion assembleDebug` |
| **APK を端末にインストール** | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| ログで動作確認 | `./logcat-app.sh`（Git Bash/WSL/macOS）または `.\logcat-app.ps1`（PowerShell） |

- **インストール**: 上記 `adb install -r` で既存インストールを上書き。別パスでビルドした場合はその `app-debug.apk` を指定する。
- **ビルドの流れ**: 1) 必要なら `local.properties` に API キーを書く → 2) `./gradlew assembleDebug` → 3) `adb install -r ...` → 4) 端末でアプリを起動して確認。
- バージョンは `version.properties` で管理。`bumpVersion` で versionCode と versionName（パッチ）を 1 ずつ増やせる。

### APIキー（音声・ビジョン用）

音声で「〇〇して」とコード生成するには **OpenRouter**、画面上の要素をタップする magicClicker には **Moondream** のキーが必要です。**ビルド時に埋め込むため、キーを設定したら必ず再ビルドしてください。**

- **設定場所（どれか1つ）**: プロジェクト直下の `local.properties`（推奨・git に含めない）、または `~/.gradle/gradle.properties`（Windows は `%USERPROFILE%\.gradle\gradle.properties`）
- **OPENROUTER_API_KEY**: [OpenRouter](https://openrouter.ai/) で発行。未設定だと音声コマンドで「AI service is unavailable」になります。
- **MOONDREAM_AUTH**: magicClicker 用。未設定ならその機能だけ使えません。

```properties
OPENROUTER_API_KEY=sk-or-v1-xxxxxxxxxxxx
MOONDREAM_AUTH=your_moondream_token_here
```

手順: 1) 上記を `local.properties` に追記・編集 → 2) `./gradlew clean assembleDebug` → 3) `adb install -r app/build/outputs/apk/debug/app-debug.apk`。`local.properties` は通常 `.gitignore` に入っているためリポジトリにはコミットされません。

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

### 使い方のイメージ

- **magicClicker**: 「〇〇ボタン」「次の画面へ」など自然言語で指定すると、画面上の該当要素を探してタップします。レイアウトが変わりやすいフロー向け。
- **magicScraper**: 「画面に表示されているOTPコードは？」など質問すると、短文で返します。分岐・検証に利用。
- 音声で「〇〇して」と話すと OpenRouter が ClawScript を生成し、その場で実行されます。生成されたコードは履歴に残り、あとから編集・再実行も可能です。

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

## ログの見方

USBで端末を接続した状態で、次のスクリプトを実行するとアプリのログだけが流れます。言語やTTSの挙動を確認するときなどに便利です。

```bash
./logcat-app.sh          # Git Bash / WSL / macOS
.\logcat-app.ps1         # PowerShell
```

**操作ログ（タップ・画面遷移）だけ見る**  
アクセシビリティで取得している「どこをタップしたか」「どの画面に変わったか」はタグ `A11yUX` で出ます。

```bash
adb logcat -s A11yUX
```

**今後の方針（画面キャプチャ・データ）**  
起動のたびに「画面を共有するか／録画するか」を聞かないように、画面キャプチャは**設定ダッシュボードの「有効にする」で必要時のみ**許可を取得する形にしています。将来的には、常時録画ではなく**毎日のログと必要データだけをローカルに保存**し、スケジュールやヘルスケアなどは**スケジューラアプリ・ヘルスケアアプリから取得**する構成を想定しています（現状は未実装）。

- **TTSの言語**: 起動時に「TTS locale: …」と「Speaking (locale=…): …」が出力されます。`locale=ja` かつ読み上げ文言が日本語なら、TTSエンジンも端末のロケールに合わせて日本語で話します。
- **英語のまま話す場合**: 設定 → 言語で「日本語」が先頭か、端末の言語が日本語になっているか確認してください。アプリは**システムのロケール**で `getString()` と TTS の言語を決めています。

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

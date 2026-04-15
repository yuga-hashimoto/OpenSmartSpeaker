# open-smart-speaker

**あなたの古いタブレットを、プライベートなスマートスピーカーに変える。**

Google Home や Alexa のように声で家電を操作できる Android アプリ。ただし、クラウドにデータを送らない。AI も音声認識も、全てタブレットの中で動く。

## 何ができるか

**「リビングの電気つけて」** と話しかけるだけで、照明がつく。

- 声で家電を操作（照明ON/OFF、エアコン温度変更、カーテン開閉）
- 質問に答える（天気、時間、一般知識）
- 壁掛けタブレットとして常時表示（時計、天気、デバイス状態）
- ウェイクワード「Hey Speaker」で起動、ハンズフリー操作

## なぜこれが必要か

| 既存のスマートスピーカー | open-smart-speaker |
|---|---|
| 音声データがクラウドに送信される | **全てデバイス上で処理。外部通信なし** |
| 月額料金やサブスクが必要な機能がある | **完全無料、オープンソース** |
| 特定メーカーのデバイスしか操作できない | **SwitchBot、Matter、MQTT 対応機器を横断操作** |
| インターネットが切れると使えない | **オフラインでも動作** |
| カスタマイズできない | **AI モデルもウェイクワードも変更可能** |

## 対応デバイス

| プロトコル | 対応機器の例 |
|-----------|------------|
| **SwitchBot** | ボット、カーテン、温湿度計、プラグ、照明 |
| **Matter** | Apple/Google/Amazon 共通規格の新世代デバイス |
| **MQTT** | Shelly、Tasmota、その他 DIY スマートホーム機器 |
| **Home Assistant** | HA サーバーを持っている人は全デバイス連携可能（任意） |

## 画面モード

| モード | 用途 |
|-------|------|
| **Chat** | AI と会話。テキストでも音声でも |
| **Dashboard** | 家中のデバイスをカード表示。タップで ON/OFF |
| **Ambient** | 壁掛け時計。天気・温度・湿度を常時表示 |

## セットアップ

### 必要なもの
- Android タブレット（Android 9以上、RAM 8GB 推奨）
- 操作したいスマートホームデバイス（SwitchBot 等）

### 手順
1. APK をインストール
2. マイクの権限を許可
3. Settings で接続するデバイスの設定を入力
   - SwitchBot: アプリから取得した Token と Secret
   - MQTT: ブローカーの URL
4. AI モデルファイル（Gemma 2B `.task`）をアプリに配置
5. 「Hey Speaker」と話しかけるか、マイクボタンをタップ

### ビルド（開発者向け）
```bash
./gradlew assembleDebug  # ビルド
./gradlew test           # テスト
```

## 技術スタック

| 項目 | 技術 |
|------|------|
| 言語 | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 |
| AI推論 | MediaPipe LLM Inference（オンデバイス） |
| 音声 | Vosk（ウェイクワード）、Android STT/TTS |
| デバイス制御 | Matter、SwitchBot API、MQTT |
| セキュリティ | AES256-GCM 暗号化（トークン保存） |
| Min SDK | 28 (Android 9) |

## アーキテクチャ

```
open-smart-speaker
├── 音声パイプライン
│   ├── ウェイクワード検出 (Vosk)
│   ├── 音声→テキスト (Android STT)
│   ├── AI 推論 (MediaPipe / OpenClaw / 外部LLM)
│   ├── デバイス操作 (DeviceToolExecutor)
│   └── テキスト→音声 (Android TTS)
├── デバイス制御
│   ├── SwitchBot (REST API + BLE)
│   ├── Matter (Android Matter API)
│   ├── MQTT (Paho クライアント)
│   └── Home Assistant (任意)
├── UI
│   ├── Chat / Dashboard / Ambient / Settings
│   └── タブレット最適化 + 常時画面ON
└── データ
    ├── Room DB (会話履歴)
    ├── 暗号化設定 (トークン)
    └── mDNS 自動検出
```

## ライセンス

MIT

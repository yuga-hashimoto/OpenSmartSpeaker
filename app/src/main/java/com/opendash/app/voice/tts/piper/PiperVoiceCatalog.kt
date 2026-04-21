package com.opendash.app.voice.tts.piper

/**
 * Curated list of piper voices the tablet ships with choices for.
 *
 * Piper voices are paired ONNX + JSON files hosted at
 * `huggingface.co/rhasspy/piper-voices`. Voice quality / size is marked
 * in the URL segment (low / medium / high). Medium-quality voices are
 * the right default — high quality is ~60 MB per voice which adds up
 * for a household that wants multilingual support.
 */
data class PiperVoice(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val modelUrl: String,
    val configUrl: String,
    val modelFilename: String,
    val configFilename: String,
    val modelSizeMb: Int
)

object PiperVoiceCatalog {

    private const val BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/main"

    val EN_US_AMY_MEDIUM = PiperVoice(
        id = "en_US-amy-medium",
        displayName = "English (US) — Amy (medium)",
        languageTag = "en-US",
        modelUrl = "$BASE/en/en_US/amy/medium/en_US-amy-medium.onnx",
        configUrl = "$BASE/en/en_US/amy/medium/en_US-amy-medium.onnx.json",
        modelFilename = "en_US-amy-medium.onnx",
        configFilename = "en_US-amy-medium.onnx.json",
        modelSizeMb = 60
    )

    val EN_US_LESSAC_MEDIUM = PiperVoice(
        id = "en_US-lessac-medium",
        displayName = "English (US) — Lessac (medium)",
        languageTag = "en-US",
        modelUrl = "$BASE/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
        configUrl = "$BASE/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json",
        modelFilename = "en_US-lessac-medium.onnx",
        configFilename = "en_US-lessac-medium.onnx.json",
        modelSizeMb = 63
    )

    val JA_JP_TAKUMI_MEDIUM = PiperVoice(
        id = "ja_JP-takumi-medium",
        displayName = "日本語 — 匠 (medium)",
        languageTag = "ja-JP",
        modelUrl = "$BASE/ja/ja_JP/takumi/medium/ja_JP-takumi-medium.onnx",
        configUrl = "$BASE/ja/ja_JP/takumi/medium/ja_JP-takumi-medium.onnx.json",
        modelFilename = "ja_JP-takumi-medium.onnx",
        configFilename = "ja_JP-takumi-medium.onnx.json",
        modelSizeMb = 63
    )

    val all: List<PiperVoice> = listOf(
        EN_US_AMY_MEDIUM,
        EN_US_LESSAC_MEDIUM,
        JA_JP_TAKUMI_MEDIUM
    )

    val default: PiperVoice = EN_US_AMY_MEDIUM
}

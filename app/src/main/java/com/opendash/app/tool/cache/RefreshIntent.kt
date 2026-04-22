package com.opendash.app.tool.cache

/**
 * Detects when the user is explicitly asking for up-to-the-minute
 * data — e.g. "what's the *latest* weather", "now", "refresh the
 * news", "最新の天気", "更新して". When this fires, the caller should
 * invalidate the tool-result cache before dispatching the LLM turn
 * so the read-only-tool memoisation doesn't serve stale data.
 *
 * Keywords are kept narrow on purpose: false positives would
 * silently defeat the latency win of the cache. Words like
 * "weather" alone don't qualify — only explicit freshness markers.
 */
object RefreshIntent {

    private val keywordsEn = listOf(
        "latest", "refresh", "reload", "update", "right now", "current",
    )

    // Japanese tokens commonly used to request a fresh fetch. These
    // are substring-matched (Japanese has no word boundaries), but
    // the set stays small enough to avoid incidental matches.
    private val keywordsJa = listOf(
        "最新", "今の", "更新", "再読み込み", "リロード",
    )

    private val enRegex: Regex by lazy {
        // Word-boundary match to prevent substring traps (e.g. "updatevehicle").
        val alternation = keywordsEn.joinToString("|") { Regex.escape(it) }
        Regex("\\b(?:$alternation)\\b", RegexOption.IGNORE_CASE)
    }

    fun matches(input: String): Boolean {
        if (input.isBlank()) return false
        if (enRegex.containsMatchIn(input)) return true
        return keywordsJa.any { it in input }
    }
}

package com.opensmarthome.speaker.ui.home

import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.tool.info.NewsItem
import com.opensmarthome.speaker.tool.info.NewsProvider
import com.opensmarthome.speaker.tool.info.WeatherInfo
import com.opensmarthome.speaker.tool.info.WeatherProvider
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds the Home dashboard with ambient, online-sourced briefing data
 * (current weather + latest headlines) so the landing screen shows
 * something useful on a fresh install — no Home Assistant, no sensors,
 * no wake word required. Alexa / Google Home ship this out of the box;
 * we close the gap with the same public data sources we already use
 * from the LLM tool layer.
 *
 * The interface layer keeps HomeViewModel unit-testable without
 * standing up fake WeatherProvider / NewsProvider in every test.
 */
interface OnlineBriefingSource {
    suspend fun currentWeather(): WeatherInfo?
    suspend fun latestHeadlines(limit: Int = 5): List<NewsItem>

    /**
     * No-op briefing. Used by tests that don't exercise the briefing
     * wiring, and as the safe fallback when network briefing is
     * disabled at some future point.
     */
    object Empty : OnlineBriefingSource {
        override suspend fun currentWeather(): WeatherInfo? = null
        override suspend fun latestHeadlines(limit: Int): List<NewsItem> = emptyList()
    }
}

/**
 * Default implementation: reuses the `WeatherProvider` / `NewsProvider`
 * already wired for LLM tool calls (Open-Meteo + RSS). All failures are
 * swallowed back to `null` / empty so the dashboard degrades to
 * "last-known + blank" rather than crashing.
 *
 * Location resolution order:
 *  1. `DEFAULT_LOCATION` preference (user-configured city).
 *  2. `null` — weather provider falls back to Tokyo per its own contract.
 *
 * News source is pinned to NHK for now because the default feed list
 * from [com.opensmarthome.speaker.tool.info.NewsToolExecutor.DEFAULT_FEEDS]
 * already ships it. A user-configurable feed ships alongside the news
 * tile whenever the Settings screen gains that knob.
 */
@Singleton
class DefaultOnlineBriefingSource @Inject constructor(
    private val weatherProvider: WeatherProvider,
    private val newsProvider: NewsProvider,
    private val preferences: AppPreferences,
) : OnlineBriefingSource {

    override suspend fun currentWeather(): WeatherInfo? {
        val location = runCatching {
            preferences.observe(PreferenceKeys.DEFAULT_LOCATION).first()
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        return runCatching { weatherProvider.getCurrent(location) }
            .onFailure { Timber.w(it, "OnlineBriefingSource: weather refresh failed") }
            .getOrNull()
    }

    override suspend fun latestHeadlines(limit: Int): List<NewsItem> {
        val safeLimit = limit.coerceIn(1, 10)
        return runCatching { newsProvider.getHeadlines(DEFAULT_FEED, safeLimit) }
            .onFailure { Timber.w(it, "OnlineBriefingSource: headlines refresh failed") }
            .getOrElse { emptyList() }
    }

    companion object {
        /**
         * Default feed. Chosen because NHK ships as one of the bundled
         * `NewsToolExecutor.DEFAULT_FEEDS` so users who have ever triggered
         * the news tool are already going through this endpoint.
         */
        const val DEFAULT_FEED: String = "https://www3.nhk.or.jp/rss/news/cat0.xml"
    }
}

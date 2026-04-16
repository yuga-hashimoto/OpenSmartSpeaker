package com.opensmarthome.speaker.voice.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks timing metrics for the voice pipeline so we can target the
 * <500ms wake-to-listening and <200ms fast-path-to-response budgets from
 * the roadmap priority 1 ("Alexa feel").
 *
 * Records per-event timestamps in memory; aggregates counters track the
 * number of each event type. Designed to be sampled by a Settings debug
 * screen and logged on state transitions.
 *
 * Data is local-only and capped to avoid unbounded memory.
 */
class LatencyRecorder(
    private val maxSamplesPerEvent: Int = 50,
    private val clock: () -> Long = { System.nanoTime() }
) {

    data class Summary(
        val event: String,
        val count: Int,
        val averageMs: Long,
        val p50Ms: Long,
        val p95Ms: Long,
        val maxMs: Long
    )

    enum class Span {
        WAKE_TO_LISTENING,
        STT_DURATION,
        FAST_PATH_TO_RESPONSE,
        LLM_ROUND_TRIP,
        TTS_PREPARATION,
        TOOL_EXECUTION
    }

    private data class OpenMark(val startNanos: Long, val key: String)

    private val open = ConcurrentHashMap<String, OpenMark>()
    private val samplesByEvent = ConcurrentHashMap<Span, ArrayDeque<Long>>()
    private val totalCount = AtomicLong(0)

    fun startSpan(span: Span, key: String = span.name) {
        open[key] = OpenMark(clock(), key)
    }

    fun endSpan(span: Span, key: String = span.name): Long? {
        val mark = open.remove(key) ?: return null
        val durationNs = clock() - mark.startNanos
        val durationMs = durationNs / 1_000_000L
        recordSample(span, durationMs)
        return durationMs
    }

    private fun recordSample(span: Span, durationMs: Long) {
        val deque = samplesByEvent.getOrPut(span) { ArrayDeque() }
        synchronized(deque) {
            deque.addLast(durationMs)
            while (deque.size > maxSamplesPerEvent) deque.removeFirst()
        }
        totalCount.incrementAndGet()
    }

    fun summarize(): List<Summary> = samplesByEvent.entries.mapNotNull { (span, deque) ->
        val snapshot = synchronized(deque) { deque.toList() }
        if (snapshot.isEmpty()) null
        else Summary(
            event = span.name,
            count = snapshot.size,
            averageMs = snapshot.average().toLong(),
            p50Ms = percentile(snapshot, 50),
            p95Ms = percentile(snapshot, 95),
            maxMs = snapshot.max()
        )
    }

    fun reset() {
        samplesByEvent.clear()
        open.clear()
        totalCount.set(0)
    }

    private fun percentile(samples: List<Long>, p: Int): Long {
        if (samples.isEmpty()) return 0
        val sorted = samples.sorted()
        val idx = (sorted.size * p / 100).coerceAtMost(sorted.size - 1)
        return sorted[idx]
    }
}

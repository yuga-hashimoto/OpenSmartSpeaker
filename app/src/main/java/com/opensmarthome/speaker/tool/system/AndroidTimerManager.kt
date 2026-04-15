package com.opensmarthome.speaker.tool.system

import android.content.Context
import android.media.RingtoneManager
import android.os.CountDownTimer
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Android implementation of TimerManager using CountDownTimer.
 * Plays a ringtone when a timer fires.
 */
class AndroidTimerManager(
    private val context: Context
) : TimerManager {

    private val activeTimers = ConcurrentHashMap<String, ActiveTimer>()

    private data class ActiveTimer(
        val id: String,
        val label: String,
        val totalSeconds: Int,
        val startTimeMs: Long,
        val countDownTimer: CountDownTimer
    )

    override suspend fun setTimer(seconds: Int, label: String): String {
        val id = "timer_${UUID.randomUUID().toString().take(8)}"

        val timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                // Timer ticking
            }

            override fun onFinish() {
                Timber.d("Timer $id ($label) fired!")
                activeTimers.remove(id)
                playAlarmSound()
            }
        }

        activeTimers[id] = ActiveTimer(
            id = id,
            label = label,
            totalSeconds = seconds,
            startTimeMs = System.currentTimeMillis(),
            countDownTimer = timer
        )

        timer.start()
        Timber.d("Timer set: $id for $seconds seconds ($label)")
        return id
    }

    override suspend fun cancelTimer(timerId: String): Boolean {
        val timer = activeTimers.remove(timerId) ?: return false
        timer.countDownTimer.cancel()
        Timber.d("Timer cancelled: $timerId")
        return true
    }

    override suspend fun getActiveTimers(): List<TimerInfo> {
        val now = System.currentTimeMillis()
        return activeTimers.values.map { timer ->
            val elapsed = ((now - timer.startTimeMs) / 1000).toInt()
            val remaining = (timer.totalSeconds - elapsed).coerceAtLeast(0)
            TimerInfo(
                id = timer.id,
                label = timer.label,
                remainingSeconds = remaining,
                totalSeconds = timer.totalSeconds
            )
        }
    }

    private fun playAlarmSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {
            Timber.e(e, "Failed to play alarm sound")
        }
    }
}

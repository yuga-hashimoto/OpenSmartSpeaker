package com.opensmarthome.speaker.tool.system

import android.content.Context
import android.media.AudioManager
import timber.log.Timber

/**
 * Android implementation of VolumeManager using AudioManager.
 */
class AndroidVolumeManager(
    private val context: Context
) : VolumeManager {

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override suspend fun setVolume(level: Int): Boolean {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (level * maxVolume / 100).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVolume,
                AudioManager.FLAG_SHOW_UI
            )
            Timber.d("Volume set to $level% ($targetVolume/$maxVolume)")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to set volume")
            false
        }
    }

    override suspend fun getVolume(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100 / max) else 0
    }

    override suspend fun mute(): Boolean {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                0
            )
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to mute")
            false
        }
    }

    override suspend fun unmute(): Boolean {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE,
                0
            )
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to unmute")
            false
        }
    }
}

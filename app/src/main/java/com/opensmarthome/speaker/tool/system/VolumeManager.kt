package com.opensmarthome.speaker.tool.system

/**
 * Controls device volume.
 * Implementation uses Android AudioManager.
 */
interface VolumeManager {
    suspend fun setVolume(level: Int): Boolean
    suspend fun getVolume(): Int
    suspend fun mute(): Boolean
    suspend fun unmute(): Boolean
}

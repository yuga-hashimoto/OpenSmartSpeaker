package com.opendash.app.di

import android.content.Context
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.voice.stt.AndroidSttProvider
import com.opendash.app.voice.stt.DelegatingSttProvider
import com.opendash.app.voice.stt.SpeechToText
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SpeechToText binding lives in its own module so instrumented tests can
 * swap the implementation via `@TestInstallIn(replaces = [SttModule::class])`
 * without rebuilding the rest of the voice graph (TTS, VoicePipeline,
 * LatencyRecorder).
 *
 * See `app/src/androidTest/.../FakeSttTestModule.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SttModule {

    @Provides
    @Singleton
    fun provideSpeechToText(
        @ApplicationContext context: Context,
        preferences: AppPreferences
    ): SpeechToText = DelegatingSttProvider(
        preferences = preferences,
        android = AndroidSttProvider(context)
    )
}

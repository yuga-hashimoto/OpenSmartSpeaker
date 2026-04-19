package com.opendash.app.di

import android.content.Context
import com.opendash.app.assistant.router.ConversationRouter
import com.opendash.app.data.db.MessageDao
import com.opendash.app.data.db.SessionDao
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.voice.fastpath.FastPathRouter
import com.opendash.app.voice.metrics.LatencyRecorder
import com.opendash.app.voice.pipeline.FastPathLlmPolisher
import com.opendash.app.voice.pipeline.VoicePipeline
import com.opendash.app.voice.stt.SpeechToText
import com.opendash.app.voice.tts.TextToSpeech
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    // provideSpeechToText moved to SttModule so @TestInstallIn can swap STT
    // independently of the rest of the voice graph.
    // provideTextToSpeech moved to TtsModule so @TestInstallIn can swap the
    // TTS implementation independently of the rest of the voice graph.

    @Provides
    @Singleton
    fun provideVoicePipeline(
        @ApplicationContext context: Context,
        stt: SpeechToText,
        tts: TextToSpeech,
        router: ConversationRouter,
        toolExecutor: ToolExecutor,
        moshi: Moshi,
        preferences: AppPreferences,
        sessionDao: SessionDao,
        messageDao: MessageDao,
        fastPathRouter: FastPathRouter,
        latencyRecorder: LatencyRecorder,
        fastPathLlmPolisher: FastPathLlmPolisher
    ): VoicePipeline = VoicePipeline(
        context = context,
        stt = stt,
        tts = tts,
        router = router,
        toolExecutor = toolExecutor,
        moshi = moshi,
        preferences = preferences,
        sessionDao = sessionDao,
        messageDao = messageDao,
        fastPathRouter = fastPathRouter,
        latencyRecorder = latencyRecorder,
        fastPathLlmPolisher = fastPathLlmPolisher
    )

    @Provides
    @Singleton
    fun provideLatencyRecorder(): LatencyRecorder = LatencyRecorder()

    @Provides
    @Singleton
    fun provideFastPathLlmPolisher(): FastPathLlmPolisher = FastPathLlmPolisher()
}

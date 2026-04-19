package com.opendash.app.e2e.fakes

import com.opendash.app.di.SttModule
import com.opendash.app.voice.stt.SpeechToText
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [SttModule] in instrumented tests so the production
 * [com.opendash.app.voice.stt.DelegatingSttProvider] (which would talk to
 * Android's `SpeechRecognizer`) is swapped for a programmatic
 * [FakeSpeechToText].
 *
 * Tests inject `FakeSpeechToText` directly via
 * `@Inject lateinit var fakeStt: FakeSpeechToText` — the binding below
 * registers the same singleton against both [SpeechToText] and the
 * concrete fake type.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [SttModule::class]
)
object FakeSttTestModule {

    @Provides
    @Singleton
    fun provideFakeStt(): FakeSpeechToText = FakeSpeechToText()

    @Provides
    @Singleton
    fun provideSpeechToText(fake: FakeSpeechToText): SpeechToText = fake
}

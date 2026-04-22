package com.opendash.app.tool.cache

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RefreshIntentTest {

    @Test
    fun `English refresh keywords trigger`() {
        assertThat(RefreshIntent.matches("what's the latest weather?")).isTrue()
        assertThat(RefreshIntent.matches("refresh the news")).isTrue()
        assertThat(RefreshIntent.matches("update the weather")).isTrue()
        assertThat(RefreshIntent.matches("weather right now")).isTrue()
        assertThat(RefreshIntent.matches("current weather")).isTrue()
    }

    @Test
    fun `Japanese refresh keywords trigger`() {
        assertThat(RefreshIntent.matches("最新の天気を教えて")).isTrue()
        assertThat(RefreshIntent.matches("今の天気")).isTrue()
        assertThat(RefreshIntent.matches("更新して")).isTrue()
        assertThat(RefreshIntent.matches("再読み込み")).isTrue()
    }

    @Test
    fun `neutral queries do not trigger`() {
        assertThat(RefreshIntent.matches("what's the weather")).isFalse()
        assertThat(RefreshIntent.matches("tell me a joke")).isFalse()
        assertThat(RefreshIntent.matches("set a timer for 5 minutes")).isFalse()
        assertThat(RefreshIntent.matches("天気を教えて")).isFalse()
    }

    @Test
    fun `empty and blank input do not trigger`() {
        assertThat(RefreshIntent.matches("")).isFalse()
        assertThat(RefreshIntent.matches("   ")).isFalse()
    }

    @Test
    fun `case insensitive`() {
        assertThat(RefreshIntent.matches("REFRESH THE NEWS")).isTrue()
        assertThat(RefreshIntent.matches("Latest Weather")).isTrue()
    }

    @Test
    fun `partial word boundaries do not false-match`() {
        // "update" should match but e.g. "updatevehicle" should not
        // (not a real word anyway but guards against sub-string traps).
        assertThat(RefreshIntent.matches("updatevehicle status")).isFalse()
    }
}

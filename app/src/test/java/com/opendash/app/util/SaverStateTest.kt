package com.opendash.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure data-class tests for [SaverState] — the precedence rules live here
 * rather than duplicated in each consumer, so the UI chip and VoiceService
 * gate always read the same story.
 */
class SaverStateTest {

    @Test
    fun `default state is inactive`() {
        val s = SaverState()
        assertThat(s.active).isFalse()
        assertThat(s.reason).isEqualTo(SaverReason.NONE)
    }

    @Test
    fun `battery low alone is inactive without preference`() {
        val s = SaverState(preferenceEnabled = false, batteryLow = true)
        assertThat(s.active).isFalse()
        assertThat(s.reason).isEqualTo(SaverReason.NONE)
    }

    @Test
    fun `thermal throttle alone is inactive without preference`() {
        val s = SaverState(preferenceEnabled = false, thermalThrottling = true)
        assertThat(s.active).isFalse()
        assertThat(s.reason).isEqualTo(SaverReason.NONE)
    }

    @Test
    fun `preference alone is inactive without battery or thermal trigger`() {
        val s = SaverState(preferenceEnabled = true)
        assertThat(s.active).isFalse()
        assertThat(s.reason).isEqualTo(SaverReason.NONE)
    }

    @Test
    fun `preference plus battery low is active with BATTERY_LOW reason`() {
        val s = SaverState(preferenceEnabled = true, batteryLow = true)
        assertThat(s.active).isTrue()
        assertThat(s.reason).isEqualTo(SaverReason.BATTERY_LOW)
    }

    @Test
    fun `preference plus thermal throttle is active with THERMAL_THROTTLE reason`() {
        val s = SaverState(preferenceEnabled = true, thermalThrottling = true)
        assertThat(s.active).isTrue()
        assertThat(s.reason).isEqualTo(SaverReason.THERMAL_THROTTLE)
    }

    @Test
    fun `battery wins over thermal when both fire`() {
        // Precedence chosen deliberately: "plug the device in" is a more
        // actionable message than "the device is hot", and low battery
        // often masquerades as thermal when the chassis throttles due to
        // voltage drop.
        val s = SaverState(
            preferenceEnabled = true,
            batteryLow = true,
            thermalThrottling = true
        )
        assertThat(s.active).isTrue()
        assertThat(s.reason).isEqualTo(SaverReason.BATTERY_LOW)
    }
}

package com.opendash.app.util

import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive "is the battery saver actively throttling work right now?" snapshot.
 *
 * P14.8: the gating logic used to live inline in
 * [com.opendash.app.service.VoiceService.maybeStartWakeWord] — battery saver
 * is enabled AND (battery is low OR thermal is throttling). That's fine for
 * the service, but the UI has no way to tell the user *why* wake-word just
 * silently stopped. This class folds the three inputs into a single
 * StateFlow so AmbientScreen / HomeScreen can render a "saver active"
 * chip whenever the same condition fires, keeping the UI truthful about
 * background behavior.
 *
 * Battery-low wins over thermal when both are true so the reason string
 * is stable and actionable ("plug in the device").
 */
@Singleton
class SaverStateProvider @Inject constructor(
    preferences: AppPreferences,
    batteryMonitor: BatteryMonitor,
    thermalMonitor: ThermalMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<SaverState> = combine(
        preferences.observe(PreferenceKeys.BATTERY_SAVER_ENABLED),
        batteryMonitor.status,
        thermalMonitor.status
    ) { enabledPref, battery, thermal ->
        val enabled = enabledPref == true
        SaverState(
            preferenceEnabled = enabled,
            batteryLow = battery.isLow,
            thermalThrottling = thermal.shouldThrottle
        )
    }.stateIn(scope, SharingStarted.Eagerly, SaverState())
}

/**
 * Outcome of folding the three saver gates. Consumers should use [active] /
 * [reason] and avoid reading the individual flags so the precedence (battery
 * beats thermal) stays centralised here.
 */
data class SaverState(
    val preferenceEnabled: Boolean = false,
    val batteryLow: Boolean = false,
    val thermalThrottling: Boolean = false
) {
    val active: Boolean get() = preferenceEnabled && (batteryLow || thermalThrottling)

    val reason: SaverReason
        get() = when {
            !active -> SaverReason.NONE
            batteryLow -> SaverReason.BATTERY_LOW
            else -> SaverReason.THERMAL_THROTTLE
        }
}

enum class SaverReason {
    NONE,
    BATTERY_LOW,
    THERMAL_THROTTLE
}

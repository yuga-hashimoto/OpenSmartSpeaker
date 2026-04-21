package com.opendash.app.tool.termux

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Runtime capability check for the P19.2 Termux Bridge.
 *
 * The `termux_shell_exec` tool is advertised to the LLM only when all three
 * gates are true:
 *
 *  1. The `com.termux` package is installed (PackageManager).
 *  2. The app holds `com.termux.permission.RUN_COMMAND` (runtime perm).
 *  3. The user has flipped `TERMUX_SHELL_EXECUTE_ENABLED=true` in Settings.
 *
 * This interface only covers (1) + (2). Preference gating (3) lives in the
 * caller (`TermuxBridgeToolExecutor`) because it needs the DataStore value,
 * not a PackageManager / Context snapshot. Implemented as an interface so
 * JVM unit tests can swap in a [FakeTermuxAvailability] without needing a
 * real Android Context.
 */
interface TermuxAvailability {

    fun isTermuxInstalled(): Boolean

    fun hasRunCommandPermission(): Boolean

    /**
     * `true` when the app can technically dispatch a `RUN_COMMAND` intent
     * right now. Does NOT consult the user's opt-in preference — the tool
     * executor combines this with the preference gate.
     */
    fun isAvailable(): Boolean = isTermuxInstalled() && hasRunCommandPermission()

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    }
}

/**
 * Production implementation: consults PackageManager + runtime permission.
 */
class ContextTermuxAvailability(private val context: Context) : TermuxAvailability {

    override fun isTermuxInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TermuxAvailability.TERMUX_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: SecurityException) {
        Timber.w(e, "PackageManager denied Termux visibility")
        false
    }

    override fun hasRunCommandPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            TermuxAvailability.RUN_COMMAND_PERMISSION
        )
        return granted == PackageManager.PERMISSION_GRANTED
    }
}

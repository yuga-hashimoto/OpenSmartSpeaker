package com.opendash.app.tool.termux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Real [TermuxBridge] built on the Termux `RUN_COMMAND` service intent
 * (https://github.com/termux/termux-app#run_command-intent).
 *
 * Flow:
 *
 *  1. Build a one-shot [PendingIntent] targeted at a private broadcast
 *     action unique to this call.
 *  2. Register a temporary [BroadcastReceiver] listening for that action.
 *  3. Fire the `com.termux.RUN_COMMAND` service intent with all the
 *     command extras + the result PendingIntent.
 *  4. `suspendCancellableCoroutine` waits for Termux to fire the result
 *     back via the PendingIntent. Timeout / cancellation unregisters the
 *     receiver so it can't leak past the suspension.
 *
 * **Not JVM-unit-testable** without Robolectric — the logic depends on
 * real `Context` dispatch. The executor tests use [FakeTermuxBridge] to
 * cover gating and result translation; this class is exercised end-to-end
 * only on-device.
 */
class IntentTermuxBridge(
    private val context: Context
) : TermuxBridge {

    override suspend fun exec(request: TermuxRequest): TermuxResult {
        val action = "${context.packageName}.TERMUX_RESULT.${UUID.randomUUID()}"
        val started = AtomicBoolean(false)

        val result = withTimeoutOrNull(request.timeoutMs) {
            suspendCancellableCoroutine<TermuxResult> { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        val bundle = intent?.getBundleExtra(EXTRA_RESULT_BUNDLE)
                        cont.resume(bundle.toTermuxResult())
                    }
                }
                registerPrivateReceiver(receiver, action)
                cont.invokeOnCancellation { unregisterSafely(receiver) }

                val pendingIntent = buildResultPendingIntent(action)
                val serviceIntent = buildRunCommandIntent(request, pendingIntent)

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    started.set(true)
                } catch (e: SecurityException) {
                    unregisterSafely(receiver)
                    cont.resume(TermuxResult.Failure("SecurityException: ${e.message}"))
                } catch (e: IllegalStateException) {
                    unregisterSafely(receiver)
                    cont.resume(TermuxResult.Failure("IllegalStateException: ${e.message}"))
                }
            }
        }

        return result ?: TermuxResult.Timeout(request.timeoutMs).also {
            Timber.w("Termux RUN_COMMAND timed out after ${request.timeoutMs}ms (started=${started.get()})")
        }
    }

    private fun registerPrivateReceiver(receiver: BroadcastReceiver, action: String) {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterSafely(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered — fine.
        }
    }

    private fun buildResultPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun buildRunCommandIntent(
        request: TermuxRequest,
        pendingIntent: PendingIntent
    ): Intent = Intent(RUN_COMMAND_ACTION).apply {
        component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
        putExtra(EXTRA_COMMAND_PATH, request.command)
        if (request.arguments.isNotEmpty()) {
            putExtra(EXTRA_ARGUMENTS, request.arguments.toTypedArray())
        }
        request.workingDir?.let { putExtra(EXTRA_WORKDIR, it) }
        putExtra(EXTRA_BACKGROUND, request.background)
        putExtra(EXTRA_SESSION_ACTION, "0")
        putExtra(EXTRA_RESULT_PENDING_INTENT, pendingIntent)
    }

    private fun Bundle?.toTermuxResult(): TermuxResult {
        if (this == null) return TermuxResult.Failure("Termux returned no result bundle")
        val stdout = getString(RESULT_STDOUT).orEmpty()
        val stderr = getString(RESULT_STDERR).orEmpty()
        val exit = if (containsKey(RESULT_EXIT_CODE)) getInt(RESULT_EXIT_CODE) else null
        val err = getString(RESULT_ERR)
        if (!err.isNullOrBlank() && exit == null) {
            return TermuxResult.Failure("Termux error: $err / ${getString(RESULT_ERR_MSG).orEmpty()}")
        }
        return TermuxResult.Success(
            stdout = stdout,
            stderr = stderr,
            exitCode = exit ?: -1
        )
    }

    companion object {
        private const val TERMUX_PACKAGE = TermuxAvailability.TERMUX_PACKAGE
        private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"

        private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        private const val EXTRA_RESULT_PENDING_INTENT =
            "com.termux.RUN_COMMAND_RESULT_PENDING_INTENT"

        private const val EXTRA_RESULT_BUNDLE = "result"
        private const val RESULT_STDOUT = "stdout"
        private const val RESULT_STDERR = "stderr"
        private const val RESULT_EXIT_CODE = "exitCode"
        private const val RESULT_ERR = "err"
        private const val RESULT_ERR_MSG = "errmsg"
    }
}

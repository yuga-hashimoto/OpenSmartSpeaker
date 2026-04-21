package com.opendash.app.tool.termux

/**
 * Test-only fake for [TermuxBridge]. Records the last [TermuxRequest] so
 * tests can assert on what the executor forwarded, and lets tests preload
 * the next result.
 */
class FakeTermuxBridge(
    var nextResult: TermuxResult = TermuxResult.Success(stdout = "", stderr = "", exitCode = 0)
) : TermuxBridge {

    var lastRequest: TermuxRequest? = null
        private set

    override suspend fun exec(request: TermuxRequest): TermuxResult {
        lastRequest = request
        return nextResult
    }
}

/**
 * Test-only fake for [TermuxAvailability] with mutable gates so a single
 * test can flip them mid-scenario.
 */
class ToggleableTermuxAvailability(
    var installed: Boolean = false,
    var permitted: Boolean = false
) : TermuxAvailability {
    override fun isTermuxInstalled(): Boolean = installed
    override fun hasRunCommandPermission(): Boolean = permitted
}

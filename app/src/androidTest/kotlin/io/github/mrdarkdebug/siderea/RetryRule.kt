package io.github.mrdarkdebug.siderea

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Runs a UI test again (up to [attempts] times in all) when it fails.
 *
 * The emulator's software-rendered GPU slows down over a long run until Compose reports "not idle", which fails
 * tests that pass on their own and on a fresh emulator. A retry absorbs that. It does not hide logic bugs: a test
 * that is really wrong fails every attempt, and every failed attempt is printed so it still shows in the log.
 */
class RetryRule(
    private val attempts: Int = 2,
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                var last: Throwable? = null
                for (attempt in 1..attempts) {
                    try {
                        base.evaluate()
                        return
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Throwable,
                    ) {
                        last = e
                        System.err.println("Attempt $attempt of $attempts failed for ${description.displayName}: $e")
                    }
                }
                throw checkNotNull(last)
            }
        }
}

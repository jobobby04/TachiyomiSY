package exh.util

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * SY-specific file
 */
class ThrottleManagerTest {

    @Test
    fun `throttleTime starts at the initial value`() {
        ThrottleManager().throttleTime shouldBe Duration.ZERO
    }

    @Test
    fun `throttle escalates by inc on each call`() = runTest {
        val throttle = ThrottleManager()

        throttle.throttle()
        throttle.throttleTime shouldBe ThrottleManager.THROTTLE_INC

        throttle.throttle()
        throttle.throttleTime shouldBe ThrottleManager.THROTTLE_INC * 2
    }

    @Test
    fun `throttleTime caps at max`() = runTest {
        val throttle = ThrottleManager()

        // Far more than enough calls to reach the cap; delays are virtualized by runTest.
        repeat(300) { throttle.throttle() }

        throttle.throttleTime shouldBe ThrottleManager.THROTTLE_MAX
    }

    @Test
    fun `resetThrottle restores the initial value`() = runTest {
        val throttle = ThrottleManager()

        repeat(5) { throttle.throttle() }
        check(throttle.throttleTime > Duration.ZERO)

        throttle.resetThrottle()

        throttle.throttleTime shouldBe Duration.ZERO
    }

    @Test
    fun `custom constructor params are respected`() = runTest {
        val throttle = ThrottleManager(
            max = 100.milliseconds,
            inc = 30.milliseconds,
            initial = 10.milliseconds,
        )

        throttle.throttleTime shouldBe 10.milliseconds

        throttle.throttle()
        throttle.throttleTime shouldBe 40.milliseconds
    }
}

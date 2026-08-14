package com.jrs.skannlet.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingSessionTrackerTest {
    @Test
    fun `initial charging state requires user selection`() {
        val tracker = ChargingSessionTracker()

        assertTrue(tracker.onChargingChanged(isCharging = true))
        assertTrue(tracker.userSelectionRequired)
    }

    @Test
    fun `initial unplugged state does not require user selection`() {
        val tracker = ChargingSessionTracker()

        assertFalse(tracker.onChargingChanged(isCharging = false))
        assertFalse(tracker.userSelectionRequired)
    }

    @Test
    fun `starting to charge requires user selection`() {
        val tracker = ChargingSessionTracker()

        tracker.onChargingChanged(isCharging = false)

        assertTrue(tracker.onChargingChanged(isCharging = true))
    }

    @Test
    fun `repeated charging broadcasts coalesce after acknowledgement`() {
        val tracker = ChargingSessionTracker()
        tracker.onChargingChanged(isCharging = true)
        tracker.acknowledgeUserSelection()

        assertFalse(tracker.onChargingChanged(isCharging = true))
        assertFalse(tracker.userSelectionRequired)
    }

    @Test
    fun `acknowledgement clears pending selection`() {
        val tracker = ChargingSessionTracker()
        tracker.onChargingChanged(isCharging = true)

        tracker.acknowledgeUserSelection()

        assertFalse(tracker.userSelectionRequired)
    }

    @Test
    fun `unplugging does not clear pending selection`() {
        val tracker = ChargingSessionTracker()
        tracker.onChargingChanged(isCharging = true)

        assertTrue(tracker.onChargingChanged(isCharging = false))
        assertTrue(tracker.userSelectionRequired)
    }

    @Test
    fun `a new charging session requires selection after acknowledgement`() {
        val tracker = ChargingSessionTracker()
        tracker.onChargingChanged(isCharging = true)
        tracker.acknowledgeUserSelection()
        tracker.onChargingChanged(isCharging = false)

        assertTrue(tracker.onChargingChanged(isCharging = true))
    }
}

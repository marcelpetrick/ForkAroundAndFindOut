// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Timing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitivityTest {
    @Test
    fun presetsRoundTripKeepTheGapBudgetAndOrderByEagerness() {
        assertEquals(Sensitivity.NORMAL, Sensitivity.of(Timing()))
        val base = Timing(maxGapMs = 800)
        for (preset in Sensitivity.entries) {
            val timing = preset.apply(base)
            assertEquals(preset, Sensitivity.of(timing))
            assertEquals(800, timing.maxGapMs)
        }
        assertNull(Sensitivity.of(Timing(triggerMs = 1250)))
        // Conservative waits longest and needs the strongest evidence; Responsive the opposite.
        val (conservative, normal, responsive) = Sensitivity.entries
        assertTrue(conservative.triggerMs > normal.triggerMs && normal.triggerMs > responsive.triggerMs)
        assertTrue(conservative.trigger > normal.trigger && normal.trigger > responsive.trigger)
        assertTrue(conservative.cooldownMs > responsive.cooldownMs)
    }
}

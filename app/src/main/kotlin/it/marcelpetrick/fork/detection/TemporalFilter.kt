// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

enum class ElbowState { UNKNOWN, CLEAR, SUSPECT, VIOLATION }

data class Timing(
    val trigger: Double = 0.75,
    val clear: Double = 0.35,
    val triggerMs: Long = 1000,
    val clearMs: Long = 500,
    val cooldownMs: Long = 1500,
    val maxGapMs: Long = 500,
) {
    init {
        require(trigger.isFinite() && clear.isFinite() && clear >= 0 && trigger <= 1 && clear < trigger)
        require(triggerMs >= 0 && clearMs >= 0 && cooldownMs >= 0 && maxGapMs > 0)
    }
}

/** A gap/occlusion discards evidence. No single frame can enter VIOLATION. */
class TemporalFilter(
    private val timing: Timing = Timing(),
) {
    var state = ElbowState.UNKNOWN
        private set
    private var last: Long? = null
    private var since = 0L
    private var clearing: Long? = null
    private var cooldownUntil = 0L

    fun update(
        timeMs: Long,
        score: Double?,
    ): ElbowState {
        val previous = last
        last = timeMs
        if (score == null || !score.isFinite() || score !in 0.0..1.0 || timeMs < 0 ||
            (previous != null && (timeMs <= previous || timeMs - previous > timing.maxGapMs))
        ) {
            state = ElbowState.UNKNOWN
            clearing = null
            return state
        }
        when (state) {
            ElbowState.UNKNOWN, ElbowState.CLEAR -> {
                state = ElbowState.CLEAR
                if (score >= timing.trigger && timeMs >= cooldownUntil) {
                    state = ElbowState.SUSPECT
                    since = timeMs
                }
            }
            ElbowState.SUSPECT -> {
                if (score < timing.trigger) {
                    state = ElbowState.CLEAR
                } else if (timeMs - since >= timing.triggerMs) {
                    state = ElbowState.VIOLATION
                }
            }
            ElbowState.VIOLATION -> {
                if (score <= timing.clear) {
                    if (clearing == null) clearing = timeMs
                    if (timeMs - clearing!! >= timing.clearMs) {
                        state = ElbowState.CLEAR
                        cooldownUntil = timeMs + timing.cooldownMs
                        clearing = null
                    }
                } else {
                    clearing = null
                }
            }
        }
        return state
    }
}

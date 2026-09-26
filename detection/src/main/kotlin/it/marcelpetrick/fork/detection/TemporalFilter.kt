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
    /**
     * How long a running reminder survives while the arm's joints are hidden in otherwise fresh
     * frames (a dish passed in front, a hand behind a glass). It never starts or builds one.
     */
    val holdMs: Long = 600,
) {
    init {
        require(trigger.isFinite() && clear.isFinite() && clear >= 0 && trigger <= 1 && clear < trigger)
        require(triggerMs >= 0 && clearMs >= 0 && cooldownMs >= 0 && maxGapMs > 0 && holdMs >= 0)
    }
}

/**
 * A frame gap or invalid evidence discards the state at once. Hidden joints (no score in a
 * fresh frame) keep a running VIOLATION for at most [Timing.holdMs], then discard it too.
 * No single frame can enter VIOLATION.
 */
class TemporalFilter(
    private val timing: Timing = Timing(),
) {
    var state = ElbowState.UNKNOWN
        private set
    private var last: Long? = null
    private var since = 0L
    private var clearing: Long? = null
    private var cooldownUntil = 0L
    private var hiddenSince: Long? = null

    /** [maxGapMs] may exceed the configured floor when the measured frame period is long. */
    fun update(
        timeMs: Long,
        score: Double?,
        maxGapMs: Long = timing.maxGapMs,
        /** The score is missing because joints are hidden; only then may a reminder hold. */
        hidden: Boolean = score == null,
    ): ElbowState {
        val previous = last
        last = timeMs
        val gap = timeMs < 0 || (previous != null && (timeMs <= previous || timeMs - previous > maxGapMs))
        if (!gap && score == null && hidden && state == ElbowState.VIOLATION) {
            val since = hiddenSince ?: timeMs.also { hiddenSince = it }
            if (timeMs - since < timing.holdMs) return state
        }
        if (gap || score == null || !score.isFinite() || score !in 0.0..1.0) {
            state = ElbowState.UNKNOWN
            clearing = null
            hiddenSince = null
            return state
        }
        hiddenSince = null
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

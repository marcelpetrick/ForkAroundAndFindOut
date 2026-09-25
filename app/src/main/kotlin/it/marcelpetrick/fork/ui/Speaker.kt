// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.ui

import android.media.AudioManager
import android.media.ToneGenerator
import it.marcelpetrick.fork.monitoring.Sound

/** Executes [Sound] decisions; policy stays in [it.marcelpetrick.fork.monitoring.AlarmPolicy]. */
interface Speaker {
    fun play(
        sound: Sound,
        volume: Int,
    )

    fun release()
}

class ToneSpeaker(
    private val factory: (Int) -> ToneGenerator = { volume -> ToneGenerator(AudioManager.STREAM_MUSIC, volume) },
) : Speaker {
    private var generator: ToneGenerator? = null
    private var volume = -1

    override fun play(
        sound: Sound,
        volume: Int,
    ) {
        when (sound) {
            Sound.NONE -> Unit
            Sound.STOP -> generator?.stopTone()
            Sound.BEEP -> tone(volume).startTone(ToneGenerator.TONE_PROP_BEEP, 400)
            Sound.START -> tone(volume).startTone(ToneGenerator.TONE_SUP_DIAL, -1)
        }
    }

    private fun tone(level: Int): ToneGenerator {
        if (generator == null || level != volume) {
            generator?.release()
            generator = factory(level)
            volume = level
        }
        return generator!!
    }

    override fun release() {
        generator?.stopTone()
        generator?.release()
        generator = null
        volume = -1
    }
}

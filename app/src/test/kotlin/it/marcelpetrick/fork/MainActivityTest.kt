// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork

import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    @Test
    fun launchesNativeApplication() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val content = controller.get().findViewById<android.view.ViewGroup>(android.R.id.content)
            assertEquals("Fork Around & Find Out", (content.getChildAt(0) as TextView).text)
        }
    }
}

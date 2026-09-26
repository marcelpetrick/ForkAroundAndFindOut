// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** End-to-end flows through the installed APK with real touches (UiAutomator). */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context: Context = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>

    /** Keeps a screenshot of every failure; the pipeline pulls them into its report directory. */
    @get:Rule
    val screenshots =
        object : TestWatcher() {
            override fun failed(
                e: Throwable,
                description: Description,
            ) {
                device.executeShellCommand("screencap -p /data/local/tmp/fork-e2e-${description.methodName}.png")
            }

            // Closing here (not in @After) keeps the app on screen for the failure screenshot.
            override fun finished(description: Description) {
                scenario.close()
            }
        }

    /** CI emulators sometimes show "System UI isn't responding"; waiting lets the test proceed. */
    private fun dismissSystemDialogs() {
        repeat(3) {
            val wait = device.findObject(By.text(Pattern.compile("Wait", Pattern.CASE_INSENSITIVE))) ?: return
            if (!device.hasObject(By.textContains("isn't responding"))) return
            wait.click()
            device.waitForIdle()
        }
    }

    @Before
    fun start() {
        context
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.filesDir.resolve("samples.json").delete()
        dismissSystemDialogs()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        device.waitForIdle()
        dismissSystemDialogs()
    }

    private fun waitFor(
        text: String,
        timeoutMs: Long = 10_000,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            scrollTo(text)
            if (device.hasObject(By.textContains(text))) return
        }
        throw AssertionError("'$text' did not appear")
    }

    private fun tap(text: String) {
        dismissSystemDialogs()
        scrollTo(text)
        // Dialog buttons may be rendered in capitals by the platform theme.
        val selector = By.text(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))
        val target = device.wait(Until.findObject(selector), 10_000) ?: device.findObject(By.desc(text))
        assertNotNull("No '$text' on screen", target)
        target.click()
        device.waitForIdle()
    }

    /**
     * Small screens and the grouped settings keep controls below the fold; swipe the panel
     * until the target (text or content description) shows.
     * Swipes stay in the middle half of the panel, away from system gesture zones.
     */
    private fun scrollTo(text: String) {
        for (down in listOf(true, false)) {
            repeat(10) {
                // Icon-like buttons (− / +) are found by their content description.
                if (device.wait(Until.hasObject(By.textContains(text)), 300) || device.hasObject(By.desc(text))) return
                val area = device.findObject(By.scrollable(true))?.visibleBounds ?: return
                val (low, high) = area.centerY() + area.height() / 4 to area.centerY() - area.height() / 4
                device.swipe(area.centerX(), if (down) low else high, area.centerX(), if (down) high else low, 30)
                device.waitForIdle()
            }
        }
    }

    @Test
    fun syntheticDemoShowsWarningWithoutCamera() {
        tap("Try demo (synthetic)")
        waitFor("SYNTHETIC DEMO")
        // The reminder card (drawn on the overlay) targets seat 2, left, within one demo loop.
        var reminder: Pair<Int, Boolean>? = null
        val deadline = System.currentTimeMillis() + 16_000
        while (reminder == null && System.currentTimeMillis() < deadline) {
            scenario.onActivity { reminder = it.stage?.reminder }
            if (reminder == null) Thread.sleep(200)
        }
        assertEquals(2 to true, reminder)
        waitFor("Left: Clear")
        device.pressBack()
        waitFor("Try demo (synthetic)")
    }

    @Test
    fun settingsPersistAcrossRestart() {
        tap("Settings")
        tap("Increase Pose model")
        waitFor("Lite (faster)")
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        tap("Settings")
        waitFor("Lite (faster)")
        scenario.onActivity { assertEquals("LITE", it.settings.model.name) }
    }

    @Test
    fun cameraSetupCalibrationMonitoringPauseAndData() {
        val manager = context.getSystemService(CameraManager::class.java)
        val rear =
            manager.cameraIdList.any {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        tap("Set up camera")
        if (!rear) {
            // Without a rear camera the app must explain recovery instead of crashing.
            waitFor("Camera or model unavailable")
            return
        }
        waitFor("Position the phone")
        waitFor("People detected")
        var ready = false
        val deadline = System.currentTimeMillis() + 30_000
        while (!ready && System.currentTimeMillis() < deadline) {
            scenario.onActivity { ready = it.cameraReady }
            if (!ready) Thread.sleep(250)
        }
        assertTrue("camera delivered no analysed frame within 30 s", ready)
        tap("Mark table without the check") // the emulator scene contains no people
        waitFor("Corners marked: 0 of 4")
        val location = IntArray(2)
        var size = 0 to 0
        scenario.onActivity { activity ->
            activity.stage!!.getLocationOnScreen(location)
            size = activity.stage!!.width to activity.stage!!.height
        }
        for ((x, y) in listOf(0.2 to 0.5, 0.8 to 0.5, 0.85 to 0.85, 0.15 to 0.85)) {
            device.click(location[0] + (x * size.first).toInt(), location[1] + (y * size.second).toInt())
            device.waitForIdle()
        }
        waitFor("Corners marked: 4 of 4")
        scrollTo("Save table")
        tap("Save table")
        waitFor("Seats (optional)")
        scrollTo("Finish setup")
        tap("Finish setup")
        tap("Start dinner")
        waitFor("Pause") // the grace countdown may already be over on a slow device
        Thread.sleep(5_000) // let the latency window fill
        tap("Adult diagnostics")
        // "latency p50" is unique to the diagnostics readout; a slow CI emulator also shows
        // an FPS figure in the "Processing is slow" status line.
        waitFor("latency p50")
        val readout = device.findObject(By.textContains("latency p50")).text
        Log.i("ForkPerformance", readout.lines().first())
        assertTrue(readout, Regex("""\d+(\.\d)? FPS · latency p50 \d+ / p95 \d+ ms · dropped \d+""").containsMatchIn(readout))
        tap("Hide diagnostics")
        tap("Pause")
        waitFor("Paused. No warnings until you resume.")
        tap("Resume")
        scrollTo("Stop")
        tap("Stop")
        scenario.onActivity { assertTrue(it.settings.table != null) }
        scrollTo("Local data")
        tap("Local data")
        waitFor("Stored records: 0")
        tap("Delete all")
        tap("Cancel")
        waitFor("Stored records: 0")
    }
}

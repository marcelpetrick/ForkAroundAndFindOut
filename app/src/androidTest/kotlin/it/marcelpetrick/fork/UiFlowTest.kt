// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** End-to-end flows through the installed APK with real touches (UiAutomator). */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context: Context = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun start() {
        context
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.filesDir.resolve("samples.json").delete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun stop() {
        scenario.close()
    }

    private fun waitFor(
        text: String,
        timeoutMs: Long = 10_000,
    ) = assertNotNull("'$text' did not appear", device.wait(Until.findObject(By.textContains(text)), timeoutMs))

    private fun tap(text: String) {
        // Dialog buttons may be rendered in capitals by the platform theme.
        val selector = By.text(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))
        val target = device.wait(Until.findObject(selector), 10_000) ?: device.findObject(By.desc(text))
        assertNotNull("No '$text' on screen", target)
        target.click()
        device.waitForIdle()
    }

    private fun scrollTo(text: String) {
        repeat(8) {
            if (device.hasObject(By.text(text))) return
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 3, 20)
        }
    }

    @Test
    fun syntheticDemoShowsWarningWithoutCamera() {
        tap("Try demo (synthetic)")
        waitFor("SYNTHETIC DEMO")
        waitFor("Elbow on table", 12_000)
        waitFor("Seat 1 · Left: Clear")
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
        tap("Mark table")
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
        tap("Start monitoring")
        waitFor("Warnings begin in")
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

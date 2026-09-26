// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.marcelpetrick.fork.camera.MediaPipeEngine
import it.marcelpetrick.fork.monitoring.PoseModel
import it.marcelpetrick.fork.monitoring.Processor
import it.marcelpetrick.fork.monitoring.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NativeModelTest {
    @Test
    fun bothBundledModelsRunOfflineThroughRealNativeLiveStream() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((model, processor) in PoseModel.entries.map { it to Processor.CPU } + (PoseModel.LITE to Processor.GPU)) {
            val done = CountDownLatch(1)
            val failure = AtomicReference<Exception?>()
            var detected = -1
            MediaPipeEngine(context, Settings(model = model, processor = processor), { poses, _ ->
                detected = poses.size
                done.countDown()
            }, { error ->
                failure.set(error)
                done.countDown()
            }).use { engine ->
                engine.submit(Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888), 100)
                assertTrue("$model on $processor timed out", done.await(30, TimeUnit.SECONDS))
                assertNull(failure.get())
                assertEquals("Blank frame must not hallucinate a person", 0, detected)
                // GPU is experimental: either it runs, or the engine fell back to the CPU.
                if (processor == Processor.CPU) assertEquals(Processor.CPU, engine.processor)
                android.util.Log.i("NativeModelTest", "$model requested $processor, runs on ${engine.processor}")
            }
        }
    }
}

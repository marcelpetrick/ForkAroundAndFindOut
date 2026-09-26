// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Privacy is a product feature: these guards fail if a dependency or change widens access. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyGuardTest {
    @Test
    fun mergedManifestRequestsOnlyTheCameraAndDisablesBackup() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        // No INTERNET, no storage, no location: frames and data cannot leave the phone.
        // ACCESS_NETWORK_STATE (read-only, from a library's job scheduling) and AndroidX's
        // own signature permission for unexported receivers cannot transmit anything.
        val allowed =
            setOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_NETWORK_STATE,
                "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            )
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertTrue("unexpected permissions: ${requested - allowed}", requested.all { it in allowed })
        assertTrue(Manifest.permission.CAMERA in requested)
        assertFalse(Manifest.permission.INTERNET in requested)
        assertTrue("backup must stay disabled", info.applicationInfo!!.flags and ApplicationInfo.FLAG_ALLOW_BACKUP == 0)
    }
}

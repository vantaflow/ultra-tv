package com.ultratv.tv.nativeapp.ui.mobile

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.ui.common.isTelevision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** La TV se reconnaît au mode d'interface ou à la fonction leanback ; tout le reste est tactile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceDetectionTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun telephone_nEstPasUneTelevision() {
        assertFalse(isTelevision(ctx))
    }

    @Test fun modeInterfaceTelevision_estUneTelevision() {
        val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        shadowOf(ui).currentModeType = Configuration.UI_MODE_TYPE_TELEVISION
        assertTrue(isTelevision(ctx))
    }

    @Test fun fonctionLeanback_estUneTelevision() {
        shadowOf(ctx.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, true)
        assertTrue(isTelevision(ctx))
    }
}

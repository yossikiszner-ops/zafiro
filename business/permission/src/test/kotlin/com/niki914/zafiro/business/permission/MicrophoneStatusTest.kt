package com.niki914.zafiro.business.permission

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MicrophoneStatusTest {
    @Test fun grantedMicrophoneIsDetectedWithoutRootOrAnActivity() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertEquals(PermissionState.GRANTED, TargetStatus.query(app, Permission.MICROPHONE, null))
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        assertEquals(PermissionState.DENIED_BY_USER, TargetStatus.query(app, Permission.MICROPHONE, null))
    }
}

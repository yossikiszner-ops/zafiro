package com.niki914.zafiro.app.voice

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.niki914.zafiro.chat.agentic.device.AppInfoCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class InstalledAppIdentityTest {
    private fun install(label: String, version: Long) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "example.installed"
            lastUpdateTime = version
            applicationInfo = ApplicationInfo().apply {
                packageName = "example.installed"
                nonLocalizedLabel = label
                icon = android.R.drawable.ic_menu_camera
            }
        })
    }
    @Test fun resolvesRealInstalledLabelAndCachesIcon() = runTest {
        install("אפליקציה חדשה", 1)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val cache = AppInfoCache(context)
        val first = cache.identity("example.installed")!!
        assertEquals("אפליקציה חדשה", first.label)
        assertEquals("example.installed", first.packageName)
        assertTrue(first.icon.width in 48..192)
        assertSame(first, cache.identity("example.installed"))
    }
    @Test fun missingPackageIsNeverPresentedAsInstalled() = runTest {
        val cache = AppInfoCache(ApplicationProvider.getApplicationContext<Application>())
        assertNull(cache.identity("example.not.installed"))
    }
    @Test fun packageUpdateInvalidatesOldLabelAndIcon() = runTest {
        install("Before", 1)
        val cache = AppInfoCache(ApplicationProvider.getApplicationContext<Application>())
        val first = cache.identity("example.installed")!!
        install("After", 2)
        val second = cache.identity("example.installed")!!
        assertEquals("After", second.label)
        assertNotSame(first, second)
    }
}

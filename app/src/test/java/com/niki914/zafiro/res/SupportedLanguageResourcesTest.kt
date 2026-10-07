package com.niki914.zafiro.res

import android.app.Application
import android.content.res.Configuration
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.niki914.zafiro.app.R
import com.niki914.zafiro.repo.UiLanguage
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SupportedLanguageResourcesTest {
    @Test fun languageSwitchUpdatesResourcesAndDirection() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val hebrew = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale("he")) })
        val english = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        assertEquals("הגדרות", hebrew.getString(R.string.ui_settings_title))
        assertEquals(View.LAYOUT_DIRECTION_RTL, hebrew.resources.configuration.layoutDirection)
        assertEquals("Settings", english.getString(R.string.ui_settings_title))
        assertEquals("בינה מקומית", hebrew.getString(R.string.local_ai_title))
        assertEquals("Local AI", english.getString(R.string.local_ai_title))
        assertEquals(View.LAYOUT_DIRECTION_LTR, english.resources.configuration.layoutDirection)
        assertEquals("ניסיון 2 מתוך 4", hebrew.getString(R.string.ui_home_retrying_attempt, 2, 4))
    }
    @Test fun unsupportedLanguageFallsBackToEnglish() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val chinese = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) })
        assertEquals("Settings", chinese.getString(R.string.ui_settings_title))
        assertEquals("", UiLanguage.normalize("zh-CN"))
        assertEquals("he", UiLanguage.normalize(" iw-IL "))
        assertEquals("he", UiLanguage.normalize("he_IL"))
        assertEquals("en", UiLanguage.normalize("en-GB"))
    }
}

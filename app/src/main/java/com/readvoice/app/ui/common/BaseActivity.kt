package com.readvoice.app.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.readvoice.app.ReadVoiceApp
import com.readvoice.app.di.AppContainer
import com.readvoice.app.domain.model.Appearance

/**
 * Applies the user's appearance choices to a screen. The font is a theme
 * overlay and the text size is a font-scale override, so layouts never set
 * either themselves. A screen that returns to the foreground after the
 * choices changed rebuilds itself.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected val container: AppContainer
        get() = (application as ReadVoiceApp).container

    private var applied: Appearance? = null

    override fun attachBaseContext(newBase: Context) {
        val appearance = (newBase.applicationContext as ReadVoiceApp)
            .container.appearanceSettings.current()
        applied = appearance
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = config.fontScale * appearance.textScale
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applied?.let { theme.applyStyle(ThemeApplier.fontOverlay(it.font), true) }
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (container.appearanceSettings.current() != applied) recreate()
    }
}

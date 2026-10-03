package com.readvoice.app

import android.app.Application
import com.readvoice.app.di.AppContainer
import com.readvoice.app.ui.common.ThemeApplier

class ReadVoiceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ThemeApplier.applyTheme(container.appearanceSettings.current().theme)
    }

    /**
     * The login and network objects are created around one client ID. After
     * the ID changes they are rebuilt from scratch, which is simpler and
     * safer than letting each of them track a value that can change.
     */
    fun rebuildContainer() {
        container = AppContainer(this)
    }
}

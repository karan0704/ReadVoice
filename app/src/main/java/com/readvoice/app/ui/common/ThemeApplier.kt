package com.readvoice.app.ui.common

import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.readvoice.app.R
import com.readvoice.app.domain.model.FontChoice
import com.readvoice.app.domain.model.ThemeMode

/** Maps appearance choices onto Android's theme machinery. */
object ThemeApplier {

    /** Black is the night palette and White the day palette. */
    fun applyTheme(theme: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                ThemeMode.BLACK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.WHITE -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.FOLLOW_PHONE -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    @StyleRes
    fun fontOverlay(font: FontChoice): Int = when (font) {
        FontChoice.SYSTEM -> R.style.FontOverlay_System
        FontChoice.SERIF -> R.style.FontOverlay_Serif
        FontChoice.MONOSPACE -> R.style.FontOverlay_Monospace
        FontChoice.ATKINSON -> R.style.FontOverlay_Atkinson
    }
}

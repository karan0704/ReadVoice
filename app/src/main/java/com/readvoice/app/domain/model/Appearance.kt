package com.readvoice.app.domain.model

enum class ThemeMode { BLACK, WHITE, FOLLOW_PHONE }

enum class FontChoice { SYSTEM, SERIF, MONOSPACE, ATKINSON }

/** The user's look-and-feel choices. [textScale] multiplies the phone's own font size. */
data class Appearance(
    val theme: ThemeMode = ThemeMode.FOLLOW_PHONE,
    val font: FontChoice = FontChoice.SYSTEM,
    val textScale: Float = 1.0f
)

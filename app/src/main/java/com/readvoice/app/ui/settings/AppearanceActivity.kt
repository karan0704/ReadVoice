package com.readvoice.app.ui.settings

import android.os.Bundle
import android.widget.SeekBar
import com.readvoice.app.R
import com.readvoice.app.databinding.ActivityAppearanceBinding
import com.readvoice.app.domain.model.Appearance
import com.readvoice.app.domain.model.FontChoice
import com.readvoice.app.domain.model.ThemeMode
import com.readvoice.app.ui.common.BaseActivity
import com.readvoice.app.ui.common.ThemeApplier
import kotlin.math.abs

/**
 * Theme, font and text-size choices. Each change is saved at once and the
 * screen rebuilds itself, so the preview at the bottom always shows the
 * result.
 */
class AppearanceActivity : BaseActivity() {

    private lateinit var binding: ActivityAppearanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppearanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        val current = container.appearanceSettings.current()
        binding.themeGroup.check(THEME_IDS.getValue(current.theme))
        binding.fontGroup.check(FONT_IDS.getValue(current.font))
        binding.textSizeBar.max = TEXT_SCALES.lastIndex
        binding.textSizeBar.progress =
            TEXT_SCALES.indices.minByOrNull { abs(TEXT_SCALES[it] - current.textScale) } ?: 1

        binding.themeGroup.setOnCheckedChangeListener { _, id ->
            THEME_IDS.entries.firstOrNull { it.value == id }?.let { change { copy(theme = it.key) } }
        }
        binding.fontGroup.setOnCheckedChangeListener { _, id ->
            FONT_IDS.entries.firstOrNull { it.value == id }?.let { change { copy(font = it.key) } }
        }
        binding.textSizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                change { copy(textScale = TEXT_SCALES[seekBar.progress]) }
            }
        })
    }

    /**
     * A theme change makes AppCompat rebuild every screen on its own; font
     * and size changes need this screen rebuilt by hand. Nothing happens when
     * the value is unchanged, which is the case when Android restores the
     * radio buttons after a rebuild.
     */
    private fun change(edit: Appearance.() -> Appearance) {
        val settings = container.appearanceSettings
        val before = settings.current()
        val after = before.edit()
        if (after == before) return
        settings.save(after)
        if (after.theme != before.theme) ThemeApplier.applyTheme(after.theme) else recreate()
    }

    private companion object {
        val TEXT_SCALES = floatArrayOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)

        val THEME_IDS = mapOf(
            ThemeMode.BLACK to R.id.themeBlack,
            ThemeMode.WHITE to R.id.themeWhite,
            ThemeMode.FOLLOW_PHONE to R.id.themeFollowPhone
        )

        val FONT_IDS = mapOf(
            FontChoice.SYSTEM to R.id.fontSystem,
            FontChoice.SERIF to R.id.fontSerif,
            FontChoice.MONOSPACE to R.id.fontMonospace,
            FontChoice.ATKINSON to R.id.fontAtkinson
        )
    }
}

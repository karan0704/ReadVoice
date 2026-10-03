package com.readvoice.app.ui.setup

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.readvoice.app.BuildConfig
import com.readvoice.app.R
import com.readvoice.app.ReadVoiceApp
import com.readvoice.app.databinding.ActivitySetupBinding
import com.readvoice.app.ui.common.BaseActivity
import com.readvoice.app.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Walks the user through creating their own Reddit "installed app" and
 * entering its client ID. Shown on first launch, and reachable later to
 * change or remove the ID.
 *
 * The clipboard is read only when Paste is tapped, never in the background.
 */
class SetupActivity : BaseActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val viewModel: SetupViewModel by viewModels { SetupViewModel.factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // On first launch this is the only screen, so there is nothing to go back to.
        binding.backButton.isVisible = !isTaskRoot
        binding.backButton.setOnClickListener { finish() }

        binding.redirectValue.text = BuildConfig.REDDIT_REDIRECT_URI
        binding.openRedditButton.setOnClickListener { openRedditApps() }
        binding.copyNameButton.setOnClickListener { copy(getString(R.string.setup_app_name_value)) }
        binding.copyRedirectButton.setOnClickListener { copy(BuildConfig.REDDIT_REDIRECT_URI) }
        binding.pasteButton.setOnClickListener { pasteClientId() }
        binding.checkButton.setOnClickListener { viewModel.check() }
        binding.saveButton.setOnClickListener { viewModel.save() }
        binding.removeButton.setOnClickListener { viewModel.remove() }

        if (savedInstanceState == null) binding.clientIdInput.setText(viewModel.state.value.input)
        binding.clientIdInput.doAfterTextChanged { viewModel.onInputChanged(it?.toString().orEmpty()) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: SetupUiState) {
        if (state.isDone) {
            restartLoginFlow()
            return
        }
        val checking = state.check == CheckStatus.CHECKING
        binding.checkButton.setUsable(state.isFormatValid && !checking && !state.isBusy)
        binding.saveButton.setUsable(state.isFormatValid && !state.isBusy)
        binding.removeButton.isVisible = state.canRemove
        binding.removeButton.setUsable(!state.isBusy)
        binding.statusText.setText(statusMessage(state))
        binding.statusText.setTextColor(
            getColor(
                if (state.check == CheckStatus.VALID || state.check == CheckStatus.INVALID) {
                    R.color.text_primary
                } else {
                    R.color.text_secondary
                }
            )
        )
    }

    private fun statusMessage(state: SetupUiState): Int = when {
        state.input.isEmpty() -> R.string.setup_status_empty
        !state.isFormatValid -> R.string.setup_status_bad_format
        else -> when (state.check) {
            CheckStatus.NONE -> R.string.setup_status_format_ok
            CheckStatus.CHECKING -> R.string.setup_status_checking
            CheckStatus.VALID -> R.string.setup_status_valid
            CheckStatus.INVALID -> R.string.setup_status_invalid
            CheckStatus.UNREACHABLE -> R.string.setup_status_unreachable
        }
    }

    /**
     * The login objects are built around the client ID, so they are rebuilt
     * and every open screen is replaced by a fresh login flow.
     */
    private fun restartLoginFlow() {
        (application as ReadVoiceApp).rebuildContainer()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun openRedditApps() {
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(REDDIT_APPS_URL))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun copy(text: String) {
        clipboard().setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        // Android 13 and later show their own "copied" confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.setup_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteClientId() {
        val pasted = clipboard().primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
        if (pasted.isNullOrEmpty()) {
            Toast.makeText(this, R.string.setup_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        binding.clientIdInput.setText(pasted)
        binding.clientIdInput.setSelection(binding.clientIdInput.length())
    }

    private fun clipboard(): ClipboardManager =
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** A button that cannot be used right now is dimmed as well as disabled. */
    private fun View.setUsable(usable: Boolean) {
        isEnabled = usable
        alpha = if (usable) 1f else DISABLED_ALPHA
    }

    private companion object {
        const val REDDIT_APPS_URL = "https://www.reddit.com/prefs/apps"
        const val DISABLED_ALPHA = 0.4f
    }
}

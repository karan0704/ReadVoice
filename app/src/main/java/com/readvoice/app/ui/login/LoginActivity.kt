package com.readvoice.app.ui.login

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.readvoice.app.databinding.ActivityLoginBinding
import com.readvoice.app.ui.common.BaseActivity
import com.readvoice.app.ui.common.messageRes
import com.readvoice.app.ui.inbox.InboxActivity
import com.readvoice.app.ui.setup.SetupActivity
import kotlinx.coroutines.launch

/**
 * Entry screen. Reddit's login page opens in a browser tab, never inside the
 * app, so the app has no access to the password typed there.
 */
class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels {
        LoginViewModel.factory(container.authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /**
         * Login is impossible without a client ID, so a phone that has none
         * yet starts on the setup screen instead.
         */
        if (container.clientIdSettings.current() == null) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        if (viewModel.isLoggedIn) {
            openInbox()
            return
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.loginButton.setOnClickListener { viewModel.startLogin() }
        binding.changeClientIdButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
        if (savedInstanceState == null) handleRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirect(intent)
    }

    /** The redirect is taken out of the intent so a rotation cannot submit it twice. */
    private fun handleRedirect(intent: Intent?) {
        val redirect = intent?.getStringExtra(EXTRA_REDIRECT) ?: return
        intent.removeExtra(EXTRA_REDIRECT)
        viewModel.finishLogin(redirect)
    }

    private fun render(state: LoginUiState) {
        binding.progress.isVisible = state is LoginUiState.Working
        binding.loginButton.isEnabled = state !is LoginUiState.Working
        binding.errorText.isVisible = state is LoginUiState.Failed
        when (state) {
            is LoginUiState.OpenBrowser -> viewModel.browserHandled(openBrowser(state.url))
            is LoginUiState.Failed -> binding.errorText.setText(state.error.messageRes())
            LoginUiState.LoggedIn -> openInbox()
            LoginUiState.Idle, LoginUiState.Working -> Unit
        }
    }

    private fun openBrowser(url: String): Boolean =
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }

    private fun openInbox() {
        startActivity(Intent(this, InboxActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_REDIRECT = "redirect"
    }
}

/**
 * Invisible landing point for readvoice://oauth. It passes the address on
 * to the existing LoginActivity, which also closes the browser tab above it.
 */
class OAuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val redirect = intent?.data?.toString()
        if (redirect != null) {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(LoginActivity.EXTRA_REDIRECT, redirect)
            )
        }
        finish()
    }
}

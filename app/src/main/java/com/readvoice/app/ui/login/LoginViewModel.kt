package com.readvoice.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readvoice.app.domain.AuthRepository
import com.readvoice.app.domain.model.AppError
import com.readvoice.app.domain.model.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Working : LoginUiState
    data class OpenBrowser(val url: String) : LoginUiState
    data class Failed(val error: AppError) : LoginUiState
    data object LoggedIn : LoginUiState
}

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state

    val isLoggedIn: Boolean
        get() = auth.isLoggedIn

    fun startLogin() {
        _state.value = when (val result = auth.beginLogin()) {
            is AppResult.Success -> LoginUiState.OpenBrowser(result.value)
            is AppResult.Failure -> LoginUiState.Failed(result.error)
        }
    }

    /** Called once the browser tab is on screen, or failed to open. */
    fun browserHandled(opened: Boolean) {
        _state.value = if (opened) LoginUiState.Idle else LoginUiState.Failed(AppError.NO_BROWSER)
    }

    fun finishLogin(redirectUri: String) {
        _state.value = LoginUiState.Working
        viewModelScope.launch {
            _state.value = when (val result = auth.completeLogin(redirectUri)) {
                is AppResult.Success -> LoginUiState.LoggedIn
                is AppResult.Failure -> LoginUiState.Failed(result.error)
            }
        }
    }

    companion object {
        fun factory(auth: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(auth) }
        }
    }
}

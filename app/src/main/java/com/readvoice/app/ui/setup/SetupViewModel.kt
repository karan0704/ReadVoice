package com.readvoice.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readvoice.app.di.AppContainer
import com.readvoice.app.domain.AuthRepository
import com.readvoice.app.domain.ClientIdCheck
import com.readvoice.app.domain.ClientIdRules
import com.readvoice.app.domain.ClientIdSettings
import com.readvoice.app.domain.ClientIdVerifier
import com.readvoice.app.domain.LogoutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CheckStatus { NONE, CHECKING, VALID, INVALID, UNREACHABLE }

data class SetupUiState(
    val input: String = "",
    val isFormatValid: Boolean = false,
    val check: CheckStatus = CheckStatus.NONE,
    val isBusy: Boolean = false,
    val canRemove: Boolean = false,
    /** Set once the client ID changed; the screen then restarts the app's login flow. */
    val isDone: Boolean = false
)

/**
 * Setting up, changing or removing the client ID. A session belongs to the
 * client ID that issued it, so replacing or removing the ID first logs the
 * user out while the old ID is still in place to revoke the token.
 */
class SetupViewModel(
    private val settings: ClientIdSettings,
    private val verifier: ClientIdVerifier,
    private val auth: AuthRepository,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SetupUiState> = _state

    private fun initialState(): SetupUiState {
        val saved = settings.current()?.takeIf { settings.isSetByUser() }.orEmpty()
        return SetupUiState(
            input = saved,
            isFormatValid = ClientIdRules.isValid(saved),
            canRemove = settings.isSetByUser()
        )
    }

    fun onInputChanged(text: String) {
        val clientId = ClientIdRules.normalize(text)
        if (clientId == _state.value.input) return
        _state.update {
            it.copy(
                input = clientId,
                isFormatValid = ClientIdRules.isValid(clientId),
                check = CheckStatus.NONE
            )
        }
    }

    fun check() {
        val clientId = _state.value.input
        if (!_state.value.isFormatValid || _state.value.check == CheckStatus.CHECKING) return
        _state.update { it.copy(check = CheckStatus.CHECKING) }
        viewModelScope.launch {
            val status = when (verifier.verify(clientId)) {
                ClientIdCheck.VALID -> CheckStatus.VALID
                ClientIdCheck.INVALID -> CheckStatus.INVALID
                ClientIdCheck.UNREACHABLE -> CheckStatus.UNREACHABLE
            }
            // The answer is dropped if the text was edited while Reddit was being asked.
            _state.update { if (it.input == clientId) it.copy(check = status) else it }
        }
    }

    fun save() {
        val clientId = _state.value.input
        if (!_state.value.isFormatValid || _state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            if (clientId != settings.current() && auth.isLoggedIn) logoutUseCase.run()
            settings.save(clientId)
            _state.update { it.copy(isBusy = false, isDone = true) }
        }
    }

    fun remove() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            if (auth.isLoggedIn) logoutUseCase.run()
            settings.clear()
            _state.update { it.copy(isBusy = false, isDone = true) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SetupViewModel(
                    container.clientIdSettings,
                    container.clientIdVerifier,
                    container.authRepository,
                    container.logoutUseCase
                )
            }
        }
    }
}

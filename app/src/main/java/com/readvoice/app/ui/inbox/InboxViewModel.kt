package com.readvoice.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readvoice.app.di.AppContainer
import com.readvoice.app.domain.AccountRepository
import com.readvoice.app.domain.ConversationCache
import com.readvoice.app.domain.ConversationGrouper
import com.readvoice.app.domain.LogoutUseCase
import com.readvoice.app.domain.MessageRepository
import com.readvoice.app.domain.model.AppResult
import com.readvoice.app.domain.model.Conversation
import com.readvoice.app.domain.model.InboxItem
import com.readvoice.app.ui.common.ListUiState
import com.readvoice.app.ui.common.PagedLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class InboxTab { REPLIES, PRIVATE }

/**
 * Holds both tabs of the main screen. Private messages are loaded flat and
 * regrouped into conversations whenever a page arrives, so a later page can
 * add older messages to a conversation that is already on screen.
 */
class InboxViewModel(
    messages: MessageRepository,
    private val account: AccountRepository,
    private val logoutUseCase: LogoutUseCase,
    grouper: ConversationGrouper,
    cache: ConversationCache
) : ViewModel() {

    private val repliesLoader = PagedLoader(viewModelScope) { messages.loadInbox(it) }
    private val privateLoader = PagedLoader(viewModelScope) { messages.loadPrivateMessages(it) }

    val replies: StateFlow<ListUiState<InboxItem>> = repliesLoader.state

    val conversations: StateFlow<ListUiState<Conversation>> =
        privateLoader.state
            .map { state ->
                val grouped = grouper.group(state.items)
                cache.replaceAll(grouped)
                ListUiState(grouped, state.isLoading, state.error, state.canLoadMore)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ListUiState())

    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut

    init {
        refreshAll()
    }

    fun refreshAll() {
        repliesLoader.refresh()
        privateLoader.refresh()
        viewModelScope.launch {
            val result = account.username()
            if (result is AppResult.Success) _username.value = result.value
        }
    }

    fun refresh(tab: InboxTab) = loaderFor(tab).refresh()

    fun loadMore(tab: InboxTab) = loaderFor(tab).loadMore()

    fun logout() {
        viewModelScope.launch {
            logoutUseCase.run()
            _loggedOut.value = true
        }
    }

    private fun loaderFor(tab: InboxTab): PagedLoader<*> = when (tab) {
        InboxTab.REPLIES -> repliesLoader
        InboxTab.PRIVATE -> privateLoader
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InboxViewModel(
                    container.messageRepository,
                    container.accountRepository,
                    container.logoutUseCase,
                    container.conversationGrouper,
                    container.conversationCache
                )
            }
        }
    }
}

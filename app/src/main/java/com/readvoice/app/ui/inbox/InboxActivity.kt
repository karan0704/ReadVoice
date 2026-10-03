package com.readvoice.app.ui.inbox

import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.readvoice.app.R
import com.readvoice.app.databinding.ActivityInboxBinding
import com.readvoice.app.domain.model.AppError
import com.readvoice.app.domain.model.Conversation
import com.readvoice.app.ui.chat.ChatActivity
import com.readvoice.app.ui.common.BaseActivity
import com.readvoice.app.ui.common.ListUiState
import com.readvoice.app.ui.common.messageRes
import com.readvoice.app.ui.common.setBold
import com.readvoice.app.ui.login.LoginActivity
import com.readvoice.app.ui.settings.AppearanceActivity
import com.readvoice.app.ui.setup.SetupActivity
import kotlinx.coroutines.launch

/**
 * Main screen with the Inbox (replies and mentions) and Private
 * (conversations) tabs. Both tabs share one list view and swap its adapter.
 */
class InboxActivity : BaseActivity() {

    private lateinit var binding: ActivityInboxBinding
    private val viewModel: InboxViewModel by viewModels { InboxViewModel.factory(container) }

    private val inboxAdapter = InboxAdapter()
    private val conversationAdapter = ConversationAdapter(::openChat)
    private lateinit var layoutManager: LinearLayoutManager
    private var tab = InboxTab.REPLIES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInboxBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tab = savedInstanceState?.getString(STATE_TAB)?.let(InboxTab::valueOf) ?: InboxTab.REPLIES

        setUpList()
        binding.tabInbox.setOnClickListener { selectTab(InboxTab.REPLIES) }
        binding.tabPrivate.setOnClickListener { selectTab(InboxTab.PRIVATE) }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, AppearanceActivity::class.java))
        }
        binding.menuButton.setOnClickListener { showMenu() }
        selectTab(tab)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.replies.collect { inboxAdapter.submitList(it.items); render() } }
                launch {
                    viewModel.conversations.collect {
                        conversationAdapter.submitList(it.items)
                        render()
                    }
                }
                launch {
                    viewModel.username.collect { name ->
                        binding.usernameText.isVisible = name != null
                        binding.usernameText.text = name?.let { "u/$it" }
                    }
                }
                launch { viewModel.loggedOut.collect { if (it) openLogin() } }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, tab.name)
    }

    private fun setUpList() {
        layoutManager = LinearLayoutManager(this)
        binding.list.layoutManager = layoutManager
        binding.list.setHasFixedSize(true)
        binding.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val total = layoutManager.itemCount
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= total - LOAD_MORE_THRESHOLD) {
                    viewModel.loadMore(tab)
                }
            }
        })
        binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.text_primary))
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.bubble_incoming)
        )
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh(tab) }
    }

    private fun selectTab(selected: InboxTab) {
        tab = selected
        binding.tabInbox.isSelected = selected == InboxTab.REPLIES
        binding.tabPrivate.isSelected = selected == InboxTab.PRIVATE
        binding.tabInbox.setBold(selected == InboxTab.REPLIES)
        binding.tabPrivate.setBold(selected == InboxTab.PRIVATE)
        binding.list.adapter = when (selected) {
            InboxTab.REPLIES -> inboxAdapter
            InboxTab.PRIVATE -> conversationAdapter
        }
        render()
    }

    private fun render() {
        val replies = viewModel.replies.value
        val conversations = viewModel.conversations.value
        binding.tabInbox.text =
            tabLabel(R.string.tab_inbox, replies.items.count { it.isUnread })
        binding.tabPrivate.text =
            tabLabel(R.string.tab_private, conversations.items.count { it.hasUnread })

        val current: ListUiState<*> = if (tab == InboxTab.REPLIES) replies else conversations
        if (replies.error == AppError.UNAUTHORIZED || conversations.error == AppError.UNAUTHORIZED) {
            openLogin()
            return
        }
        binding.swipeRefresh.isRefreshing = current.isLoading
        val message = when {
            current.error != null -> getString(current.error.messageRes())
            current.items.isEmpty() && !current.isLoading -> getString(
                if (tab == InboxTab.REPLIES) R.string.empty_inbox else R.string.empty_private
            )
            else -> null
        }
        // An error over a list that already has rows would hide them, so it shows only when empty.
        binding.messageText.isVisible = message != null && current.items.isEmpty()
        binding.messageText.text = message
    }

    private fun tabLabel(nameRes: Int, unread: Int): String {
        val name = getString(nameRes)
        return if (unread > 0) getString(R.string.tab_with_count, name, unread) else name
    }

    private fun showMenu() {
        PopupMenu(this, binding.menuButton).apply {
            inflate(R.menu.menu_inbox)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menuRefresh -> viewModel.refreshAll()
                    R.id.menuClientId -> startActivity(
                        Intent(this@InboxActivity, SetupActivity::class.java)
                    )
                    R.id.menuLogout -> viewModel.logout()
                }
                true
            }
        }.show()
    }

    private fun openChat(conversation: Conversation) {
        startActivity(ChatActivity.intent(this, conversation.key))
    }

    private fun openLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private companion object {
        const val STATE_TAB = "tab"
        const val LOAD_MORE_THRESHOLD = 5
    }
}

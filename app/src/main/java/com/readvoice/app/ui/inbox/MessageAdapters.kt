package com.readvoice.app.ui.inbox

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.readvoice.app.R
import com.readvoice.app.databinding.ItemMessageBinding
import com.readvoice.app.domain.model.Conversation
import com.readvoice.app.domain.model.InboxItem
import com.readvoice.app.domain.model.InboxItemType
import com.readvoice.app.ui.common.relativeTimeLabel
import com.readvoice.app.ui.common.setBold

class MessageRowHolder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {

    /** Unread rows get the dot and bold text, the same cue Reddit uses. */
    fun showUnread(unread: Boolean) {
        binding.unreadDot.isVisible = unread
        binding.authorText.setBold(unread)
        binding.bodyText.setBold(unread)
    }

    companion object {
        fun create(parent: ViewGroup): MessageRowHolder =
            MessageRowHolder(
                ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
    }
}

/** Replies and mentions. Message text is shown as plain text, never rendered as HTML. */
class InboxAdapter : ListAdapter<InboxItem, MessageRowHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MessageRowHolder.create(parent)

    override fun onBindViewHolder(holder: MessageRowHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val type = context.getString(labelFor(item.type))
        with(holder.binding) {
            authorText.text = item.author
            timeText.text = context.relativeTimeLabel(item.createdAtMillis)
            labelText.isVisible = true
            labelText.text = item.subreddit
                ?.let { context.getString(R.string.type_with_subreddit, type, it) }
                ?: type
            bodyText.maxLines = 4
            bodyText.text = item.body
        }
        holder.showUnread(item.isUnread)
        holder.itemView.setOnClickListener(null)
        holder.itemView.isClickable = false
    }

    private fun labelFor(type: InboxItemType): Int = when (type) {
        InboxItemType.COMMENT_REPLY -> R.string.type_comment_reply
        InboxItemType.POST_REPLY -> R.string.type_post_reply
        InboxItemType.MENTION -> R.string.type_mention
        InboxItemType.OTHER -> R.string.type_other
    }

    private object Diff : DiffUtil.ItemCallback<InboxItem>() {
        override fun areItemsTheSame(old: InboxItem, new: InboxItem) = old.id == new.id
        override fun areContentsTheSame(old: InboxItem, new: InboxItem) = old == new
    }
}

/** One row per person, previewing the latest message of the conversation. */
class ConversationAdapter(
    private val onOpen: (Conversation) -> Unit
) : ListAdapter<Conversation, MessageRowHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MessageRowHolder.create(parent)

    override fun onBindViewHolder(holder: MessageRowHolder, position: Int) {
        val conversation = getItem(position)
        val context = holder.itemView.context
        val last = conversation.lastMessage
        with(holder.binding) {
            authorText.text = conversation.partner
            timeText.text = context.relativeTimeLabel(last.createdAtMillis)
            labelText.isVisible = false
            bodyText.maxLines = 2
            bodyText.text =
                if (last.isMine) context.getString(R.string.you_prefix, last.body) else last.body
        }
        holder.showUnread(conversation.hasUnread)
        holder.itemView.setOnClickListener { onOpen(conversation) }
    }

    private object Diff : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(old: Conversation, new: Conversation) = old.key == new.key
        override fun areContentsTheSame(old: Conversation, new: Conversation) = old == new
    }
}

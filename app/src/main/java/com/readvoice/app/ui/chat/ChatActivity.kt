package com.readvoice.app.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.readvoice.app.R
import com.readvoice.app.databinding.ActivityChatBinding
import com.readvoice.app.databinding.ItemChatIncomingBinding
import com.readvoice.app.databinding.ItemChatOutgoingBinding
import com.readvoice.app.databinding.ItemChatSubjectBinding
import com.readvoice.app.ui.common.BaseActivity
import java.text.DateFormat
import java.util.Date

/**
 * Read-only chat view of one conversation: the other person's messages on
 * the left, the user's own on the right. Only the conversation key travels
 * in the intent; the messages themselves stay in memory.
 */
class ChatActivity : BaseActivity() {

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.factory(
            container.conversationCache,
            intent.getStringExtra(EXTRA_KEY).orEmpty()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val conversation = viewModel.conversation
        if (conversation == null) {
            finish()
            return
        }
        val binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.titleText.text = conversation.partner
        binding.backButton.setOnClickListener { finish() }
        binding.list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.list.adapter = ChatAdapter(viewModel.rows)
    }

    companion object {
        private const val EXTRA_KEY = "conversation_key"

        fun intent(context: Context, conversationKey: String): Intent =
            Intent(context, ChatActivity::class.java).putExtra(EXTRA_KEY, conversationKey)
    }
}

private class ChatAdapter(private val rows: List<ChatRow>) :
    RecyclerView.Adapter<ChatAdapter.Holder>() {

    private val timeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    /** [time] is absent on subject rows. */
    class Holder(root: View, val body: TextView, val time: TextView?) :
        RecyclerView.ViewHolder(root)

    override fun getItemViewType(position: Int): Int = when (val row = rows[position]) {
        is ChatRow.Subject -> TYPE_SUBJECT
        is ChatRow.Bubble -> if (row.message.isMine) TYPE_OUTGOING else TYPE_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SUBJECT -> ItemChatSubjectBinding.inflate(inflater, parent, false)
                .let { Holder(it.root, it.subjectText, null) }
            TYPE_OUTGOING -> ItemChatOutgoingBinding.inflate(inflater, parent, false)
                .let { Holder(it.root, it.bodyText, it.timeText) }
            else -> ItemChatIncomingBinding.inflate(inflater, parent, false)
                .let { Holder(it.root, it.bodyText, it.timeText) }
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val context = holder.itemView.context
        when (val row = rows[position]) {
            is ChatRow.Subject -> holder.body.text = context.getString(
                R.string.subject_divider,
                row.text.ifBlank { context.getString(R.string.no_subject) }
            )
            is ChatRow.Bubble -> {
                holder.body.text = row.message.body
                holder.time?.text = timeFormat.format(Date(row.message.createdAtMillis))
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    private companion object {
        const val TYPE_SUBJECT = 0
        const val TYPE_INCOMING = 1
        const val TYPE_OUTGOING = 2
    }
}

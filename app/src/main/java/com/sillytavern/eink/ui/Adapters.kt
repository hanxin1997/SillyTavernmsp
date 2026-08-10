package com.sillytavern.eink.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sillytavern.eink.R
import com.sillytavern.eink.model.CharacterSummary
import com.sillytavern.eink.model.ChatSummary
import com.sillytavern.eink.model.MessageItem
import java.text.DateFormat
import java.util.Date

class CharacterAdapter(private val onClick: (CharacterSummary) -> Unit) : ListAdapter<CharacterSummary, CharacterAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_character, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.name)
        fun bind(item: CharacterSummary) {
            name.text = item.name
            itemView.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CharacterSummary>() {
        override fun areItemsTheSame(oldItem: CharacterSummary, newItem: CharacterSummary) = oldItem.avatarUrl == newItem.avatarUrl
        override fun areContentsTheSame(oldItem: CharacterSummary, newItem: CharacterSummary) = oldItem == newItem
    }
}

class ChatAdapter(private val onClick: (ChatSummary) -> Unit) : ListAdapter<ChatSummary, ChatAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(R.id.title)
        private val meta = view.findViewById<TextView>(R.id.meta)
        fun bind(item: ChatSummary) {
            title.text = item.fileId
            meta.text = "${item.messageCount.coerceAtLeast(0)} messages  ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.modifiedAt))}"
            itemView.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatSummary>() {
        override fun areItemsTheSame(oldItem: ChatSummary, newItem: ChatSummary) = oldItem.fileId == newItem.fileId
        override fun areContentsTheSame(oldItem: ChatSummary, newItem: ChatSummary) = oldItem == newItem
    }
}

class MessageAdapter(private val onLongClick: (MessageItem) -> Unit) : ListAdapter<MessageItem, MessageAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.name)
        private val message = view.findViewById<TextView>(R.id.message)
        fun bind(item: MessageItem) {
            name.text = item.name
            message.text = item.text
            itemView.setOnLongClickListener {
                if (item.rawIndex >= 0) onLongClick(item)
                item.rawIndex >= 0
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MessageItem>() {
        override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem) = oldItem.rawIndex == newItem.rawIndex
        override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem) = oldItem == newItem
    }
}

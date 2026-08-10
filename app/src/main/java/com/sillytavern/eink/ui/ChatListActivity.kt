package com.sillytavern.eink.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sillytavern.eink.EinkApplication
import com.sillytavern.eink.databinding.ActivityChatListBinding
import com.sillytavern.eink.model.ChatSummary
import com.sillytavern.eink.network.SillyTavernClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatListBinding
    private lateinit var client: SillyTavernClient
    private lateinit var avatar: String
    private lateinit var characterName: String
    private var worldName: String? = null
    private val adapter = ChatAdapter(::openChat)
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        avatar = intent.getStringExtra(EXTRA_AVATAR) ?: run { finish(); return }
        characterName = intent.getStringExtra(EXTRA_NAME) ?: avatar.removeSuffix(".png")
        worldName = intent.getStringExtra(EXTRA_WORLD)
        val profile = (application as EinkApplication).profileStore.load() ?: run { finish(); return }
        client = (application as EinkApplication).client(profile)
        binding.title.text = characterName
        binding.back.setOnClickListener { finish() }
        binding.newChat.setOnClickListener { createChat() }
        binding.chats.layoutManager = LinearLayoutManager(this)
        binding.chats.adapter = adapter
        binding.chats.itemAnimator = null
    }

    private fun loadChats() {
        loadJob?.cancel()
        binding.status.text = "Loading..."
        loadJob = lifecycleScope.launch {
            try {
                client.initialize()
                val chats = client.getChats(avatar)
                adapter.submitList(chats)
                binding.status.text = if (chats.isEmpty()) "No chats" else "${chats.size} chats"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                binding.status.text = error.message ?: "Unable to load chats"
            }
        }
    }

    private fun createChat() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
        val id = "${timestamp}_${UUID.randomUUID().toString().take(8)}"
        openChat(ChatSummary(id, "$id.jsonl", null, System.currentTimeMillis(), 0), true)
    }

    private fun openChat(chat: ChatSummary) = openChat(chat, false)

    private fun openChat(chat: ChatSummary, isNew: Boolean) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_AVATAR, avatar)
            putExtra(ChatActivity.EXTRA_CHARACTER_NAME, characterName)
            putExtra(ChatActivity.EXTRA_FILE_ID, chat.fileId)
            putExtra(ChatActivity.EXTRA_NEW_CHAT, isNew)
            putExtra(ChatActivity.EXTRA_WORLD, worldName)
        })
    }

    override fun onResume() {
        super.onResume()
        if (::client.isInitialized) loadChats()
    }

    companion object {
        const val EXTRA_AVATAR = "avatar"
        const val EXTRA_NAME = "character_name"
        const val EXTRA_WORLD = "world_name"
    }
}

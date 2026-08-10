package com.sillytavern.eink.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sillytavern.eink.EinkApplication
import com.sillytavern.eink.data.SillyTavernRepository
import com.sillytavern.eink.databinding.ActivityChatBinding
import com.sillytavern.eink.eink.GenericEinkController
import com.sillytavern.eink.model.ChatSnapshot
import com.sillytavern.eink.model.GenerationEvent
import com.sillytavern.eink.model.MessageItem
import com.sillytavern.eink.network.ApiException
import com.sillytavern.eink.network.SillyTavernClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Date

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var client: SillyTavernClient
    private lateinit var repository: SillyTavernRepository
    private lateinit var avatar: String
    private lateinit var characterName: String
    private lateinit var fileId: String
    private var worldName: String? = null
    private var isNewChat = false
    private var snapshot = ChatSnapshot(JSONArray(), null)
    private val adapter = MessageAdapter(::showMessageActions)
    private val einkController = GenericEinkController()
    private var generationJob: Job? = null
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        avatar = intent.getStringExtra(EXTRA_AVATAR) ?: run { finish(); return }
        characterName = intent.getStringExtra(EXTRA_CHARACTER_NAME) ?: avatar.removeSuffix(".png")
        fileId = intent.getStringExtra(EXTRA_FILE_ID) ?: run { finish(); return }
        worldName = intent.getStringExtra(EXTRA_WORLD)
        isNewChat = intent.getBooleanExtra(EXTRA_NEW_CHAT, false)
        val app = application as EinkApplication
        val profile = app.profileStore.load() ?: run { finish(); return }
        client = app.client(profile)
        repository = SillyTavernRepository(client, app.database)

        binding.title.text = characterName
        binding.messages.layoutManager = LinearLayoutManager(this)
        binding.messages.adapter = adapter
        binding.messages.itemAnimator = null
        binding.back.setOnClickListener { finish() }
        binding.send.setOnClickListener { startGeneration("normal", binding.composer.text.toString()) }
        binding.stop.setOnClickListener { generationJob?.cancel() }
        binding.continueGeneration.setOnClickListener { startGeneration("continue", "") }
        binding.fullRefresh.setOnClickListener { einkController.fullRefresh(binding.root) }
        setLoaded(false)
        loadChat()
    }

    private fun loadChat() {
        setStatus("Loading...")
        lifecycleScope.launch {
            try {
                try {
                    client.initialize()
                } catch (error: ApiException) {
                    throw error
                } catch (_: IOException) {
                    // Existing chats may still be opened from Room while the server is unreachable.
                }
                snapshot = repository.loadChat(avatar, fileId, isNewChat)
                if (snapshot.chat.length() == 0) initializeNewChat()
                renderMessages()
                setLoaded(true)
                setStatus(if (snapshot.fromCache) "Offline cache" else "")
                scrollToBottom()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showError(error)
            }
        }
    }

    private suspend fun initializeNewChat() {
        val card = client.getCharacter(avatar)
        val data = card.optJSONObject("data") ?: JSONObject()
        snapshot.chat.put(JSONObject()
            .put("chat_metadata", JSONObject())
            .put("user_name", "unused")
            .put("character_name", "unused"))
        val firstMessage = data.optString("first_mes").trim()
        if (firstMessage.isNotEmpty()) snapshot.chat.put(assistantMessage(firstMessage, ""))
        worldName = worldName ?: data.optJSONObject("extensions")?.optString("world")?.takeIf(String::isNotBlank)
    }

    private fun startGeneration(type: String, draftValue: String) {
        if (generationJob?.isActive == true) return
        val draft = draftValue.trim()
        if (type == "normal" && draft.isEmpty()) return
        val baseChat = JSONArray(snapshot.chat.toString())
        setGenerating(true)
        generationJob = lifecycleScope.launch {
            val generated = StringBuilder()
            val reasoning = StringBuilder()
            var lastUiUpdate = 0L
            try {
                val messages = client.preparePrompt(
                    avatar = avatar,
                    fileId = fileId,
                    chat = baseChat,
                    draft = draft,
                    generationType = type,
                    worldNames = listOfNotNull(worldName),
                )
                renderPending(type, draft, "")
                client.streamCompletion(messages, type, characterName).collect { event ->
                    when (event) {
                        is GenerationEvent.Delta -> {
                            generated.append(event.text)
                            reasoning.append(event.reasoning)
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate >= STREAM_UI_INTERVAL_MS || event.text.endsWithSentence()) {
                                renderPending(type, draft, generated.toString())
                                einkController.contentChanged(binding.messages)
                                lastUiUpdate = now
                            }
                        }
                        GenerationEvent.Done -> Unit
                    }
                }
                if (generated.isEmpty()) throw IllegalStateException("The model returned an empty response.")
                setSaving()
                snapshot = commitGeneration(baseChat, type, draft, generated.toString(), reasoning.toString())
                binding.composer.text?.clear()
                renderMessages()
                scrollToBottom()
                setStatus("Saved")
                einkController.fullRefresh(binding.root)
            } catch (_: CancellationException) {
                if (!isFinishing && !isDestroyed) {
                    setStatus("Generation stopped. Partial text was not saved.")
                    renderMessages()
                }
            } catch (error: Throwable) {
                renderMessages()
                showError(error)
            } finally {
                if (!isDestroyed) setGenerating(false)
            }
        }
    }

    private suspend fun commitGeneration(base: JSONArray, type: String, draft: String, reply: String, reasoning: String): ChatSnapshot {
        when (type) {
            "normal" -> {
                base.put(userMessage(draft))
                base.put(assistantMessage(reply, reasoning))
            }
            "regenerate" -> {
                findLastAssistantIndex(base).takeIf { it >= 1 }?.let { base.remove(it) }
                base.put(assistantMessage(reply, reasoning))
            }
            "continue" -> {
                val index = findLastAssistantIndex(base)
                if (index >= 1) {
                    val previous = base.getJSONObject(index)
                    previous.put("mes", previous.optString("mes") + reply)
                    if (reasoning.isNotBlank()) previous.optJSONObject("extra")?.put("reasoning", reasoning)
                } else {
                    base.put(assistantMessage(reply, reasoning))
                }
            }
        }
        return repository.saveChat(avatar, fileId, base, snapshot.revision)
    }

    private fun renderPending(type: String, draft: String, generated: String) {
        val items = messageItems(snapshot.chat).toMutableList()
        if (type == "normal") items.add(MessageItem(-2, client.userName, draft, true, false))
        items.add(MessageItem(-1, characterName, generated.ifEmpty { "..." }, false, false))
        adapter.submitList(items) { scrollToBottom() }
    }

    private fun renderMessages() {
        adapter.submitList(messageItems(snapshot.chat))
    }

    private fun messageItems(chat: JSONArray): List<MessageItem> = buildList {
        for (index in 1 until chat.length()) {
            val value = chat.optJSONObject(index) ?: continue
            val text = value.optString("mes", value.optString("content"))
            if (text.isBlank()) continue
            val isUser = value.optBoolean("is_user")
            val isSystem = value.optBoolean("is_system")
            add(MessageItem(index, value.optString("name").ifBlank { if (isUser) client.userName else characterName }, text, isUser, isSystem))
        }
    }

    private fun showMessageActions(item: MessageItem) {
        val actions = mutableListOf("Copy", "Edit", "Delete")
        if (!item.isUser && item.rawIndex == findLastAssistantIndex(snapshot.chat)) actions.add("Regenerate")
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "Copy" -> copyMessage(item.text)
                    "Edit" -> editMessage(item)
                    "Delete" -> confirmDelete(item)
                    "Regenerate" -> startGeneration("regenerate", "")
                }
            }.show()
    }

    private fun copyMessage(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
        setStatus("Copied")
    }

    private fun editMessage(item: MessageItem) {
        val input = EditText(this).apply { setText(item.text); setSelection(text.length) }
        AlertDialog.Builder(this)
            .setTitle("Edit message")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val changed = JSONArray(snapshot.chat.toString())
                changed.getJSONObject(item.rawIndex).put("mes", input.text.toString())
                saveChangedChat(changed)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(item: MessageItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete message")
            .setMessage("Delete this message from the chat?")
            .setPositiveButton("Delete") { _, _ ->
                val changed = JSONArray(snapshot.chat.toString())
                changed.remove(item.rawIndex)
                saveChangedChat(changed)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveChangedChat(chat: JSONArray) {
        lifecycleScope.launch {
            try {
                snapshot = repository.saveChat(avatar, fileId, chat, snapshot.revision)
                renderMessages()
                setStatus("Saved")
                einkController.fullRefresh(binding.root)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showError(error)
            }
        }
    }

    private fun userMessage(text: String) = JSONObject()
        .put("name", client.userName)
        .put("is_user", true)
        .put("is_system", false)
        .put("send_date", Date().toString())
        .put("mes", text)
        .put("extra", JSONObject())

    private fun assistantMessage(text: String, reasoning: String) = JSONObject()
        .put("name", characterName)
        .put("is_user", false)
        .put("is_system", false)
        .put("send_date", Date().toString())
        .put("mes", text)
        .put("extra", JSONObject().apply { if (reasoning.isNotBlank()) put("reasoning", reasoning) })

    private fun findLastAssistantIndex(chat: JSONArray): Int {
        for (index in chat.length() - 1 downTo 1) {
            val item = chat.optJSONObject(index) ?: continue
            if (!item.optBoolean("is_user") && !item.optBoolean("is_system")) return index
        }
        return -1
    }

    private fun setGenerating(generating: Boolean) {
        binding.send.visibility = if (generating) View.GONE else View.VISIBLE
        binding.stop.visibility = if (generating) View.VISIBLE else View.GONE
        binding.send.isEnabled = loaded && !generating
        binding.continueGeneration.isEnabled = loaded && !generating
        binding.composer.isEnabled = loaded && !generating
        if (generating) setStatus("Generating...")
    }

    private fun setSaving() {
        binding.stop.visibility = View.GONE
        binding.send.visibility = View.GONE
        binding.continueGeneration.isEnabled = false
        binding.composer.isEnabled = false
        setStatus("Saving...")
    }

    private fun setLoaded(value: Boolean) {
        loaded = value
        binding.send.isEnabled = value
        binding.continueGeneration.isEnabled = value
        binding.composer.isEnabled = value
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) binding.messages.scrollToPosition(adapter.itemCount - 1)
    }

    private fun setStatus(value: String) {
        binding.status.text = value
    }

    private fun showError(error: Throwable) {
        setStatus(if (error is ApiException && error.status == 412) "Remote chat changed. Reopen the chat before saving." else error.message ?: "Operation failed")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !binding.composer.hasFocus()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_PAGE_DOWN -> { pageBy(1); return true }
                KeyEvent.KEYCODE_PAGE_UP -> { pageBy(-1); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun pageBy(direction: Int) {
        val overlap = (48 * resources.displayMetrics.density).toInt()
        binding.messages.scrollBy(0, direction * (binding.messages.height - overlap).coerceAtLeast(overlap))
        einkController.pageTurn(binding.messages)
    }

    override fun onDestroy() {
        generationJob?.cancel()
        einkController.dispose()
        super.onDestroy()
    }

    private fun String.endsWithSentence(): Boolean = endsWith('.') || endsWith('!') || endsWith('?') || endsWith('。') || endsWith('！') || endsWith('？') || endsWith('\n')

    companion object {
        const val EXTRA_AVATAR = "avatar"
        const val EXTRA_CHARACTER_NAME = "character_name"
        const val EXTRA_FILE_ID = "file_id"
        const val EXTRA_NEW_CHAT = "new_chat"
        const val EXTRA_WORLD = "world_name"
        private const val STREAM_UI_INTERVAL_MS = 750L
    }
}

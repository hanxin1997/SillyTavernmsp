package com.sillytavern.eink.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sillytavern.eink.EinkApplication
import com.sillytavern.eink.databinding.ActivityMainBinding
import com.sillytavern.eink.model.CharacterSummary
import com.sillytavern.eink.model.ServerProfile
import com.sillytavern.eink.network.NetworkPolicy
import com.sillytavern.eink.network.SillyTavernClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CharacterAdapter
    private var client: SillyTavernClient? = null

    private val applicationState get() = application as EinkApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        adapter = CharacterAdapter(::openCharacter)
        binding.characters.layoutManager = LinearLayoutManager(this)
        binding.characters.adapter = adapter
        binding.characters.itemAnimator = null
        applicationState.profileStore.load()?.let(::populate)
        binding.connect.setOnClickListener { connect() }
        binding.refresh.setOnClickListener { loadCharacters() }
    }

    private fun populate(profile: ServerProfile) = with(binding) {
        serverUrl.setText(profile.baseUrl)
        username.setText(profile.handle)
        source.setText(profile.source)
        model.setText(profile.model)
        allowLanHttp.isChecked = profile.allowPrivateLanHttp
    }

    private fun readProfile() = ServerProfile(
        baseUrl = binding.serverUrl.text.toString().trim().trimEnd('/'),
        handle = binding.username.text.toString().trim(),
        source = binding.source.text.toString().trim().ifBlank { "openai" },
        model = binding.model.text.toString().trim(),
        allowPrivateLanHttp = binding.allowLanHttp.isChecked,
    )

    private fun connect() {
        val profile = try {
            readProfile().also { NetworkPolicy.validate(it) }
        } catch (error: Exception) {
            showError(error)
            return
        }
        client = applicationState.client(profile)
        setBusy(true, "Connecting...")
        lifecycleScope.launch {
            try {
                val active = client ?: return@launch
                active.initialize(binding.password.text.toString())
                applicationState.profileStore.save(profile)
                binding.password.text?.clear()
                showCharacters(active, active.getCharacters())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun loadCharacters() {
        val active = client ?: applicationState.profileStore.load()?.let { applicationState.client(it).also { active -> client = active } } ?: return
        setBusy(true, "Loading characters...")
        lifecycleScope.launch {
            try {
                active.initialize()
                showCharacters(active, active.getCharacters())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun showCharacters(active: SillyTavernClient, characters: List<CharacterSummary>) {
        adapter.submitList(characters)
        binding.status.text = buildString {
            append("${characters.size} characters")
            active.compatibilityWarning?.let { warning -> append(". $warning") }
        }
    }

    private fun openCharacter(character: CharacterSummary) {
        startActivity(Intent(this, ChatListActivity::class.java).apply {
            putExtra(ChatListActivity.EXTRA_AVATAR, character.avatarUrl)
            putExtra(ChatListActivity.EXTRA_NAME, character.name)
            putExtra(ChatListActivity.EXTRA_WORLD, character.worldName)
        })
    }

    private fun setBusy(busy: Boolean, text: String? = null) {
        binding.connect.isEnabled = !busy
        binding.refresh.isEnabled = !busy
        binding.characters.visibility = if (busy && adapter.itemCount == 0) View.INVISIBLE else View.VISIBLE
        if (text != null) binding.status.text = text
    }

    private fun showError(error: Throwable) {
        binding.status.text = error.message ?: "Connection failed"
    }
}

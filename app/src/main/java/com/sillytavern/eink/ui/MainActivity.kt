package com.sillytavern.eink.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sillytavern.eink.data.CredentialStore
import com.sillytavern.eink.databinding.ActivityMainBinding
import com.sillytavern.eink.model.StoredProfile
import com.sillytavern.eink.network.AuthenticationResult
import com.sillytavern.eink.network.NetworkPolicy
import com.sillytavern.eink.network.PrivateLanHttpApprovalRequired
import com.sillytavern.eink.network.SessionAuthenticationException
import com.sillytavern.eink.network.WebSessionAuthenticator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Small Chinese connection screen. All feature UI lives in the real web app. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var credentialStore: CredentialStore
    private val authenticator = WebSessionAuthenticator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        credentialStore = CredentialStore(this)
        binding.connect.setOnClickListener { connect(false) }

        val saved = credentialStore.loadProfile()
        val forceLogin = intent.getBooleanExtra(EXTRA_FORCE_LOGIN, false)
        if (!forceLogin && saved != null && (saved.handle.isBlank() || credentialStore.loadPassword() != null)) {
            openWeb()
        } else {
            saved?.let(::populate)
        }
    }

    private fun populate(profile: StoredProfile) = with(binding) {
        serverUrl.setText(profile.baseUrl)
        username.setText(profile.handle)
    }

    private fun readProfile(allowPrivateLanHttp: Boolean): StoredProfile {
        val baseUrl = binding.serverUrl.text.toString().trim()
        val existing = credentialStore.loadProfile()
        val previouslyApproved = existing?.allowPrivateLanHttp == true &&
            existing.baseUrl.trimEnd('/').equals(baseUrl.trimEnd('/'), ignoreCase = true)
        return StoredProfile(
            baseUrl = baseUrl,
            handle = binding.username.text.toString().trim(),
            allowPrivateLanHttp = allowPrivateLanHttp || previouslyApproved,
        )
    }

    private fun connect(allowPrivateLanHttp: Boolean) {
        val draft = try {
            val requested = readProfile(allowPrivateLanHttp)
            val origin = NetworkPolicy.normalizeBaseUrl(requested.baseUrl, requested.allowPrivateLanHttp)
            requested.copy(baseUrl = origin.value)
        } catch (approval: PrivateLanHttpApprovalRequired) {
            AlertDialog.Builder(this)
                .setTitle(R.string.private_http_title)
                .setMessage(R.string.private_http_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_connect) { _, _ -> connect(true) }
                .show()
            return
        } catch (error: Exception) {
            showError(error.message ?: getString(R.string.connection_failed))
            return
        }

        val password = binding.password.text?.toString().orEmpty()
        setBusy(true, getString(R.string.connecting))
        lifecycleScope.launch {
            try {
                when (authenticator.authenticate(draft, password)) {
                    AuthenticationResult.InvalidCredentials -> {
                        showError(getString(R.string.wrong_credentials))
                        return@launch
                    }
                    AuthenticationResult.BasicAuthRequired,
                    AuthenticationResult.Success,
                    AuthenticationResult.NoServerLoginRequired,
                    -> {
                        credentialStore.saveProfile(draft)
                        if (draft.handle.isNotBlank()) credentialStore.savePassword(password)
                        openWeb()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: SessionAuthenticationException) {
                showError(error.message ?: getString(R.string.connection_failed))
            } catch (error: Throwable) {
                showError(error.message ?: getString(R.string.connection_failed))
            } finally {
                setBusy(false)
            }
        }
    }

    private fun openWeb() {
        startActivity(Intent(this, WebAppActivity::class.java))
        finish()
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        binding.connect.isEnabled = !busy
        binding.serverUrl.isEnabled = !busy
        binding.username.isEnabled = !busy
        binding.password.isEnabled = !busy
        if (message != null) binding.status.text = message
        binding.status.visibility = if (busy || binding.status.text.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        binding.status.text = message
        binding.status.visibility = View.VISIBLE
        setBusy(false)
    }

    companion object {
        const val EXTRA_FORCE_LOGIN = "force_login"
    }
}

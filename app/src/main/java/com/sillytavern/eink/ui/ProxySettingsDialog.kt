package com.sillytavern.eink.ui

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sillytavern.eink.R
import com.sillytavern.eink.databinding.DialogProxySettingsBinding
import com.sillytavern.eink.model.ProxySettings
import com.sillytavern.eink.model.ProxyType
import com.sillytavern.eink.network.ProxyPolicy

/** Shared editor used before login and from the in-browser settings dialog. */
object ProxySettingsDialog {
    fun show(
        activity: AppCompatActivity,
        current: ProxySettings,
        onSave: (ProxySettings) -> Unit,
    ) {
        val binding = DialogProxySettingsBinding.inflate(activity.layoutInflater)
        val types = ProxyType.entries
        val labels = types.map { type ->
            when (type) {
                ProxyType.HTTP -> activity.getString(R.string.proxy_type_http)
                ProxyType.HTTPS -> activity.getString(R.string.proxy_type_https)
                ProxyType.SOCKS5 -> activity.getString(R.string.proxy_type_socks5)
            }
        }
        binding.proxyType.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
        binding.proxyType.setSelection(types.indexOf(current.type).coerceAtLeast(0))
        binding.proxyEnabled.isChecked = current.enabled
        binding.proxyHost.setText(current.host)
        binding.proxyPort.setText(current.port.toString())

        fun setEndpointEnabled(enabled: Boolean) {
            binding.proxyType.isEnabled = enabled
            binding.proxyHost.isEnabled = enabled
            binding.proxyPort.isEnabled = enabled
        }
        setEndpointEnabled(current.enabled)
        binding.proxyEnabled.setOnCheckedChangeListener { _, enabled -> setEndpointEnabled(enabled) }

        var previousType = current.type
        binding.proxyType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = types[position]
                val existingPort = binding.proxyPort.text.toString().toIntOrNull()
                // Preserve custom ports, but replace an untouched protocol default.
                if (existingPort == previousType.defaultPort) binding.proxyPort.setText(selected.defaultPort.toString())
                previousType = selected
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.proxy_settings)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedType = types[binding.proxyType.selectedItemPosition.coerceAtLeast(0)]
                val port = binding.proxyPort.text.toString().toIntOrNull() ?: selectedType.defaultPort
                val draft = ProxySettings(
                    enabled = binding.proxyEnabled.isChecked,
                    type = selectedType,
                    host = binding.proxyHost.text.toString(),
                    port = port,
                )
                runCatching { ProxyPolicy.normalize(draft) }
                    .onSuccess { normalized ->
                        binding.proxyHost.error = null
                        binding.proxyPort.error = null
                        onSave(normalized)
                        dialog.dismiss()
                    }
                    .onFailure { error ->
                        binding.proxyHost.error = error.message ?: activity.getString(R.string.proxy_invalid)
                    }
            }
        }
        dialog.show()
    }
}

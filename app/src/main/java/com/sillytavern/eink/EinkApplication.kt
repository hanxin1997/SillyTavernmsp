package com.sillytavern.eink

import android.app.Application
import com.sillytavern.eink.data.AppDatabase
import com.sillytavern.eink.data.ProfileStore
import com.sillytavern.eink.model.ServerProfile
import com.sillytavern.eink.network.SillyTavernClient

class EinkApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val profileStore: ProfileStore by lazy { ProfileStore(this) }
    private val clients = mutableMapOf<String, SillyTavernClient>()

    @Synchronized
    fun client(profile: ServerProfile): SillyTavernClient {
        val key = "${profile.baseUrl.trimEnd('/')}|${profile.handle}"
        val current = clients[key]
        if (current != null && current.profile == profile) return current
        return SillyTavernClient(this, profile).also { clients[key] = it }
    }
}

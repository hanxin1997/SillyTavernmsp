package com.sillytavern.eink.data

import com.sillytavern.eink.model.ChatSnapshot
import com.sillytavern.eink.network.ApiException
import com.sillytavern.eink.network.SillyTavernClient
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import java.io.IOException
import java.security.MessageDigest

class SillyTavernRepository(
    private val client: SillyTavernClient,
    private val database: AppDatabase,
) {
    private val serverId = MessageDigest.getInstance("SHA-256")
        .digest("${client.profile.baseUrl.trimEnd('/')}|${client.profile.handle}".toByteArray())
        .joinToString("") { "%02x".format(it) }

    suspend fun loadChat(avatar: String, fileId: String, allowNew: Boolean): ChatSnapshot {
        return try {
            val remote = client.getChat(avatar, fileId)
            cache(avatar, fileId, remote)
            remote
        } catch (error: ApiException) {
            if (error.status == 404 && allowNew) return ChatSnapshot(JSONArray(), null)
            throw error
        } catch (error: IOException) {
            val cached = database.cachedChatDao().get(serverId, avatar, fileId) ?: throw error
            ChatSnapshot(JSONArray(cached.json), cached.revision, fromCache = true)
        }
    }

    suspend fun saveChat(avatar: String, fileId: String, chat: JSONArray, revision: String?): ChatSnapshot {
        val nextRevision = client.saveChat(avatar, fileId, chat, revision)
        val result = ChatSnapshot(chat, nextRevision)
        try {
            cache(avatar, fileId, result)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The server revision is authoritative; a local cache failure must not turn a successful save into a retry.
        }
        return result
    }

    private suspend fun cache(avatar: String, fileId: String, snapshot: ChatSnapshot) {
        database.cachedChatDao().put(CachedChatEntity(serverId, avatar, fileId, snapshot.revision, snapshot.chat.toString(), System.currentTimeMillis()))
    }
}

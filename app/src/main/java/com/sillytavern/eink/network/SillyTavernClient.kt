package com.sillytavern.eink.network

import android.content.Context
import com.sillytavern.eink.model.CharacterSummary
import com.sillytavern.eink.model.ChatSnapshot
import com.sillytavern.eink.model.ChatSummary
import com.sillytavern.eink.model.GenerationEvent
import com.sillytavern.eink.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String, val responseBody: String = "") : IOException(message)

class SillyTavernClient(context: Context, val profile: ServerProfile) {
    private val cookieJar = PersistentCookieJar(context, "${profile.baseUrl.trimEnd('/')}|${profile.handle}")
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(false)
        .followSslRedirects(false)
        .apply { if (profile.baseUrl.startsWith("http://", ignoreCase = true)) dns(PrivateLanDns()) }
        .build()
    private var csrfToken: String? = null
    private val sessionMutex = Mutex()
    var userName: String = profile.handle.ifBlank { "User" }
        private set
    private var defaultPersonaDescription: String = ""
    private var defaultPersonaPosition: Any = "in_prompt"
    private var contextTokens: Int = profile.contextTokens
    private var responseTokens: Int = profile.responseTokens
    var compatibilityWarning: String? = null
        private set

    init {
        NetworkPolicy.validate(profile)
    }

    suspend fun initialize(password: String = ""): JSONObject = sessionMutex.withLock {
        requireOpenAiCompatibleStream(profile.source)
        withContext(Dispatchers.IO) {
            fetchCsrfToken()
            var bootstrap = try {
                getObject("/api/plugins/eink-companion/v1/bootstrap")
            } catch (error: ApiException) {
                if (error.status != 403 || profile.handle.isBlank()) throw@withContext error
                login(password)
                getObject("/api/plugins/eink-companion/v1/bootstrap")
            }
            val activeHandle = bootstrap.optJSONObject("account")?.optString("handle").orEmpty()
            if (profile.handle.isNotBlank() && activeHandle != profile.handle) {
                cookieJar.clear()
                fetchCsrfToken()
                login(password)
                bootstrap = getObject("/api/plugins/eink-companion/v1/bootstrap")
                val authenticatedHandle = bootstrap.optJSONObject("account")?.optString("handle").orEmpty()
                if (authenticatedHandle != profile.handle) throw ApiException(403, "The authenticated account does not match the selected profile.")
            }
            bootstrap.optJSONObject("defaults")?.let { defaults ->
                userName = defaults.optString("user_name").ifBlank { profile.handle.ifBlank { "User" } }
                defaultPersonaDescription = defaults.optString("persona_description")
                defaultPersonaPosition = defaults.opt("persona_position") ?: "in_prompt"
                contextTokens = defaults.optInt("context_tokens", profile.contextTokens).coerceAtLeast(256)
                responseTokens = defaults.optInt("response_tokens", profile.responseTokens).coerceAtLeast(1)
            }
            compatibilityWarning = bootstrap.optJSONObject("server")?.optString("warning")?.takeIf { it.isNotBlank() && it != "null" }
            bootstrap
        }
    }

    suspend fun getCharacters(): List<CharacterSummary> = withContext(Dispatchers.IO) {
        val array = getArray("/api/plugins/eink-companion/v1/characters")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(CharacterSummary(item.getString("avatar_url"), item.getString("name"), item.optJSONObject("data")?.optJSONObject("extensions")?.optString("world")?.takeIf(String::isNotBlank)))
            }
        }
    }

    suspend fun getCharacter(avatar: String): JSONObject = withContext(Dispatchers.IO) {
        getObject("/api/plugins/eink-companion/v1/characters/${encode(avatar)}")
    }

    suspend fun getChats(avatar: String): List<ChatSummary> = withContext(Dispatchers.IO) {
        val array = getArray("/api/plugins/eink-companion/v1/characters/${encode(avatar)}/chats")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(ChatSummary(item.getString("file_id"), item.getString("file_name"), item.optString("revision").takeIf(String::isNotBlank), item.optLong("modified_at"), item.optInt("message_count")))
            }
        }.sortedByDescending { it.modifiedAt }
    }

    suspend fun getChat(avatar: String, fileId: String): ChatSnapshot = withContext(Dispatchers.IO) {
        val response = execute(Request.Builder().url(url("/api/plugins/eink-companion/v1/chats/${encode(avatar)}/${encode(fileId)}")).get().build())
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw apiError(it.code, raw)
            val json = JSONObject(raw)
            ChatSnapshot(json.getJSONArray("chat"), it.header("ETag") ?: json.optString("revision").takeIf(String::isNotBlank))
        }
    }

    suspend fun saveChat(avatar: String, fileId: String, chat: JSONArray, revision: String?): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("chat", chat)
        val builder = jsonRequest("/api/plugins/eink-companion/v1/chats/${encode(avatar)}/${encode(fileId)}", "PUT", body).newBuilder()
        if (revision == null) builder.header("If-None-Match", "*") else builder.header("If-Match", revision)
        val request = builder.build()
        val response = execute(request)
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw apiError(it.code, raw)
            it.header("ETag") ?: JSONObject(raw).getString("revision")
        }
    }

    suspend fun preparePrompt(
        avatar: String,
        fileId: String,
        chat: JSONArray,
        draft: String,
        generationType: String,
        worldNames: List<String>,
        personaDescription: String? = null,
    ): JSONArray = withContext(Dispatchers.IO) {
        val worlds = JSONArray().apply { worldNames.forEach { put(it) } }
        val body = JSONObject()
            .put("avatar", avatar)
            .put("file_name", fileId)
            .put("chat", chat)
            .put("draft", draft)
            .put("generation_type", generationType)
            .put("user_name", userName)
            .put("persona_description", personaDescription ?: defaultPersonaDescription)
            .put("persona_position", defaultPersonaPosition)
            .put("world_names", worlds)
            .put("context_tokens", contextTokens)
            .put("response_tokens", responseTokens)
        postObject("/api/plugins/eink-companion/v1/prompts/prepare", body).getJSONArray("messages")
    }

    fun streamCompletion(messages: JSONArray, generationType: String, characterName: String): Flow<GenerationEvent> = callbackFlow {
        requireOpenAiCompatibleStream(profile.source)
        val body = JSONObject()
            .put("type", generationType)
            .put("messages", messages)
            .put("model", profile.model)
            .put("temperature", 0.8)
            .put("frequency_penalty", 0.0)
            .put("presence_penalty", 0.0)
            .put("top_p", 1.0)
            .put("max_tokens", responseTokens)
            .put("stream", true)
            .put("chat_completion_source", profile.source)
            .put("user_name", userName)
            .put("char_name", characterName)
            .put("include_reasoning", true)
        val request = jsonRequest("/api/backends/chat-completions/generate", "POST", body)
        val streamingClient = client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
        val call = streamingClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (!call.isCanceled()) close(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        close(apiError(it.code, it.body?.string().orEmpty()))
                        return
                    }
                    try {
                        val source = it.body?.source() ?: throw IOException("Generation returned an empty response.")
                        val reader = SseReader(source)
                        var reachedTerminal = false
                        while (!call.isCanceled()) {
                            val event = reader.next() ?: break
                            val parsed = parseOpenAiGenerationChunk(event.data)
                            parsed.delta?.let { delta ->
                                if (trySendBlocking(delta).isFailure) return
                            }
                            if (parsed.terminal) {
                                if (trySendBlocking(GenerationEvent.Done).isFailure) return
                                reachedTerminal = true
                                break
                            }
                        }
                        if (!reachedTerminal && !call.isCanceled()) {
                            throw IOException("Generation stream ended before a completion marker was received.")
                        }
                        close()
                    } catch (error: Throwable) {
                        if (!call.isCanceled()) close(error)
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }

    private suspend fun fetchCsrfToken() {
        csrfToken = getObject("/csrf-token").getString("token")
    }

    private suspend fun login(password: String) {
        val body = JSONObject().put("handle", profile.handle).put("password", password)
        postObject("/api/users/login", body)
    }

    private suspend fun getObject(path: String): JSONObject {
        val response = execute(Request.Builder().url(url(path)).get().build())
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw apiError(it.code, raw)
            return JSONObject(raw)
        }
    }

    private suspend fun getArray(path: String): JSONArray {
        val response = execute(Request.Builder().url(url(path)).get().build())
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw apiError(it.code, raw)
            return JSONArray(raw)
        }
    }

    private suspend fun postObject(path: String, body: JSONObject): JSONObject {
        val response = execute(jsonRequest(path, "POST", body))
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw apiError(it.code, raw)
            return JSONObject(raw)
        }
    }

    private fun jsonRequest(path: String, method: String, body: JSONObject): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val builder = Request.Builder().url(url(path)).method(method, body.toString().toRequestBody(mediaType))
        csrfToken?.let { builder.header("X-CSRF-Token", it) }
        return builder.build()
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(response))
                } else {
                    response.close()
                }
            }
        })
    }
    private fun url(path: String) = "${profile.baseUrl.trimEnd('/')}${if (path.startsWith('/')) path else "/$path"}"
    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun apiError(status: Int, body: String): ApiException = ApiException(status, "SillyTavern request failed with HTTP $status.", body)
}

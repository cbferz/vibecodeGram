package com.vibecodegram

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApiUser(val id: Long, val username: String, val displayName: String)
data class ApiConversation(val id: Long, val kind: String, val title: String, val lastMessage: String)
data class ApiMessage(val id: Long, val senderId: Long, val username: String, val body: String, val createdAt: String)
data class ApiPost(val id: Long, val authorId: Long, val username: String, val displayName: String, val body: String, val tag: String, val createdAt: String, val likes: Long, val liked: Boolean, val saved: Boolean)
data class AuthResult(val token: String, val user: ApiUser)

class ApiClient(private val prefs: SharedPreferences) {
    private val base = BuildConfig.API_BASE_URL.trimEnd('/')
    var token: String?
        get() = prefs.getString("auth_token", null)
        set(value) { prefs.edit().putString("auth_token", value).apply() }

    private suspend fun call(method: String, path: String, payload: JSONObject? = null, authenticated: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                if (authenticated) token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            try {
                if (payload != null) connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val message = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                    throw IllegalStateException(message.ifBlank { "HTTP $code" })
                }
                text
            } finally { connection.disconnect() }
        }

    private fun parseUser(json: JSONObject) = ApiUser(json.optLong("id"), json.optString("username"), json.optString("display_name"))
    suspend fun authenticate(username: String, password: String, displayName: String, register: Boolean): AuthResult {
        val body = JSONObject().put("username", username.trim().lowercase()).put("password", password)
        if (register) body.put("display_name", displayName.trim())
        val json = JSONObject(call("POST", if (register) "/v1/auth/register" else "/v1/auth/login", body, false))
        token = json.getString("token")
        return AuthResult(token!!, parseUser(json.getJSONObject("user")))
    }
    suspend fun me(): ApiUser = parseUser(JSONObject(call("GET", "/v1/me")).also { })
    suspend fun logout() {
        runCatching { call("POST", "/v1/auth/logout", JSONObject()) }
        token = null
    }
    suspend fun sessions(): JSONArray = JSONObject(call("GET", "/v1/sessions")).optJSONArray("sessions") ?: JSONArray()
    suspend fun revokeSession(id: String) { call("DELETE", "/v1/sessions/$id") }
    suspend fun posts(sort: String = "latest"): List<ApiPost> {
        val arr = JSONArray(call("GET", "/v1/posts?sort=$sort"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { p ->
            ApiPost(p.getLong("id"), p.getLong("author_id"), p.optString("username"), p.optString("display_name"),
                p.optString("body"), p.optString("tag"), p.optString("created_at"), p.optLong("likes"), p.optBoolean("liked"), p.optBoolean("saved"))
        } }
    }
    suspend fun createPost(body: String, tag: String) {
        call("POST", "/v1/posts", JSONObject().put("body", body).put("tag", tag))
    }
    suspend fun toggleLike(postId: Long): Pair<Boolean, Long> {
        val p = JSONObject(call("POST", "/v1/posts/$postId/like", JSONObject()))
        return p.optBoolean("liked") to p.optLong("likes")
    }
    suspend fun setSaved(postId: Long, saved: Boolean) {
        call(if (saved) "PUT" else "DELETE", "/v1/posts/$postId/save", if (saved) JSONObject() else null)
    }
    suspend fun hidePost(postId: Long) {
        call("POST", "/v1/posts/$postId/hide", JSONObject())
    }
    suspend fun unhidePost(postId: Long) {
        call("DELETE", "/v1/posts/$postId/hide")
    }
    suspend fun searchUsers(query: String): List<ApiUser> {
        val arr = JSONArray(call("GET", "/v1/users/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")))
        return (0 until arr.length()).map { i -> parseUser(arr.getJSONObject(i)) }
    }
    suspend fun createDirectConversation(userId: Long): Long {
        return JSONObject(call("POST", "/v1/conversations", JSONObject().put("kind", "direct").put("member_ids", JSONArray().put(userId)))).getLong("id")
    }
    suspend fun conversations(): List<ApiConversation> {
        val arr = JSONArray(call("GET", "/v1/conversations"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { ApiConversation(it.getLong("id"), it.optString("kind"), it.optString("title"), it.optString("last_message")) } }
    }
    suspend fun messages(id: Long): List<ApiMessage> {
        val arr = JSONArray(call("GET", "/v1/conversations/$id/messages"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { ApiMessage(it.getLong("id"), it.getLong("sender_id"), it.optString("username"), it.optString("body"), it.optString("created_at")) } }.reversed()
    }
    suspend fun sendMessage(id: Long, body: String) { call("POST", "/v1/conversations/$id/messages", JSONObject().put("body", body)) }
}

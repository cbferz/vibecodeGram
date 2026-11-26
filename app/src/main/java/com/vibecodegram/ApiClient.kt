package com.vibecodegram

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.io.DataOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApiUser(val id: Long, val username: String, val displayName: String)
data class ApiConversation(val id: Long, val kind: String, val title: String, val lastMessage: String)
data class ApiMessage(val id: Long, val senderId: Long, val username: String, val body: String, val createdAt: String)
data class ApiPost(
    val id: Long,
    val authorId: Long,
    val username: String,
    val displayName: String,
    val body: String,
    val tag: String,
    val createdAt: String,
    val likes: Long,
    val liked: Boolean,
    val saved: Boolean,
    val videoUrl: String = "",
    val mediaType: String = "",
    val mediaSize: Long = 0L,
    val contentKind: String = "post"
)
data class AuthResult(val token: String, val user: ApiUser)
data class ApiStory(val id: Long, val authorId: Long, val username: String, val displayName: String, val caption: String, val mediaType: String, val expiresAt: String, val viewed: Boolean, val mediaUrl: String)
data class ApiProfile(val id: Long, val username: String, val displayName: String, val bio: String, val avatarUrl: String, val coverUrl: String, val website: String, val location: String, val followers: Long, val following: Long, val posts: Long, val followed: Boolean, val isMe: Boolean)
data class ApiComment(val id: Long, val authorId: Long, val username: String, val displayName: String, val body: String, val createdAt: String)
data class ApiNotification(val id: Long, val actorId: Long, val username: String, val displayName: String, val kind: String, val read: Boolean, val createdAt: String)
data class ApiBlockedUser(val id: Long, val username: String, val displayName: String, val blockedAt: String)
data class ApiPostReport(val id: Long, val postId: Long, val reason: String, val details: String, val status: String, val createdAt: String, val updatedAt: String)

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
                p.optString("body"), p.optString("tag"), p.optString("created_at"), p.optLong("likes"), p.optBoolean("liked"), p.optBoolean("saved"),
                p.optString("video_url"), p.optString("media_type"), p.optLong("media_size"), p.optString("content_kind", "post"))
        } }
    }
    suspend fun reels(offset: Int = 0, limit: Int = 20): List<ApiPost> {
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, 30)
        val arr = JSONArray(call("GET", "/v1/reels?limit=$safeLimit&offset=$safeOffset"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { p ->
            ApiPost(p.getLong("id"), p.getLong("author_id"), p.optString("username"), p.optString("display_name"),
                p.optString("body"), p.optString("tag"), p.optString("created_at"), p.optLong("likes"),
                p.optBoolean("liked"), p.optBoolean("saved"), p.optString("video_url"),
                p.optString("media_type"), p.optLong("media_size"), p.optString("content_kind", "reel"))
        } }
    }
    suspend fun createPost(body: String, tag: String, videoUri: Uri? = null, context: Context? = null, isReel: Boolean = false) {
        if (videoUri == null) {
            require(!isReel) { "Для Reels нужно выбрать видео MP4" }
            call("POST", "/v1/posts", JSONObject().put("body", body).put("tag", tag))
            return
        }
        val appContext = context ?: throw IllegalArgumentException("Context is required for video upload")
        withContext(Dispatchers.IO) {
            val boundary = "vibecodegram-${UUID.randomUUID().toString().replace("-", "")}"
            val connection = (URL("$base${if (isReel) "/v1/reels/video" else "/v1/posts/video"}").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 120000
                doOutput = true
                setChunkedStreamingMode(64 * 1024)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            try {
                DataOutputStream(connection.outputStream).use { output ->
                    output.writeBytes("--$boundary\r\n")
                    output.writeBytes("Content-Disposition: form-data; name=\"body\"\r\n\r\n")
                    output.write(body.toByteArray(Charsets.UTF_8))
                    output.writeBytes("\r\n--$boundary\r\n")
                    output.writeBytes("Content-Disposition: form-data; name=\"tag\"\r\n\r\n")
                    output.write(tag.toByteArray(Charsets.UTF_8))
                    output.writeBytes("\r\n--$boundary\r\n")
                    output.writeBytes("Content-Disposition: form-data; name=\"video\"; filename=\"upload.mp4\"\r\n")
                    output.writeBytes("Content-Type: video/mp4\r\n\r\n")
                    val input = appContext.contentResolver.openInputStream(videoUri)
                        ?: throw IllegalStateException("Не удалось открыть выбранное видео")
                    input.use { it.copyTo(output, 64 * 1024) }
                    output.writeBytes("\r\n--$boundary--\r\n")
                    output.flush()
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val message = runCatching { JSONObject(response).optString("error") }.getOrDefault("")
                    throw IllegalStateException(message.ifBlank { "HTTP $code" })
                }
            } finally {
                connection.disconnect()
            }
        }
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

    suspend fun stories(): List<ApiStory> {
        val arr = JSONArray(call("GET", "/v1/stories"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { s ->
            ApiStory(s.getLong("id"), s.getLong("author_id"), s.optString("username"), s.optString("display_name"),
                s.optString("caption"), s.optString("media_type"), s.optString("expires_at"), s.optBoolean("viewed"), s.optString("media_url"))
        } }
    }
    suspend fun createStory(uri: Uri, caption: String, durationHours: Int, context: Context) {
        require(durationHours in listOf(5, 12, 24, 48)) { "Недопустимый срок истории" }
        withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val boundary = "vibecodegram-${UUID.randomUUID().toString().replace("-", "")}"
            val connection = (URL("$base/v1/stories").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 120000; doOutput = true
                setChunkedStreamingMode(64 * 1024)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            try {
                DataOutputStream(connection.outputStream).use { out ->
                    fun field(name: String, value: String) {
                        out.writeBytes("--$boundary\r\n")
                        out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                        out.write(value.toByteArray(Charsets.UTF_8)); out.writeBytes("\r\n")
                    }
                    field("caption", caption.take(500))
                    field("duration_hours", durationHours.toString())
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"media\"; filename=\"story-upload\"\r\n")
                    out.writeBytes("Content-Type: $mime\r\n\r\n")
                    val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Не удалось открыть файл")
                    input.use { it.copyTo(out, 64 * 1024) }
                    out.writeBytes("\r\n--$boundary--\r\n"); out.flush()
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val message = runCatching { JSONObject(response).optString("error") }.getOrDefault("")
                    throw IllegalStateException(message.ifBlank { "HTTP $code" })
                }
            } finally { connection.disconnect() }
        }
    }
    suspend fun viewStory(id: Long) { call("POST", "/v1/stories/$id/view", JSONObject()) }
    suspend fun deleteStory(id: Long) { call("DELETE", "/v1/stories/$id") }
    suspend fun profile(id: Long): ApiProfile {
        val p = JSONObject(call("GET", "/v1/users/$id"))
        return ApiProfile(p.getLong("id"),p.optString("username"),p.optString("display_name"),p.optString("bio"),
            p.optString("avatar_url"),p.optString("cover_url"),p.optString("website"),p.optString("location"),
            p.optLong("followers"),p.optLong("following"),p.optLong("posts"),p.optBoolean("followed"),p.optBoolean("is_me"))
    }
    suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String, coverUrl: String, website: String, location: String) {
        call("PUT", "/v1/me/profile", JSONObject().put("display_name",displayName).put("bio",bio)
            .put("avatar_url",avatarUrl).put("cover_url",coverUrl).put("website",website).put("location",location))
    }
    suspend fun followUser(id: Long, follow: Boolean) {
        call(if (follow) "POST" else "DELETE", "/v1/users/$id/follow", if (follow) JSONObject() else null)
    }
    suspend fun searchPosts(query: String): List<ApiPost> {
        val arr = JSONArray(call("GET", "/v1/posts/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { p ->
            ApiPost(p.getLong("id"),p.getLong("author_id"),p.optString("username"),p.optString("display_name"),
                p.optString("body"),p.optString("tag"),p.optString("created_at"),p.optLong("likes"),p.optBoolean("liked"),
                p.optBoolean("saved"),p.optString("video_url"),p.optString("media_type"),p.optLong("media_size"))
        } }
    }
    suspend fun recommendations(): List<ApiPost> {
        val arr = JSONArray(call("GET", "/v1/recommendations"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { p ->
            ApiPost(p.getLong("id"),p.getLong("author_id"),p.optString("username"),p.optString("display_name"),
                p.optString("body"),p.optString("tag"),p.optString("created_at"),p.optLong("likes"),p.optBoolean("liked"),
                p.optBoolean("saved"),p.optString("video_url"),p.optString("media_type"),p.optLong("media_size"))
        } }
    }
    suspend fun comments(postId: Long): List<ApiComment> {
        val arr = JSONArray(call("GET", "/v1/posts/$postId/comments"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { x ->
            ApiComment(x.getLong("id"),x.getLong("author_id"),x.optString("username"),x.optString("display_name"),x.optString("body"),x.optString("created_at"))
        } }
    }
    suspend fun addComment(postId: Long, body: String) {
        call("POST", "/v1/posts/$postId/comments", JSONObject().put("body",body))
    }
    suspend fun blockUser(userId: Long) {
        require(userId > 0) { "Некорректный пользователь" }
        call("POST", "/v1/users/$userId/block", JSONObject())
    }
    suspend fun unblockUser(userId: Long) {
        require(userId > 0) { "Некорректный пользователь" }
        call("DELETE", "/v1/users/$userId/block")
    }
    suspend fun blockedUsers(): List<ApiBlockedUser> {
        val arr = JSONArray(call("GET", "/v1/me/blocked"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { item ->
            ApiBlockedUser(item.getLong("id"), item.optString("username"),
                item.optString("display_name"), item.optString("blocked_at"))
        } }
    }
    suspend fun reportPost(postId: Long, reason: String, details: String = "") {
        require(postId > 0) { "Некорректная публикация" }
        call("POST", "/v1/posts/$postId/report",
            JSONObject().put("reason", reason).put("details", details))
    }
    suspend fun myReports(limit: Int = 50): List<ApiPostReport> {
        val safeLimit = limit.coerceIn(1, 100)
        val arr = JSONArray(call("GET", "/v1/me/reports?limit=$safeLimit"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { item ->
            ApiPostReport(item.getLong("id"), item.getLong("post_id"), item.optString("reason"),
                item.optString("details"), item.optString("status"),
                item.optString("created_at"), item.optString("updated_at"))
        } }
    }

    suspend fun notifications(): List<ApiNotification> {
        val arr = JSONArray(call("GET", "/v1/notifications"))
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { x ->
            ApiNotification(x.getLong("id"),x.getLong("actor_id"),x.optString("username"),x.optString("display_name"),x.optString("kind"),x.optBoolean("read"),x.optString("created_at"))
        } }
    }
    suspend fun markNotificationsRead() { call("POST", "/v1/notifications/read", JSONObject()) }
}

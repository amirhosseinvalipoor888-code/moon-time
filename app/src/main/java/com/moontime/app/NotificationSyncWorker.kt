package com.moontime.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

class NotificationSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val prefs = applicationContext.getSharedPreferences("moon_secure_session", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val base = "https://krmuwbxjsjrezwnhybxa.supabase.co"
    private val key = "sb_publishable_v-3Kw8OKJszThrMlQ1ojBQ_ixj1BbFk"

    override suspend fun doWork(): Result {
        var access = prefs.getString("access", null) ?: return Result.success()
        val uid = prefs.getString("uid", null) ?: return Result.success()
        val refresh = prefs.getString("refresh", null)
        return try {
            val pendingRaw = prefs.getString("moon_pending_messages", "[]") ?: "[]"
            val pending = JSONArray(pendingRaw)
            val remaining = JSONArray()
            for (i in 0 until pending.length()) {
                val item = pending.getJSONObject(i)
                val body = JSONObject()
                    .put("conversation_id", item.getString("conversation_id"))
                    .put("sender_id", uid)
                    .put("body", item.getString("body"))
                    .put("message_kind", "text")
                val sendRequest = Request.Builder()
                    .url(base + "/rest/v1/messages")
                    .header("apikey", key)
                    .header("Authorization", "Bearer " + access)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val sendResponse = client.newCall(sendRequest).execute()
                if (!sendResponse.isSuccessful) remaining.put(item)
                sendResponse.close()
            }
            prefs.edit().putString("moon_pending_messages", remaining.toString()).apply()

            // Deliver due scheduled messages when Android runs this periodic worker.
            val duePath = "/rest/v1/scheduled_messages?select=id,conversation_id,body&user_id=eq.$uid&sent_message_id=is.null&send_at=lte.${URLEncoder.encode(Instant.now().toString(), "UTF-8")}&order=send_at.asc&limit=30"
            val dueResponse = call(duePath, access)
            if (dueResponse.isSuccessful) {
                val dueRows = JSONArray(dueResponse.body?.string().orEmpty())
                dueResponse.close()
                for (i in 0 until dueRows.length()) {
                    val scheduled = dueRows.getJSONObject(i)
                    val messageBody = JSONObject()
                        .put("conversation_id", scheduled.getString("conversation_id"))
                        .put("sender_id", uid)
                        .put("body", scheduled.getString("body"))
                        .put("message_kind", "text")
                    val sendReq = Request.Builder().url(base + "/rest/v1/messages")
                        .header("apikey", key).header("Authorization", "Bearer " + access)
                        .header("Content-Type", "application/json").header("Prefer", "return=representation")
                        .post(messageBody.toString().toRequestBody("application/json".toMediaType())).build()
                    val sentResponse = client.newCall(sendReq).execute()
                    val sentRows = JSONArray(sentResponse.body?.string().orEmpty())
                    sentResponse.close()
                    if (sentRows.length() > 0) {
                        val sentId = sentRows.getJSONObject(0).optString("id")
                        val patchBody = JSONObject().put("sent_message_id", sentId).toString()
                        val patchReq = Request.Builder().url(base + "/rest/v1/scheduled_messages?id=eq.${scheduled.getString("id")}&user_id=eq.$uid")
                            .header("apikey", key).header("Authorization", "Bearer " + access)
                            .header("Content-Type", "application/json")
                            .method("PATCH", patchBody.toRequestBody("application/json".toMediaType())).build()
                        client.newCall(patchReq).execute().close()
                    }
                }
            } else dueResponse.close()

            var last = prefs.getString("last_notification_check", null)
                ?: Instant.now().minusSeconds(15 * 60).toString()
            var membersResponse = call("/rest/v1/conversation_members?select=conversation_id&user_id=eq.$uid", access)
            if (membersResponse.code == 401 && refresh != null) {
                access = refreshSession(refresh) ?: return Result.retry()
                membersResponse = call("/rest/v1/conversation_members?select=conversation_id&user_id=eq.$uid", access)
            }
            if (!membersResponse.isSuccessful) return if (membersResponse.code >= 500) Result.retry() else Result.success()
            val members = JSONArray(membersResponse.body?.string().orEmpty())
            membersResponse.close()
            val ids = (0 until members.length()).map { members.getJSONObject(it).optString("conversation_id") }.filter { it.isNotBlank() }.distinct()
            if (ids.isEmpty()) {
                prefs.edit().putString("last_notification_check", Instant.now().toString()).apply()
                return Result.success()
            }
            val encodedLast = URLEncoder.encode(last, "UTF-8")
            val filter = ids.joinToString(",")
            val path = "/rest/v1/messages?select=id,body,sender_id,created_at,conversation_id&conversation_id=in.($filter)&sender_id=neq.$uid&created_at=gt.$encodedLast&order=created_at.asc&limit=30"
            val response = call(path, access)
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return if (code >= 500 || code == 401) Result.retry() else Result.success()
            }
            val rows = JSONArray(response.body?.string().orEmpty())
            response.close()
            if (rows.length() > 0) {
                val soundOn = applicationContext.getSharedPreferences("moon_offline_assets", Context.MODE_PRIVATE).getBoolean("sound", true)
                val channelId = if (soundOn) "moon_messages_sound_on" else "moon_messages_sound_off"
                ensureChannel(soundOn, channelId)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val preview = row.optString("body").ifBlank { "رسانه جدید دریافت شد" }.take(100)
                    val intent = Intent(applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pending = PendingIntent.getActivity(applicationContext, row.optString("id").hashCode(), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification = NotificationCompat.Builder(applicationContext, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_email)
                        .setContentTitle("پیام جدید در Moon Time")
                        .setContentText(preview)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build()
                    try { NotificationManagerCompat.from(applicationContext).notify(row.optString("id").hashCode(), notification) }
                    catch (_: SecurityException) { }
                    val created = row.optString("created_at")
                    if (created.isNotBlank() && created > last) last = created
                }
            }
            prefs.edit().putString("last_notification_check", Instant.now().toString()).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun call(path: String, access: String) = client.newCall(
        Request.Builder().url(base + path).header("apikey", key).header("Authorization", "Bearer $access").get().build()
    ).execute()

    private fun refreshSession(refresh: String): String? {
        val body = JSONObject().put("refresh_token", refresh).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$base/auth/v1/token?grant_type=refresh_token")
            .header("apikey", key).post(body).build()
        val response = client.newCall(request).execute()
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) return null
        val obj = JSONObject(text)
        val token = obj.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        prefs.edit().putString("access", token).putString("refresh", obj.optString("refresh_token", refresh)).apply()
        return token
    }

    private fun ensureChannel(soundOn: Boolean, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "پیام‌های Moon Time", NotificationManager.IMPORTANCE_HIGH)
            if (soundOn) {
                val soundUri = Uri.parse("android.resource://" + applicationContext.packageName + "/" + R.raw.moon_notification)
                val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                channel.setSound(soundUri, audio)
            } else channel.setSound(null, null)
            manager.createNotificationChannel(channel)
        }
    }

}

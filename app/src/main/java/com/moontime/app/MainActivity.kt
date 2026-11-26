package com.moontime.app

import android.content.Context
import android.media.MediaPlayer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

private const val API_URL = "https://krmuwbxjsjrezwnhybxa.supabase.co"
private const val API_KEY = "sb_publishable_v-3Kw8OKJszThrMlQ1ojBQ_ixj1BbFk"
private var activeDarkTheme by mutableStateOf(false)
private var activeAccent by mutableStateOf(Color(0xFF3390EC))
private val Night: Color get() = if (activeDarkTheme) Color(0xFF17212B) else Color(0xFFFFFFFF)
private val Panel: Color get() = if (activeDarkTheme) Color(0xFF242F3D) else Color(0xFFFFFFFF)
private val Panel2: Color get() = if (activeDarkTheme) Color(0xFF1E2A36) else Color(0xFFF1F3F5)
private val Violet: Color get() = activeAccent
private val Cyan: Color get() = if (activeDarkTheme) Color(0xFF7DB7F0) else Color(0xFF5288C1)
private val White: Color get() = if (activeDarkTheme) Color(0xFFF4F7FA) else Color(0xFF202124)
private val Muted: Color get() = if (activeDarkTheme) Color(0xFF9AA9B8) else Color(0xFF707579)
private val http = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()

data class Room(val id:String,val title:String,val kind:String)
data class Msg(val id:String,val text:String,val mine:Boolean,val time:String,val kind:String="text",val path:String?=null,val name:String?=null,val mediaUrl:String?=null,val pending:Boolean=false)
data class MoonContact(val id:String,val name:String,val email:String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(255,255,255)
        window.navigationBarColor=android.graphics.Color.rgb(255,255,255)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("moon-message-notifications", ExistingPeriodicWorkPolicy.KEEP, work)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 900)
        }
        setContent { MoonNativeApp() }
    }
}

private class MoonApi(context:Context) {
    private val prefs=context.getSharedPreferences("moon_secure_session",Context.MODE_PRIVATE)
    var token:String? = prefs.getString("access",null); private set
    var refresh:String? = prefs.getString("refresh",null); private set
    var userId:String? = prefs.getString("uid",null); private set
    var email:String? = prefs.getString("email",null); private set
    var displayName:String? = prefs.getString("display_name",null); private set
    private fun save(o:JSONObject) {
        token=o.optString("access_token").takeIf{it.isNotBlank()}
        refresh=o.optString("refresh_token").takeIf{it.isNotBlank()}
        val u=o.optJSONObject("user")
        userId=u?.optString("id")?.takeIf{it.isNotBlank()}
        email=u?.optString("email")?.takeIf{it.isNotBlank()}
        displayName=u?.optJSONObject("user_metadata")?.optString("display_name")?.takeIf{it.isNotBlank()} ?: displayName
        prefs.edit().putString("access",token).putString("refresh",refresh).putString("uid",userId).putString("email",email).putString("display_name",displayName).apply()
    }
    fun logout(){token=null;refresh=null;userId=null;email=null;displayName=null;prefs.edit().clear().apply()}
    private suspend fun request(path:String,method:String="GET",body:String?=null,auth:Boolean=true,contentType:String="application/json",extra:Map<String,String> = emptyMap()):String = withContext(Dispatchers.IO) {
        suspend fun send():Response {
            val b=Request.Builder().url(API_URL+path).header("apikey",API_KEY).header("Accept","application/json")
            if(auth && token!=null)b.header("Authorization","Bearer "+token)
            else b.header("Authorization","Bearer "+API_KEY)
            extra.forEach{(k,v)->b.header(k,v)}
            if(method=="POST"||method=="PATCH"||method=="PUT"||method=="DELETE") b.method(method,body?.toRequestBody(contentType.toMediaType()) ?: ByteArray(0).toRequestBody(null))
            else b.get()
            return http.newCall(b.build()).execute()
        }
        var response=send()
        if(response.code==401 && auth && refresh!=null) {
            response.close()
            val refreshBody=JSONObject().put("refresh_token",refresh).toString().toRequestBody("application/json".toMediaType())
            val req=Request.Builder().url("$API_URL/auth/v1/token?grant_type=refresh_token").header("apikey",API_KEY).post(refreshBody).build()
            val refreshed=http.newCall(req).execute()
            val text=refreshed.body?.string().orEmpty()
            if(refreshed.isSuccessful){save(JSONObject(text));response=send()}else throw IllegalStateException("نشست منقضی شده؛ دوباره وارد شو.")
        }
        val text=response.body?.string().orEmpty()
        if(!response.isSuccessful) {
            val message=try{JSONObject(text).optString("message",JSONObject(text).optString("msg",JSONObject(text).optString("error_description",text)))}catch(_:Exception){text}
            throw IllegalStateException(message.ifBlank{"خطای سرور ${response.code}"})
        }
        text
    }
    suspend fun register(name:String,emailIn:String,password:String,invite:String):String {
        val valid=request("/rest/v1/rpc/check_moon_invite","POST",JSONObject().put("p_code",invite.uppercase()).toString(),true)
        if(valid.trim()!="true")throw IllegalStateException("کد معرف معتبر نیست.")
        displayName=name
        prefs.edit().putString("display_name",name).apply()
        val result=request("/auth/v1/signup","POST",JSONObject().put("email",emailIn.trim()).put("password",password).put("data",JSONObject().put("display_name",name)).put("email_redirect_to",API_URL+"/functions/v1/moon-time").toString(),false)
        val o=JSONObject(result)
        if(o.has("access_token"))save(o)
        return if(token==null) "VERIFY" else "OK"
    }
    suspend fun verify(emailIn:String,code:String) { save(JSONObject(request("/auth/v1/verify","POST",JSONObject().put("type","signup").put("email",emailIn).put("token",code).toString(),false))) }
    suspend fun login(emailIn:String,password:String) { save(JSONObject(request("/auth/v1/token?grant_type=password","POST",JSONObject().put("email",emailIn).put("password",password).toString(),false))) }
    suspend fun profile() { val id=userId?:return; val name=displayName?.takeIf{it.isNotBlank()} ?: (email?: "کاربر").substringBefore("@"); request("/rest/v1/profiles?on_conflict=id","POST",JSONObject().put("id",id).put("email",email).put("display_name",name).toString(),true,"application/json",mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")) }
    suspend fun readProfile():JSONObject { val id=userId?:error("ابتدا وارد حساب شو"); val a=JSONArray(request("/rest/v1/profiles?select=display_name,username,bio,avatar_url,email&id=eq.$id")); return if(a.length()>0)a.getJSONObject(0) else JSONObject() }
    suspend fun updateProfile(name:String,username:String,bio:String) {
        val id=userId?:error("ابتدا وارد حساب شو"); val n=name.trim(); val u=username.trim()
        if(n.length !in 2..32) error("نام نمایشی باید بین ۲ تا ۳۲ نویسه باشد.")
        if(u.isNotEmpty() && !Regex("^[A-Za-z0-9_]{3,24}$").matches(u)) error("نام کاربری باید ۳ تا ۲۴ حرف انگلیسی، عدد یا زیرخط باشد.")
        request("/rest/v1/profiles?id=eq.$id","PATCH",JSONObject().put("display_name",n).put("username",if(u.isBlank()) JSONObject.NULL else u).put("bio",bio.trim().take(160)).toString(),true,"application/json",mapOf("Prefer" to "return=minimal"))
        displayName=n; prefs.edit().putString("display_name",n).apply()
    }
    suspend fun loadPreferences():JSONObject { val id=userId?:return JSONObject(); val a=JSONArray(request("/rest/v1/user_settings?select=preferences&user_id=eq.$id")); return if(a.length()>0)a.getJSONObject(0).optJSONObject("preferences")?:JSONObject() else JSONObject() }
    suspend fun savePreferences(v:JSONObject) { val id=userId?:error("ابتدا وارد حساب شو"); request("/rest/v1/user_settings?on_conflict=user_id","POST",JSONObject().put("user_id",id).put("preferences",v).toString(),true,"application/json",mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")) }
    suspend fun changePassword(v:String) { if(v.length<8)error("رمز عبور باید حداقل ۸ نویسه باشد."); request("/auth/v1/user","PUT",JSONObject().put("password",v).toString(),true) }
    suspend fun changeEmail(v:String) { if(!android.util.Patterns.EMAIL_ADDRESS.matcher(v.trim()).matches())error("ایمیل را درست وارد کن."); request("/auth/v1/user","PUT",JSONObject().put("email",v.trim()).toString(),true) }
    suspend fun uploadAvatar(uri:Uri,context:Context):String = withContext(Dispatchers.IO) {
        val uid=userId?:error("ابتدا وارد حساب شو"); val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:error("خواندن تصویر ناموفق بود")
        if(bytes.size>3*1024*1024)error("حجم تصویر باید کمتر از ۳ مگابایت باشد.")
        val mime=context.contentResolver.getType(uri)?:"image/jpeg"; if(mime !in setOf("image/jpeg","image/png","image/webp"))error("فقط JPG، PNG یا WebP پشتیبانی می‌شود.")
        val ext=when(mime){"image/png"->"png";"image/webp"->"webp";else->"jpg"}; val path="$uid/avatar.$ext"
        val req=Request.Builder().url("$API_URL/storage/v1/object/moon-avatars/$path").header("apikey",API_KEY).header("Authorization","Bearer ${token?:error("ورود لازم است")}").header("Content-Type",mime).header("x-upsert","true").post(bytes.toRequestBody(mime.toMediaType())).build()
        val res=http.newCall(req).execute(); val body=res.body?.string().orEmpty(); if(!res.isSuccessful)error("بارگذاری تصویر ناموفق بود: $body")
        val url="$API_URL/storage/v1/object/public/moon-avatars/$path"
        request("/rest/v1/profiles?id=eq.$uid","PATCH",JSONObject().put("avatar_url",url).toString(),true,"application/json",mapOf("Prefer" to "return=minimal")); url
    }
    suspend fun signOutEverywhere() { request("/auth/v1/logout?scope=global","POST","{}",true); logout() }
    suspend fun rooms():List<Room> {
        val uid=userId?:return emptyList()
        val ms=JSONArray(request("/rest/v1/conversation_members?user_id=eq.$uid&select=conversation_id"))
        if(ms.length()==0)return emptyList()
        val ids=(0 until ms.length()).map{ms.getJSONObject(it).getString("conversation_id")}.distinct()
        val filter=ids.joinToString(",")
        val arr=JSONArray(request("/rest/v1/conversations?select=id,title,kind& id=in.("+filter+")&order=created_at.desc".replace(" ","")))
        return (0 until arr.length()).map{val o=arr.getJSONObject(it);Room(o.getString("id"),o.optString("title","گفتگو"),o.optString("kind","group"))}
    }
    suspend fun createRoom(title:String,kind:String):Room {
        val uid=userId?:error("ابتدا وارد حساب شو")
        val cleanTitle=title.trim()
        if(cleanTitle.length<2) error("نام گروه یا کانال باید حداقل ۲ نویسه باشد.")
        if(kind!="group" && kind!="channel") error("نوع گفتگو نامعتبر است.")
        val created=JSONArray(request("/rest/v1/rpc/create_moon_conversation","POST",
            JSONObject().put("p_title",cleanTitle.take(60)).put("p_kind",kind).toString(),true))
        if(created.length()==0) error("سرور گفتگو را نساخت؛ پاسخ خالی دریافت شد.")
        val row=created.getJSONObject(0)
        val id=row.getString("id")
        return Room(id,row.optString("title",cleanTitle),kind)
    }
    suspend fun lookupContact(emailTarget:String):MoonContact {
        val result=JSONArray(request("/rest/v1/rpc/lookup_profile_by_email","POST",JSONObject().put("p_email",emailTarget.trim()).toString()))
        if(result.length()==0) error("کاربری با این ایمیل پیدا نشد.")
        val row=result.getJSONObject(0)
        return MoonContact(row.getString("id"),row.optString("display_name","کاربر"),emailTarget.trim())
    }
    suspend fun direct(emailTarget:String):Room {
        val uid=userId?:error("ابتدا وارد حساب شو")
        val cleanEmail=emailTarget.trim().lowercase()
        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) error("ایمیل کاربر را درست وارد کن.")
        val result=JSONArray(request("/rest/v1/rpc/lookup_profile_by_email","POST",JSONObject().put("p_email",cleanEmail).toString(),true))
        if(result.length()==0) error("این ایمیل در Moon Time ثبت‌نام نکرده یا هنوز پروفایلش آماده نیست.")
        val target=result.getJSONObject(0)
        val targetId=target.getString("id")
        if(targetId==uid) error("نمی‌توانی با حساب خودت گفتگوی خصوصی بسازی.")
        val title=target.optString("display_name").ifBlank{"گفتگوی خصوصی"}
        val created=JSONArray(request("/rest/v1/rpc/create_moon_conversation","POST",
            JSONObject().put("p_title",title).put("p_kind","direct").toString(),true))
        if(created.length()==0) error("سرور گفتگوی خصوصی را نساخت؛ پاسخ خالی دریافت شد.")
        val room=created.getJSONObject(0)
        val id=room.getString("id")
        request("/rest/v1/conversation_members?on_conflict=conversation_id,user_id","POST",
            JSONObject().put("conversation_id",id).put("user_id",targetId).toString(),true,"application/json",
            mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal"))
        return Room(id,room.optString("title",title),"direct")
    }
    suspend fun addMember(room:Room,emailTarget:String) {
        val cleanEmail=emailTarget.trim().lowercase()
        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) error("ایمیل عضو را درست وارد کن.")
        val found=JSONArray(request("/rest/v1/rpc/lookup_profile_by_email","POST",JSONObject().put("p_email",cleanEmail).toString(),true))
        if(found.length()==0)error("کاربری با این ایمیل پیدا نشد.")
        val targetId=found.getJSONObject(0).getString("id")
        request("/rest/v1/conversation_members?on_conflict=conversation_id,user_id","POST",
            JSONObject().put("conversation_id",room.id).put("user_id",targetId).toString(),true,"application/json",
            mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal"))
    }
    fun queueText(room:Room, text:String):Msg {
        userId ?: error("ابتدا وارد شو")
        val items = try { JSONArray(prefs.getString("moon_pending_messages","[]") ?: "[]") } catch (_:Exception) { JSONArray() }
        val id = "local-" + UUID.randomUUID().toString()
        val now = java.time.Instant.now().toString()
        items.put(JSONObject().put("id",id).put("conversation_id",room.id).put("body",text).put("created_at",now))
        prefs.edit().putString("moon_pending_messages",items.toString()).apply()
        return Msg(id,text,true,now,pending=true)
    }
    fun pendingFor(roomId:String):List<Msg> {
        val items = try { JSONArray(prefs.getString("moon_pending_messages","[]") ?: "[]") } catch (_:Exception) { JSONArray() }
        return (0 until items.length()).map { items.getJSONObject(it) }.filter { it.optString("conversation_id")==roomId }.map { Msg(it.optString("id"),it.optString("body"),true,it.optString("created_at"),pending=true) }
    }
    suspend fun syncPending() {
        val items = try { JSONArray(prefs.getString("moon_pending_messages","[]") ?: "[]") } catch (_:Exception) { JSONArray() }
        val remaining = JSONArray()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            try { request("/rest/v1/messages","POST",JSONObject().put("conversation_id",item.getString("conversation_id")).put("sender_id",userId).put("body",item.getString("body")).put("message_kind","text").toString(),true,"application/json",mapOf("Prefer" to "return=minimal")) }
            catch (_:Exception) { remaining.put(item) }
        }
        prefs.edit().putString("moon_pending_messages",remaining.toString()).apply()
    }
    suspend fun messages(room:Room):List<Msg> {
        val uid=userId?:return emptyList()
        val a=JSONArray(request("/rest/v1/messages?select=id,body,sender_id,created_at,message_kind,attachment_path,attachment_name&conversation_id=eq.${room.id}&order=created_at.asc&limit=200"))
        return (0 until a.length()).map{val o=a.getJSONObject(it);Msg(o.getString("id"),o.optString("body"),o.optString("sender_id")==uid,o.optString("created_at"),o.optString("message_kind","text"),o.optString("attachment_path").takeIf{s->s.isNotBlank()},o.optString("attachment_name").takeIf{s->s.isNotBlank()})}
    }
    suspend fun markRead(messageIds:List<String>) {
        val uid=userId?:return
        if(messageIds.isEmpty())return
        val rows=JSONArray()
        messageIds.distinct().forEach { rows.put(JSONObject().put("message_id",it).put("user_id",uid)) }
        request("/rest/v1/message_read_receipts?on_conflict=message_id,user_id","POST",rows.toString(),true,"application/json",mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal"))
    }
    suspend fun send(room:Room,text:String,kind:String="text",path:String?=null,name:String?=null,mime:String?=null,size:Long?=null):Msg {
        val uid=userId?:error("ابتدا وارد شو")
        val rows=JSONArray(request("/rest/v1/messages","POST",JSONObject().put("conversation_id",room.id).put("sender_id",uid).put("body",text.ifBlank{if(kind=="image")"تصویر" else "رسانه"}).put("message_kind",kind).put("attachment_path",path).put("attachment_name",name).put("attachment_mime",mime).put("attachment_size",size).toString(),true,"application/json",mapOf("Prefer" to "return=representation")))
        if(rows.length()==0) error("سرور پیام را ذخیره نکرد.")
        val o=rows.getJSONObject(0)
        return Msg(o.getString("id"),o.optString("body"),true,o.optString("created_at"),kind,path,name)
    }
    suspend fun upload(room:Room,uri:Uri,context:Context):Triple<String,String,String> = withContext(Dispatchers.IO) {
        val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:error("خواندن فایل ناموفق بود")
        if(bytes.size>50*1024*1024)error("حجم فایل باید کمتر از ۵۰ مگابایت باشد.")
        val mime=context.contentResolver.getType(uri)?: "application/octet-stream"
        val name=uri.lastPathSegment?.substringAfterLast('/')?.take(90)?: "media"
        val path="${room.id}/${userId}/${UUID.randomUUID()}_${name.replace(Regex("[^A-Za-z0-9._-]"),"_")}"
        val req=Request.Builder().url("$API_URL/storage/v1/object/moon-media/$path").header("apikey",API_KEY).header("Authorization","Bearer ${token?:error("ورود لازم است")}").header("Content-Type",mime).post(bytes.toRequestBody(mime.toMediaType())).build()
        val response=http.newCall(req).execute();val body=response.body?.string().orEmpty()
        if(!response.isSuccessful)error("آپلود فایل ناموفق بود: $body")
        Triple(path,name,mime)
    }
    suspend fun signed(path:String):String {
        val body=request("/storage/v1/object/sign/moon-media/"+path.split("/").joinToString("/") { URLEncoder.encode(it,"UTF-8") },"POST",JSONObject().put("expiresIn",3600).toString())
        val signed=JSONObject(body).optString("signedURL")
        return if(signed.startsWith("http"))signed else API_URL+"/storage/v1"+signed
    }
}

private class Realtime(private val roomId:String,private val token:String,private val onMessage:(JSONObject)->Unit) {
    private val client=OkHttpClient.Builder().pingInterval(25,TimeUnit.SECONDS).build()
    private var socket:WebSocket?=null
    private var heartbeat:java.util.Timer?=null
    fun connect() {
        val req=Request.Builder().url(API_URL.replace("https://","wss://")+"/realtime/v1/websocket?apikey=$API_KEY&vsn=1.0.0").build()
        socket=client.newWebSocket(req,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response) {
                val config=JSONObject().put("broadcast",JSONObject().put("ack",false).put("self",false)).put("presence",JSONObject().put("key","")).put("postgres_changes",JSONArray().put(JSONObject().put("event","INSERT").put("schema","public").put("table","messages").put("filter","conversation_id=eq.$roomId")))
                val payload=JSONObject().put("config",config).put("access_token",token)
                webSocket.send(JSONObject().put("topic","realtime:room:$roomId").put("event","phx_join").put("payload",payload).put("ref","1").put("join_ref","1").toString())
                heartbeat=java.util.Timer("moon-realtime-heartbeat",true).also { timer -> timer.scheduleAtFixedRate(object:java.util.TimerTask(){override fun run(){webSocket.send(JSONObject().put("topic","phoenix").put("event","heartbeat").put("payload",JSONObject()).put("ref",System.nanoTime().toString()).toString())}},25000,25000) }
            }
            override fun onMessage(webSocket:WebSocket,text:String) {
                try { val root=JSONObject(text);val payload=root.optJSONObject("payload")?:return;val data=payload.optJSONObject("data")?:return;val record=data.optJSONObject("record")?:return;if(record.optString("conversation_id")==roomId)onMessage(record) }catch(_:Exception){}
            }
        })
    }
    fun close(){heartbeat?.cancel();heartbeat=null;socket?.close(1000,"switch room");socket=null;client.dispatcher.executorService.shutdown()}
}

@Composable
private fun MoonNativeApp() {
    val context = LocalContext.current
    val api = remember { MoonApi(context) }
    val scope = rememberCoroutineScope()
    var logged by remember { mutableStateOf(api.token != null && api.userId != null) }
    var email by remember { mutableStateOf(api.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var registerMode by remember { mutableStateOf(false) }
    var verifyMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var infoText by remember { mutableStateOf("") }
    var rooms by remember { mutableStateOf(emptyList<Room>()) }
    var current by remember { mutableStateOf<Room?>(null) }
    var showChat by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("چت‌ها") }
    var messages by remember { mutableStateOf(emptyList<Msg>()) }
    var input by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var dialogValue by remember { mutableStateOf("") }
    var realtime by remember { mutableStateOf<Realtime?>(null) }
    var online by remember { mutableStateOf(false) }
    val mediaLoader = remember(context) { ImageLoader.Builder(context).components { if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory()) }.build() }
    val assetPrefs = remember { context.getSharedPreferences("moon_offline_assets", Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(assetPrefs.getBoolean("dark_mode", false)) }
    var wallpaperChoice by remember { mutableStateOf(assetPrefs.getString("wallpaper", "default") ?: "default") }
    var fontChoice by remember { mutableStateOf(assetPrefs.getString("font", "sans") ?: "sans") }
    var fontScale by remember { mutableFloatStateOf(assetPrefs.getFloat("font_scale", 1f)) }
    var motionEnabled by remember { mutableStateOf(assetPrefs.getBoolean("motion", true)) }
    var emojiMotionEnabled by remember { mutableStateOf(assetPrefs.getBoolean("emoji_motion", true)) }
    var soundEnabled by remember { mutableStateOf(assetPrefs.getBoolean("sound", true)) }
    var accentChoice by remember { mutableStateOf(assetPrefs.getString("accent", "blue") ?: "blue") }
    fun saveAsset(key: String, value: Any) {
        val editor = assetPrefs.edit()
        when (value) { is Boolean -> editor.putBoolean(key, value); is String -> editor.putString(key, value); is Float -> editor.putFloat(key, value) }
        editor.apply()
    }
    val accentColor = when (accentChoice) { "purple" -> Color(0xFF8B70D6); "green" -> Color(0xFF269B76); "orange" -> Color(0xFFE58A36); else -> Violet }
    val chatWallpaper = when (wallpaperChoice) { "night" -> Color(0xFF17212B); "mint" -> Color(0xFFE5F4EA); "sand" -> Color(0xFFF5EBDD); "lavender" -> Color(0xFFECE8F8); else -> Color(0xFFE7EEF3) }
    var contactEmail by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf(api.displayName.orEmpty()) }
    var profileUsername by remember { mutableStateOf("") }
    var profileBio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf(api.email.orEmpty()) }
    var privacyPrefs by remember { mutableStateOf(JSONObject()) }
    val featurePrefs = remember { context.getSharedPreferences("moon_features", Context.MODE_PRIVATE) }
    var readReceipts by remember { mutableStateOf(featurePrefs.getBoolean("read_receipts", true)) }
    var onlineStatus by remember { mutableStateOf(featurePrefs.getBoolean("online_status", true)) }
    var profileSearchable by remember { mutableStateOf(featurePrefs.getBoolean("profile_searchable", true)) }
    var appLanguage by remember { mutableStateOf(featurePrefs.getString("language", "فارسی") ?: "فارسی") }
    var folderNames by remember { mutableStateOf((featurePrefs.getString("folders", "[]") ?: "[]").let { raw -> try { val a=JSONArray(raw); (0 until a.length()).map { a.getString(it) } } catch (_:Exception) { emptyList<String>() } }) }
    var newFolderName by remember { mutableStateOf("") }
    var roomFolders by remember { mutableStateOf(try { JSONObject(featurePrefs.getString("room_folders","{}") ?: "{}") } catch(_:Exception){JSONObject()} ) }
    var activeFolder by remember { mutableStateOf("") }
    var savedItems by remember { mutableStateOf((featurePrefs.getString("saved_items", "[]") ?: "[]").let { raw -> try { val a=JSONArray(raw); (0 until a.length()).map { a.getJSONObject(it).optString("text") } } catch (_:Exception) { emptyList<String>() } }) }
    var contacts by remember {
        val saved = context.getSharedPreferences("moon_contacts", Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
        val loaded: List<MoonContact> = try {
            val array = JSONArray(saved)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                MoonContact(item.getString("id"), item.getString("name"), item.getString("email"))
            }
        } catch (_: Exception) {
            emptyList<MoonContact>()
        }
        mutableStateOf<List<MoonContact>>(loaded)
    }

    fun connectRoom(room: Room) {
        scope.launch {
            try {
                val loadedMessages = api.messages(room)
                if(readReceipts) try { api.markRead(loadedMessages.filter{!it.mine}.map{it.id}) } catch(_:Exception) { }
                messages = (loadedMessages + api.pendingFor(room.id)).sortedBy { it.time }.map { m ->
                    if (m.path != null) try { m.copy(mediaUrl = api.signed(m.path)) } catch (_:Exception) { m } else m
                }
                realtime?.close()
                val access = api.token
                if (access != null) {
                    val connection = Realtime(room.id, access) { record ->
                        scope.launch {
                            val messageId = record.optString("id")
                            if (messages.none { it.id == messageId }) {
                                messages = messages + Msg(
                                    messageId,
                                    record.optString("body"),
                                    record.optString("sender_id") == api.userId,
                                    record.optString("created_at"),
                                    record.optString("message_kind", "text"),
                                    record.optString("attachment_path").takeIf { it.isNotBlank() },
                                    record.optString("attachment_name").takeIf { it.isNotBlank() }
                                )
                                if (record.optString("attachment_path").isNotBlank()) {
                                    try {
                                        val url = api.signed(record.optString("attachment_path"))
                                        messages = messages.map { if (it.id == messageId) it.copy(mediaUrl = url) else it }
                                    } catch (_:Exception) { }
                                }
                            }
                        }
                    }
                    realtime = connection
                    connection.connect()
                    online = true
                }
            } catch (e: Exception) {
                errorText = e.message ?: "اتصال به گفتگو ناموفق بود"
                online = false
            }
        }
    }

    fun refreshRooms() {
        scope.launch {
            busy = true
            try {
                try { api.syncPending() } catch (_:Exception) { }
                rooms = api.rooms()
                val selected = current?.let { old -> rooms.firstOrNull { it.id == old.id } }
                    ?: rooms.firstOrNull()
                current = selected
                if (selected != null) connectRoom(selected)
                else {
                    messages = emptyList()
                    realtime?.close()
                    realtime = null
                    online = false
                }
            } catch (e: Exception) {
                errorText = e.message ?: "بارگذاری گفتگوها ناموفق بود"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (logged) {
            try {
                api.profile()
                val p=api.readProfile()
                profileName=p.optString("display_name",api.displayName.orEmpty()); profileUsername=p.optString("username")
                profileBio=p.optString("bio"); avatarUrl=p.optString("avatar_url"); newEmail=api.email.orEmpty()
                privacyPrefs=api.loadPreferences()
                onlineStatus=privacyPrefs.optBoolean("online_status",onlineStatus)
                profileSearchable=privacyPrefs.optBoolean("profile_searchable",profileSearchable)
                readReceipts=privacyPrefs.optBoolean("read_receipts",readReceipts)
                featurePrefs.edit().putBoolean("online_status",onlineStatus).putBoolean("profile_searchable",profileSearchable).putBoolean("read_receipts",readReceipts).apply()
                refreshRooms()
            } catch (e: Exception) {
                errorText = e.message ?: "ورود دوباره لازم است"; api.logout(); logged = false
            }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) scope.launch { busy=true; try { avatarUrl=api.uploadAvatar(uri,context); infoText="عکس پروفایل به‌روزرسانی شد." } catch(e:Exception){errorText=e.message?:"بارگذاری تصویر ناموفق بود"} finally{busy=false} }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && current != null) {
            scope.launch {
                busy = true
                try {
                    val uploaded = api.upload(current!!, uri, context)
                    val kind = when (uploaded.third.substringBefore('/')) {
                        "image" -> "image"
                        "video" -> "video"
                        "audio" -> "audio"
                        else -> "file"
                    }
                    val sent = api.send(current!!, "", kind, uploaded.first, uploaded.second, uploaded.third)
                    messages = messages + sent
                    infoText = "رسانه ارسال شد."
                } catch (e: Exception) {
                    errorText = e.message ?: "ارسال رسانه ناموفق بود"
                } finally {
                    busy = false
                }
            }
        }
    }

    SideEffect {
        activeDarkTheme = darkMode
        activeAccent = accentColor
        (context as? android.app.Activity)?.window?.apply {
            statusBarColor = if (darkMode) android.graphics.Color.rgb(23,33,43) else android.graphics.Color.WHITE
            navigationBarColor = if (darkMode) android.graphics.Color.rgb(23,33,43) else android.graphics.Color.WHITE
            if (Build.VERSION.SDK_INT >= 26) {
                decorView.systemUiVisibility = if (darkMode) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }
    val selectedFont = when (fontChoice) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> FontFamily(Font(R.font.vazirmatn_regular, FontWeight.Normal))
    }
    MaterialTheme(
        colorScheme = if (darkMode) darkColorScheme(
            primary = Violet, secondary = Cyan, background = Night,
            surface = Panel, onPrimary = Color.White, onBackground = White, onSurface = White
        ) else lightColorScheme(
            primary = Violet, secondary = Cyan, background = Night,
            surface = Panel, onPrimary = Color.White, onBackground = White, onSurface = White
        )
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(density = LocalDensity.current.density, fontScale = fontScale),
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = selectedFont)
        ) {
        Box(
            Modifier.fillMaxSize()
                .background(Night)
        ) {
            if (!logged) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("☾", fontSize = 60.sp, color = Violet)
                    Text("MOON TIME", fontSize = 27.sp, color = White, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text("ارتباطی فراتر از مرزها", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(28.dp))

                    if (verifyMode) {
                        Text("تأیید ایمیل", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Field(otp, { otp = it }, "کد تأیید ایمیل")
                    } else {
                        if (registerMode) {
                            Field(displayName, { displayName = it }, "نام نمایشی")
                            Field(invite, { invite = it }, "کد معرف")
                        }
                        Field(email, { email = it }, "ایمیل")
                        Field(password, { password = it }, "رمز عبور", true)
                    }

                    if (errorText.isNotBlank()) Text(errorText, color = Color(0xFFFF9BAF), fontSize = 12.sp)
                    if (infoText.isNotBlank()) Text(infoText, color = Cyan, fontSize = 12.sp)

                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                errorText = ""
                                try {
                                    when {
                                        verifyMode -> {
                                            api.verify(email, otp)
                                            logged = true
                                            api.profile()
                                            refreshRooms()
                                        }
                                        registerMode -> {
                                            val result = api.register(displayName, email, password, invite)
                                            if (result == "VERIFY") {
                                                verifyMode = true
                                                infoText = "کد تأیید به ایمیلت ارسال شد."
                                            } else {
                                                logged = true
                                                api.profile()
                                                refreshRooms()
                                            }
                                        }
                                        else -> {
                                            api.login(email, password)
                                            logged = true
                                            api.profile()
                                            refreshRooms()
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorText = e.message ?: "عملیات ناموفق بود"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(
                            when {
                                busy -> "لطفاً صبر کن…"
                                verifyMode -> "تأیید و ورود"
                                registerMode -> "ساخت حساب"
                                else -> "ورود"
                            }
                        )
                    }
                    TextButton(onClick = {
                        registerMode = !registerMode
                        verifyMode = false
                        errorText = ""
                        infoText = ""
                    }) {
                        Text(if (registerMode) "حساب داری؟ ورود" else "حساب نداری؟ ثبت‌نام")
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().background(Night).statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().height(58.dp).background(Panel).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (showChat) showChat = false else dialog = "menu" }) {
                            Icon(if (showChat) Icons.Default.ArrowBack else Icons.Default.Menu, null, tint = Violet)
                        }
                        if (showChat && current != null) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF64A9D9)), contentAlignment = Alignment.Center) {
                                Text(current!!.title.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(current!!.title, color = Color(0xFF202124), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
                                Text(if (online) "آنلاین" else "آخرین بازدید", color = Color(0xFF707579), fontSize = 11.sp)
                            }
                            IconButton(onClick = { dialog = "profile" }) { Icon(Icons.Default.MoreVert, null, tint = Color(0xFF707579)) }
                        } else {
                            Text("Moon Time", color = Color(0xFF202124), fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { dialog = "direct" }) { Icon(Icons.Default.Edit, null, tint = Violet) }
                            IconButton(onClick = { dialog = "menu" }) { Icon(Icons.Default.MoreVert, null, tint = Color(0xFF707579)) }
                        }
                    }
                    if (!showChat) {
                        BasicTextField(
                            searchQuery, { searchQuery = it },
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(22.dp)).background(Color(0xFFF1F3F5)).padding(horizontal = 16.dp, vertical = 12.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF202124), fontSize = 14.sp),
                            decorationBox = { inner -> if (searchQuery.isEmpty()) Text("⌕  جستجو", color = Color(0xFF707579)); inner() }
                        )
                        val visibleRooms = rooms.filter { it.title.contains(searchQuery, ignoreCase = true) }
                            .filter { selectedTab != "گروه‌ها" || it.kind == "group" }
                            .filter { activeFolder.isBlank() || roomFolders.optString(it.id) == activeFolder }
                        if(folderNames.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                FilterChip(selected=activeFolder.isBlank(),onClick={activeFolder=""},label={Text("همه")})
                                folderNames.forEach { folder -> FilterChip(selected=activeFolder==folder,onClick={activeFolder=if(activeFolder==folder)"" else folder},label={Text(folder,maxLines=1)}) }
                            }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(visibleRooms, key = { it.id }) { room ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { current = room; showChat = true; connectRoom(room) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF64A9D9)), contentAlignment = Alignment.Center) {
                                        Text(room.title.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(room.title, color = Color(0xFF202124), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                                        Text(when(room.kind) { "group" -> "گروه"; "channel" -> "کانال"; else -> "گفتگوی خصوصی" }, color = Color(0xFF707579), fontSize = 12.sp)
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFF0F1F2), thickness = 0.7.dp)
                            }
                            if (visibleRooms.isEmpty()) item {
                                Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("☾", fontSize = 42.sp, color = Violet)
                                    Text(if (searchQuery.isBlank()) "هنوز گفتگویی نداری" else "گفتگویی پیدا نشد", color = Color(0xFF202124), fontWeight = FontWeight.Bold)
                                    Text("برای شروع، گفتگوی جدید بساز.", color = Color(0xFF707579), fontSize = 12.sp)
                                    Button(onClick = { dialog = "direct" }, modifier = Modifier.padding(top = 12.dp)) { Text("گفتگوی جدید") }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().background(Panel).navigationBarsPadding().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("چت‌ها" to Icons.Default.Chat, "گروه‌ها" to Icons.Default.Group, "تماس‌ها" to Icons.Default.Call, "تنظیمات" to Icons.Default.Settings).forEach { (label, icon) ->
                                Column(Modifier.weight(1f).clickable {
                                    selectedTab = label
                                    when(label) { "تماس‌ها" -> dialog = "calls"; "تنظیمات" -> dialog = "settings" }
                                }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, null, tint = if(selectedTab == label) Violet else Color(0xFF707579))
                                    Text(label, color = if(selectedTab == label) Violet else Color(0xFF707579), fontSize = 10.sp)
                                }
                            }
                        }
                    } else if (current != null) {
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth().background(if (darkMode && wallpaperChoice == "default") Panel2 else chatWallpaper),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(message, mediaLoader, emojiMotionEnabled && motionEnabled, onOpen = {
                                    if (message.path != null) scope.launch {
                                        try { val url = api.signed(message.path); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        catch (e: Exception) { errorText = e.message ?: "باز کردن رسانه ناموفق بود" }
                                    }
                                })
                            }
                        }
                        Row(Modifier.fillMaxWidth().background(chatWallpaper).navigationBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { picker.launch("*/*") }) { Icon(Icons.Default.AttachFile, null, tint = Color(0xFF707579)) }
                            BasicTextField(
                                input, { input = it },
                                Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Panel).padding(horizontal = 15.dp, vertical = 13.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF202124), fontSize = 14.sp),
                                decorationBox = { inner -> if (input.isEmpty()) Text("پیام...", color = Color(0xFF707579)); inner() }
                            )
                            IconButton(onClick = { dialog = "emoji" }) { Text("😊", fontSize = 22.sp) }
                            IconButton(onClick = {
                                val room = current; val value = input.trim()
                                if (room != null && value.isNotEmpty()) scope.launch {
                                    try { val sent = api.send(room, value); messages = messages + sent; input = "" }
                                    catch (e: Exception) {
                                        try { val queued = api.queueText(room, value); messages = messages + queued; input = ""; infoText = "پیام در صف ارسال قرار گرفت." }
                                        catch (_: Exception) { errorText = e.message ?: "ارسال ناموفق بود" }
                                    }
                                }
                            }) { Icon(Icons.Default.Send, null, tint = Violet) }
                        }
                    }
                    if (infoText.isNotBlank()) Text(infoText, color = Color(0xFF5288C1), fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                    if (errorText.isNotBlank()) Text(errorText, color = Color(0xFFD95365), fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                }
            }

            if (dialog.isNotBlank()) {
                val dialogTitle = when (dialog) {
                    "group" -> "ساخت گروه"
                    "channel" -> "ساخت کانال"
                    "direct" -> "گفتگوی خصوصی"
                    "new" -> "افزودن عضو"
                    "emoji" -> "ایموجی‌ها"
                    "contacts" -> "مخاطبین من"
                    "menu" -> "منوی اصلی"
                    "settings" -> "تنظیمات Moon Time"
                    "assets" -> "ظاهر و منابع آفلاین"
                    "notifications" -> "اعلان‌ها و صدا"
                    "offline" -> "راهنما و امکانات آفلاین"
                    else -> if (dialog.startsWith("feature:")) dialog.removePrefix("feature:") else "Moon Time"
                }
                AlertDialog(
                    onDismissRequest = { dialog = "" },
                    containerColor = Panel,
                    title = { Text(dialogTitle, color = White) },
                    text = {
                        if (dialog == "menu") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "گروه جدید" to "group",
                                    "پیام جدید" to "direct",
                                    "کانال جدید" to "channel",
                                    "مخاطبین" to "contacts",
                                    "تماس‌ها" to "feature:تماس‌ها",
                                    "پیام‌های ذخیره‌شده" to "feature:پیام‌های ذخیره‌شده",
                                    "پوشه‌های گفتگو" to "feature:پوشه‌ها",
                                    "حالت شب" to "toggle_dark",
                                    "تنظیمات" to "settings",
                                    "راهنما و پشتیبانی" to "offline",
                                    "درباره Moon Time" to "feature:درباره برنامه",
                                    "نسخه برنامه" to "feature:نسخه برنامه",
                                    "به‌روزرسانی گفتگوها" to "refresh"
                                ).forEach { (label, action) ->
                                    Text(label, color = Cyan, modifier = Modifier.fillMaxWidth().clickable {
                                        dialog = action
                                        if (action == "refresh") { dialog = ""; refreshRooms() }
                                    }.padding(vertical = 8.dp))
                                }
                            }
                        } else if (dialog == "settings") {
                            Column(
                                modifier = Modifier.heightIn(max = 480.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("حساب فعال: ${api.email ?: "—"}", color = White)
                                Text("شناسه حساب: ${api.userId ?: "—"}", color = Muted, fontSize = 11.sp)
                                Button(onClick = { dialog = ""; refreshRooms() }, modifier = Modifier.fillMaxWidth()) { Text("همگام‌سازی گفتگوها") }
                                listOf(
                                    "حساب کاربری", "تغییر رمز عبور", "تغییر ایمیل",
                                    "حریم خصوصی و امنیت", "دستگاه‌های فعال",
                                    "اعلان‌ها", "داده و حافظه", "ظاهر و پوسته",
                                    "زبان برنامه", "پوشه‌های گفتگو", "پرسش‌های متداول",
                                    "قوانین و حریم خصوصی", "گزارش مشکل و پیشنهاد"
                                ).forEach { item ->
                                    OutlinedButton(
                                        onClick = { dialog = "feature:$item" },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(item) }
                                }
                                Text("تنظیمات محلی روی همین دستگاه ذخیره می‌شوند. قابلیت‌های وابسته به سرور فقط در صورت پشتیبانی backend فعال‌اند.", color = Muted, fontSize = 11.sp)
                                Button(onClick = {
                                    dialog = ""
                                    api.logout()
                                    realtime?.close()
                                    realtime = null
                                    logged = false
                                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB83E55))) { Text("خروج از حساب") }
                            }
                        } else if (dialog == "assets") {
                            Column(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("این منابع و تنظیمات داخل برنامه هستند و روی همین گوشی ذخیره می‌شوند.", color = Muted, fontSize = 11.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("حالت شب", Modifier.weight(1f), color = White); Switch(checked = darkMode, onCheckedChange = { darkMode=it; saveAsset("dark_mode",it) }) }
                                Text("رنگ برنامه", color = White)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("blue" to "آبی","purple" to "بنفش","green" to "سبز","orange" to "نارنجی").forEach { (k,v) -> FilterChip(selected=accentChoice==k,onClick={accentChoice=k;saveAsset("accent",k)},label={Text(v)}) } }
                                Text("پس‌زمینه گفتگو", color = White)
                                listOf("default" to "پیش‌فرض","night" to "شب","mint" to "نعنایی","sand" to "شنی","lavender" to "یاسی").forEach { (k,v) ->
                                    Row(Modifier.fillMaxWidth().clickable { wallpaperChoice=k;saveAsset("wallpaper",k) }.padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Box(Modifier.size(20.dp).clip(CircleShape).background(when(k){"night"->Color(0xFF17212B);"mint"->Color(0xFFE5F4EA);"sand"->Color(0xFFF5EBDD);"lavender"->Color(0xFFECE8F8);else->Color(0xFFE7EEF3)}))
                                        Spacer(Modifier.width(8.dp)); Text(v,color=White,modifier=Modifier.weight(1f)); RadioButton(selected=wallpaperChoice==k,onClick={wallpaperChoice=k;saveAsset("wallpaper",k)})
                                    }
                                }
                                Text("قلم نوشته", color = White)
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("sans" to "وزیرمتن","serif" to "سریف","mono" to "تک‌فاصله").forEach { (k,v)->FilterChip(selected=fontChoice==k,onClick={fontChoice=k;saveAsset("font",k)},label={Text(v)}) } }
                                Text("اندازه نوشته: ${(fontScale*100).toInt()}٪",color=White)
                                Slider(value=fontScale,onValueChange={fontScale=it;saveAsset("font_scale",it)},valueRange=0.85f..1.25f)
                                Row(verticalAlignment=Alignment.CenterVertically) { Text("انیمیشن‌های رابط",Modifier.weight(1f),color=White);Switch(checked=motionEnabled,onCheckedChange={motionEnabled=it;saveAsset("motion",it)}) }
                                Row(verticalAlignment=Alignment.CenterVertically) { Text("ایموجی‌های متحرک",Modifier.weight(1f),color=White);Switch(checked=emojiMotionEnabled,onCheckedChange={emojiMotionEnabled=it;saveAsset("emoji_motion",it)}) }
                                Text("آیکون‌ها از Material Icons موجود در APK استفاده می‌کنند و رنگ آن‌ها با رنگ انتخابی هماهنگ می‌شود.",color=Muted,fontSize=11.sp)
                            }
                        } else if (dialog == "notifications") {
                            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) { Text("صدای اعلان",Modifier.weight(1f),color=White);Switch(checked=soundEnabled,onCheckedChange={soundEnabled=it;saveAsset("sound",it)}) }
                                Button(onClick={try { if (soundEnabled) MediaPlayer.create(context, R.raw.moon_notification)?.apply { setOnCompletionListener { it.release() }; start() } else infoText = "صدای اعلان خاموش است." } catch (_:Exception) { errorText="پخش صدای داخلی ناموفق بود." }},modifier=Modifier.fillMaxWidth()) { Text("آزمایش صدای اعلان داخلی") }
                                Text("فایل صدای اعلان همراه APK نصب می‌شود؛ انتخاب روشن/خاموش در تنظیمات اعلان اندروید نیز اعمال می‌شود.",color=Muted,fontSize=11.sp)
                            }
                        } else if (dialog == "offline") {
                            Column(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text("راهنمای آفلاین Moon Time",color=White,fontWeight=FontWeight.Bold)
                                Text("• ظاهر، رنگ‌ها، قلم، اندازه نوشته و پس‌زمینه‌ها روی دستگاه ذخیره می‌شوند.\n\n• ایموجی‌ها و افکت‌های پایه بدون اینترنت اجرا می‌شوند.\n\n• راهنما و اطلاعات برنامه داخل APK هستند.\n\n• ورود، ارسال پیام، همگام‌سازی و دریافت فایل کاربران دیگر به اینترنت و سرور نیاز دارد.",color=White,fontSize=13.sp)
                                Text("این بخش از متن داخلی برنامه استفاده می‌کند و آفلاین در دسترس است.",color=Muted,fontSize=11.sp)
                            }
                        } else if (dialog == "toggle_dark") {
                            Row(verticalAlignment=Alignment.CenterVertically) { Text("حالت شب",Modifier.weight(1f),color=White);Switch(checked=darkMode,onCheckedChange={darkMode=it;saveAsset("dark_mode",it)}) }
                        } else if (dialog == "feature:حساب کاربری") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("ایمیل: ${api.email ?: "—"}",color=Muted,fontSize=11.sp)
                                Field(profileName,{profileName=it},"نام نمایشی")
                                Field(profileUsername,{profileUsername=it},"نام کاربری انگلیسی (اختیاری)")
                                Field(profileBio,{profileBio=it},"درباره من (حداکثر ۱۶۰ نویسه)")
                                Button(enabled=!busy,onClick={scope.launch { busy=true; try{api.updateProfile(profileName,profileUsername,profileBio);infoText="پروفایل ذخیره شد.";dialog=""}catch(e:Exception){errorText=e.message?:"ذخیره پروفایل ناموفق بود"}finally{busy=false} }},modifier=Modifier.fillMaxWidth()){Text("ذخیره پروفایل")}
                                OutlinedButton(onClick={avatarPicker.launch("image/*")},modifier=Modifier.fillMaxWidth()){Text(if(avatarUrl.isBlank())"انتخاب عکس پروفایل" else "تغییر عکس پروفایل")}
                            }
                        } else if (dialog == "feature:حریم خصوصی و امنیت") {
                            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) { Text("نمایش وضعیت آنلاین",Modifier.weight(1f),color=White); Switch(onlineStatus,{v->onlineStatus=v;featurePrefs.edit().putBoolean("online_status",v).apply();privacyPrefs.put("online_status",v);scope.launch{try{api.savePreferences(privacyPrefs)}catch(e:Exception){errorText=e.message?:"ذخیره تنظیمات ناموفق بود"}}}) }
                                Row(verticalAlignment=Alignment.CenterVertically) { Text("قابل جست‌وجو بودن پروفایل",Modifier.weight(1f),color=White); Switch(profileSearchable,{v->profileSearchable=v;featurePrefs.edit().putBoolean("profile_searchable",v).apply();privacyPrefs.put("profile_searchable",v);scope.launch{try{api.savePreferences(privacyPrefs)}catch(e:Exception){errorText=e.message?:"ذخیره تنظیمات ناموفق بود"}}}) }
                                Row(verticalAlignment=Alignment.CenterVertically) { Text("رسید خواندن پیام‌ها",Modifier.weight(1f),color=White); Switch(readReceipts,{v->readReceipts=v;featurePrefs.edit().putBoolean("read_receipts",v).apply();privacyPrefs.put("read_receipts",v);scope.launch{try{api.savePreferences(privacyPrefs)}catch(e:Exception){errorText=e.message?:"ذخیره تنظیمات ناموفق بود"}}}) }
                                Text("تنظیمات در حساب کاربری ذخیره می‌شوند؛ جست‌وجوی پروفایل بر اساس ایمیل از این ترجیح پیروی می‌کند.",color=Muted,fontSize=11.sp)
                            }
                        } else if (dialog == "feature:دستگاه‌های فعال") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("دستگاه فعلی: Android • ${Build.MODEL}",color=White,fontWeight=FontWeight.Bold)
                                Text("خروج سراسری همه نشست‌ها را باطل می‌کند؛ پس از آن باید دوباره وارد حساب شوی.",color=Muted,fontSize=12.sp)
                                Button(enabled=!busy,onClick={scope.launch{busy=true;try{api.signOutEverywhere();realtime?.close();realtime=null;logged=false;dialog="";infoText="همه نشست‌ها باطل شدند."}catch(e:Exception){errorText=e.message?:"خروج سراسری ناموفق بود"}finally{busy=false}}},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFB83E55))){Text("خروج از همه دستگاه‌ها")}
                                Text("به‌دلیل محدودیت API امن کلاینت، فهرست نشست‌های دیگر نمایش داده نمی‌شود.",color=Muted,fontSize=11.sp)
                            }
                        } else if (dialog == "feature:تغییر رمز عبور") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Field(newPassword,{newPassword=it},"رمز جدید (حداقل ۸ نویسه)",true)
                                Button(enabled=newPassword.length>=8 && !busy,onClick={scope.launch{busy=true;try{api.changePassword(newPassword);newPassword="";infoText="رمز عبور تغییر کرد.";dialog=""}catch(e:Exception){errorText=e.message?:"تغییر رمز ناموفق بود"}finally{busy=false}}},modifier=Modifier.fillMaxWidth()){Text("تغییر رمز عبور")}
                            }
                        } else if (dialog == "feature:تغییر ایمیل") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Field(newEmail,{newEmail=it},"ایمیل جدید")
                                Button(enabled=newEmail.contains("@") && !busy,onClick={scope.launch{busy=true;try{api.changeEmail(newEmail);infoText="درخواست تغییر ایمیل ثبت شد؛ ایمیل جدید را برای تأیید بررسی کن.";dialog=""}catch(e:Exception){errorText=e.message?:"تغییر ایمیل ناموفق بود"}finally{busy=false}}},modifier=Modifier.fillMaxWidth()){Text("درخواست تغییر ایمیل")}
                            }
                        } else if (dialog == "feature:داده و حافظه") {
                            val cacheBytes = remember { context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
                            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text("حافظه موقت برنامه: ${android.text.format.Formatter.formatShortFileSize(context,cacheBytes)}",color=White)
                                Text("پاک‌سازی فقط فایل‌های موقت (cache) را حذف می‌کند؛ نشست ورود، مخاطبین و پیام‌های ذخیره‌شده دست‌نخورده می‌مانند.",color=Muted,fontSize=11.sp)
                                OutlinedButton(onClick={ context.cacheDir.deleteRecursively(); context.cacheDir.mkdirs(); infoText="حافظه موقت پاک شد."; dialog="" },modifier=Modifier.fillMaxWidth()) { Text("پاک‌سازی حافظه موقت") }
                            }
                        } else if (dialog == "feature:زبان برنامه") {
                            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf("فارسی","English","العربية","Türkçe","Русский","Deutsch").forEach { lang ->
                                    Row(Modifier.fillMaxWidth().clickable { appLanguage=lang; featurePrefs.edit().putString("language",lang).apply(); infoText="زبان انتخابی ذخیره شد؛ ترجمه کامل صفحات در نسخه بعدی تکمیل می‌شود." }.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Text(lang,Modifier.weight(1f),color=White)
                                        RadioButton(selected=appLanguage==lang,onClick={appLanguage=lang;featurePrefs.edit().putString("language",lang).apply();infoText="زبان انتخابی ذخیره شد؛ ترجمه کامل صفحات در نسخه بعدی تکمیل می‌شود."})
                                    }
                                }
                                Text("زبان انتخابی ذخیره می‌شود؛ در حال حاضر رابط اصلی فارسی است و ترجمه کامل زبان‌های دیگر هنوز تکمیل نشده.",color=Muted,fontSize=11.sp)
                            }
                        } else if (dialog == "feature:پوشه‌های گفتگو" || dialog == "feature:پوشه‌ها") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Field(newFolderName,{newFolderName=it},"نام پوشه جدید")
                                Button(enabled=newFolderName.trim().length>=2,onClick={
                                    val clean=newFolderName.trim().take(30)
                                    if(clean !in folderNames){folderNames=folderNames+clean;featurePrefs.edit().putString("folders",JSONArray(folderNames).toString()).apply()}
                                    newFolderName=""
                                },modifier=Modifier.fillMaxWidth()) { Text("ساخت پوشه") }
                                if(folderNames.isEmpty()) Text("هنوز پوشه‌ای نساخته‌ای.",color=Muted)
                                folderNames.forEach { folder ->
                                    Text(folder,color=Cyan,fontWeight=FontWeight.Bold)
                                    rooms.forEach { room ->
                                        Row(verticalAlignment=Alignment.CenterVertically) {
                                            Text(room.title,Modifier.weight(1f),color=White,fontSize=12.sp)
                                            TextButton(onClick={
                                                val next=JSONObject(roomFolders.toString())
                                                if(next.optString(room.id)==folder) next.remove(room.id) else next.put(room.id,folder)
                                                roomFolders=next
                                                featurePrefs.edit().putString("room_folders",next.toString()).apply()
                                            }) { Text(if(roomFolders.optString(room.id)==folder)"برداشتن" else "افزودن") }
                                        }
                                    }
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Text("فیلتر این پوشه",Modifier.weight(1f),color=Muted,fontSize=11.sp)
                                        TextButton(onClick={activeFolder=folder;dialog=""}){Text("نمایش")}
                                        TextButton(onClick={
                                            folderNames=folderNames-folder
                                            if(activeFolder==folder)activeFolder=""
                                            val next=JSONObject(roomFolders.toString()); val keys=next.keys(); val remove=mutableListOf<String>()
                                            while(keys.hasNext()){val key=keys.next();if(next.optString(key)==folder)remove.add(key)}
                                            remove.forEach{key->next.remove(key)}
                                            roomFolders=next
                                            featurePrefs.edit().putString("folders",JSONArray(folderNames).toString()).putString("room_folders",next.toString()).apply()
                                        }){Text("حذف")}
                                    }
                                }
                                Text("تخصیص گفتگوها و فیلتر پوشه‌ها روی همین دستگاه ذخیره می‌شود.",color=Muted,fontSize=11.sp)
                            }
                        } else if (dialog == "feature:پیام‌های ذخیره‌شده") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("برای ذخیره پیام، متن آن را در کادر زیر وارد کن. موارد فقط روی همین دستگاه نگهداری می‌شوند.",color=Muted,fontSize=11.sp)
                                Field(dialogValue,{dialogValue=it},"متن پیام برای ذخیره")
                                Button(enabled=dialogValue.isNotBlank(),onClick={
                                    savedItems=savedItems+dialogValue.trim()
                                    featurePrefs.edit().putString("saved_items",JSONArray(savedItems).put(dialogValue.trim()).toString()).apply()
                                    dialogValue=""
                                    infoText="پیام در ذخیره‌شده‌های محلی ثبت شد."
                                },modifier=Modifier.fillMaxWidth()){Text("ذخیره پیام")}
                                if(savedItems.isEmpty()) Text("هنوز پیامی ذخیره نشده.",color=Muted)
                                savedItems.forEachIndexed { index,item -> Row(verticalAlignment=Alignment.CenterVertically) { Text(item,Modifier.weight(1f),color=White,fontSize=13.sp); TextButton(onClick={savedItems=savedItems.toMutableList().also{it.removeAt(index)};featurePrefs.edit().putString("saved_items",JSONArray().also{a->savedItems.forEach{v->a.put(v)}}.toString()).apply()}){Text("حذف")} } }
                            }
                        } else if (dialog == "feature:پرسش‌های متداول") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("چطور گفتگو بسازم؟ از منوی اصلی «پیام جدید»، «گروه جدید» یا «کانال جدید» را انتخاب کن.",color=White)
                                Text("چطور فایل بفرستم؟ داخل گفتگو دکمه پیوست را بزن و فایل را انتخاب کن؛ ارسال به اینترنت نیاز دارد.",color=White)
                                Text("چرا اعلان فوری نیست؟ همگام‌سازی پس‌زمینه اندروید ممکن است با محدودیت باتری تأخیر داشته باشد.",color=White)
                                Text("چطور ظاهر را عوض کنم؟ از تنظیمات، بخش ظاهر و پوسته را باز کن.",color=White)
                            }
                        } else if (dialog == "feature:قوانین و حریم خصوصی") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("از ارسال اطلاعات شخصی دیگران بدون اجازه خودداری کن.",color=White)
                                Text("پیام‌ها و فایل‌های ارسالی برای همگام‌سازی روی زیرساخت سرور ذخیره می‌شوند؛ این نسخه رمزنگاری سرتاسری را ارائه نمی‌کند.",color=White)
                                Text("از رمز عبور قوی و منحصربه‌فرد استفاده کن و آن را با دیگران به اشتراک نگذار.",color=White)
                            }
                        } else if (dialog == "feature:گزارش مشکل و پیشنهاد") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Field(dialogValue,{dialogValue=it},"شرح مشکل یا پیشنهاد")
                                Button(enabled=dialogValue.isNotBlank(),onClick={
                                    val intent=Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:")).putExtra(Intent.EXTRA_SUBJECT,"Moon Time - گزارش مشکل").putExtra(Intent.EXTRA_TEXT,dialogValue)
                                    try { context.startActivity(intent); dialog="" } catch (_:Exception) { errorText="برنامه ایمیل روی دستگاه پیدا نشد." }
                                },modifier=Modifier.fillMaxWidth()){Text("ارسال با برنامه ایمیل")}
                                Text("گزارش از طریق برنامه ایمیل دستگاه ارسال می‌شود؛ سرور Moon Time هنوز سامانه ثبت تیکت ندارد.",color=Muted,fontSize=11.sp)
                            }
                        } else if (dialog == "feature:درباره برنامه" || dialog == "feature:نسخه برنامه") {
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Moon Time",color=White,fontWeight=FontWeight.Bold)
                                Text("نسخه ${context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "نامشخص"} • کد ${context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode}",color=White)
                                Text("پیام‌رسان آزمایشی؛ برخی قابلیت‌های پیشرفته هنوز در حال توسعه‌اند.",color=Muted,fontSize=11.sp)
                            }
                        } else if (dialog == "emoji") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("😀","🥳","😍","🔥","✨","🚀","🌙","💜","🎉","😂","🤩","😎","🫶","💫","🌈","🪐","👾","🎊","💯","❤️").chunked(5).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { emoji ->
                                            Text(emoji, fontSize = 25.sp, modifier = Modifier.clickable {
                                                input += emoji
                                                dialog = ""
                                            })
                                        }
                                    }
                                }
                            }
                        } else if (dialog == "contacts") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(contactEmail, { contactEmail = it }, "ایمیل کاربر برای افزودن")
                                Button(
                                    enabled = contactEmail.contains("@") && !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            try {
                                                val contact = api.lookupContact(contactEmail)
                                                contacts = (contacts.filterNot { it.id == contact.id } + contact)
                                                val arr = JSONArray()
                                                contacts.forEach { arr.put(JSONObject().put("id",it.id).put("name",it.name).put("email",it.email)) }
                                                context.getSharedPreferences("moon_contacts",Context.MODE_PRIVATE).edit().putString("items",arr.toString()).apply()
                                                contactEmail = ""
                                                infoText = "مخاطب ذخیره شد."
                                            } catch(e:Exception) { errorText=e.message ?: "یافتن مخاطب ناموفق بود" }
                                            finally { busy=false }
                                        }
                                    }
                                ) { Text("افزودن مخاطب") }
                                if (contacts.isEmpty()) Text("هنوز مخاطبی ذخیره نکرده‌ای.", color=Muted)
                                contacts.forEach { contact ->
                                    Row(Modifier.fillMaxWidth().clickable {
                                        scope.launch {
                                            busy=true
                                            try {
                                                val room=api.direct(contact.email)
                                                rooms=api.rooms()
                                                current=room
                                                connectRoom(room)
                                                dialog=""
                                            } catch(e:Exception) { errorText=e.message ?: "شروع گفتگو ناموفق بود" }
                                            finally { busy=false }
                                        }
                                    }.padding(vertical=7.dp), verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(contact.name,color=White)
                                            Text(contact.email,color=Muted,fontSize=11.sp)
                                        }
                                        Text("گفتگو",color=Cyan)
                                    }
                                }
                            }
                        } else {
                            Field(
                                dialogValue,
                                { dialogValue = it },
                                if (dialog == "direct" || dialog == "new") "ایمیل کاربر" else "نام"
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                busy = true
                                errorText = ""
                                try {
                                    when (dialog) {
                                        "group", "channel" -> {
                                            val created = api.createRoom(dialogValue, dialog)
                                            rooms = api.rooms()
                                            current = created
                                            connectRoom(created)
                                        }
                                        "direct" -> {
                                            val created = api.direct(dialogValue)
                                            rooms = api.rooms()
                                            current = created
                                            connectRoom(created)
                                        }
                                        "new" -> {
                                            val room = current
                                            if (room != null) api.addMember(room, dialogValue)
                                            infoText = "عضو اضافه شد."
                                        }
                                    }
                                    dialog = ""
                                    dialogValue = ""
                                } catch (e: Exception) {
                                    errorText = e.message ?: "عملیات ناموفق بود"
                                } finally {
                                    busy = false
                                }
                            }
                        }) { Text("تأیید") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = "" }) { Text("انصراف") }
                    }
                )
            }
        }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    secret: Boolean = false
) {
    BasicTextField(
        value,
        onChange,
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp)).background(Panel).padding(15.dp),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp),
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { inner ->
            if (value.isEmpty()) Text(hint, color = Muted, fontSize = 13.sp)
            inner()
        }
    )
}

@Composable
private fun MessageBubble(m: Msg, loader: ImageLoader, animateEmoji: Boolean = true, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.mine) Arrangement.Start else Arrangement.End
    ) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                .background(
                    if(m.mine)Brush.horizontalGradient(listOf(Color(0xFFEFFDDE),Color(0xFFEFFDDE)))
else Brush.horizontalGradient(listOf(Color.White,Color.White))
                )
                .clickable { if (m.path != null) onOpen() }
                .padding(12.dp)
        ) {
            when (m.kind) {
                "image" -> {
                    if (m.mediaUrl != null) {
                        coil.compose.AsyncImage(
                            model = m.mediaUrl,
                            contentDescription = m.name ?: "تصویر",
                            imageLoader = loader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.widthIn(max=260.dp).heightIn(max=260.dp).clip(RoundedCornerShape(12.dp))
                        )
                    } else Text("🖼️ تصویر پیوست‌شده", color = White)
                }
                "video" -> {
                    Text("🎬 ${m.name ?: "ویدیو پیوست‌شده"}", color = White)
                    if (m.mediaUrl != null) Text("برای پخش، لمس کن", color = Cyan, fontSize = 10.sp)
                }
                "audio" -> Text("🎵 فایل صوتی", color = White)
                "file" -> Text("📎 ${m.name ?: "فایل پیوست"}", color = White)
            }
            if (m.text.isNotBlank()) {
                if (m.text.trim() in setOf("😀","🥳","😍","🔥","✨","🚀","🌙","💜","🎉","😂")) {
                    AnimatedEmoji(m.text.trim(), animateEmoji)
                } else {
                    Text(m.text, color = White, fontSize = 14.sp)
                }
            }
            Text(
                if (m.pending) "در صف ارسال" else m.time.take(16).replace("T", " "),
                color = if (m.pending) Violet else Color(0xFF707579),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun AnimatedEmoji(value: String, enabled: Boolean = true) {
    if (!enabled) { Text(value, fontSize = 34.sp); return }
    val transition = rememberInfiniteTransition(label = "emoji-pulse")
    val scale by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emoji-scale"
    )
    Text(value, modifier = Modifier.scale(scale), fontSize = 34.sp)
}

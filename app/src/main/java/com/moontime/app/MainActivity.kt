package com.moontime.app

import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
private val Night = Color(0xFF090D1B)
private val Panel = Color(0xFF141B30)
private val Panel2 = Color(0xFF1B2540)
private val Violet = Color(0xFF9B8CFF)
private val Cyan = Color(0xFF64E6D5)
private val White = Color(0xFFF4F5FF)
private val Muted = Color(0xFF9BA8C8)
private val http = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()

data class Room(val id:String,val title:String,val kind:String)
data class Msg(val id:String,val text:String,val mine:Boolean,val time:String,val kind:String="text",val path:String?=null,val name:String?=null,val mediaUrl:String?=null,val pending:Boolean=false)
data class MoonContact(val id:String,val name:String,val email:String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(9,13,27)
        window.navigationBarColor=android.graphics.Color.rgb(9,13,27)
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
    var messages by remember { mutableStateOf(emptyList<Msg>()) }
    var input by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var dialogValue by remember { mutableStateOf("") }
    var realtime by remember { mutableStateOf<Realtime?>(null) }
    var online by remember { mutableStateOf(false) }
    val mediaLoader = remember(context) { ImageLoader.Builder(context).components { if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory()) }.build() }
    var contactEmail by remember { mutableStateOf("") }
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
                messages = (api.messages(room) + api.pendingFor(room.id)).sortedBy { it.time }.map { m ->
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
                refreshRooms()
            } catch (e: Exception) {
                errorText = e.message ?: "ورود دوباره لازم است"
                api.logout()
                logged = false
            }
        }
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

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet, secondary = Cyan, background = Night,
            surface = Panel, onPrimary = White, onBackground = White, onSurface = White
        )
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF171536), Night, Night)))
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
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("☾", fontSize = 34.sp, color = Violet)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("MOON TIME", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text(if (online) "متصل به گفتگو" else "آماده اتصال", color = Muted, fontSize = 10.sp)
                        }
                        IconButton(onClick = { dialog = "menu" }) { Icon(Icons.Default.MoreVert, null, tint = White) }
                        IconButton(onClick = { dialog = "contacts" }) { Icon(Icons.Default.Contacts, null, tint = White) }
                        IconButton(onClick = { dialog = "direct" }) { Icon(Icons.Default.PersonAdd, null, tint = White) }
                        IconButton(onClick = { dialog = "group" }) { Icon(Icons.Default.GroupAdd, null, tint = White) }
                        IconButton(onClick = { dialog = "channel" }) { Icon(Icons.Default.Campaign, null, tint = White) }
                        if (current != null && current!!.kind != "direct") {
                            IconButton(onClick = { dialog = "new" }) { Icon(Icons.Default.PersonAddAlt, null, tint = White) }
                        }
                        IconButton(onClick = {
                            api.logout()
                            realtime?.close()
                            logged = false
                        }) { Icon(Icons.Default.Logout, null, tint = White) }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = current == null,
                            onClick = {
                                current = null
                                messages = emptyList()
                                realtime?.close()
                                online = false
                            },
                            label = { Text("همه") }
                        )
                        rooms.forEach { room ->
                            FilterChip(
                                selected = current?.id == room.id,
                                onClick = {
                                    current = room
                                    connectRoom(room)
                                },
                                label = { Text(room.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                        IconButton(onClick = { refreshRooms() }) {
                            Icon(Icons.Default.Refresh, null, tint = White)
                        }
                    }

                    if (current == null) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🌙", fontSize = 52.sp)
                            Text("هنوز گفتگویی نداری", color = White, fontWeight = FontWeight.Bold)
                            Text("از دکمه‌های بالا برای شروع گفتگو استفاده کن.", color = Muted)
                            Button(onClick = { dialog = "channel" }, modifier = Modifier.padding(top = 12.dp)) {
                                Text("ساخت کانال")
                            }
                        }
                    } else {
                        Text(current!!.title, color = White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(message, mediaLoader, onOpen = {
                                    if (message.path != null) {
                                        scope.launch {
                                            try {
                                                val mediaUrl = api.signed(message.path)
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mediaUrl)))
                                            } catch (e: Exception) {
                                                errorText = e.message ?: "باز کردن رسانه ناموفق بود"
                                            }
                                        }
                                    }
                                })
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().background(Color(0xFF10172A))
                                .navigationBarsPadding().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { picker.launch("*/*") }) {
                                Icon(Icons.Default.AttachFile, null, tint = Muted)
                            }
                            BasicTextField(
                                input,
                                { input = it },
                                Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                    .background(Panel).padding(13.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp),
                                decorationBox = { inner ->
                                    if (input.isEmpty()) Text("پیامت را بنویس…", color = Muted)
                                    inner()
                                }
                            )
                            IconButton(onClick = { dialog = "emoji" }) { Text("✨", fontSize = 23.sp) }
                            IconButton(onClick = {
                                val room = current
                                val value = input.trim()
                                if (room != null && value.isNotEmpty()) {
                                    scope.launch {
                                        try {
                                            val sent = api.send(room, value)
                                            messages = messages + sent
                                            input = ""
                                        } catch (e: Exception) {
                                            try {
                                                val queued = api.queueText(room,value)
                                                messages = messages + queued
                                                input = ""
                                                infoText = "پیام ذخیره شد و با اتصال اینترنت ارسال می‌شود."
                                            } catch (_:Exception) { errorText = e.message ?: "ارسال ناموفق بود" }
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Send, null, tint = Violet)
                            }
                        }
                    }

                    if (infoText.isNotBlank()) Text(infoText, color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                    if (errorText.isNotBlank()) Text(errorText, color = Color(0xFFFF9BAF), fontSize = 10.sp, modifier = Modifier.padding(8.dp))
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
                    else -> "Moon Time"
                }
                AlertDialog(
                    onDismissRequest = { dialog = "" },
                    containerColor = Panel,
                    title = { Text(dialogTitle, color = White) },
                    text = {
                        if (dialog == "menu") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("مخاطبین" to "contacts", "گفتگوی خصوصی جدید" to "direct", "ساخت گروه" to "group", "ساخت کانال" to "channel", "تنظیمات" to "settings", "به‌روزرسانی گفتگوها" to "refresh").forEach { (label, action) ->
                                    Text(label, color = Cyan, modifier = Modifier.fillMaxWidth().clickable {
                                        dialog = action
                                        if (action == "refresh") { dialog = ""; refreshRooms() }
                                    }.padding(vertical = 8.dp))
                                }
                            }
                        } else if (dialog == "settings") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("حساب فعال: ${api.email ?: "—"}", color = White)
                                Text("شناسه حساب: ${api.userId ?: "—"}", color = Muted, fontSize = 11.sp)
                                Text("برای تازه‌سازی فهرست گفتگوها، گزینه زیر را انتخاب کن.", color = Muted)
                                Button(onClick = { dialog = ""; refreshRooms() }, modifier = Modifier.fillMaxWidth()) { Text("همگام‌سازی گفتگوها") }
                                Button(onClick = {
                                    dialog = ""
                                    api.logout()
                                    realtime?.close()
                                    realtime = null
                                    logged = false
                                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB83E55))) { Text("خروج از حساب") }
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
private fun MessageBubble(m: Msg, loader: ImageLoader, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.mine) Arrangement.Start else Arrangement.End
    ) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                .background(
                    if (m.mine) Brush.horizontalGradient(listOf(Color(0xFF6255C5), Color(0xFF5146A5)))
                    else Brush.horizontalGradient(listOf(Panel2, Panel2))
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
                    AnimatedEmoji(m.text.trim())
                } else {
                    Text(m.text, color = White, fontSize = 14.sp)
                }
            }
            Text(
                if (m.pending) "در صف ارسال" else m.time.take(16).replace("T", " "),
                color = if (m.pending) Cyan else Color(0xFFD2D0F5),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun AnimatedEmoji(value: String) {
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

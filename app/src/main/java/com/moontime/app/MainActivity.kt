package com.moontime.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
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
import java.util.concurrent.TimeUnit

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
data class Msg(val id:String,val text:String,val mine:Boolean,val time:String,val kind:String="text",val path:String?=null,val name:String?=null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=android.graphics.Color.rgb(9,13,27); window.navigationBarColor=android.graphics.Color.rgb(9,13,27); setContent { MoonNativeApp() } }
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
        val created=JSONArray(request("/rest/v1/conversations","POST",JSONObject().put("title",title).put("is_group",true).put("kind",kind).put("created_by",uid).toString(),true,"application/json",mapOf("Prefer" to "return=representation")))
        if(created.length()==0) error("ساخت گفتگو ناموفق بود")
        val r=created.getJSONObject(0)
        val id=r.getString("id")
        request("/rest/v1/conversation_members","POST",JSONObject().put("conversation_id",id).put("user_id",uid).toString(),true,"application/json",mapOf("Prefer" to "return=minimal"))
        return Room(id,title,kind)
    }
    suspend fun direct(emailTarget:String):Room {
        val uid=userId?:error("ابتدا وارد حساب شو")
        val result=JSONArray(request("/rest/v1/rpc/lookup_profile_by_email","POST",JSONObject().put("p_email",emailTarget).toString()))
        if(result.length()==0)error("این ایمیل در مون‌تایم ثبت‌نام نکرده است.")
        val target=result.getJSONObject(0)
        val created=JSONArray(request("/rest/v1/conversations","POST",JSONObject().put("title",target.optString("display_name","گفتگوی خصوصی")).put("is_group",false).put("kind","direct").put("created_by",uid).toString(),true,"application/json",mapOf("Prefer" to "return=representation")))
        if(created.length()==0) error("ساخت گفتگوی خصوصی ناموفق بود")
        val room=created.getJSONObject(0)
        val id=room.getString("id")
        val arr=JSONArray().put(JSONObject().put("conversation_id",id).put("user_id",uid)).put(JSONObject().put("conversation_id",id).put("user_id",target.getString("id")))
        request("/rest/v1/conversation_members","POST",arr.toString(),true,"application/json",mapOf("Prefer" to "return=minimal"))
        return Room(id,room.optString("title","گفتگوی خصوصی"),"direct")
    }
    suspend fun addMember(room:Room,emailTarget:String) {
        val found=JSONArray(request("/rest/v1/rpc/lookup_profile_by_email","POST",JSONObject().put("p_email",emailTarget).toString()))
        if(found.length()==0)error("کاربری با این ایمیل پیدا نشد.")
        request("/rest/v1/conversation_members","POST",JSONObject().put("conversation_id",room.id).put("user_id",found.getJSONObject(0).getString("id")).toString(),true,"application/json",mapOf("Prefer" to "return=minimal"))
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

    fun connectRoom(room: Room) {
        scope.launch {
            try {
                messages = api.messages(room)
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
                                MessageBubble(message, onOpen = {
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
                                            errorText = e.message ?: "ارسال ناموفق بود"
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
                    else -> "Moon Time"
                }
                AlertDialog(
                    onDismissRequest = { dialog = "" },
                    containerColor = Panel,
                    title = { Text(dialogTitle, color = White) },
                    text = {
                        if (dialog == "emoji") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("😀", "🥳", "😍", "🔥", "✨", "🚀", "🌙", "💜", "🎉", "😂").forEach { emoji ->
                                    Text(emoji, fontSize = 25.sp, modifier = Modifier.clickable {
                                        input += emoji
                                        dialog = ""
                                    })
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
private fun MessageBubble(m: Msg, onOpen: () -> Unit) {
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
                "image" -> Text("🖼️ تصویر پیوست‌شده", color = White)
                "video" -> Text("🎬 ویدیو پیوست‌شده", color = White)
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
                m.time.take(16).replace("T", " "),
                color = Color(0xFFD2D0F5),
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

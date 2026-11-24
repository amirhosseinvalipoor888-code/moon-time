package com.moontime.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Night = Color(0xFF090D1B)
private val Panel = Color(0xFF141B30)
private val Panel2 = Color(0xFF1B2540)
private val Violet = Color(0xFF9B8CFF)
private val Cyan = Color(0xFF64E6D5)
private val White = Color(0xFFF4F5FF)
private val Muted = Color(0xFF9BA8C8)

data class ChatItem(val id:Int,val name:String,val message:String,val time:String,val emoji:String,val unread:Int=0,val online:Boolean=false)
data class ChatMessage(val chatId:Int,val text:String,val mine:Boolean,val time:String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9,13,27)
        window.navigationBarColor = android.graphics.Color.rgb(9,13,27)
        setContent { MoonTimeApp() }
    }
}

@Composable
fun MoonTimeApp() {
    val context= LocalContext.current
    val store= remember { LocalStore(context) }
    var selected by remember { mutableStateOf<ChatItem?>(null) }
    var tab by remember { mutableStateOf("چت‌ها") }
    var search by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val chats= remember(refresh) { store.chats() }
    MaterialTheme(colorScheme= darkColorScheme(primary=Violet,secondary=Cyan,background=Night,surface=Panel,onPrimary=White,onBackground=White,onSurface=White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171536),Night,Night)))) {
            if(selected==null) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Header()
                    Row(Modifier.padding(horizontal=18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Search,null,tint=Muted); Spacer(Modifier.width(10.dp))
                        BasicTextField(search,{search=it},Modifier.weight(1f),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=White,fontSize=14.sp),decorationBox={ inner -> if(search.isEmpty()) Text("جست‌وجوی پیام، مخاطب یا گروه...",color=Muted,fontSize=13.sp); inner() })
                        if(search.isNotEmpty()) Icon(Icons.Default.Close,null,tint=Muted,modifier=Modifier.clickable { search="" })
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("گفتگوهای شما",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.weight(1f)); Text("همه  •  ${chats.size}",color=Cyan,fontSize=12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=14.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(chats.filter { it.name.contains(search,true)||it.message.contains(search,true) },key={it.id}) { ChatRow(it) { selected=it } }
                    }
                    NavigationBar(containerColor=Color(0xFF10172A),tonalElevation=0.dp) {
                        listOf("چت‌ها" to Icons.Default.ChatBubble,"مخاطبین" to Icons.Default.People,"تماس‌ها" to Icons.Default.Call,"تنظیمات" to Icons.Default.Settings).forEach { (label,icon) ->
                            NavigationBarItem(selected=tab==label,onClick={tab=label},icon={Icon(icon,null)},label={Text(label,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Violet,selectedTextColor=Violet,unselectedIconColor=Muted,unselectedTextColor=Muted,indicatorColor=Panel2))
                        }
                    }
                }
                if(tab!="چت‌ها") Box(Modifier.fillMaxSize().background(Night.copy(alpha=.97f)),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(28.dp)) {
                        Text(when(tab){"مخاطبین"->"👥";"تماس‌ها"->"☎️";else->"⚙️"},fontSize=54.sp)
                        Text(tab,color=White,fontSize=23.sp,fontWeight=FontWeight.Bold)
                        Text("این بخش در حال آماده‌سازی است",color=Muted,modifier=Modifier.padding(top=8.dp))
                        TextButton(onClick={tab="چت‌ها"}){Text("بازگشت به گفتگوها")}
                    }
                }
            } else ChatScreen(selected!!,store,{selected=null},{refresh++})
        }
    }
}

@Composable private fun Header() {
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Violet,Color(0xFF5144B8)))),contentAlignment=Alignment.Center){Text("☾",fontSize=29.sp,color=White)}
        Spacer(Modifier.width(12.dp))
        Column { Text("MOON TIME",color=White,fontWeight=FontWeight.ExtraBold,fontSize=19.sp,letterSpacing=1.4.sp); Text("فراتر از یک پیام‌رسان",color=Muted,fontSize=10.sp) }
        Spacer(Modifier.weight(1f)); AnimatedStatus(); Spacer(Modifier.width(10.dp))
        IconButton(onClick={}){Icon(Icons.Default.MoreVert,null,tint=White)}
    }
}

@Composable private fun AnimatedStatus() {
    val t= rememberInfiniteTransition(label="pulse")
    val pulse by t.animateFloat(.65f,1f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="pulse")
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.clip(CircleShape).background(Color(0xFF173A3A)).padding(horizontal=10.dp,vertical=7.dp)) {
        Box(Modifier.size(7.dp).scale(pulse).clip(CircleShape).background(Cyan)); Spacer(Modifier.width(6.dp)); Text("Moon",color=Cyan,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable private fun ChatRow(chat:ChatItem,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Panel.copy(alpha=.92f)).clickable(onClick=onClick).padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
        Box {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF4E65A5),Color(0xFF7958B9)))),contentAlignment=Alignment.Center){Text(chat.emoji,fontSize=25.sp)}
            if(chat.online) Box(Modifier.align(Alignment.BottomStart).padding(1.dp).size(12.dp).clip(CircleShape).background(Cyan).border(2.dp,Panel,CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){Text(chat.name,color=White,fontWeight=FontWeight.Bold,fontSize=14.sp);Spacer(Modifier.height(5.dp));Text(chat.message,color=Muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
        Column(horizontalAlignment=Alignment.End){Text(chat.time,color=Muted,fontSize=10.sp);if(chat.unread>0) Box(Modifier.padding(top=7.dp).clip(CircleShape).background(Violet).padding(horizontal=7.dp,vertical=3.dp)){Text(chat.unread.toString(),color=White,fontSize=10.sp)}}
    }
}

@Composable private fun ChatScreen(chat:ChatItem,store:LocalStore,onBack:()->Unit,onChanged:()->Unit) {
    var input by remember { mutableStateOf("") }
    var messages by remember(chat.id) { mutableStateOf(store.messages(chat.id)) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF11182B)).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=White)}
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Panel2),contentAlignment=Alignment.Center){Text(chat.emoji,fontSize=21.sp)}
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){Text(chat.name,color=White,fontWeight=FontWeight.Bold,fontSize=14.sp);Text(if(chat.online)"آنلاین" else "آخرین بازدید اخیراً",color=if(chat.online)Cyan else Muted,fontSize=10.sp)}
            IconButton(onClick={}){Icon(Icons.Default.Search,null,tint=White)}
            IconButton(onClick={}){Icon(Icons.Default.MoreVert,null,tint=White)}
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Text("امروز",color=Muted,fontSize=10.sp,modifier=Modifier.clip(CircleShape).background(Panel).padding(horizontal=13.dp,vertical=6.dp))} }
            items(messages) { msg ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(msg.mine)Arrangement.Start else Arrangement.End) {
                    Column(Modifier.widthIn(max=290.dp).clip(RoundedCornerShape(18.dp)).background(if(msg.mine)Brush.horizontalGradient(listOf(Color(0xFF6255C5),Color(0xFF5146A5))) else Brush.horizontalGradient(listOf(Panel2,Panel2))).padding(horizontal=14.dp,vertical=10.dp)) {
                        Text(msg.text,color=White,fontSize=14.sp,lineHeight=21.sp)
                        Text(msg.time+if(msg.mine)"  ✓✓" else "",color=Color(0xFFD2D0F5),fontSize=9.sp,modifier=Modifier.align(Alignment.End).padding(top=4.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF10172A)).navigationBarsPadding().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
            AnimatedEmojiButton { input+="✨" }; Spacer(Modifier.width(7.dp))
            BasicTextField(input,{input=it},Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Panel).padding(horizontal=14.dp,vertical=13.dp),textStyle=androidx.compose.ui.text.TextStyle(color=White,fontSize=14.sp),singleLine=true,decorationBox={inner->if(input.isEmpty())Text("پیام خود را بنویسید...",color=Muted,fontSize=12.sp);inner()})
            Spacer(Modifier.width(7.dp))
            IconButton(onClick={val value=input.trim();if(value.isNotEmpty()){val msg=ChatMessage(chat.id,value,true,"اکنون");store.add(msg);messages=messages+msg;input="";onChanged()}},modifier=Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Violet,Color(0xFF5B4EC0))))){Icon(Icons.Default.Send,null,tint=White)}
        }
    }
}

@Composable private fun AnimatedEmojiButton(onClick:()->Unit) {
    val t= rememberInfiniteTransition(label="emoji")
    val angle by t.animateFloat(-8f,8f,infiniteRepeatable(tween(700),RepeatMode.Reverse),label="tilt")
    val scale by t.animateFloat(.92f,1.08f,infiniteRepeatable(tween(850),RepeatMode.Reverse),label="scale")
    Box(Modifier.size(43.dp).scale(scale).rotate(angle).clip(RoundedCornerShape(14.dp)).background(Panel2).clickable(onClick=onClick),contentAlignment=Alignment.Center){Text("✨",fontSize=22.sp)}
}

private class LocalStore(context:Context) {
    private val prefs=context.getSharedPreferences("moon_time_local",Context.MODE_PRIVATE)
    fun chats()=listOf(ChatItem(1,"تیم مون‌تایم","به فضای جدید خوش اومدی ✨","۱۸:۴۲","🌙",2,true),ChatItem(2,"آرین","فردا می‌بینمت!","۱۷:۲۰","🪐",0,true),ChatItem(3,"گروه دوستان","چه خبر از همگی؟","دیروز","🚀",5),ChatItem(4,"طراحی و ایده‌ها","طرح جدید خیلی خوب شده","دوشنبه","🎨"))
    fun messages(id:Int):List<ChatMessage> {
        val raw=prefs.getString("chat_" + id,null)?:return emptyList()
        return try {val arr=JSONArray(raw);(0 until arr.length()).map{i->arr.getJSONObject(i).let{ChatMessage(id,it.getString("text"),it.getBoolean("mine"),it.getString("time"))}}}catch(_:Exception){emptyList()}
    }
    fun add(message:ChatMessage) {
        val arr=JSONArray()
        (messages(message.chatId)+message).forEach{arr.put(JSONObject().put("text",it.text).put("mine",it.mine).put("time",it.time))}
        prefs.edit().putString("chat_" + message.chatId,arr.toString()).apply()
    }
}

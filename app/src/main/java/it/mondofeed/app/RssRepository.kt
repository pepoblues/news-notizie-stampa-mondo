package it.mondofeed.app

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.TimeUnit

class RssRepository {
    private val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).followRedirects(true).build()
    suspend fun fetch(source:FeedSource):List<Article> = withContext(Dispatchers.IO) {
        val response=client.newCall(Request.Builder().url(source.feedUrl).header("User-Agent","MondoFeed/0.1 (+Android RSS reader)").build()).execute()
        if(!response.isSuccessful) error("HTTP ${response.code}")
        response.body?.byteStream()?.use { input ->
            val p=Xml.newPullParser(); p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false); p.setInput(input,null)
            val out=mutableListOf<Article>(); var inItem=false; var tag=""; var title=""; var link=""; var desc=""; var date=""
            while(p.eventType!=XmlPullParser.END_DOCUMENT){
                when(p.eventType){
                    XmlPullParser.START_TAG -> { tag=p.name.lowercase(); if(tag=="item"||tag=="entry"){inItem=true;title="";link="";desc="";date=""}; if(inItem&&tag=="link"&&p.getAttributeValue(null,"href")!=null) link=p.getAttributeValue(null,"href") }
                    XmlPullParser.TEXT -> if(inItem) when(tag){"title"->title+=p.text;"link"->if(link.isBlank())link+=p.text;"description","summary","content"->desc+=p.text;"pubdate","published","updated"->date+=p.text}
                    XmlPullParser.END_TAG -> if(p.name.equals("item",true)||p.name.equals("entry",true)){ if(title.isNotBlank()&&link.isNotBlank()) out+=Article(title.trim(),link.trim(),desc.replace(Regex("<[^>]*>"),"").trim(),date.trim(),source.name,source.country,source.territory); inItem=false }
                }; p.next()
            }; out.take(30)
        } ?: emptyList()
    }
    suspend fun check(source:FeedSource)=runCatching{fetch(source);FeedStatus(source,true,"OK")}.getOrElse{FeedStatus(source,false,it.message?:"Errore")}
}

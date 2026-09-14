package it.mondofeed.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val Blue = Color(0xFF155EEF)
private val Navy = Color(0xFF071B3A)
private val Gold = Color(0xFFFFB800)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = lightColorScheme(primary = Blue, secondary = Gold, surfaceVariant = Color(0xFFF0F4FA))
            MaterialTheme(colorScheme = colors) { MondoFeedScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MondoFeedScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val favorites by vm.favoriteSources.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    var selectedSource by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var articleUrl by remember { mutableStateOf<String?>(null) }
    val grouped = remember(state.sources) { state.sources.filter { it.enabled }.groupBy { it.country }.toSortedMap() }

    if (articleUrl != null) {
        InternalArticle(url = articleUrl!!, onClose = { articleUrl = null })
        return
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(.60f).fillMaxHeight(), drawerContainerColor = Color.White) {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Navy, Blue))).padding(20.dp)) {
                    Column { Text("MONDOFEED", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("La stampa mondiale in una sola app", color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall) }
                }
                Text("Tutte le notizie", Modifier.fillMaxWidth().clickable { selectedCountry=null; selectedSource=null; scope.launch { drawer.close() } }.padding(16.dp), fontWeight = FontWeight.Bold, color = Blue)
                HorizontalDivider()
                LazyColumn {
                    grouped.forEach { (country, sources) ->
                        item("c-$country") { Text(country.uppercase(), Modifier.fillMaxWidth().background(Color(0xFFF3F6FB)).clickable { selectedCountry=country; selectedSource=null; scope.launch { drawer.close() } }.padding(12.dp,10.dp), fontWeight=FontWeight.Black, color=Navy) }
                        items(sources, key={it.id}) { source ->
                            Row(Modifier.fillMaxWidth().clickable { selectedCountry=source.country; selectedSource=source.name; scope.launch { drawer.close() } }.padding(start=18.dp,end=4.dp), verticalAlignment=Alignment.CenterVertically) {
                                Text(source.name, Modifier.weight(1f).padding(vertical=13.dp), style=MaterialTheme.typography.bodyMedium)
                                IconButton(onClick={vm.toggleFavoriteSource(source.id)}) { Text(if(source.id in favorites) "★" else "☆", color=if(source.id in favorites) Gold else Color.Gray, style=MaterialTheme.typography.titleLarge) }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(containerColor=Color(0xFFF5F7FB), topBar={
            TopAppBar(
                colors=TopAppBarDefaults.topAppBarColors(containerColor=Navy, titleContentColor=Color.White, navigationIconContentColor=Color.White),
                navigationIcon={IconButton(onClick={scope.launch{drawer.open()} }){Text("☰",color=Color.White,style=MaterialTheme.typography.headlineSmall)}},
                title={Column{Text("MondoFeed",fontWeight=FontWeight.Black);Text(selectedSource?:selectedCountry?:"Edizione globale",style=MaterialTheme.typography.labelSmall,color=Color.White.copy(.75f))}},
                actions={IconButton(onClick={vm.refresh()}){Text("↻",color=Color.White,style=MaterialTheme.typography.headlineSmall)}}
            )
        }) { padding ->
            Column(Modifier.padding(padding)) {
                Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Navy,Blue))).padding(16.dp)) {
                    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Cerca tra le notizie...")},singleLine=true,shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Color.White,unfocusedContainerColor=Color.White,focusedBorderColor=Gold,unfocusedBorderColor=Color.Transparent))
                }
                if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Gold)
                val shown=state.articles.filter { a -> (query.isBlank()||a.title.contains(query,true)||a.source.contains(query,true))&&(selectedCountry==null||a.country==selectedCountry)&&(selectedSource==null||a.source==selectedSource) }
                if(shown.isEmpty()&&!state.loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(state.error?:"Nessuna notizia per questo filtro",color=Color.Gray)}
                LazyColumn(Modifier.padding(horizontal=12.dp),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(shown,key={it.link}) { a ->
                        Card(Modifier.fillMaxWidth().clickable{articleUrl=a.link},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(3.dp)) {
                            if (!a.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(a.imageUrl).crossfade(true).build(),
                                    contentDescription = "Immagine della notizia: ${a.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(190.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(110.dp).background(Brush.horizontalGradient(listOf(Navy, Blue))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(a.source.uppercase(), color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).background(Blue,RoundedCornerShape(9.dp)));Spacer(Modifier.width(8.dp));Text(a.source.uppercase(),Modifier.weight(1f),color=Blue,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium);Text(a.displayDateTime,color=Color.Gray,style=MaterialTheme.typography.labelSmall)}
                                Text(a.title,Modifier.padding(top=10.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black,color=Navy)
                                if(a.description.isNotBlank()) Text(a.description.take(240),Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium,color=Color(0xFF526071))
                                Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){AssistChip(onClick={},label={Text(a.country)});Spacer(Modifier.weight(1f));Text("LEGGI NELL'APP  ›",color=Blue,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium)}
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InternalArticle(url:String,onClose:()->Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    BackHandler { if(webView?.canGoBack()==true) webView?.goBack() else onClose() }
    Scaffold(topBar={TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=Navy,titleContentColor=Color.White),navigationIcon={IconButton(onClick=onClose){Text("‹",color=Color.White,style=MaterialTheme.typography.headlineMedium)}},title={Text("Articolo",fontWeight=FontWeight.Bold)},actions={IconButton(onClick={webView?.reload()}){Text("↻",color=Color.White)}})}) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(factory={ctx->WebView(ctx).apply{webView=this;settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.loadsImagesAutomatically=true;webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView?,url:String?){loading=false}};webChromeClient=WebChromeClient();loadUrl(url)}},modifier=Modifier.fillMaxSize())
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Gold)
        }
    }
}

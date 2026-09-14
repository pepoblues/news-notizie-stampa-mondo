package it.mondofeed.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MaterialTheme{MondoFeedScreen{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(it)))}}}}}

@Composable fun MondoFeedScreen(open:(String)->Unit, vm:MainViewModel=viewModel()){
    val state by vm.state.collectAsState(); var q by remember{mutableStateOf("")}; var area by remember{mutableStateOf("Tutte")}
    val areas=listOf("Tutte","Italia","Europa","Americhe","Africa","Asia e Medio Oriente")
    Scaffold(topBar={TopAppBar(title={Text("MondoFeed")},actions={Button(onClick={vm.refresh()}){Text("Aggiorna")}})}){pad->Column(Modifier.padding(pad).padding(12.dp)){
        OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),label={Text("Cerca notizie")});
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical=8.dp)){areas.take(3).forEachIndexed{i,a->SegmentedButton(selected=area==a,onClick={area=a},shape=SegmentedButtonDefaults.itemShape(i,3)){Text(a)}}}
        if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        val shown=state.articles.filter{(q.isBlank()||it.title.contains(q,true))&&(area=="Tutte"||group(it)==area)}
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(shown){a->Card(Modifier.fillMaxWidth().clickable{open(a.link)}){Column(Modifier.padding(16.dp)){Text(a.source,style=MaterialTheme.typography.labelMedium);Text(a.title,style=MaterialTheme.typography.titleMedium);if(a.description.isNotBlank())Text(a.description.take(220),style=MaterialTheme.typography.bodyMedium);Text("${a.country} · ${a.territory}",style=MaterialTheme.typography.labelSmall)}}}}
    }}
}
private fun group(a:Article)=when{a.country=="Italia"->"Italia";a.territory.contains("America",true)->"Americhe";a.territory.contains("Africa",true)->"Africa";a.territory in listOf("Cina","Giappone","India","Russia","Medio Oriente")->"Asia e Medio Oriente";else->"Europa"}

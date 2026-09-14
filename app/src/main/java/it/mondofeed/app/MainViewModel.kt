package it.mondofeed.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(val loading:Boolean=false,val articles:List<Article> = emptyList(),val error:String?=null)
class MainViewModel(app:Application):AndroidViewModel(app){
    private val repo=RssRepository(); private val _state=MutableStateFlow(UiState()); val state:StateFlow<UiState> = _state
    init{refresh()}
    fun refresh()=viewModelScope.launch{_state.value=UiState(loading=true);val src=Catalog.load(getApplication()).filter{it.enabled};val results=src.map{s->async{runCatching{repo.fetch(s)}.getOrDefault(emptyList())}}.awaitAll().flatten().distinctBy{it.link};_state.value=UiState(articles=results,error=if(results.isEmpty())"Nessun feed raggiungibile. Controlla feeds.json." else null)}
}

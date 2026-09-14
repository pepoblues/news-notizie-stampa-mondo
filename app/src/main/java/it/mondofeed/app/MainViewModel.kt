package it.mondofeed.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false,
    val sources: List<FeedSource> = emptyList(),
    val articles: List<Article> = emptyList(),
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = RssRepository()
    private val preferences = app.getSharedPreferences("mondofeed", Context.MODE_PRIVATE)
    private val catalog = Catalog.load(app)

    private val _state = MutableStateFlow(UiState(sources = catalog))
    val state: StateFlow<UiState> = _state

    private val _favoriteSources = MutableStateFlow(
        preferences.getStringSet("favorite_sources", emptySet())?.toSet() ?: emptySet()
    )
    val favoriteSources: StateFlow<Set<String>> = _favoriteSources

    init { refresh() }

    fun toggleFavoriteSource(sourceId: String) {
        val updated = _favoriteSources.value.toMutableSet().apply {
            if (!add(sourceId)) remove(sourceId)
        }.toSet()
        _favoriteSources.value = updated
        preferences.edit().putStringSet("favorite_sources", updated).apply()
    }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        val results = catalog.filter { it.enabled }.map { source ->
            async { runCatching { repository.fetch(source) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
            .distinctBy { it.link }
            .sortedByDescending { it.publishedEpochMillis }

        _state.value = UiState(
            loading = false,
            sources = catalog,
            articles = results,
            error = if (results.isEmpty()) "Nessun feed raggiungibile. Controlla feeds.json." else null
        )
    }
}

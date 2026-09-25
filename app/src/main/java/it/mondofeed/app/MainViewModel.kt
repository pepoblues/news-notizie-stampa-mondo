package it.mondofeed.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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

    private val downloadSlots = Semaphore(permits = 6)
    private val articleCache = mutableMapOf<String, List<Article>>()
    private val loadedCategories = mutableSetOf<String>()

    private var selectedCategory: String? = "generalista"
    private var selectedSourceId: String? = null
    private var loadingJob: Job? = null

    init {
        loadCategory("generalista")
    }

    fun toggleFavoriteSource(sourceId: String) {
        val updated = _favoriteSources.value.toMutableSet().apply {
            if (!add(sourceId)) remove(sourceId)
        }.toSet()
        _favoriteSources.value = updated
        preferences.edit().putStringSet("favorite_sources", updated).apply()
    }

    fun showOverview() {
        selectedCategory = null
        selectedSourceId = null
        publishVisibleArticles()

        if (articleCache.isEmpty()) {
            loadCategory("generalista")
        }
    }

    fun selectCategory(category: String) {
        selectedCategory = category
        selectedSourceId = null
        loadCategory(category)
    }

    fun selectSource(sourceId: String) {
        val source = catalog.firstOrNull { it.id == sourceId } ?: return
        selectedCategory = source.category
        selectedSourceId = sourceId
        loadSource(sourceId)
    }

    fun refresh() {
        when {
            selectedSourceId != null -> loadSource(selectedSourceId!!, force = true)
            selectedCategory != null -> loadCategory(selectedCategory!!, force = true)
            else -> loadCategory("generalista", force = true)
        }
    }

    private fun loadCategory(category: String, force: Boolean = false) {
        val sources = catalog.filter {
            it.enabled && it.category.equals(category, ignoreCase = true)
        }

        if (sources.isEmpty()) {
            _state.value = _state.value.copy(
                loading = false,
                articles = emptyList(),
                error = "Nessuna fonte disponibile per questa categoria."
            )
            return
        }

        if (!force && category in loadedCategories) {
            publishVisibleArticles()
            return
        }

        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            sources.map { source ->
                async {
                    downloadSlots.withPermit {
                        source.id to runCatching { repository.fetch(source) }
                            .getOrDefault(emptyList())
                    }
                }
            }.awaitAll().forEach { (sourceId, articles) ->
                articleCache[sourceId] = articles
            }

            loadedCategories += category
            publishVisibleArticles("Nessun feed raggiungibile per questa categoria.")
        }
    }

    private fun loadSource(sourceId: String, force: Boolean = false) {
        val source = catalog.firstOrNull { it.id == sourceId && it.enabled } ?: return

        if (!force && articleCache.containsKey(sourceId)) {
            publishVisibleArticles()
            return
        }

        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            articleCache[sourceId] = downloadSlots.withPermit {
                runCatching { repository.fetch(source) }.getOrDefault(emptyList())
            }

            publishVisibleArticles(
                "Il feed ${source.name} non è raggiungibile in questo momento."
            )
        }
    }

    /**
     * Pubblica soltanto gli articoli pertinenti alla selezione corrente.
     * La cache resta indicizzata con source.id, non con source.name, perché nomi
     * come "Ansa" compaiono in più categorie con feed RSS differenti.
     */
    private fun publishVisibleArticles(emptyMessage: String? = null) {
        val visibleSourceIds: Set<String> = when {
            selectedSourceId != null -> setOf(selectedSourceId!!)

            selectedCategory != null -> catalog
                .asSequence()
                .filter {
                    it.enabled &&
                        it.category.equals(selectedCategory, ignoreCase = true)
                }
                .map { it.id }
                .toSet()

            else -> articleCache.keys.toSet()
        }

        val articles = visibleSourceIds
            .asSequence()
            .flatMap { sourceId -> articleCache[sourceId].orEmpty().asSequence() }
            .distinctBy { it.link }
            .sortedByDescending { it.publishedEpochMillis }
            .toList()

        _state.value = UiState(
            loading = false,
            sources = catalog,
            articles = articles,
            error = if (articles.isEmpty()) emptyMessage else null
        )
    }
}

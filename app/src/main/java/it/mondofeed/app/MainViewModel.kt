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
import java.util.concurrent.TimeUnit

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

    private val downloadSlots = Semaphore(permits = 10)

    // Cache completa usata quando si apre una categoria o una singola fonte.
    private val articleCache = mutableMapOf<String, List<Article>>()

    // Cache della pagina iniziale: conserva al massimo due articoli per fonte.
    private val overviewCache = mutableMapOf<String, List<Article>>()

    private val loadedCategories = mutableSetOf<String>()

    // La panoramica viene riutilizzata per 15 minuti.
    private var overviewLoadedAt = 0L
    private val overviewCacheDurationMillis = TimeUnit.MINUTES.toMillis(15)

    // Null indica la pagina iniziale "Tutte le notizie".
    private var selectedCategory: String? = null
    private var selectedSourceId: String? = null
    private var loadingJob: Job? = null

    init {
        loadOverview()
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
        loadOverview()
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
            else -> loadOverview(force = true)
        }
    }

    /**
     * Carica la pagina iniziale prendendo al massimo i due articoli più recenti
     * di ogni fonte abilitata. La cache è separata da quella delle categorie,
     * quindi l'apertura di una categoria o di una fonte continua a mostrare
     * l'elenco completo restituito dal repository.
     */
    private fun loadOverview(force: Boolean = false) {
        val sources = catalog
            .filter { it.enabled }
            .sortedWith(
                compareBy<FeedSource> {
                    if (it.category.equals("generalista", ignoreCase = true)) 0 else 1
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )

        if (sources.isEmpty()) {
            _state.value = _state.value.copy(
                loading = false,
                articles = emptyList(),
                error = "Nessuna fonte disponibile."
            )
            return
        }

        val cacheIsFresh =
            System.currentTimeMillis() - overviewLoadedAt < overviewCacheDurationMillis

        if (
            !force &&
            cacheIsFresh &&
            sources.all { overviewCache.containsKey(it.id) }
        ) {
            publishVisibleArticles()
            return
        }

        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            if (force || !cacheIsFresh) {
                overviewCache.clear()
            }

            _state.value = _state.value.copy(loading = true, error = null)

            sources.map { source ->
                async {
                    val articles = downloadSlots.withPermit {
                        runCatching {
                            repository.fetch(source)
                        }.getOrDefault(emptyList())
                            .sortedByDescending { it.publishedEpochMillis }
                            .take(2)
                    }

                    // Ogni coroutine torna sul Main dispatcher prima di aggiornare
                    // cache e stato, quindi le fonti veloci appaiono subito.
                    overviewCache[source.id] = articles
                    publishVisibleArticles(keepLoading = true)
                }
            }.awaitAll()

            overviewLoadedAt = System.currentTimeMillis()
            publishVisibleArticles(
                emptyMessage = "Nessun feed raggiungibile in questo momento.",
                keepLoading = false
            )
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
     * La pagina iniziale usa overviewCache; categorie e fonti usano articleCache.
     */
    private fun publishVisibleArticles(
        emptyMessage: String? = null,
        keepLoading: Boolean = false
    ) {
        val isOverview = selectedCategory == null && selectedSourceId == null

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

            else -> overviewCache.keys.toSet()
        }

        val selectedCache = if (isOverview) overviewCache else articleCache

        val articles = visibleSourceIds
            .asSequence()
            .flatMap { sourceId -> selectedCache[sourceId].orEmpty().asSequence() }
            .distinctBy {
                it.link
                    .trim()
                    .removeSuffix("/")
                    .lowercase()
            }
            .sortedByDescending { it.publishedEpochMillis }
            .toList()

        _state.value = UiState(
            loading = keepLoading,
            sources = catalog,
            articles = articles,
            error = if (articles.isEmpty() && !keepLoading) emptyMessage else null
        )
    }
}

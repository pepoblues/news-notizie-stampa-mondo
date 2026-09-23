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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.util.Locale

private val Blue = Color(0xFF155EEF)
private val Navy = Color(0xFF071B3A)
private val Gold = Color(0xFFFFB800)

private val thematicCategoryOrder = listOf(
    "generalista",
    "ambiente",
    "cinema",
    "cronaca",
    "cultura",
    "economia",
    "gastronomia",
    "musica",
    "politica",
    "religione",
    "scienza",
    "scuola",
    "sport",
    "tecnologia",
    "viaggi"
)

private fun categoryRank(category: String): Int {
    val index = thematicCategoryOrder.indexOfFirst {
        it.equals(category, ignoreCase = true)
    }
    return if (index >= 0) index else thematicCategoryOrder.size
}

private fun categoryLabel(category: String): String =
    when (category.lowercase(Locale.ITALIAN)) {
        "generalista" -> "Italia"
        "ambiente" -> "Ambiente e territorio"
        "cinema" -> "Film, serie TV e cinema"
        "cronaca" -> "Gossip e cronaca"
        "cultura" -> "Cultura, arte e storia"
        "economia" -> "Economia e finanze"
        "gastronomia" -> "Gastronomia"
        "musica" -> "Musica"
        "politica" -> "Politica ed esteri"
        "religione" -> "Religioni e spiritualità"
        "scienza" -> "Scienza e medicina"
        "scuola" -> "Scuola e università"
        "sport" -> "Sport"
        "tecnologia" -> "Tecnologia"
        "viaggi" -> "Viaggi e turismo"
        else -> category
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = lightColorScheme(
                primary = Blue,
                secondary = Gold,
                surfaceVariant = Color(0xFFF0F4FA)
            )
            MaterialTheme(colorScheme = colors) {
                MondoFeedScreen()
            }
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

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedSource by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var articleUrl by remember { mutableStateOf<String?>(null) }

    val groupedByCategory = remember(state.sources) {
        state.sources
            .asSequence()
            .filter { it.enabled }
            .groupBy { it.category.trim().ifBlank { "Altro" } }
            .mapValues { (_, sources) ->
                sources.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
    }

    val orderedCategories = remember(groupedByCategory) {
        groupedByCategory.keys.sortedWith(
            compareBy<String> { categoryRank(it) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { categoryLabel(it) }
        )
    }

    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    val selectedCategorySources = remember(selectedCategory, groupedByCategory) {
        selectedCategory
            ?.let { groupedByCategory[it].orEmpty().map { source -> source.name }.toSet() }
            .orEmpty()
    }

    if (articleUrl != null) {
    InternalBrowser(
        url = articleUrl!!,
        onClose = { articleUrl = null }
    )
    return
}

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxWidth(.60f)
                    .fillMaxHeight(),
                drawerContainerColor = Color.White
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Navy, Blue)))
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            "MONDOFEED",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "La stampa mondiale in una sola app",
                            color = Color.White.copy(.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text = "Tutte le notizie",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedCategory = null
                            selectedSource = null
                            scope.launch { drawer.close() }
                        }
                        .padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    color = Blue
                )
                HorizontalDivider()

                LazyColumn {
                    orderedCategories.forEach { category ->
                        val sources = groupedByCategory[category].orEmpty()
                        val isExpanded = expandedCategories[category]
                            ?: category.equals("generalista", ignoreCase = true)

                        item(key = "category-$category") {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = categoryLabel(category).uppercase(Locale.ITALIAN),
                                        fontWeight = FontWeight.Black,
                                        color = Navy
                                    )
                                },
                                supportingContent = {
                                    Text("${sources.size} fonti")
                                },
                                trailingContent = {
                                    Text(
                                        text = if (isExpanded) "▴" else "▾",
                                        color = Blue,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color(0xFFF3F6FB)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategories[category] = !isExpanded
                                    }
                            )
                            HorizontalDivider()
                        }

                        if (isExpanded) {
                            item(key = "all-$category") {
                                Text(
                                    text = "Tutte: ${categoryLabel(category)}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCategory = category
                                            selectedSource = null
                                            scope.launch { drawer.close() }
                                        }
                                        .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = Blue
                                )
                            }

                            items(sources, key = { it.id }) { source ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCategory = category
                                            selectedSource = source.name
                                            scope.launch { drawer.close() }
                                        }
                                        .padding(start = 18.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = source.name,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 13.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = { vm.toggleFavoriteSource(source.id) }
                                    ) {
                                        Text(
                                            text = if (source.id in favorites) "★" else "☆",
                                            color = if (source.id in favorites) Gold else Color.Gray,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color(0xFFF5F7FB),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Navy,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Text(
                                "☰",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    },
                    title = {
                        Column {
                            Text("MondoFeed", fontWeight = FontWeight.Black)
                            Text(
                                text = selectedSource
                                    ?: selectedCategory?.let(::categoryLabel)
                                    ?: "Edizione globale",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(.75f)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refresh() }) {
                            Text(
                                "↻",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Navy, Blue)))
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Cerca tra le notizie...") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Gold,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }

                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold)
                }

                val shown = state.articles.filter { article ->
                    val matchesQuery = query.isBlank() ||
                        article.title.contains(query, true) ||
                        article.source.contains(query, true)

                    val matchesCategory = selectedCategory == null ||
                        article.source in selectedCategorySources

                    val matchesSource = selectedSource == null ||
                        article.source == selectedSource

                    matchesQuery && matchesCategory && matchesSource
                }

                if (shown.isEmpty() && !state.loading) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            state.error ?: "Nessuna notizia per questo filtro",
                            color = Color.Gray
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shown, key = { it.link }) { article ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { articleUrl = article.link },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp)
                        ) {
                            if (!article.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(article.imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Immagine della notizia: ${article.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .background(
                                            Brush.horizontalGradient(listOf(Navy, Blue))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        article.source.uppercase(Locale.ITALIAN),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(9.dp)
                                            .background(Blue, RoundedCornerShape(9.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        article.source.uppercase(Locale.ITALIAN),
                                        Modifier.weight(1f),
                                        color = Blue,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        article.displayDateTime,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Text(
                                    article.title,
                                    Modifier.padding(top = 10.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Navy
                                )
                                if (article.description.isNotBlank()) {
                                    Text(
                                        article.description.take(240),
                                        Modifier.padding(top = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF526071)
                                    )
                                }
                                Row(
                                    Modifier.padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(article.country) }
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "LEGGI NELL'APP  ›",
                                        color = Blue,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

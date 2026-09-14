package it.mondofeed.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MondoFeedScreen(
                    open = { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MondoFeedScreen(
    open: (String) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val favoriteSources by vm.favoriteSources.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    var selectedSource by remember { mutableStateOf<String?>(null) }

    if (drawerState.isOpen) BackHandler { scope.launch { drawerState.close() } }

    val sourcesByCountry = remember(state.sources) {
        state.sources
            .filter { it.enabled }
            .groupBy { it.country }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxWidth(0.60f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = "Indice delle fonti",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    text = "Seleziona una nazione o un giornale",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Button(
                    onClick = {
                        selectedCountry = null
                        selectedSource = null
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(16.dp)
                ) { Text("Tutte le notizie") }
                Divider()

                LazyColumn {
                    sourcesByCountry.forEach { (country, countrySources) ->
                        item(key = "country-$country") {
                            Text(
                                text = country,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCountry = country
                                        selectedSource = null
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        items(countrySources, key = { it.id }) { source ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCountry = source.country
                                        selectedSource = source.name
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = source.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 10.dp)
                                )
                                IconButton(onClick = { vm.toggleFavoriteSource(source.id) }) {
                                    Text(
                                        text = if (source.id in favoriteSources) "★" else "☆",
                                        color = if (source.id in favoriteSources) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            }
                        }
                        item(key = "divider-$country") { Divider() }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    title = {
                        Column {
                            Text("MondoFeed")
                            Text(
                                text = selectedSource ?: selectedCountry ?: "Tutte le fonti",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    actions = {
                        Button(onClick = { vm.refresh() }) { Text("Aggiorna") }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cerca notizie") },
                    singleLine = true
                )

                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                val shownArticles = state.articles.filter { article ->
                    val matchesQuery = query.isBlank() ||
                        article.title.contains(query, ignoreCase = true) ||
                        article.source.contains(query, ignoreCase = true)
                    val matchesCountry = selectedCountry == null || article.country == selectedCountry
                    val matchesSource = selectedSource == null || article.source == selectedSource
                    matchesQuery && matchesCountry && matchesSource
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shownArticles, key = { article -> article.link }) { article ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { open(article.link) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = article.source,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = article.displayDateTime,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Text(
                                    text = article.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                if (article.description.isNotBlank()) {
                                    Text(
                                        text = article.description.take(220),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                                Text(
                                    text = "${article.country} | ${article.territory}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

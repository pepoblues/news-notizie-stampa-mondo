package it.mondofeed.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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

@Composable
fun MondoFeedScreen(
    open: (String) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedArea by remember { mutableStateOf("Tutte") }
    val areas = listOf(
        "Tutte",
        "Italia",
        "Europa",
        "Americhe",
        "Africa",
        "Asia e Medio Oriente"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MondoFeed") },
                actions = {
                    Button(onClick = { vm.refresh() }) {
                        Text("Aggiorna")
                    }
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                areas.forEach { area ->
                    FilterChip(
                        selected = selectedArea == area,
                        onClick = { selectedArea = area },
                        label = { Text(area) }
                    )
                }
            }

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
                val matchesArea = selectedArea == "Tutte" ||
                    articleArea(article) == selectedArea
                matchesQuery && matchesArea
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = shownArticles,
                    key = { article -> article.link }
                ) { article ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { open(article.link) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = article.source,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.titleMedium
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

private fun articleArea(article: Article): String = when {
    article.country == "Italia" -> "Italia"
    article.territory.contains("America", ignoreCase = true) -> "Americhe"
    article.territory.contains("Africa", ignoreCase = true) -> "Africa"
    article.territory in listOf("Cina", "Giappone", "India", "Russia", "Medio Oriente") ->
        "Asia e Medio Oriente"
    else -> "Europa"
}

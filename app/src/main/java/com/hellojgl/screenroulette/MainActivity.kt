package com.hellojgl.screenroulette

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hellojgl.screenroulette.ui.theme.ScreenRouletteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

data class Movie(
    val id: Int,
    val title: String,
    val year: String,
    val rating: String,
    val overview: String
)

private val httpClient = OkHttpClient()

private suspend fun tmdbRequest(path: String): JSONObject =
    withContext(Dispatchers.IO) {
        val token = BuildConfig.TMDB_API_KEY

        require(token.isNotBlank()) {
            "TMDB token is missing from local.properties"
        }

        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/$path")
            .header("Authorization", "Bearer $token")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("TMDB error: HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: throw Exception("Empty TMDB response")

            JSONObject(body)
        }
    }

private fun parseMovies(json: JSONObject): List<Movie> {
    val results = json.getJSONArray("results")

    return buildList {
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val date = item.optString("release_date")

            val rating = if (item.isNull("vote_average")) {
                "N/A"
            } else {
                String.format(
                    Locale.US,
                    "%.1f",
                    item.optDouble("vote_average")
                )
            }

            add(
                Movie(
                    id = item.getInt("id"),
                    title = item.optString("title", "Unknown"),
                    year = date.take(4).ifBlank { "N/A" },
                    rating = rating,
                    overview = item.optString("overview", "")
                )
            )
        }
    }
}

private suspend fun fetchRecommendations(
    excludedIds: Set<Int>
): List<Movie> {
    val candidates = mutableListOf<Movie>()

    // Retrieve several pages to increase variety.
    for (page in (1..10).shuffled().take(4)) {
        val response = tmdbRequest(
            "discover/movie?language=en-US&page=$page" +
                    "&sort_by=popularity.desc&vote_count.gte=100"
        )
        candidates.addAll(parseMovies(response))
    }

    return candidates
        .distinctBy { it.id }
        .filter { it.id !in excludedIds }
        .shuffled()
        .take(5)
}

private suspend fun fetchSimilarMovies(
    movieId: Int
): List<Movie> {
    val response = tmdbRequest(
        "movie/$movieId/similar?language=en-US&page=1"
    )

    return parseMovies(response)
        .filter { it.id != movieId }
        .distinctBy { it.id }
        .take(3)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScreenRouletteTheme {
                ScreenRouletteScreen()
            }
        }
    }
}

@Composable
fun ScreenRouletteScreen() {
    var recommendations by remember {
        mutableStateOf<List<Movie>>(emptyList())
    }
    var selectedMovie by remember {
        mutableStateOf<Movie?>(null)
    }
    var similarMovies by remember {
        mutableStateOf<List<Movie>>(emptyList())
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rollNumber by remember { mutableIntStateOf(0) }
    var seenIds by remember { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(rollNumber) {
        loading = true
        error = null

        try {
            val newMovies = fetchRecommendations(seenIds)
            recommendations = newMovies
            seenIds = seenIds + newMovies.map { it.id }
        } catch (e: Exception) {
            error = e.message ?: "Unable to load films"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(selectedMovie?.id) {
        similarMovies = emptyList()
        val movie = selectedMovie ?: return@LaunchedEffect

        try {
            similarMovies = fetchSimilarMovies(movie.id)
        } catch (e: Exception) {
            // Similar films are optional for this first version.
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (selectedMovie == null) {
                Text(
                    "ScreenRoulette",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(Modifier.height(8.dp))

                Text("Five films. One decision.")

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { rollNumber++ },
                    enabled = !loading
                ) {
                    Text("Roll Again")
                }

                Spacer(Modifier.height(16.dp))

                if (loading) {
                    CircularProgressIndicator()
                }

                error?.let {
                    Text(
                        "Error: $it",
                        color = MaterialTheme.colorScheme.error
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recommendations, key = { it.id }) { movie ->
                        MovieCard(movie) {
                            selectedMovie = movie
                        }
                    }
                }
            } else {
                val movie = selectedMovie!!

                TextButton(onClick = { selectedMovie = null }) {
                    Text("← Back to Recommendations")
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            movie.title,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Text("Year: ${movie.year}")
                        Text("TMDB rating: ${movie.rating}/10")

                        Spacer(Modifier.height(12.dp))

                        Text(
                            movie.overview.ifBlank {
                                "No synopsis available."
                            }
                        )

                        Spacer(Modifier.height(24.dp))

                        Text(
                            "You Might Also Like",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    items(similarMovies, key = { it.id }) { similar ->
                        MovieCard(similar) {
                            selectedMovie = similar
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieCard(
    movie: Movie,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                movie.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text("Year: ${movie.year}")
            Text("TMDB rating: ${movie.rating}/10")
        }
    }
}

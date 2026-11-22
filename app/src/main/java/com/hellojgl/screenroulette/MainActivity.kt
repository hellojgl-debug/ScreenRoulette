package com.hellojgl.screenroulette

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hellojgl.screenroulette.ui.theme.ScreenRouletteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.Locale

data class Movie(
    val id: Int,
    val title: String,
    val year: String,
    val rating: String,
    val overview: String,
    val posterPath: String?
)

data class ImdbInfo(
    val imdbRating: String = "N/A",
    val rottenTomatoes: String = "N/A"
)

private val httpClient = OkHttpClient()

private fun posterUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return "https://image.tmdb.org/t/p/w500$path"
}

private suspend fun tmdbRequest(path: String): JSONObject =
    withContext(Dispatchers.IO) {
        val token = BuildConfig.TMDB_API_KEY

        require(token.isNotBlank()) {
            "TMDB token is missing."
        }

        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/$path")
            .header("Authorization", "Bearer $token")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("TMDB HTTP ${response.code}")
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

            val rating = if (
                item.isNull("vote_average") ||
                item.optInt("vote_count") == 0
            ) {
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
                    overview = item.optString("overview", ""),
                    posterPath = item.optString("poster_path")
                        .takeIf { it.isNotBlank() && it != "null" }
                )
            )
        }
    }
}

private suspend fun fetchRecommendations(
    excludedIds: Set<Int>
): List<Movie> {
    val candidates = mutableListOf<Movie>()

    for (page in (1..20).shuffled().take(5)) {
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

private suspend fun fetchSimilarMovies(movieId: Int): List<Movie> {
    val response = tmdbRequest(
        "movie/$movieId/similar?language=en-US&page=1"
    )

    return parseMovies(response)
        .filter { it.id != movieId }
        .distinctBy { it.id }
        .take(3)
}

private suspend fun fetchImdbInfo(movieId: Int): ImdbInfo =
    withContext(Dispatchers.IO) {
        val omdbKey = BuildConfig.OMDB_API_KEY

        if (omdbKey.isBlank()) {
            return@withContext ImdbInfo()
        }

        val externalIds = tmdbRequest(
            "movie/$movieId/external_ids"
        )

        val imdbId = externalIds.optString("imdb_id")

        if (imdbId.isBlank() || imdbId == "null") {
            return@withContext ImdbInfo()
        }

        val url = "https://www.omdbapi.com/".toHttpUrl()
            .newBuilder()
            .addQueryParameter("apikey", omdbKey)
            .addQueryParameter("i", imdbId)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("OMDb HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: throw Exception("Empty OMDb response")

            val json = JSONObject(body)

            if (json.optString("Response") == "False") {
                throw Exception(
                    json.optString("Error", "OMDb lookup failed")
                )
            }

            val imdb = json.optString("imdbRating")
                .takeUnless { it.isBlank() || it == "N/A" }
                ?: "N/A"

            var rotten = "N/A"

            val ratings = json.optJSONArray("Ratings")

            if (ratings != null) {
                for (i in 0 until ratings.length()) {
                    val item = ratings.getJSONObject(i)

                    if (item.optString("Source") == "Rotten Tomatoes") {
                        rotten = item.optString("Value", "N/A")
                        break
                    }
                }
            }

            ImdbInfo(
                imdbRating = imdb,
                rottenTomatoes = rotten
            )
        }
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

    var seenIds by remember {
        mutableStateOf(emptySet<Int>())
    }

    var rollNumber by remember {
        mutableIntStateOf(0)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    // Navigation history: each selected film is added here.
    val navigationStack = remember {
        mutableStateListOf<Movie>()
    }

    val selectedMovie = navigationStack.lastOrNull()

    fun goBack() {
        if (navigationStack.isNotEmpty()) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
    }

    BackHandler(enabled = navigationStack.isNotEmpty()) {
        goBack()
    }

    LaunchedEffect(rollNumber) {
        loading = true
        error = null

        try {
            val newMovies = fetchRecommendations(seenIds)

            if (newMovies.isEmpty()) {
                error = "No unseen films found. Try restarting the app."
            } else {
                recommendations = newMovies
                seenIds = seenIds + newMovies.map { it.id }
            }
        } catch (e: Exception) {
            error = e.message ?: "Unable to load films"
        } finally {
            loading = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        if (selectedMovie == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Text(
                    text = "ScreenRoulette",
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
                    Spacer(Modifier.height(12.dp))
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        recommendations,
                        key = { it.id }
                    ) { movie ->
                        MovieCard(movie) {
                            navigationStack.add(movie)
                        }
                    }
                }
            }
        } else {
            MovieDetailsScreen(
                movie = selectedMovie,
                padding = innerPadding,
                onBack = { goBack() },
                onMovieSelected = { movie ->
                    navigationStack.add(movie)
                }
            )
        }
    }
}

@Composable
private fun MoviePoster(
    movie: Movie,
    modifier: Modifier = Modifier
) {
    val url = posterUrl(movie.posterPath)

    if (url == null) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No poster",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = "Poster for ${movie.title}",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
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
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MoviePoster(
                movie = movie,
                modifier = Modifier
                    .width(95.dp)
                    .height(140.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium
                )

                Text("Year: ${movie.year}")

                Text("TMDB: ${movie.rating}/10")

                Text(
                    text = "Tap for details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MovieDetailsScreen(
    movie: Movie,
    padding: PaddingValues,
    onBack: () -> Unit,
    onMovieSelected: (Movie) -> Unit
) {
    var similarMovies by remember(movie.id) {
        mutableStateOf<List<Movie>>(emptyList())
    }

    var imdbInfo by remember(movie.id) {
        mutableStateOf(ImdbInfo())
    }

    var similarLoading by remember(movie.id) {
        mutableStateOf(true)
    }

    var ratingsLoading by remember(movie.id) {
        mutableStateOf(true)
    }

    var similarError by remember(movie.id) {
        mutableStateOf<String?>(null)
    }

    var ratingsError by remember(movie.id) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(movie.id) {
        similarLoading = true
        similarError = null

        try {
            similarMovies = fetchSimilarMovies(movie.id)
        } catch (e: Exception) {
            similarError = e.message ?: "Unable to load similar films"
        } finally {
            similarLoading = false
        }
    }

    LaunchedEffect(movie.id) {
        ratingsLoading = true
        ratingsError = null

        try {
            imdbInfo = fetchImdbInfo(movie.id)
        } catch (e: Exception) {
            ratingsError = e.message ?: "Unable to load ratings"
        } finally {
            ratingsLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text("← Back")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    movie.title,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            item {
                MoviePoster(
                    movie = movie,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )
            }

            item {
                Text(
                    "${movie.year}  •  TMDB ${movie.rating}/10"
                )
            }

            item {
                if (ratingsLoading) {
                    Text("Loading IMDb and Rotten Tomatoes ratings...")
                } else {
                    Text("IMDb: ${imdbInfo.imdbRating}/10")
                    Text("Rotten Tomatoes: ${imdbInfo.rottenTomatoes}")

                    ratingsError?.let {
                        Text(
                            "Ratings unavailable: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                Text(
                    "Synopsis",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    movie.overview.ifBlank {
                        "No synopsis available."
                    }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))

                Text(
                    "You Might Also Like",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (similarLoading) {
                item {
                    CircularProgressIndicator()
                }
            } else if (similarError != null) {
                item {
                    Text(
                        "Similar films unavailable: $similarError",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (similarMovies.isEmpty()) {
                item {
                    Text("No similar films found.")
                }
            } else {
                items(
                    similarMovies,
                    key = { it.id }
                ) { similar ->
                    MovieCard(similar) {
                        onMovieSelected(similar)
                    }
                }
            }
        }
    }
}

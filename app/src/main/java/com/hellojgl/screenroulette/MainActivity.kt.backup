package com.hellojgl.screenroulette

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hellojgl.screenroulette.ui.theme.ScreenRouletteTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class Movie(
    val id: Int,
    val title: String,
    val year: String,
    val rating: String,
    val overview: String,
    val posterPath: String?,
    val genreIds: Set<Int>
)

data class ImdbInfo(
    val imdbRating: String = "N/A",
    val rottenTomatoes: String = "N/A"
)

data class MovieGenre(
    val id: Int,
    val name: String
)

data class MovieFilters(
    val genreIds: Set<Int>,
    val matchAll: Boolean,
    val startYear: Int,
    val endYear: Int
)

private val movieGenres = listOf(
    MovieGenre(28, "Action"),
    MovieGenre(12, "Adventure"),
    MovieGenre(16, "Animation"),
    MovieGenre(35, "Comedy"),
    MovieGenre(80, "Crime"),
    MovieGenre(99, "Documentary"),
    MovieGenre(18, "Drama"),
    MovieGenre(10751, "Family"),
    MovieGenre(14, "Fantasy"),
    MovieGenre(36, "History"),
    MovieGenre(27, "Horror"),
    MovieGenre(10402, "Music"),
    MovieGenre(9648, "Mystery"),
    MovieGenre(10749, "Romance"),
    MovieGenre(878, "Science Fiction"),
    MovieGenre(10770, "TV Movie"),
    MovieGenre(53, "Thriller"),
    MovieGenre(10752, "War"),
    MovieGenre(37, "Western")
)

private val genreNames = movieGenres.associate {
    it.id to it.name
}

private val httpClient = OkHttpClient()

private fun posterUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return "https://image.tmdb.org/t/p/w500$path"
}

private fun genreLabel(movie: Movie): String {
    val names = movie.genreIds
        .mapNotNull { genreNames[it] }
        .sorted()

    return if (names.isEmpty()) {
        "Genres unavailable"
    } else {
        names.joinToString(", ")
    }
}

private fun activeFilterLabel(filters: MovieFilters): String {
    val genres = if (filters.genreIds.isEmpty()) {
        "All genres"
    } else {
        filters.genreIds
            .mapNotNull { genreNames[it] }
            .sorted()
            .joinToString(", ")
    }

    val matching = if (filters.matchAll) {
        "Match All"
    } else {
        "Match Any"
    }

    return "$genres | $matching | " +
            "${filters.startYear}-${filters.endYear}"
}

private suspend fun tmdbRequest(
    path: String,
    parameters: Map<String, String> = emptyMap()
): JSONObject = withContext(Dispatchers.IO) {
    val token = BuildConfig.TMDB_API_KEY

    require(token.isNotBlank()) {
        "TMDB token is missing from local.properties"
    }

    val urlBuilder =
        "https://api.themoviedb.org/3/$path"
            .toHttpUrl()
            .newBuilder()

    parameters.forEach { (key, value) ->
        urlBuilder.addQueryParameter(key, value)
    }

    val request = Request.Builder()
        .url(urlBuilder.build())
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

            val genres = mutableSetOf<Int>()
            val genreArray = item.optJSONArray("genre_ids")

            if (genreArray != null) {
                for (j in 0 until genreArray.length()) {
                    genres.add(genreArray.getInt(j))
                }
            }

            add(
                Movie(
                    id = item.getInt("id"),
                    title = item.optString("title", "Unknown"),
                    year = date.take(4).ifBlank { "N/A" },
                    rating = rating,
                    overview = item.optString("overview", ""),
                    posterPath = item.optString("poster_path")
                        .takeIf {
                            it.isNotBlank() && it != "null"
                        },
                    genreIds = genres
                )
            )
        }
    }
}

private fun movieMatchesFilters(
    movie: Movie,
    filters: MovieFilters
): Boolean {
    val year = movie.year.toIntOrNull() ?: return false

    if (year !in filters.startYear..filters.endYear) {
        return false
    }

    if (filters.genreIds.isEmpty()) {
        return true
    }

    return if (filters.matchAll) {
        movie.genreIds.containsAll(filters.genreIds)
    } else {
        movie.genreIds.any { it in filters.genreIds }
    }
}

private suspend fun fetchRecommendations(
    excludedIds: Set<Int>,
    filters: MovieFilters
): List<Movie> {
    val parameters = mutableMapOf(
        "language" to "en-US",
        "sort_by" to "popularity.desc",
        "vote_count.gte" to "100",
        "primary_release_date.gte" to
                "${filters.startYear}-01-01",
        "primary_release_date.lte" to
                "${filters.endYear}-12-31"
    )

    if (filters.genreIds.isNotEmpty()) {
        val separator = if (filters.matchAll) "," else "|"

        parameters["with_genres"] =
            filters.genreIds
                .sorted()
                .joinToString(separator)
    }

    val firstPage = tmdbRequest(
        "discover/movie",
        parameters + ("page" to "1")
    )

    val totalPages = firstPage
        .optInt("total_pages", 1)
        .coerceIn(1, 500)

    val candidates = mutableListOf<Movie>()
    val candidateIds = mutableSetOf<Int>()

    fun collectValidMovies(response: JSONObject) {
        parseMovies(response).forEach { movie ->
            if (
                movie.id !in excludedIds &&
                movie.id !in candidateIds &&
                movieMatchesFilters(movie, filters)
            ) {
                candidates.add(movie)
                candidateIds.add(movie.id)
            }
        }
    }

    collectValidMovies(firstPage)

    val randomPages = (2..totalPages)
        .shuffled()
        .take(24)

    for (page in randomPages) {
        if (candidates.size >= 30) {
            break
        }

        val response = tmdbRequest(
            "discover/movie",
            parameters + ("page" to page.toString())
        )

        collectValidMovies(response)
    }

    return candidates
        .shuffled()
        .take(5)
}

private suspend fun fetchSimilarMovies(movieId: Int): List<Movie> {
    val response = tmdbRequest(
        "movie/$movieId/similar",
        mapOf(
            "language" to "en-US",
            "page" to "1"
        )
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

        val url = "https://www.omdbapi.com/"
            .toHttpUrl()
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
                .takeUnless {
                    it.isBlank() || it == "N/A"
                } ?: "N/A"

            var rotten = "N/A"

            val ratings = json.optJSONArray("Ratings")

            if (ratings != null) {
                for (i in 0 until ratings.length()) {
                    val item = ratings.getJSONObject(i)

                    if (
                        item.optString("Source") ==
                        "Rotten Tomatoes"
                    ) {
                        rotten = item.optString(
                            "Value",
                            "N/A"
                        )
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
    val currentYear = remember {
        Calendar.getInstance().get(Calendar.YEAR)
    }

    val defaultFilters = remember {
        MovieFilters(
            genreIds = emptySet(),
            matchAll = false,
            startYear = 1990,
            endYear = currentYear
        )
    }

    var activeFilters by remember {
        mutableStateOf(defaultFilters)
    }

    var selectedGenres by remember {
        mutableStateOf(emptySet<Int>())
    }

    var matchAll by remember {
        mutableStateOf(false)
    }

    var startYearText by remember {
        mutableStateOf("1990")
    }

    var endYearText by remember {
        mutableStateOf(currentYear.toString())
    }

    var filtersExpanded by remember {
        mutableStateOf(false)
    }

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

    val navigationStack = remember {
        mutableStateListOf<Movie>()
    }

    val selectedMovie = navigationStack.lastOrNull()

    val recommendationListState = rememberLazyListState()

    fun goBack() {
        if (navigationStack.isNotEmpty()) {
            navigationStack.removeAt(
                navigationStack.lastIndex
            )
        }
    }

    BackHandler(enabled = navigationStack.isNotEmpty()) {
        goBack()
    }

    // A new request is triggered whenever the active
    // filters or roll number changes.
    LaunchedEffect(activeFilters, rollNumber) {
        loading = true
        error = null
        recommendations = emptyList()

        try {
            val newMovies = fetchRecommendations(
                excludedIds = seenIds,
                filters = activeFilters
            )

            // Final verification before displaying films.
            val verifiedMovies = newMovies.filter {
                movieMatchesFilters(it, activeFilters)
            }

            if (verifiedMovies.isEmpty()) {
                error =
                    "No matching films found. " +
                            "Try broader filters."
            } else {
                recommendations = verifiedMovies
                seenIds = seenIds +
                        verifiedMovies.map { it.id }
            }
        } catch (e: CancellationException) {
            throw e
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
                    "ScreenRoulette",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(Modifier.height(6.dp))

                Text("Five films. One decision.")

                Spacer(Modifier.height(12.dp))

                Text(
                    "Active Filters",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    activeFilterLabel(activeFilters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        filtersExpanded = !filtersExpanded
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (filtersExpanded) {
                            "Hide Filters"
                        } else {
                            "Show Filters"
                        }
                    )
                }

                if (filtersExpanded) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "Genres",
                                style = MaterialTheme
                                    .typography.titleLarge
                            )

                            Text(
                                "Select one or more genres."
                            )
                        }

                        items(movieGenres) { genre ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedGenres =
                                            if (
                                                genre.id in selectedGenres
                                            ) {
                                                selectedGenres - genre.id
                                            } else {
                                                selectedGenres + genre.id
                                            }
                                    },
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked =
                                        genre.id in selectedGenres,
                                    onCheckedChange = { checked ->
                                        selectedGenres =
                                            if (checked) {
                                                selectedGenres + genre.id
                                            } else {
                                                selectedGenres - genre.id
                                            }
                                    }
                                )

                                Text(genre.name)
                            }
                        }

                        item {
                            Text(
                                "Genre matching",
                                style = MaterialTheme
                                    .typography.titleMedium
                            )

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !matchAll,
                                    onClick = { matchAll = false }
                                )

                                Text(
                                    "Match Any",
                                    modifier = Modifier.clickable {
                                        matchAll = false
                                    }
                                )
                            }

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = matchAll,
                                    onClick = { matchAll = true }
                                )

                                Text(
                                    "Match All",
                                    modifier = Modifier.clickable {
                                        matchAll = true
                                    }
                                )
                            }
                        }

                        item {
                            Text(
                                "Release years",
                                style = MaterialTheme
                                    .typography.titleMedium
                            )

                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = startYearText,
                                    onValueChange = {
                                        startYearText =
                                            it.filter { char ->
                                                char.isDigit()
                                            }.take(4)
                                    },
                                    label = { Text("From") },
                                    keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType =
                                                KeyboardType.Number
                                        ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = endYearText,
                                    onValueChange = {
                                        endYearText =
                                            it.filter { char ->
                                                char.isDigit()
                                            }.take(4)
                                    },
                                    label = { Text("To") },
                                    keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType =
                                                KeyboardType.Number
                                        ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val from =
                                        startYearText.toIntOrNull()

                                    val to =
                                        endYearText.toIntOrNull()

                                    if (
                                        from == null ||
                                        to == null ||
                                        from < 1874 ||
                                        to > currentYear ||
                                        from > to
                                    ) {
                                        error =
                                            "Enter a valid year range " +
                                                    "between 1874 and $currentYear."
                                    } else {
                                        val newFilters = MovieFilters(
                                            genreIds =
                                                selectedGenres.toSet(),
                                            matchAll = matchAll,
                                            startYear = from,
                                            endYear = to
                                        )

                                        // Clear the previous session
                                        // before applying new filters.
                                        seenIds = emptySet()
                                        recommendations = emptyList()

                                        filtersExpanded = false

                                        if (newFilters == activeFilters) {
                                            rollNumber++
                                        } else {
                                            activeFilters = newFilters
                                        }
                                    }
                                },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Apply Filters")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { rollNumber++ },
                        enabled = !loading
                    ) {
                        Text("Roll Again")
                    }

                    Text("${recommendations.size} films")
                }

                Spacer(Modifier.height(12.dp))

                if (loading) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(Modifier.height(12.dp))
                }

                if (!filtersExpanded) {
                    LazyColumn(
                        state = recommendationListState,
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
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
            }
        } else {
            MovieDetailsScreen(
                movie = selectedMovie,
                padding = innerPadding,
                onBack = { goBack() },
                onMovieSelected = {
                    navigationStack.add(it)
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
            horizontalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {
            MoviePoster(
                movie = movie,
                modifier = Modifier
                    .width(95.dp)
                    .height(140.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    movie.title,
                    style = MaterialTheme.typography.titleMedium
                )

                Text("Year: ${movie.year}")

                Text("TMDB: ${movie.rating}/10")

                Text(
                    "Genres: ${genreLabel(movie)}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    "Tap for details",
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            similarError =
                e.message ?: "Unable to load similar films"
        } finally {
            similarLoading = false
        }
    }

    LaunchedEffect(movie.id) {
        ratingsLoading = true
        ratingsError = null

        try {
            imdbInfo = fetchImdbInfo(movie.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ratingsError =
                e.message ?: "Unable to load ratings"
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
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
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
                    "${movie.year} • TMDB ${movie.rating}/10"
                )

                Text(
                    "Genres: ${genreLabel(movie)}"
                )
            }

            item {
                if (ratingsLoading) {
                    Text(
                        "Loading IMDb and Rotten Tomatoes ratings..."
                    )
                } else {
                    Text("IMDb: ${imdbInfo.imdbRating}/10")

                    Text(
                        "Rotten Tomatoes: " +
                                imdbInfo.rottenTomatoes
                    )

                    ratingsError?.let {
                        Text(
                            "Ratings unavailable: $it",
                            color = MaterialTheme
                                .colorScheme.error
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

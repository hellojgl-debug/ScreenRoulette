package com.hellojgl.screenroulette

import android.os.Bundle
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalContext
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
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale

data class Movie(
    val id: Int,
    val title: String,
    val year: String,
    val rating: String,
    val overview: String,
    val posterPath: String?,
    val genreIds: Set<Int>,
    val originalLanguage: String = ""
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
    val endYear: Int,
    val minRating: Double = 0.0,
    val originalLanguageFilter: String = "all",
    val streamingRegion: String = "",
    val streamingProviderId: Int = 0
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


private fun saveCategoryFilters(
    context: Context,
    category: ContentCategory,
    filters: MovieFilters
) {
    val data = JSONObject().apply {
        put("genreIds", JSONArray(filters.genreIds.sorted()))
        put("matchAll", filters.matchAll)
        put("startYear", filters.startYear)
        put("endYear", filters.endYear)
        put("minRating", filters.minRating)
        put("originalLanguageFilter", filters.originalLanguageFilter)
        put("streamingRegion", filters.streamingRegion)
        put("streamingProviderId", filters.streamingProviderId)
    }

    context.getSharedPreferences(
        "screenroulette_settings",
        Context.MODE_PRIVATE
    ).edit()
        .putString("filters_${category.name}", data.toString())
        .apply()
}

private fun loadCategoryFilters(
    context: Context,
    category: ContentCategory,
    fallback: MovieFilters
): MovieFilters {
    val raw = context.getSharedPreferences(
        "screenroulette_settings",
        Context.MODE_PRIVATE
    ).getString("filters_${category.name}", null) ?: return fallback

    return runCatching {
        val data = JSONObject(raw)
        val genres = data.optJSONArray("genreIds")
        val genreIds = mutableSetOf<Int>()

        if (genres != null) {
            for (i in 0 until genres.length()) {
                genreIds.add(genres.getInt(i))
            }
        }

        val from = data.optInt("startYear", fallback.startYear)
        val to = data.optInt("endYear", fallback.endYear)

        require(
            from >= 1874 &&
            from <= to &&
            to <= Calendar.getInstance().get(Calendar.YEAR)
        )

        val region = data.optString("streamingRegion", "")
            .takeIf { code ->
                streamingCountries.any { it.first == code }
            } ?: ""

        MovieFilters(
            genreIds = genreIds,
            matchAll = data.optBoolean("matchAll", false),
            startYear = from,
            endYear = to,
            minRating = data.optDouble("minRating", 0.0)
                .coerceIn(0.0, 9.0),
            originalLanguageFilter =
                data.optString("originalLanguageFilter", "all")
                    .takeIf {
                        it in setOf("all", "en", "international")
                    } ?: "all",
            streamingRegion = region,
            streamingProviderId =
                if (region.isBlank()) 0
                else data.optInt("streamingProviderId", 0)
                    .coerceAtLeast(0)
        )
    }.getOrDefault(fallback)
}

private val streamingCountries = listOf(
    "" to "Worldwide (no restriction)",
    "US" to "United States",
    "GB" to "United Kingdom",
    "ZA" to "South Africa",
    "CA" to "Canada",
    "AU" to "Australia",
    "NZ" to "New Zealand",
    "IE" to "Ireland",
    "IN" to "India",
    "DE" to "Germany",
    "FR" to "France",
    "ES" to "Spain",
    "IT" to "Italy",
    "NL" to "Netherlands",
    "JP" to "Japan",
    "KR" to "South Korea",
    "BR" to "Brazil"
)

data class StreamingProvider(val id: Int, val name: String)

data class StreamingAvailability(
    val subscription: List<String> = emptyList(),
    val rent: List<String> = emptyList(),
    val buy: List<String> = emptyList()
)

private suspend fun fetchStreamingProviders(
    category: ContentCategory,
    region: String
): List<StreamingProvider> {
    if (region.isBlank()) return emptyList()
    val type = if (category == ContentCategory.TV_SERIES) "tv" else "movie"
    val json = tmdbRequest(
        "watch/providers/$type",
        mapOf("watch_region" to region, "language" to "en-US")
    )
    val results = json.optJSONArray("results") ?: return emptyList()
    return buildList {
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val id = item.optInt("provider_id")
            val name = item.optString("provider_name")
            if (id > 0 && name.isNotBlank()) {
                add(StreamingProvider(id, name))
            }
        }
    }.distinctBy { it.id }.sortedBy { it.name.lowercase(Locale.ROOT) }
}

private suspend fun fetchStreamingAvailability(
    movieId: Int,
    category: ContentCategory,
    region: String
): StreamingAvailability {
    if (region.isBlank()) return StreamingAvailability()
    val type = if (category == ContentCategory.TV_SERIES) "tv" else "movie"
    val response = tmdbRequest("$type/$movieId/watch/providers")
    val country = response.optJSONObject("results")
        ?.optJSONObject(region) ?: return StreamingAvailability()
    fun names(key: String): List<String> {
        val array = country.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val name = array.getJSONObject(i).optString("provider_name")
                if (name.isNotBlank()) add(name)
            }
        }.distinct().sorted()
    }
    return StreamingAvailability(
        subscription = (names("flatrate") + names("free") + names("ads")).distinct().sorted(),
        rent = names("rent"),
        buy = names("buy")
    )
}

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
            "${filters.startYear}-${filters.endYear} | " +
            "TMDB >= ${String.format(Locale.US, "%.1f", filters.minRating)} | " +
            (when (filters.originalLanguageFilter) {
                "en" -> "English only"
                "international" -> "Non-English only"
                else -> "All languages"
            }) + " | " +
            (streamingCountries.firstOrNull {
                it.first == filters.streamingRegion
            }?.second ?: "Worldwide") +
            (if (filters.streamingProviderId > 0) " | Streaming provider selected" else "")
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
                    genreIds = genres,
                    originalLanguage =
                        item.optString("original_language", "")
                )
            )
        }
    }
}


private val tvGenreNames = mapOf(
    10759 to "Action & Adventure",
    16 to "Animation",
    35 to "Comedy",
    80 to "Crime",
    99 to "Documentary",
    18 to "Drama",
    10751 to "Family",
    10762 to "Kids",
    9648 to "Mystery",
    10763 to "News",
    10764 to "Reality",
    10765 to "Sci-Fi & Fantasy",
    10766 to "Soap",
    10767 to "Talk",
    10768 to "War & Politics",
    37 to "Western"
)

private fun parseTitles(
    json: JSONObject,
    category: ContentCategory
): List<Movie> {
    if (category != ContentCategory.TV_SERIES) {
        return parseMovies(json)
    }

    val results = json.getJSONArray("results")

    return buildList {
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val date = item.optString("first_air_date")
            val genres = mutableSetOf<Int>()
            val array = item.optJSONArray("genre_ids")

            if (array != null) {
                for (j in 0 until array.length()) {
                    genres.add(array.getInt(j))
                }
            }

            val votes = item.optInt("vote_count")
            val rating = if (votes > 0) {
                String.format(
                    Locale.US,
                    "%.1f",
                    item.optDouble("vote_average")
                )
            } else {
                "N/A"
            }

            add(
                Movie(
                    id = item.getInt("id"),
                    title = item.optString("name", "Unknown"),
                    year = date.take(4).ifBlank { "N/A" },
                    rating = rating,
                    overview = item.optString("overview", ""),
                    posterPath = item.optString("poster_path")
                        .takeIf { it.isNotBlank() && it != "null" },
                    genreIds = genres,
                    originalLanguage =
                        item.optString("original_language", "")
                )
            )
        }
    }
}

private fun titleGenreLabel(
    movie: Movie,
    category: ContentCategory
): String {
    val names = if (category == ContentCategory.TV_SERIES) {
        tvGenreNames
    } else {
        genreNames
    }

    return movie.genreIds
        .mapNotNull { names[it] }
        .sorted()
        .joinToString(", ")
        .ifBlank { "Genres unavailable" }
}

private suspend fun fetchCategoryRecommendations(
    category: ContentCategory,
    excludedIds: Set<Int>,
    filters: MovieFilters
): List<Movie> {
    val isTV = category == ContentCategory.TV_SERIES

    val effectiveGenres = if (
        category == ContentCategory.DOCUMENTARIES
    ) {
        setOf(99)
    } else {
        filters.genreIds
    }

    val effectiveFilters = filters.copy(
        genreIds = effectiveGenres,
        matchAll = if (
            category == ContentCategory.DOCUMENTARIES
        ) false else filters.matchAll
    )

    val parameters = mutableMapOf(
        "language" to "en-US",
        "sort_by" to "popularity.desc",
        "vote_count.gte" to "100"
    )

    if (filters.originalLanguageFilter == "en") {
        parameters["with_original_language"] = "en"
    }

    if (filters.minRating > 0.0) {
        parameters["vote_average.gte"] =
            filters.minRating.toString()
    }

    if (filters.streamingRegion.isNotBlank() &&
        filters.streamingProviderId > 0) {
        parameters["watch_region"] = filters.streamingRegion
        parameters["with_watch_providers"] =
            filters.streamingProviderId.toString()
        parameters["with_watch_monetization_types"] = "flatrate|free|ads"
    }

    if (isTV) {
        parameters["first_air_date.gte"] =
            "${filters.startYear}-01-01"
        parameters["first_air_date.lte"] =
            "${filters.endYear}-12-31"
    } else {
        parameters["primary_release_date.gte"] =
            "${filters.startYear}-01-01"
        parameters["primary_release_date.lte"] =
            "${filters.endYear}-12-31"
    }

    if (effectiveGenres.isNotEmpty()) {
        parameters["with_genres"] = effectiveGenres
            .sorted()
            .joinToString(
                if (effectiveFilters.matchAll) "," else "|"
            )
    }

    val endpoint = if (isTV) "discover/tv" else "discover/movie"

    val first = tmdbRequest(
        endpoint,
        parameters + ("page" to "1")
    )

    val totalPages = first.optInt("total_pages", 1)
        .coerceIn(1, 500)

    val candidates = mutableListOf<Movie>()
    val candidateIds = mutableSetOf<Int>()

    fun collect(response: JSONObject) {
        parseTitles(response, category).forEach { title ->
            if (
                title.id !in excludedIds &&
                title.id !in candidateIds &&
                movieMatchesFilters(title, effectiveFilters)
            ) {
                candidates.add(title)
                candidateIds.add(title.id)
            }
        }
    }

    collect(first)

    for (page in (2..totalPages).shuffled().take(24)) {
        if (candidates.size >= 30) break

        collect(
            tmdbRequest(
                endpoint,
                parameters + ("page" to page.toString())
            )
        )
    }

    return candidates.shuffled().take(5)
}

private fun matchesOriginalLanguage(
    movie: Movie,
    filters: MovieFilters
): Boolean {
    return when (filters.originalLanguageFilter) {
        "en" -> movie.originalLanguage == "en"
        "international" ->
            movie.originalLanguage.isNotBlank() &&
                movie.originalLanguage != "en"
        else -> true
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

    if (!matchesOriginalLanguage(movie, filters)) {
        return false
    }

    if (filters.minRating > 0.0) {
        val rating = movie.rating.toDoubleOrNull()
            ?: return false

        if (rating < filters.minRating) {
            return false
        }
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

private suspend fun fetchSimilarMovies(
    movieId: Int,
    category: ContentCategory
): List<Movie> {
    val response = tmdbRequest(
        if (category == ContentCategory.TV_SERIES) {
            "tv/$movieId/similar"
        } else {
            "movie/$movieId/similar"
        },
        mapOf(
            "language" to "en-US",
            "page" to "1"
        )
    )

    return parseTitles(response, category)
        .filter { it.id != movieId }
        .distinctBy { it.id }
        .take(3)
}

private suspend fun fetchImdbInfo(
    movieId: Int,
    category: ContentCategory
): ImdbInfo =
    withContext(Dispatchers.IO) {
        val omdbKey = BuildConfig.OMDB_API_KEY

        if (omdbKey.isBlank()) {
            return@withContext ImdbInfo()
        }

        val externalIds = tmdbRequest(
            if (category == ContentCategory.TV_SERIES) {
                "tv/$movieId/external_ids"
            } else {
                "movie/$movieId/external_ids"
            }
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

enum class ContentCategory {
    FILMS,
    TV_SERIES,
    DOCUMENTARIES
}

enum class AppearanceMode {
    DARK,
    LIGHT,
    SYSTEM
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = getSharedPreferences(
            "screenroulette_settings",
            MODE_PRIVATE
        )

        setContent {
            var appearance by remember {
                mutableStateOf(
                    runCatching {
                        AppearanceMode.valueOf(
                            preferences.getString(
                                "appearance",
                                "SYSTEM"
                            ) ?: "SYSTEM"
                        )
                    }.getOrDefault(AppearanceMode.SYSTEM)
                )
            }

            val systemDark = isSystemInDarkTheme()

            val useDarkTheme = when (appearance) {
                AppearanceMode.DARK -> true
                AppearanceMode.LIGHT -> false
                AppearanceMode.SYSTEM -> systemDark
            }

            ScreenRouletteTheme(
                darkTheme = useDarkTheme,
                dynamicColor = false
            ) {
                ScreenRouletteScreen(
                    appearance = appearance,
                    onAppearanceChange = { selected ->
                        appearance = selected
                        preferences.edit()
                            .putString("appearance", selected.name)
                            .apply()
                    }
                )
            }
        }
    }
}

@Composable
fun ScreenRouletteScreen(
    appearance: AppearanceMode,
    onAppearanceChange: (AppearanceMode) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember {
        mutableStateOf(ContentCategory.FILMS)
    }

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

    val initialFilters = remember(context, defaultFilters) {
        loadCategoryFilters(
            context,
            ContentCategory.FILMS,
            defaultFilters
        )
    }

    var activeFilters by remember {
        mutableStateOf(initialFilters)
    }

    val categoryGenres = when (selectedCategory) {
        ContentCategory.FILMS -> movieGenres
        ContentCategory.TV_SERIES -> tvGenreNames
            .map { (id, name) -> MovieGenre(id, name) }
            .sortedBy { it.name }
        ContentCategory.DOCUMENTARIES ->
            listOf(MovieGenre(99, "Documentary"))
    }

    val savedCategoryFilters = remember(context, defaultFilters) {
        mutableStateMapOf<ContentCategory, MovieFilters>().apply {
            ContentCategory.values().forEach { category ->
                this[category] = loadCategoryFilters(
                    context, category, defaultFilters
                )
            }
        }
    }

    var selectedGenres by remember {
        mutableStateOf(initialFilters.genreIds)
    }

    var matchAll by remember {
        mutableStateOf(initialFilters.matchAll)
    }

    var startYearText by remember {
        mutableStateOf(initialFilters.startYear.toString())
    }

    var endYearText by remember {
        mutableStateOf(initialFilters.endYear.toString())
    }

    var minimumRating by remember {
        mutableFloatStateOf(initialFilters.minRating.toFloat())
    }

    var originalLanguageFilter by remember {
        mutableStateOf(initialFilters.originalLanguageFilter)
    }

    var streamingRegion by remember { mutableStateOf(initialFilters.streamingRegion) }
    var streamingProviderId by remember { mutableIntStateOf(initialFilters.streamingProviderId) }
    var streamingProviders by remember {
        mutableStateOf<List<StreamingProvider>>(emptyList())
    }
    var streamingProvidersLoading by remember { mutableStateOf(false) }
    var streamingProvidersError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedCategory, streamingRegion) {
        streamingProviders = emptyList()
        streamingProvidersError = null
        if (streamingRegion.isNotBlank()) {
            streamingProvidersLoading = true
            try {
                streamingProviders = fetchStreamingProviders(
                    selectedCategory, streamingRegion
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                streamingProvidersError = e.message
            } finally {
                streamingProvidersLoading = false
            }
        }
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
    LaunchedEffect(selectedCategory, activeFilters, rollNumber) {
        loading = true
        error = null
        recommendations = emptyList()

        try {
            val newMovies = fetchCategoryRecommendations(
                category = selectedCategory,
                excludedIds = seenIds,
                filters = activeFilters
            )

            // Final verification before displaying films.
            val verifiedMovies = newMovies.filter {
                movieMatchesFilters(
                    it,
                    if (selectedCategory == ContentCategory.DOCUMENTARIES) {
                        activeFilters.copy(
                            genreIds = setOf(99),
                            matchAll = false
                        )
                    } else {
                        activeFilters
                    }
                )
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

                Spacer(Modifier.height(8.dp))

                Text(
                    "Appearance",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        AppearanceMode.DARK to "Dark",
                        AppearanceMode.LIGHT to "Light",
                        AppearanceMode.SYSTEM to "System"
                    ).forEach { (mode, label) ->
                        if (appearance == mode) {
                            Button(
                                onClick = {
                                    onAppearanceChange(mode)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    horizontal = 4.dp,
                                    vertical = 8.dp
                                )
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onAppearanceChange(mode)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    horizontal = 4.dp,
                                    vertical = 8.dp
                                )
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("Five films. One decision.")

                Spacer(Modifier.height(12.dp))

                Text(
                    "What would you like to watch?",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf(
                        ContentCategory.FILMS to "Films",
                        ContentCategory.TV_SERIES to "TV Series",
                        ContentCategory.DOCUMENTARIES to "Documentaries"
                    )

                    categories.forEach { (category, label) ->
                        if (selectedCategory == category) {
                            Button(
                                onClick = {
                                    if (selectedCategory != category) {
                                        val restored =
                                            savedCategoryFilters[category]
                                                ?: defaultFilters

                                        selectedCategory = category
                                        activeFilters = restored
                                        selectedGenres =
                                            restored.genreIds
                                        matchAll = restored.matchAll
                                        minimumRating =
                                            restored.minRating.toFloat()
                                        originalLanguageFilter =
                                            restored.originalLanguageFilter
                                        streamingRegion = restored.streamingRegion
                                        streamingProviderId = restored.streamingProviderId
                                        startYearText =
                                            restored.startYear.toString()
                                        endYearText =
                                            restored.endYear.toString()

                                        seenIds = emptySet()
                                        recommendations = emptyList()
                                        navigationStack.clear()
                                        filtersExpanded = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    horizontal = 4.dp,
                                    vertical = 10.dp
                                )
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    if (selectedCategory != category) {
                                        val restored =
                                            savedCategoryFilters[category]
                                                ?: defaultFilters

                                        selectedCategory = category
                                        activeFilters = restored
                                        selectedGenres =
                                            restored.genreIds
                                        matchAll = restored.matchAll
                                        minimumRating =
                                            restored.minRating.toFloat()
                                        originalLanguageFilter =
                                            restored.originalLanguageFilter
                                        streamingRegion = restored.streamingRegion
                                        streamingProviderId = restored.streamingProviderId
                                        startYearText =
                                            restored.startYear.toString()
                                        endYearText =
                                            restored.endYear.toString()

                                        seenIds = emptySet()
                                        recommendations = emptyList()
                                        navigationStack.clear()
                                        filtersExpanded = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    horizontal = 4.dp,
                                    vertical = 10.dp
                                )
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                if (selectedCategory != ContentCategory.FILMS) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (selectedCategory == ContentCategory.TV_SERIES) {
                            "Showing television series from TMDB."
                        } else {
                            "Showing documentary films from TMDB."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "Active Filters",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    if (selectedCategory == ContentCategory.DOCUMENTARIES) {
                        activeFilterLabel(
                            activeFilters.copy(
                                genreIds = setOf(99),
                                matchAll = false
                            )
                        )
                    } else {
                        activeFilterLabel(activeFilters)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Step 41: Worldwide Discovery shortcut
                if (
                    activeFilters.streamingRegion.isNotBlank() ||
                    activeFilters.streamingProviderId != 0
                ) {
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val updated = activeFilters.copy(
                                streamingRegion = "",
                                streamingProviderId = 0
                            )

                            activeFilters = updated
                            streamingRegion = ""
                            streamingProviderId = 0

                            savedCategoryFilters[selectedCategory] = updated
                            saveCategoryFilters(
                                context,
                                selectedCategory,
                                updated
                            )

                            seenIds = emptySet()
                            recommendations = emptyList()
                            navigationStack.clear()
                            filtersExpanded = false
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🌍 Switch to Worldwide Discovery")
                    }
                }

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
                                when (selectedCategory) {
                                    ContentCategory.FILMS ->
                                        "Film Genres"
                                    ContentCategory.TV_SERIES ->
                                        "TV Series Genres"
                                    ContentCategory.DOCUMENTARIES ->
                                        "Documentary"
                                },
                                style = MaterialTheme
                                    .typography.titleLarge
                            )

                            Text(
                                "Select one or more genres."
                            )
                        }

                        items(categoryGenres) { genre ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = selectedCategory !=
                                            ContentCategory.DOCUMENTARIES
                                    ) {
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
                                    enabled = selectedCategory !=
                                        ContentCategory.DOCUMENTARIES,
                                    checked = if (
                                        selectedCategory ==
                                        ContentCategory.DOCUMENTARIES
                                    ) {
                                        true
                                    } else {
                                        genre.id in selectedGenres
                                    },
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
                            if (
                                selectedCategory ==
                                ContentCategory.DOCUMENTARIES
                            ) {
                                Text(
                                    "Documentary is automatically selected."
                                )
                            } else {
                                Text(
                                    "Choose Match Any or Match All below."
                                )
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
                            Spacer(Modifier.height(8.dp))

                            Text(
                                "Original Language",
                                style = MaterialTheme.typography.titleMedium
                            )

                            listOf(
                                "all" to "All languages",
                                "en" to "English only",
                                "international" to "Non-English only"
                            ).forEach { (value, label) ->
                                Row(
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected =
                                            originalLanguageFilter == value,
                                        onClick = {
                                            originalLanguageFilter = value
                                        }
                                    )

                                    Text(
                                        text = label,
                                        modifier = Modifier.clickable {
                                            originalLanguageFilter = value
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                "Streaming Availability",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Worldwide discovery is unrestricted. Select a country and " +
                                    "a service to filter titles included with streaming.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            var countryMenuOpen by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { countryMenuOpen = true }) {
                                    Text(streamingCountries.firstOrNull {
                                        it.first == streamingRegion
                                    }?.second ?: "Choose country")
                                }
                                DropdownMenu(
                                    expanded = countryMenuOpen,
                                    onDismissRequest = { countryMenuOpen = false }
                                ) {
                                    streamingCountries.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                streamingRegion = code
                                                streamingProviderId = 0
                                                countryMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            if (streamingRegion.isNotBlank()) {
                                if (streamingProvidersLoading) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                streamingProvidersError?.let {
                                    Text("Providers unavailable: $it")
                                }
                                var providerMenuOpen by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { providerMenuOpen = true },
                                        enabled = !streamingProvidersLoading
                                    ) {
                                        Text(
                                            streamingProviders.firstOrNull {
                                                it.id == streamingProviderId
                                            }?.name ?: "Any service (no provider filter)"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = providerMenuOpen,
                                        onDismissRequest = { providerMenuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Any service (no provider filter)") },
                                            onClick = {
                                                streamingProviderId = 0
                                                providerMenuOpen = false
                                            }
                                        )
                                        streamingProviders.forEach { provider ->
                                            DropdownMenuItem(
                                                text = { Text(provider.name) },
                                                onClick = {
                                                    streamingProviderId = provider.id
                                                    providerMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Text(
                                    if (streamingProviderId == 0)
                                        "Country selected for availability details; " +
                                            "recommendations are not restricted by service."
                                    else
                                        "Recommendations must be listed for subscription, " +
                                            "free or ad-supported streaming on this service.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(8.dp))

                            Text(
                                "Minimum TMDB Rating",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                String.format(
                                    Locale.US,
                                    "%.1f / 10",
                                    minimumRating
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Slider(
                                value = minimumRating,
                                onValueChange = { minimumRating = it },
                                valueRange = 0f..9f,
                                steps = 17,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                if (minimumRating == 0f) {
                                    "No minimum rating"
                                } else {
                                    "Only titles rated at least " +
                                        String.format(
                                            Locale.US,
                                            "%.1f",
                                            minimumRating
                                        ) +
                                        " on TMDB"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
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


                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Step 45: Reset applied filters
                        OutlinedButton(
                            onClick = {
                                val reset = defaultFilters

                                selectedGenres = reset.genreIds
                                matchAll = reset.matchAll
                                startYearText = reset.startYear.toString()
                                endYearText = reset.endYear.toString()
                                minimumRating = reset.minRating.toFloat()
                                originalLanguageFilter =
                                    reset.originalLanguageFilter
                                streamingRegion = reset.streamingRegion
                                streamingProviderId =
                                    reset.streamingProviderId

                                savedCategoryFilters[selectedCategory] = reset
                                saveCategoryFilters(
                                    context,
                                    selectedCategory,
                                    reset
                                )

                                seenIds = emptySet()
                                recommendations = emptyList()
                                navigationStack.clear()
                                filtersExpanded = false

                                if (activeFilters == reset) {
                                    rollNumber++
                                } else {
                                    activeFilters = reset
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset to Defaults")
                        }

                        Button(
                            onClick = {
                                val from = startYearText.toIntOrNull()
                                val to = endYearText.toIntOrNull()

                                if (
                                    from == null ||
                                    to == null ||
                                    from < 1874 ||
                                    to > currentYear ||
                                    from > to
                                ) {
                                    error = "Enter a valid year range between 1874 and $currentYear."
                                } else {
                                    val newFilters = MovieFilters(
                                        genreIds = selectedGenres.toSet(),
                                        matchAll = matchAll,
                                        startYear = from,
                                        endYear = to,
                                        minRating = minimumRating.toDouble(),
                                        originalLanguageFilter =
                                            originalLanguageFilter,
                                        streamingRegion = streamingRegion,
                                        streamingProviderId = streamingProviderId
                                    )

                                    savedCategoryFilters[selectedCategory] =
                                        newFilters
                                    saveCategoryFilters(
                                        context,
                                        selectedCategory,
                                        newFilters
                                    )

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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply Filters")
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
                            MovieCard(
                                movie = movie,
                                category = selectedCategory,
                                streamingRegion = activeFilters.streamingRegion
                            ) {
                                navigationStack.add(movie)
                            }
                        }
                    }
                }
            }
        } else {
            MovieDetailsScreen(
                movie = selectedMovie,
                category = selectedCategory,
                streamingRegion = activeFilters.streamingRegion,
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

// Step 42: Compact recommendation cards
// Step 43: Streaming availability on cards
@Composable
private fun MovieCard(
    movie: Movie,
    category: ContentCategory,
    streamingRegion: String,
    onClick: () -> Unit
) {
    var availability by remember(
        movie.id, category, streamingRegion
    ) {
        mutableStateOf<StreamingAvailability?>(null)
    }

    var availabilityLoading by remember(
        movie.id, category, streamingRegion
    ) {
        mutableStateOf(streamingRegion.isNotBlank())
    }

    var availabilityError by remember(
        movie.id, category, streamingRegion
    ) {
        mutableStateOf(false)
    }

    LaunchedEffect(movie.id, category, streamingRegion) {
        availability = null
        availabilityError = false
        availabilityLoading = streamingRegion.isNotBlank()

        if (streamingRegion.isNotBlank()) {
            try {
                availability = fetchStreamingAvailability(
                    movie.id,
                    category,
                    streamingRegion
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                availabilityError = true
            } finally {
                availabilityLoading = false
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            MoviePoster(
                movie = movie,
                modifier = Modifier
                    .width(90.dp)
                    .height(135.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = if (category == ContentCategory.TV_SERIES) {
                        "TV Series • ${movie.year}"
                    } else if (category == ContentCategory.DOCUMENTARIES) {
                        "Documentary • ${movie.year}"
                    } else {
                        "Film • ${movie.year}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "★ ${movie.rating}/10",
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (movie.originalLanguage.isNotBlank()) {
                        Text(
                            text = movie.originalLanguage.uppercase(
                                Locale.ROOT
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = titleGenreLabel(movie, category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (streamingRegion.isNotBlank()) {
                    HorizontalDivider()

                    val streamingText = when {
                        availabilityLoading ->
                            "Checking streaming services…"

                        availabilityError ->
                            "Streaming information unavailable"

                        availability == null ->
                            "Streaming information unavailable"

                        availability!!.subscription.isNotEmpty() ->
                            "Streaming: " +
                                availability!!.subscription.joinToString(", ")

                        else ->
                            "No streaming information reported"
                    }

                    Text(
                        text = streamingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "View details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MovieDetailsScreen(
    movie: Movie,
    category: ContentCategory,
    streamingRegion: String,
    padding: PaddingValues,
    onBack: () -> Unit,
    onMovieSelected: (Movie) -> Unit
) {
    var streamingAvailability by remember(movie.id, streamingRegion) {
        mutableStateOf<StreamingAvailability?>(null)
    }
    var streamingError by remember(movie.id, streamingRegion) {
        mutableStateOf<String?>(null)
    }
    var streamingLoading by remember(movie.id, streamingRegion) {
        mutableStateOf(false)
    }

    LaunchedEffect(movie.id, category, streamingRegion) {
        streamingAvailability = null
        streamingError = null
        if (streamingRegion.isNotBlank()) {
            streamingLoading = true
            try {
                streamingAvailability = fetchStreamingAvailability(
                    movie.id, category, streamingRegion
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                streamingError = e.message
            } finally {
                streamingLoading = false
            }
        }
    }

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
            similarMovies = fetchSimilarMovies(movie.id, category)
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
            imdbInfo = fetchImdbInfo(movie.id, category)
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
                    "Genres: ${titleGenreLabel(movie, category)}"
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
                    "Streaming availability",
                    style = MaterialTheme.typography.titleLarge
                )
                if (streamingRegion.isBlank()) {
                    Text("Worldwide discovery. Select a country in Filters " +
                        "to see streaming availability.")
                } else if (streamingLoading) {
                    CircularProgressIndicator()
                } else if (streamingError != null) {
                    Text("Availability unavailable: $streamingError")
                } else {
                    val availability = streamingAvailability ?: StreamingAvailability()
                    Text("Country: " + (
                        streamingCountries.firstOrNull { it.first == streamingRegion }
                            ?.second ?: streamingRegion
                    ))
                    Text("Subscription / free / ads: " +
                        availability.subscription.ifEmpty { listOf("Not listed") }
                            .joinToString(", "))
                    Text(
                        "Provider information supplied by TMDB/JustWatch; " +
                            "availability may change.",
                        style = MaterialTheme.typography.bodySmall
                    )
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
                    MovieCard(
                        movie = similar,
                        category = category,
                        streamingRegion = streamingRegion
                    ) {
                        onMovieSelected(similar)
                    }
                }
            }
        }
    }
}

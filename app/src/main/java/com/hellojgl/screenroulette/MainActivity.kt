package com.hellojgl.screenroulette

import android.os.Build
import android.os.Bundle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hellojgl.screenroulette.ui.theme.ScreenRouletteTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion


// Static roulette loading indicator
@Composable
// SCREENROULETTE_TOUCH_ACCESSIBILITY_V2
private fun RouletteLoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier.size(42.dp)
        ) {
            val radius = size.minDimension / 2f
            val centre = Offset(
                size.width / 2f,
                size.height / 2f
            )

            drawCircle(
                color = Color(0xFFD6B66A),
                radius = radius
            )

            drawCircle(
                color = Color(0xFF10291F),
                radius = radius * 0.87f
            )

            for (index in 0 until 12) {
                drawArc(
                    color = if (index % 2 == 0) {
                        Color(0xFFB3262E)
                    } else {
                        Color(0xFF181818)
                    },
                    startAngle = index * 30f - 90f,
                    sweepAngle = 27.5f,
                    useCenter = true,
                    style = androidx.compose.ui.graphics.drawscope.Fill,
                    topLeft = Offset(
                        centre.x - radius * 0.79f,
                        centre.y - radius * 0.79f
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        radius * 1.58f,
                        radius * 1.58f
                    )
                )
            }

            drawCircle(
                color = Color(0xFFD6B66A),
                radius = radius * 0.20f
            )

            drawCircle(
                color = Color(0xFF10291F),
                radius = radius * 0.11f
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            "Finding recommendations...",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

data class Movie(
    val id: Int,
    val title: String,
    val year: String,
    val rating: String,
    val overview: String,
    val posterPath: String?,
    val genreIds: Set<Int>,
    val originalLanguage: String = "",
    val releaseDate: String = ""
)

// Step 46: Persistent Watchlist
data class WatchlistEntry(
    val movie: Movie,
    val category: ContentCategory
)

private fun loadWatchlist(context: Context): List<WatchlistEntry> {
    return runCatching {
        val raw = context.getSharedPreferences(
            "screenroulette_watchlist", Context.MODE_PRIVATE
        ).getString("entries", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val movieJson = item.getJSONObject("movie")
                val genres = movieJson.optJSONArray("genres")
                val genreIds = mutableSetOf<Int>()
                if (genres != null) {
                    for (j in 0 until genres.length()) {
                        genreIds.add(genres.getInt(j))
                    }
                }
                add(
                    WatchlistEntry(
                        movie = Movie(
                            id = movieJson.getInt("id"),
                            title = movieJson.getString("title"),
                            year = movieJson.optString("year"),
                            rating = movieJson.optString("rating"),
                            overview = movieJson.optString("overview"),
                            posterPath = movieJson.optString("poster")
                                .takeIf { it.isNotBlank() },
                            genreIds = genreIds,
                            originalLanguage =
                                movieJson.optString("language")
                        ),
                        category = ContentCategory.valueOf(
                            item.getString("category")
                        )
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveWatchlist(
    context: Context,
    entries: List<WatchlistEntry>
) {
    val array = JSONArray()
    entries.forEach { entry ->
        val movie = entry.movie
        val movieJson = JSONObject().apply {
            put("id", movie.id)
            put("title", movie.title)
            put("year", movie.year)
            put("rating", movie.rating)
            put("overview", movie.overview)
            put("poster", movie.posterPath ?: "")
            put("genres", JSONArray(movie.genreIds.toList()))
            put("language", movie.originalLanguage)
        }
        array.put(
            JSONObject().apply {
                put("category", entry.category.name)
                put("movie", movieJson)
            }
        )
    }
    context.getSharedPreferences(
        "screenroulette_watchlist", Context.MODE_PRIVATE
    ).edit().putString("entries", array.toString()).apply()
}

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

private const val DYSTOPIAN_GENRE_ID = -1
private const val DYSTOPIA_TMDB_KEYWORD_ID = "4565"
private const val DEFAULT_RECOMMENDATION_COUNT = 3
private const val MAX_RECOMMENDATION_COUNT = 5
private const val RECOMMENDATION_COUNT_KEY = "recommendation_count"
private const val MAX_DISCOVERY_PAGES = 500

private val movieGenres = listOf(
    MovieGenre(28, "Action"),
    MovieGenre(12, "Adventure"),
    MovieGenre(16, "Animation"),
    MovieGenre(35, "Comedy"),
    MovieGenre(80, "Crime"),
    MovieGenre(99, "Documentary"),
    MovieGenre(18, "Drama"),
    MovieGenre(DYSTOPIAN_GENRE_ID, "Dystopian"),
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

// Step 4: Roulette button styling
private val httpClient = OkHttpClient()

// Performance optimisation 2: TMDB response cache
private data class CachedTmdbResponse(
    val json: String,
    val savedAt: Long
)

private val tmdbCache =
    java.util.concurrent.ConcurrentHashMap<String, CachedTmdbResponse>()

private const val EARLIEST_RELEASE_YEAR = 1870

private const val TMDB_CACHE_DURATION_MS = 10 * 60 * 1000L

private fun shouldCacheTmdb(path: String): Boolean {
    return path.startsWith("watch/providers/") ||
        path.endsWith("/watch/providers") ||
        path.endsWith("/similar") ||
        path.endsWith("/external_ids") ||
        path.endsWith("/videos")
}


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
            from >= EARLIEST_RELEASE_YEAR &&
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

// Step 6: Optimised poster sizes
private fun posterUrl(
    path: String?,
    large: Boolean = false
): String? {
    if (path.isNullOrBlank()) return null
    val width = if (large) "w500" else "w185"
    return "https://image.tmdb.org/t/p/$width$path"
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
    val urlBuilder =
        "${BuildConfig.BACKEND_BASE_URL}/api/tmdb/$path"
            .toHttpUrl()
            .newBuilder()

    parameters.forEach { (key, value) ->
        urlBuilder.addQueryParameter(key, value)
    }

    val url = urlBuilder.build()
    val cacheKey = url.toString()
    val cacheEnabled = shouldCacheTmdb(path)

    if (cacheEnabled) {
        val cached = tmdbCache[cacheKey]
        if (
            cached != null &&
            android.os.SystemClock.elapsedRealtime() -
                cached.savedAt < TMDB_CACHE_DURATION_MS
        ) {
            return@withContext JSONObject(cached.json)
        }
    }

    val request = Request.Builder()
        .url(url)
        .build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("TMDB HTTP ${response.code}")
        }

        val body = response.body?.string()
            ?: throw Exception("Empty TMDB response")

        val parsed = JSONObject(body)

        if (cacheEnabled) {
            if (tmdbCache.size >= 150) {
                tmdbCache.clear()
            }

            tmdbCache[cacheKey] = CachedTmdbResponse(
                json = body,
                savedAt = android.os.SystemClock.elapsedRealtime()
            )
        }

        parsed
    }
}

private fun JSONObject.tmdbPosterPath(): String? =
    optString("poster_path").takeIf { it.startsWith('/') && it.length > 1 }

private fun parseMovies(json: JSONObject): List<Movie> {
    val results = json.getJSONArray("results")

    return buildList {
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val posterPath = item.tmdbPosterPath() ?: continue
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
                    posterPath = posterPath,
                    genreIds = genres,
                    originalLanguage =
                        item.optString("original_language", ""),
                    releaseDate = date
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
    DYSTOPIAN_GENRE_ID to "Dystopian",
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
            val posterPath = item.tmdbPosterPath() ?: continue
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
                    posterPath = posterPath,
                    genreIds = genres,
                    originalLanguage =
                        item.optString("original_language", ""),
                    releaseDate = date
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
    filters: MovieFilters,
    newestFirst: Boolean,
    resultLimit: Int
): List<Movie> {
    val standardGenres = filters.genreIds - DYSTOPIAN_GENRE_ID
    val needsSeparateQueries = category != ContentCategory.DOCUMENTARIES &&
        DYSTOPIAN_GENRE_ID in filters.genreIds && standardGenres.isNotEmpty() &&
        !filters.matchAll

    if (!needsSeparateQueries) {
        return fetchDiscoveryRecommendations(category, excludedIds, filters, newestFirst, resultLimit)
    }

    // TMDB combines genre and keyword parameters with AND. Query both sides for Match Any.
    val candidates = kotlinx.coroutines.coroutineScope {
        listOf(standardGenres, setOf(DYSTOPIAN_GENRE_ID)).map { genres ->
            async {
                fetchDiscoveryRecommendations(
                    category, excludedIds, filters.copy(genreIds = genres), newestFirst, resultLimit,
                    extraPageLimit = 20
                )
            }
        }.awaitAll().flatten().distinctBy { it.id }
    }

    return if (newestFirst) {
        candidates.sortedByDescending { it.releaseDate }.take(resultLimit)
    } else {
        selectSpinRecommendations(candidates, excludedIds, filters, resultLimit)
    }
}

private suspend fun fetchDiscoveryRecommendations(
    category: ContentCategory,
    excludedIds: Set<Int>,
    filters: MovieFilters,
    newestFirst: Boolean,
    resultLimit: Int,
    extraPageLimit: Int = 40
): List<Movie> {
    val diagnosticStart = android.os.SystemClock.elapsedRealtime()
    var diagnosticPages = 0
    var diagnosticExcluded = 0
    var diagnosticParsed = 0
    var diagnosticFailedPages = 0

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

    val dateParameter = if (isTV) "first_air_date" else "primary_release_date"
    val endDate = LocalDate.of(filters.endYear, 12, 31)
    val dateCutoff = if (newestFirst) minOf(endDate, LocalDate.now()) else endDate
    val parameters = mutableMapOf(
        "language" to "en-US",
        "sort_by" to "$dateParameter.${if (newestFirst) "desc" else "asc"}",
        "$dateParameter.gte" to "${filters.startYear}-01-01",
        "$dateParameter.lte" to dateCutoff.toString()
    )

    if (filters.originalLanguageFilter == "en") {
        parameters["with_original_language"] = "en"
    }

    if (filters.minRating > 0.0) {
        parameters["vote_average.gte"] =
            String.format(Locale.US, "%.2f", filters.minRating)
    }

    if (filters.streamingRegion.isNotBlank() &&
        filters.streamingProviderId > 0) {
        parameters["watch_region"] = filters.streamingRegion
        parameters["with_watch_providers"] =
            filters.streamingProviderId.toString()
        parameters["with_watch_monetization_types"] = "flatrate|free|ads"
    }

    val dystopian = DYSTOPIAN_GENRE_ID in effectiveGenres
    val tmdbGenres = effectiveGenres - DYSTOPIAN_GENRE_ID
    if (dystopian) {
        parameters["with_keywords"] = DYSTOPIA_TMDB_KEYWORD_ID
    }
    if (tmdbGenres.isNotEmpty()) {
        parameters["with_genres"] = tmdbGenres
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

    val candidates = mutableListOf<Movie>()
    val candidateIds = mutableSetOf<Int>()

    fun collect(response: JSONObject) {
        diagnosticPages++
        parseTitles(response, category).forEach { movie ->
            val title = if (dystopian) {
                movie.copy(genreIds = movie.genreIds + DYSTOPIAN_GENRE_ID)
            } else {
                movie
            }
            diagnosticParsed++
            if (title.id in excludedIds) diagnosticExcluded++
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

    suspend fun requestPage(query: Map<String, String>, page: Int): JSONObject? =
        try {
            tmdbRequest(endpoint, query + ("page" to page.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ScreenRouletteSpin", "TMDB discovery page $page failed", e)
            null
        }

    fun collectPage(response: JSONObject?) {
        if (response == null) {
            diagnosticFailedPages++
        } else {
            collect(response)
        }
    }

    val targetCandidateCount = if (newestFirst) resultLimit else 20
    if (newestFirst) collect(first)

    if (!newestFirst && totalPages >= MAX_DISCOVERY_PAGES && filters.startYear < filters.endYear) {
        // Sample years throughout the range when TMDB caps the full query at 500 pages.
        val years = (filters.startYear..filters.endYear).shuffled().take(extraPageLimit / 2)
        for (batch in years.chunked(4)) {
            if (candidates.size >= targetCandidateCount) break

            kotlinx.coroutines.coroutineScope {
                batch.map { year ->
                    async {
                        val query = parameters + mapOf(
                            "$dateParameter.gte" to "$year-01-01",
                            "$dateParameter.lte" to "$year-12-31"
                        )
                        val yearFirst = requestPage(query, 1)
                        val yearPages = yearFirst?.optInt("total_pages", 0) ?: return@async null
                        val page = randomDiscoveryPages(yearPages).firstOrNull() ?: return@async yearFirst
                        if (page == 1) yearFirst else requestPage(query, page)
                    }
                }.awaitAll().forEach(::collectPage)
            }
        }
        if (candidates.isEmpty()) collect(first)
    } else {
        val pages = if (newestFirst) {
            (2..totalPages.coerceAtMost(MAX_DISCOVERY_PAGES)).take(extraPageLimit)
        } else {
            randomDiscoveryPages(totalPages).take(extraPageLimit)
        }

        for (batch in pages.chunked(4)) {
            if (candidates.size >= targetCandidateCount) break

            kotlinx.coroutines.coroutineScope {
                batch.map { page ->
                    async { if (page == 1) first else requestPage(parameters, page) }
                }.awaitAll().forEach(::collectPage)
            }
        }
    }

    val elapsed = android.os.SystemClock.elapsedRealtime() -
        diagnosticStart

    android.util.Log.d(
        "ScreenRouletteSpin",
        "category=$category " +
        "pages=$diagnosticPages " +
        "parsed=$diagnosticParsed " +
        "failedPages=$diagnosticFailedPages " +
        "excluded=$diagnosticExcluded " +
        "eligible=${candidates.size} " +
        "previouslySeen=${excludedIds.size} " +
        "durationMs=$elapsed"
    )

    if (candidates.isEmpty() && diagnosticFailedPages > 0) {
        throw java.io.IOException(
            "Some film requests failed and no matching " +
            "recommendations could be retrieved. " +
            "Please check your connection and try again."
        )
    }

    if (newestFirst) return candidates.take(targetCandidateCount)

    return selectSpinRecommendations(
        candidates = candidates,
        excludedIds = emptySet(),
        filters = effectiveFilters,
        limit = resultLimit
    )
}

internal fun randomDiscoveryPages(
    totalPages: Int,
    random: Random = Random.Default
): List<Int> = (1..totalPages.coerceAtMost(MAX_DISCOVERY_PAGES)).shuffled(random)

internal fun selectSpinRecommendations(
    candidates: List<Movie>,
    excludedIds: Set<Int>,
    filters: MovieFilters,
    limit: Int = DEFAULT_RECOMMENDATION_COUNT
): List<Movie> {
    if (limit <= 0) return emptyList()

    return candidates
        .asSequence()
        .filter { it.id !in excludedIds }
        .filter { movieMatchesFilters(it, filters) }
        .distinctBy { it.id }
        .toList()
        .shuffled()
        .take(limit)
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

private suspend fun fetchYouTubeTrailer(movie: Movie, category: ContentCategory): String? {
    val type = if (category == ContentCategory.TV_SERIES) "tv" else "movie"
    val path = "$type/${movie.id}/videos"

    suspend fun findTrailer(parameters: Map<String, String>): String? {
        val results = tmdbRequest(path, parameters).getJSONArray("results")
        val videos = (0 until results.length()).mapNotNull { index ->
            results.optJSONObject(index)?.let { video ->
                TrailerVideo(
                    site = video.optString("site"),
                    type = video.optString("type"),
                    key = video.optString("key"),
                    official = video.optBoolean("official")
                )
            }
        }
        return selectYouTubeTrailer(videos)
    }

    return findTrailer(emptyMap()) ?: movie.originalLanguage
        .takeIf { it != "en" && it.matches(Regex("[a-z]{2}")) }
        ?.let { findTrailer(mapOf("language" to it)) }
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

        val url = "${BuildConfig.BACKEND_BASE_URL}/api/omdb"
            .toHttpUrl()
            .newBuilder()
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

private const val SPLASH_SPIN_DURATION_MS = 1_250
private const val SPLASH_MINIMUM_AFTER_START_MS = 1_700L
private const val SPLASH_LOAD_TIMEOUT_MS = 5_000L

@Composable
private fun RouletteSplashScreen(startSpin: Boolean, onSpinFinished: () -> Unit) {
    val wheelAngle = remember { Animatable(127f) }
    LaunchedEffect(startSpin) {
        if (startSpin) {
            wheelAngle.animateTo(
                targetValue = 487f,
                animationSpec = tween(
                    durationMillis = SPLASH_SPIN_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
            onSpinFinished()
        }
    }
    val ballAngleDegrees = -127f

    val green = Color(0xFF073D2B)
    val gold = Color(0xFFD6B66A)
    val red = Color(0xFFB71C1C)
    val black = Color(0xFF171717)

    val numbers = remember {
        listOf(
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34,
            6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
            24, 16, 33, 1, 20, 14, 31, 9, 22, 18,
            29, 7, 28, 12, 35, 3, 26
        )
    }

    val redNumbers = remember {
        setOf(
            1, 3, 5, 7, 9, 12, 14, 16, 18,
            19, 21, 23, 25, 27, 30, 32, 34, 36
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(green)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SCREENROULETTE",
            style = MaterialTheme.typography.headlineMedium,
            color = gold
        )

        Spacer(Modifier.height(34.dp))

        Box(
            modifier = Modifier.size(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val sweep = 360f / numbers.size

                drawCircle(
                    color = gold,
                    radius = radius
                )

                drawCircle(
                    color = Color(0xFF33210F),
                    radius = radius * 0.965f
                )

                drawCircle(
                    color = gold,
                    radius = radius * 0.92f
                )

                drawCircle(
                    color = black,
                    radius = radius * 0.895f
                )

                // Rotate numbered wheel clockwise.
                rotate(
                    degrees = wheelAngle.value,
                    pivot = Offset(cx, cy)
                ) {
                    for (i in numbers.indices) {
                        val number = numbers[i]

                        val colour = when {
                            number == 0 -> Color(0xFF138447)
                            number in redNumbers -> red
                            else -> black
                        }

                        drawArc(
                            color = colour,
                            startAngle = -90f +
                                i * sweep - sweep / 2f,
                            sweepAngle = sweep - 0.8f,
                            useCenter = true,
                            topLeft = Offset(
                                cx - radius * 0.86f,
                                cy - radius * 0.86f
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                radius * 1.72f,
                                radius * 1.72f
                            )
                        )
                    }

                    // SCREENROULETTE_ACCESSIBLE_POCKETS_V1
                    // Pocket boundaries remain visible without colour.
                    drawCircle(
                        color = Color(0xFFFFE6A3),
                        radius = radius * 0.86f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.5.dp.toPx()
                        )
                    )

                    drawCircle(
                        color = gold,
                        radius = radius * 0.63f
                    )

                    drawCircle(
                        color = Color(0xFF56371A),
                        radius = radius * 0.605f
                    )

                    drawCircle(
                        color = gold,
                        radius = radius * 0.20f
                    )

                    drawCircle(
                        color = Color(0xFFFFE7A5),
                        radius = radius * 0.085f
                    )

                    // Draw the real European pocket numbers.
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint(
                            android.graphics.Paint.ANTI_ALIAS_FLAG
                        ).apply {
                            color = android.graphics.Color.WHITE
                            textAlign =
                                android.graphics.Paint.Align.CENTER
                            textSize = radius * 0.105f
                            typeface = android.graphics.Typeface
                                .create(
                                    android.graphics.Typeface.DEFAULT,
                                    android.graphics.Typeface.BOLD
                                )
                        }

                        for (i in numbers.indices) {
                            val theta = Math.toRadians(
                                (-90.0 + i * sweep)
                            )
                            val textRadius = radius * 0.755f
                            val x = cx +
                                kotlin.math.cos(theta).toFloat() *
                                textRadius
                            val y = cy +
                                kotlin.math.sin(theta).toFloat() *
                                textRadius

                            drawText(
                                numbers[i].toString(),
                                x,
                                y - (paint.ascent() +
                                    paint.descent()) / 2f,
                                paint
                            )
                        }
                    }
                }

                // Ball travels anticlockwise independently.
                val ballAngle = Math.toRadians(
                    (-90.0 + ballAngleDegrees)
                )
                val ballRadius = radius * 0.93f
                val ballX = cx +
                    kotlin.math.cos(ballAngle).toFloat() *
                    ballRadius
                val ballY = cy +
                    kotlin.math.sin(ballAngle).toFloat() *
                    ballRadius

                drawCircle(
                    color = Color(0xFF444444),
                    radius = 10.dp.toPx(),
                    center = Offset(
                        ballX + 2.dp.toPx(),
                        ballY + 2.dp.toPx()
                    )
                )

                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = Offset(ballX, ballY)
                )

                drawCircle(
                    color = Color(0xFFE6E6E6),
                    radius = 3.dp.toPx(),
                    center = Offset(
                        ballX - 2.dp.toPx(),
                        ballY - 2.dp.toPx()
                    )
                )
            }
        }

        Spacer(Modifier.height(34.dp))

        Text(
            text = "Winners know what to watch.",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "LET THE WHEEL DECIDE",
            style = MaterialTheme.typography.labelLarge,
            color = gold
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var platformSplashDismissed by mutableStateOf(Build.VERSION.SDK_INT < 31)
        if (Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { view ->
                view.remove()
                platformSplashDismissed = true
            }
        }

        setContent {
            var showSplash by remember {
                mutableStateOf(true)
            }

            var initialRecommendationsFinished by remember {
                mutableStateOf(false)
            }

            var startSpin by remember { mutableStateOf(false) }
            var spinFinished by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                withTimeoutOrNull(1000) {
                    snapshotFlow { platformSplashDismissed }.first { it }
                }
                withFrameNanos { }
                delay(120)
                startSpin = true
                withTimeoutOrNull(SPLASH_LOAD_TIMEOUT_MS) {
                    delay(SPLASH_MINIMUM_AFTER_START_MS)
                    snapshotFlow { spinFinished && initialRecommendationsFinished }.first { it }
                }
                showSplash = false
            }

            ScreenRouletteTheme(
                darkTheme = true,
                dynamicColor = false
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Keep the main screen composed immediately.
                    // Its initial TMDB request starts during the splash.
                    ScreenRouletteScreen(
                        onInitialLoadFinished = {
                            initialRecommendationsFinished = true
                        }
                    )

                    // The splash covers the main screen
                    // without delaying its initial data loading.
                    if (showSplash) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF10291F))
                        ) {
                            RouletteSplashScreen(startSpin) { spinFinished = true }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenRouletteScreen(
    onInitialLoadFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedCategory by remember {
        mutableStateOf(ContentCategory.FILMS)
    }

    val settings = remember(context) {
        context.getSharedPreferences("screenroulette_settings", Context.MODE_PRIVATE)
    }
    var activeRecommendationCount by remember(settings) {
        mutableIntStateOf(
            settings.getInt(RECOMMENDATION_COUNT_KEY, DEFAULT_RECOMMENDATION_COUNT)
                .coerceIn(1, MAX_RECOMMENDATION_COUNT)
        )
    }
    var selectedRecommendationCount by remember(settings) {
        mutableIntStateOf(activeRecommendationCount)
    }

    fun saveRecommendationCount(count: Int) {
        activeRecommendationCount = count
        settings.edit().putInt(RECOMMENDATION_COUNT_KEY, count).apply()
    }

    val currentYear = remember {
        Calendar.getInstance().get(Calendar.YEAR)
    }

    fun defaultFilters(category: ContentCategory): MovieFilters =
        MovieFilters(
            genreIds = emptySet(),
            matchAll = false,
            startYear = if (category == ContentCategory.FILMS) EARLIEST_RELEASE_YEAR else 1990,
            endYear = currentYear
        )

    val initialFilters = remember(context, currentYear) {
        loadCategoryFilters(
            context,
            ContentCategory.FILMS,
            defaultFilters(ContentCategory.FILMS)
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

    val savedCategoryFilters = remember(context, currentYear) {
        mutableStateMapOf<ContentCategory, MovieFilters>().apply {
            ContentCategory.values().forEach { category ->
                this[category] = loadCategoryFilters(
                    context, category, defaultFilters(category)
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

    var showYearEditor by remember {
        mutableStateOf(false)
    }

    var recommendations by remember(selectedCategory, activeFilters, activeRecommendationCount) {
        mutableStateOf<List<Movie>>(emptyList())
    }

    val spinHistory = remember(selectedCategory, activeFilters, activeRecommendationCount) {
        mutableStateListOf<List<Movie>>()
    }

    var seenIds by remember {
        mutableStateOf(emptySet<Int>())
    }

    var rollNumber by remember {
        mutableIntStateOf(0)
    }

    var openingRecommendations by remember { mutableStateOf(true) }
    var shuffleRecommendations by remember {
        mutableStateOf(false)
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

    val watchlist = remember(context) {
        mutableStateListOf<WatchlistEntry>().apply {
            addAll(loadWatchlist(context))
        }
    }
    var showWatchlist by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    fun toggleWatchlist(movie: Movie, category: ContentCategory) {
        val existing = watchlist.indexOfFirst {
            it.movie.id == movie.id && it.category == category
        }
        if (existing >= 0) {
            watchlist.removeAt(existing)
        } else {
            watchlist.add(WatchlistEntry(movie, category))
        }
        saveWatchlist(context, watchlist.toList())
    }

    val recommendationListState = rememberLazyListState()

    fun goBack() {
        if (navigationStack.isNotEmpty()) {
            navigationStack.removeAt(
                navigationStack.lastIndex
            )
        }
    }

    BackHandler(
        enabled = navigationStack.isNotEmpty() || showWatchlist
    ) {
        if (navigationStack.isNotEmpty()) {
            goBack()
        } else {
            showWatchlist = false
        }
    }

    // A new request is triggered whenever the active
    // filters or roll number changes.
    LaunchedEffect(selectedCategory, activeFilters, activeRecommendationCount, rollNumber) {
        val spinStart = android.os.SystemClock.elapsedRealtime()
        var spinRetried = false
        val newestFirst = selectedCategory == ContentCategory.FILMS && openingRecommendations
        openingRecommendations = false
        val previousResults = recommendations

        loading = true
        error = null
        recommendations = emptyList()

        try {
            val newMovies = fetchCategoryRecommendations(
                category = selectedCategory,
                excludedIds = seenIds,
                filters = activeFilters,
                newestFirst = newestFirst,
                resultLimit = activeRecommendationCount
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

            if (verifiedMovies.isEmpty() && seenIds.isNotEmpty()) {
                // Retry once without the previously viewed history.
                // This does not trigger another LaunchedEffect.
                spinRetried = true
                val retryMovies = fetchCategoryRecommendations(
                    category = selectedCategory,
                    excludedIds = emptySet(),
                    filters = activeFilters,
                    newestFirst = newestFirst,
                    resultLimit = activeRecommendationCount
                )

                val retryVerified = retryMovies.filter {
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

                if (retryVerified.isNotEmpty()) {
                    recommendations = retryVerified
                    seenIds = retryVerified.map { it.id }.toSet()

                    android.util.Log.d(
                        "ScreenRouletteSpin",
                        "Recommendation history reset after exhaustion"
                    )
                } else {
                    error = "No matching recommendations found. " +
                        "Try broader filters."
                }
            } else if (verifiedMovies.isEmpty()) {
                error = "No matching recommendations found. " +
                    "Try broader filters."
            } else {
                recommendations = verifiedMovies
                seenIds = seenIds + verifiedMovies.map { it.id }
            }

            if (shuffleRecommendations && previousResults.isNotEmpty() &&
                recommendations.isNotEmpty() && previousResults != recommendations
            ) {
                spinHistory.add(previousResults)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Unable to load films"
        } finally {
            if (recommendations.isEmpty() && error != null) {
                recommendations = previousResults
            }

            val spinDuration =
                android.os.SystemClock.elapsedRealtime() - spinStart

            android.util.Log.d(
                "ScreenRouletteSpin",
                "SPIN_PERFORMANCE " +
                    "category=$selectedCategory " +
                    "durationMs=$spinDuration " +
                    "results=${recommendations.size} " +
                    "historyRetry=$spinRetried " +
                    "success=${error == null}"
            )

            loading = false
            onInitialLoadFinished()
        }
    }

    if (showAbout) {
        AboutScreenRouletteDialog(onDismiss = { showAbout = false })
    }

    if (showYearEditor) {
        ReleaseYearsDialog(
            startYear = startYearText,
            endYear = endYearText,
            latestYear = currentYear,
            onDismiss = { showYearEditor = false },
            onConfirm = { from, to ->
                startYearText = from
                endYearText = to
                showYearEditor = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        if (selectedMovie == null && showWatchlist) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                TextButton(onClick = { showWatchlist = false }) {
                    Text("← Back to recommendations")
                }
                Text(
                    "My Watchlist (${watchlist.size})",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(12.dp))

                if (watchlist.isEmpty()) {
                    Text("No saved titles yet.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            watchlist.toList(),
                            key = { "${it.category.name}_${it.movie.id}" }
                        ) { entry ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        entry.movie.title,
                                        style =
                                            MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "${entry.category.name.replace('_', ' ')}" +
                                            " • ${entry.movie.year}"
                                    )
                                    Row(
                                        horizontalArrangement =
                                            Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(onClick = {
                                            selectedCategory = entry.category
                                            activeFilters =
                                                savedCategoryFilters[entry.category]
                                                    ?: defaultFilters(entry.category)
                                            navigationStack.add(entry.movie)
                                        }) {
                                            Text("View details")
                                        }
                                        OutlinedButton(onClick = {
                                            toggleWatchlist(
                                                entry.movie, entry.category
                                            )
                                        }) {
                                            Text("Remove")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedMovie == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ScreenRoulette",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    OutlinedButton(
                        onClick = { showWatchlist = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Watchlist (${watchlist.size})")
                    }
                    TextButton(
                        onClick = { showAbout = true },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("About")
                    }
                }

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
                                                ?: defaultFilters(category)

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
                                        shuffleRecommendations = false
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
                                                ?: defaultFilters(category)

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
                                        shuffleRecommendations = false
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

                Spacer(Modifier.height(4.dp))

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filtersExpanded = !filtersExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (filtersExpanded) "Hide Filters" else "Show Filters")
                    }

                    if (activeFilters.streamingRegion.isNotBlank()) {
                        Button(
                            onClick = {
                                val updated = activeFilters.copy(
                                    streamingRegion = "",
                                    streamingProviderId = 0
                                )

                                activeFilters = updated
                                streamingRegion = ""
                                streamingProviderId = 0
                                savedCategoryFilters[selectedCategory] = updated
                                saveCategoryFilters(context, selectedCategory, updated)

                                seenIds = emptySet()
                                shuffleRecommendations = false
                                recommendations = emptyList()
                                navigationStack.clear()
                                filtersExpanded = false
                            },
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Switch to Worldwide")
                        }
                    }
                }

                if (filtersExpanded) {
                    OutlinedButton(
                        onClick = { showYearEditor = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Release years · $startYearText–$endYearText")
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text("Titles per spin", style = MaterialTheme.typography.titleSmall)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                for (count in 1..MAX_RECOMMENDATION_COUNT) {
                                    SegmentedButton(
                                        selected = selectedRecommendationCount == count,
                                        onClick = { selectedRecommendationCount = count },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = count - 1,
                                            count = MAX_RECOMMENDATION_COUNT
                                        )
                                    ) {
                                        Text(count.toString())
                                    }
                                }
                            }
                        }

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
                                    .heightIn(min = 48.dp)
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = !matchAll,
                                        onClick = { matchAll = false },
                                        role = androidx.compose.ui.semantics.Role.RadioButton
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !matchAll,
                                    onClick = null
                                )
                                Text("Match Any")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = matchAll,
                                        onClick = { matchAll = true },
                                        role = androidx.compose.ui.semantics.Role.RadioButton
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = matchAll,
                                    onClick = null
                                )
                                Text("Match All")
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

                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Step 45: Reset applied filters
                        OutlinedButton(
                            onClick = {
                                val reset = defaultFilters(selectedCategory)

                                selectedRecommendationCount = DEFAULT_RECOMMENDATION_COUNT
                                saveRecommendationCount(selectedRecommendationCount)
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
                                shuffleRecommendations = false
                                recommendations = emptyList()
                                spinHistory.clear()
                                navigationStack.clear()
                                filtersExpanded = false

                                if (activeFilters == reset) {
                                    rollNumber++
                                } else {
                                    activeFilters = reset
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error
                            ),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
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
                                    from < EARLIEST_RELEASE_YEAR ||
                                    to > currentYear ||
                                    from > to
                                ) {
                                    error = "Enter a valid year range between $EARLIEST_RELEASE_YEAR and $currentYear."
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

                                    saveRecommendationCount(selectedRecommendationCount)
                                    savedCategoryFilters[selectedCategory] =
                                        newFilters
                                    saveCategoryFilters(
                                        context,
                                        selectedCategory,
                                        newFilters
                                    )

                                    seenIds = emptySet()
                                    shuffleRecommendations = false
                                    recommendations = emptyList()
                                    spinHistory.clear()
                                    filtersExpanded = false

                                    if (newFilters == activeFilters) {
                                        rollNumber++
                                    } else {
                                        activeFilters = newFilters
                                    }
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (MaterialTheme.colorScheme.background == androidx.compose.ui.graphics.Color(0xFFF7F8F4)) {
                                    androidx.compose.ui.graphics.Color(0xFF176344)
                                } else {
                                    androidx.compose.ui.graphics.Color(0xFF146B48)
                                },
                                contentColor = androidx.compose.ui.graphics.Color.White
                            )
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
                        // SCREENROULETTE_ACCESSIBILITY_STEP25
                        onClick = {
                            shuffleRecommendations = true
                            rollNumber++
                        },
                        modifier = Modifier.semantics {
                            contentDescription =
                                "Spin for new recommendations"
                        },
                        enabled = !loading,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("SPIN")
                    }

                    OutlinedButton(
                        onClick = {
                            recommendations = spinHistory.removeAt(spinHistory.lastIndex)
                            error = null
                            recommendationListState.requestScrollToItem(0)
                        },
                        enabled = !loading && spinHistory.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reverse spin")
                    }

                    Text(
                        text = "${recommendations.size} films",
                        modifier = Modifier.semantics {
                            liveRegion =
                                androidx.compose.ui.semantics.LiveRegionMode.Polite
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (loading) {
                    RouletteLoadingIndicator()
                    Spacer(Modifier.height(12.dp))
                }

                error?.let {
                    Text(
                        it,
                        modifier = Modifier.semantics {
                            liveRegion =
                                androidx.compose.ui.semantics.LiveRegionMode.Assertive
                        },
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(Modifier.height(12.dp))
                }

                if (!filtersExpanded) {
                    LazyColumn(
                        state = recommendationListState,
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
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
                isWatchlisted = watchlist.any {
                    it.movie.id == selectedMovie.id &&
                        it.category == selectedCategory
                },
                onToggleWatchlist = {
                    toggleWatchlist(selectedMovie, selectedCategory)
                },
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
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val context = LocalContext.current
    val url = posterUrl(movie.posterPath, large)

    if (url == null) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "No poster",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val request = remember(context, url) {
            coil3.request.ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build()
        }

        AsyncImage(
            model = request,
            contentDescription = "Poster for ${movie.title}",
            modifier = modifier,
            contentScale = ContentScale.Crop,
            placeholder = androidx.compose.ui.res.painterResource(
                android.R.drawable.ic_menu_gallery
            ),
            error = androidx.compose.ui.res.painterResource(
                android.R.drawable.ic_menu_report_image
            )
        )
    }
}

@Composable
private fun AboutScreenRouletteDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About ScreenRoulette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Version ${BuildConfig.VERSION_NAME}")
                Image(
                    painter = painterResource(R.drawable.tmdb_logo),
                    contentDescription = "The Movie Database",
                    modifier = Modifier.width(120.dp)
                )
                Text("This product uses the TMDB API but is not endorsed or certified by TMDB.")
                Text("Streaming availability is supplied by TMDB and JustWatch. IMDb and Rotten Tomatoes ratings are retrieved through OMDb.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { uriHandler.openUri("https://www.themoviedb.org") }) {
                Text("Visit TMDB")
            }
        }
    )
}

@Composable
private fun ReleaseYearsDialog(
    startYear: String,
    endYear: String,
    latestYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var from by remember { mutableStateOf(startYear) }
    var to by remember { mutableStateOf(endYear) }
    val focusManager = LocalFocusManager.current
    val earliest = from.toIntOrNull()
    val latest = to.toIntOrNull()
    val valid = earliest != null && latest != null &&
        earliest >= EARLIEST_RELEASE_YEAR && earliest <= latest && latest <= latestYear

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Release years", style = MaterialTheme.typography.titleMedium) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.weight(1f),
                    label = { Text("From") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                    )
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.weight(1f),
                    label = { Text("To") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (valid) onConfirm(from, to)
                    })
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(from, to) }, enabled = valid) {
                Text("Set years")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            MoviePoster(
                movie = movie,
                modifier = Modifier
                    .width(70.dp)
                    .height(105.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
private fun WatchTrailerButton(movie: Movie, category: ContentCategory) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading,
        onClick = {
            loading = true
            message = null
            scope.launch {
                try {
                    val trailer = fetchYouTubeTrailer(movie, category)
                    if (trailer == null) {
                        message = "No YouTube trailer is listed for this title."
                    } else {
                        uriHandler.openUri(trailer)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    message = "Couldn’t open the trailer. Try again."
                } finally {
                    loading = false
                }
            }
        }
    ) {
        Text(if (loading) "Finding trailer…" else "Watch trailer on YouTube")
    }

    message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics {
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
            }
        )
    }
}

@Composable
private fun MovieDetailsScreen(
    movie: Movie,
    category: ContentCategory,
    streamingRegion: String,
    padding: PaddingValues,
    onBack: () -> Unit,
    isWatchlisted: Boolean,
    onToggleWatchlist: () -> Unit,
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
                Button(
                    onClick = onToggleWatchlist,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isWatchlisted) {
                            "Remove from Watchlist"
                        } else {
                            "Save to Watchlist"
                        }
                    )
                }
            }

            item(key = "trailer-${category.name}-${movie.id}") {
                WatchTrailerButton(movie, category)
            }

            item {
                MoviePoster(
                    movie = movie,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    large = true
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

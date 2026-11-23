package com.hellojgl.screenroulette

internal fun matchesOriginalLanguage(
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

internal fun imdbScore(value: String?): Double? =
    value?.toDoubleOrNull()?.takeIf { it in 0.0..10.0 }

internal fun movieMatchesFilters(
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
        val rating = imdbScore(movie.imdbRating)
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

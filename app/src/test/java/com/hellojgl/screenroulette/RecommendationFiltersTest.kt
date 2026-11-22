package com.hellojgl.screenroulette

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationFiltersTest {

    private fun movie(
        year: String = "2020",
        rating: String = "8.0",
        genres: Set<Int> = setOf(28, 35),
        language: String = "en"
    ) = Movie(
        id = 1,
        title = "Test Film",
        year = year,
        rating = rating,
        overview = "",
        posterPath = null,
        genreIds = genres,
        originalLanguage = language
    )

    private fun filters(
        genres: Set<Int> = emptySet(),
        matchAll: Boolean = false,
        startYear: Int = 1990,
        endYear: Int = 2026,
        minRating: Double = 0.0,
        language: String = "all"
    ) = MovieFilters(
        genreIds = genres,
        matchAll = matchAll,
        startYear = startYear,
        endYear = endYear,
        minRating = minRating,
        originalLanguageFilter = language
    )

    @Test
    fun matchingFilmIsAccepted() {
        assertTrue(movieMatchesFilters(movie(), filters()))
    }

    @Test
    fun filmOutsideYearRangeIsRejected() {
        assertFalse(
            movieMatchesFilters(
                movie(year = "1985"),
                filters()
            )
        )
    }

    @Test
    fun filmBelowMinimumRatingIsRejected() {
        assertFalse(
            movieMatchesFilters(
                movie(rating = "5.5"),
                filters(minRating = 7.0)
            )
        )
    }

    @Test
    fun matchAnyAcceptsOneMatchingGenre() {
        assertTrue(
            movieMatchesFilters(
                movie(genres = setOf(28)),
                filters(
                    genres = setOf(28, 35),
                    matchAll = false
                )
            )
        )
    }

    @Test
    fun matchAllRequiresEverySelectedGenre() {
        assertFalse(
            movieMatchesFilters(
                movie(genres = setOf(28)),
                filters(
                    genres = setOf(28, 35),
                    matchAll = true
                )
            )
        )
    }

    @Test
    fun internationalFilterRejectsEnglishFilm() {
        assertFalse(
            movieMatchesFilters(
                movie(language = "en"),
                filters(language = "international")
            )
        )
    }
}

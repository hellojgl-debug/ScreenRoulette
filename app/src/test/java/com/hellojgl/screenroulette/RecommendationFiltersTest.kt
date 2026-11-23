package com.hellojgl.screenroulette

import org.junit.Assert.assertEquals
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
    fun ratingAtMinimumIsAccepted() {
        assertTrue(movieMatchesFilters(movie(rating = "7.0"), filters(minRating = 7.0)))
    }

    @Test
    fun unavailableTmdbRatingOnlyMatchesWhenThereIsNoMinimum() {
        val title = movie(rating = "N/A")
        assertTrue(movieMatchesFilters(title, filters()))
        assertFalse(movieMatchesFilters(title, filters(minRating = 7.0)))
    }

    @Test
    fun sliderFloatingPointNoiseDoesNotChangeRatingOrReachTheApi() {
        val noisyMinimum = 3.500000238418579
        val noisyMaximum = 8.499999046325684
        assertEquals("3.50", formatTmdbRatingParameter(noisyMinimum))
        assertEquals(8.5, normalizeRatingBound(noisyMaximum), 0.0)
        val range = filters(minRating = normalizeRatingBound(noisyMinimum))
            .copy(maxRating = normalizeRatingBound(noisyMaximum))
        assertTrue(movieMatchesFilters(movie(rating = "3.5"), range))
        assertTrue(movieMatchesFilters(movie(rating = "8.5"), range))
    }

    @Test
    fun maximumRatingIsInclusiveAndExcludesPerfectAndUnratedTitles() {
        val range = filters(minRating = 6.0).copy(maxRating = 8.5)
        assertTrue(movieMatchesFilters(movie(rating = "8.5"), range))
        assertFalse(movieMatchesFilters(movie(rating = "8.6"), range))
        assertFalse(movieMatchesFilters(movie(rating = "10.0"), range))
        assertFalse(movieMatchesFilters(movie(rating = "N/A"), filters().copy(maxRating = 8.5)))
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

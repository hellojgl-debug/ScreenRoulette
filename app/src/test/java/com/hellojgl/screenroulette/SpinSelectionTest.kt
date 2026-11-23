package com.hellojgl.screenroulette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpinSelectionTest {
    @Test
    fun spinSamplesAllAvailablePagesWithoutAlwaysStartingOnPageOne() {
        val orders = (0..4).map { randomDiscoveryPages(50, Random(it)) }
        for (order in orders) {
            assertEquals((1..50).toSet(), order.toSet())
            assertEquals(50, order.size)
        }
        assertTrue(orders.any { it.first() != 1 })
    }

    @Test
    fun spinDoesNotRequestPagesOutsideTheProviderLimit() {
        assertEquals((1..500).toSet(), randomDiscoveryPages(900, Random(0)).toSet())
        assertTrue(randomDiscoveryPages(0, Random(0)).isEmpty())
    }


    private val filters = MovieFilters(
        genreIds = emptySet(),
        matchAll = false,
        startYear = 1990,
        endYear = 2026
    )

    private fun movie(
        id: Int,
        year: String = "2020"
    ) = Movie(
        id = id,
        title = "Test Film $id",
        year = year,
        rating = "8.0",
        overview = "",
        posterPath = null,
        genreIds = setOf(28),
        originalLanguage = "en"
    )

    @Test
    fun spinNeverReturnsDuplicateIds() {
        val candidates = listOf(
            movie(1),
            movie(1),
            movie(2),
            movie(3)
        )

        val result = selectSpinRecommendations(
            candidates, emptySet(), filters
        )

        assertEquals(
            result.size,
            result.map { it.id }.distinct().size
        )
    }

    @Test
    fun previouslyViewedTitlesAreExcluded() {
        val candidates = (1..10).map { movie(it) }

        val result = selectSpinRecommendations(
            candidates,
            setOf(1, 2, 3),
            filters
        )

        assertTrue(result.none { it.id in setOf(1, 2, 3) })
    }

    @Test
    fun spinDefaultsToThreeAndRespectsEachSelectedLimit() {
        val candidates = (1..30).map { movie(it) }

        assertEquals(3, selectSpinRecommendations(candidates, emptySet(), filters).size)
        for (limit in 1..5) {
            assertEquals(limit, selectSpinRecommendations(candidates, emptySet(), filters, limit).size)
        }
    }

    @Test
    fun emptyCandidatePoolReturnsEmptyResult() {
        val result = selectSpinRecommendations(
            emptyList(), emptySet(), filters
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun exhaustedCandidatePoolReturnsEmptyResult() {
        val candidates = (1..5).map { movie(it) }

        val result = selectSpinRecommendations(
            candidates, (1..5).toSet(), filters
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun invalidYearsAreExcluded() {
        val candidates = listOf(
            movie(1, year = "1980"),
            movie(2, year = "2020")
        )

        val result = selectSpinRecommendations(
            candidates, emptySet(), filters
        )

        assertEquals(1, result.size)
        assertEquals(2, result.first().id)
    }
}

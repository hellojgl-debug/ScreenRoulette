package com.hellojgl.screenroulette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeTrailerTest {
    @Test
    fun prefersAnOfficialYouTubeTrailer() {
        val videos = listOf(
            TrailerVideo("YouTube", "Trailer", "abcdefghijk", false),
            TrailerVideo("Vimeo", "Trailer", "ABCDEFGHIJK", true),
            TrailerVideo("YouTube", "Teaser", "12345678901", true),
            TrailerVideo("YouTube", "Trailer", "Abc123_-XYZ", true)
        )
        assertEquals("Abc123_-XYZ", selectYouTubeTrailer(videos))
    }

    @Test
    fun acceptsAValidTrailerWhenThereIsNoOfficialOne() {
        val videos = listOf(TrailerVideo("YouTube", "Trailer", "abcdefghijk", false))
        assertEquals("abcdefghijk", selectYouTubeTrailer(videos))
    }

    @Test
    fun rejectsInvalidIdentifiersAndNonTrailers() {
        val videos = listOf(
            TrailerVideo("YouTube", "Trailer", "bad&v=other", true),
            TrailerVideo("YouTube", "Trailer", "https://example.com/video", true),
            TrailerVideo("YouTube", "Teaser", "12345678901", true),
            TrailerVideo("Vimeo", "Trailer", "ABCDEFGHIJK", true)
        )
        assertNull(selectYouTubeTrailer(videos))
    }
}

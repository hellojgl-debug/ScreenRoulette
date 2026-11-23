package com.hellojgl.screenroulette

internal data class TrailerVideo(
    val site: String,
    val type: String,
    val key: String,
    val official: Boolean
)

private val youTubeVideoId = Regex("[A-Za-z0-9_-]{11}")

internal fun selectYouTubeTrailer(videos: List<TrailerVideo>): String? {
    val trailers = videos.filter {
        it.site.equals("YouTube", ignoreCase = true) &&
            it.type.equals("Trailer", ignoreCase = true) &&
            youTubeVideoId.matches(it.key)
    }
    val video = trailers.firstOrNull { it.official } ?: trailers.firstOrNull()
    return video?.let { "https://www.youtube.com/watch?v=${it.key}" }
}

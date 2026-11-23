package com.hellojgl.screenroulette

internal sealed interface AppPage {
    data object Discover : AppPage
    data object Watchlist : AppPage
    data class Details(val movie: Movie, val category: ContentCategory) : AppPage
}

internal data class ScreenHistory(
    val current: AppPage = AppPage.Discover,
    private val back: List<AppPage> = emptyList(),
    private val forward: List<AppPage> = emptyList()
) {
    val canGoBack: Boolean get() = back.isNotEmpty()
    val canGoForward: Boolean get() = forward.isNotEmpty()

    fun open(page: AppPage): ScreenHistory =
        if (page == current) this else ScreenHistory(page, back + current)

    fun goBack(): ScreenHistory = back.lastOrNull()?.let { previous ->
        ScreenHistory(previous, back.dropLast(1), forward + current)
    } ?: this

    fun goForward(): ScreenHistory = forward.lastOrNull()?.let { next ->
        ScreenHistory(next, back + current, forward.dropLast(1))
    } ?: this
}

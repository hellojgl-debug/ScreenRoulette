package com.hellojgl.screenroulette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenHistoryTest {
    private val title = AppPage.Details(
        Movie(1, "Test title", "2001", "8.0", "", null, emptySet()),
        ContentCategory.TV_SERIES
    )

    @Test
    fun backAndForwardFollowTheScreensActuallyOpened() {
        val current = ScreenHistory().open(AppPage.Watchlist).open(title)
        assertEquals(title, current.current)

        val home = current.goBack().goBack()
        assertEquals(AppPage.Discover, home.current)
        assertTrue(home.canGoForward)
        assertFalse(home.canGoBack)
        assertEquals(AppPage.Watchlist, home.goForward().current)
        assertEquals(current, home.goForward().goForward())
    }

    @Test
    fun openingANewScreenAfterGoingBackClearsForwardHistory() {
        val home = ScreenHistory().open(AppPage.Watchlist).goBack()

        val newNavigation = home.open(title)
        assertFalse(newNavigation.canGoForward)
        assertEquals(title, newNavigation.goForward().current)
    }
}

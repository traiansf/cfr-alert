package ro.trenuri.app.ui.nav

import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.Station
import kotlin.test.*

class TabNavigatorTest {
    private val date = AppDate(2026,6,29)
    private val brasov = Station("Brașov","Brasov")

    @Test fun openTrainSelectsTrenAndInvokesCallback() {
        var opened: Pair<String, AppDate>? = null
        val nav = TabNavigator(Tab.STATIE, onOpenTrain = { n,d -> opened = n to d }, onOpenStation = { _,_ -> })
        nav.openTrain("1733", date)
        assertEquals(Tab.TREN, nav.selectedTab.value)
        assertEquals("1733" to date, opened)
    }

    @Test fun openStationSelectsStatieAndInvokesCallback() {
        var opened: Station? = null
        val nav = TabNavigator(Tab.TREN, onOpenTrain = { _,_ -> }, onOpenStation = { s,_ -> opened = s })
        nav.openStation(brasov, date)
        assertEquals(Tab.STATIE, nav.selectedTab.value)
        assertEquals(brasov, opened)
    }

    @Test fun plainSelectDoesNotInvokeCallbacks() {
        var called = false
        val nav = TabNavigator(Tab.TREN, onOpenTrain = { _,_ -> called = true }, onOpenStation = { _,_ -> called = true })
        nav.select(Tab.RUTE)
        assertEquals(Tab.RUTE, nav.selectedTab.value)
        assertFalse(called)
    }

    @Test fun backReturnsToPreviousTabThenFalseWhenEmpty() {
        val nav = TabNavigator(Tab.TREN, onOpenTrain = { _,_ -> }, onOpenStation = { _,_ -> })
        nav.select(Tab.RUTE)        // back-stack: [TREN]
        nav.openTrain("1", date)     // back-stack: [TREN, RUTE], now TREN
        assertTrue(nav.back()); assertEquals(Tab.RUTE, nav.selectedTab.value)
        assertTrue(nav.back()); assertEquals(Tab.TREN, nav.selectedTab.value)
        assertFalse(nav.back())     // empty -> system handles
    }
}

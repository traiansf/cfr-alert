package ro.trenuri.app.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.Station

enum class Tab { TREN, RUTE, STATIE }

class TabNavigator(
    initial: Tab = Tab.TREN,
    private val onOpenTrain: (String, AppDate) -> Unit,
    private val onOpenStation: (Station, AppDate) -> Unit,
) {
    private val _selectedTab = MutableStateFlow(initial)
    val selectedTab: StateFlow<Tab> = _selectedTab.asStateFlow()
    private val backStack = ArrayDeque<Tab>()

    private fun goto(tab: Tab) {
        if (tab != _selectedTab.value) {
            backStack.addLast(_selectedTab.value)
            _selectedTab.value = tab
        }
    }

    fun select(tab: Tab) = goto(tab)

    fun openTrain(number: String, date: AppDate) {
        onOpenTrain(number, date)
        goto(Tab.TREN)
    }

    fun openStation(station: Station, date: AppDate) {
        onOpenStation(station, date)
        goto(Tab.STATIE)
    }

    fun back(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        _selectedTab.value = prev
        return true
    }
}

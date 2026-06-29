package ro.trenuri.app.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import ro.trenuri.app.ui.TrainViewModel
import ro.trenuri.app.ui.board.BoardViewModel
import ro.trenuri.app.ui.board.StationBoardScreen
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.Today
import ro.trenuri.app.ui.itinerary.ItinerarySearchScreen
import ro.trenuri.app.ui.itinerary.ItineraryViewModel
import ro.trenuri.app.ui.train.TrainDetailScreen

@Composable
fun AppScaffold() {
    // Hoist ViewModels here so they survive tab switches
    val trainVm: TrainViewModel = koinViewModel()
    val itineraryVm: ItineraryViewModel = koinViewModel()
    val boardVm: BoardViewModel = koinViewModel()
    val today: Today = koinInject(qualifier = named("today"))

    // Single shared date across all tabs — survives configuration changes
    val appDateSaver = listSaver<AppDate, Int>(
        save = { listOf(it.year, it.month, it.day) },
        restore = { AppDate(it[0], it[1], it[2]) },
    )
    var selectedDate by rememberSaveable(stateSaver = appDateSaver) { mutableStateOf(today()) }

    val nav = remember {
        TabNavigator(
            onOpenTrain = { number, date -> trainVm.load(number, date) },
            onOpenStation = { station, date -> boardVm.load(station, date) },
        )
    }
    val selected by nav.selectedTab.collectAsStateWithLifecycle()
    val canGoBack by nav.canGoBack.collectAsStateWithLifecycle()

    // Only intercept back when there is tab history to pop; otherwise let system handle it
    BackHandler(enabled = canGoBack) { nav.back() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selected == Tab.TREN,
                    onClick = { nav.select(Tab.TREN) },
                    icon = { Icon(Icons.Default.Train, contentDescription = null) },
                    label = { Text("Tren") },
                )
                NavigationBarItem(
                    selected = selected == Tab.RUTE,
                    onClick = { nav.select(Tab.RUTE) },
                    icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null) },
                    label = { Text("Rute") },
                )
                NavigationBarItem(
                    selected = selected == Tab.STATIE,
                    onClick = { nav.select(Tab.STATIE) },
                    icon = { Icon(Icons.Default.Place, contentDescription = null) },
                    label = { Text("Stație") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selected) {
                Tab.TREN -> TrainDetailScreen(
                    viewModel = trainVm,
                    date = selectedDate,
                    onDateChange = { selectedDate = it },
                    onStationClick = { station -> nav.openStation(station, selectedDate) },
                )
                Tab.RUTE -> ItinerarySearchScreen(
                    vm = itineraryVm,
                    date = selectedDate,
                    onDateChange = { selectedDate = it },
                    onTrainClick = { number -> nav.openTrain(number, selectedDate) },
                )
                Tab.STATIE -> StationBoardScreen(
                    vm = boardVm,
                    date = selectedDate,
                    onDateChange = { selectedDate = it },
                    onTrainClick = { number -> nav.openTrain(number, selectedDate) },
                )
            }
        }
    }
}

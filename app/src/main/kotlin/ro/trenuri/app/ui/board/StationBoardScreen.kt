package ro.trenuri.app.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.DatePickerField
import ro.trenuri.app.ui.common.EmptyState
import ro.trenuri.app.ui.common.ErrorState
import ro.trenuri.app.ui.common.LoadingState
import ro.trenuri.app.ui.common.Today
import ro.trenuri.app.ui.history.QueryHistoryStore
import ro.trenuri.app.ui.history.StationQuery
import ro.trenuri.app.ui.station.StationPickerField
import ro.trenuri.infofer.model.BoardEntry
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.Station

private val DelayGreen = Color(0xFF1B5E20)
private val DelayRed = Color(0xFFB71C1C)

/** Returns the day-separator label: "Azi", "Mâine", or the formatted date. */
private fun dayLabel(date: AppDate, todayDate: AppDate): String = when (date) {
    todayDate -> "Azi"
    todayDate.nextDay() -> "Mâine"
    else -> date.format()
}

/** Number of day-sections to eagerly auto-load when content doesn't fill the viewport. */
private const val EAGER_AUTO_LOAD_DAYS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationBoardScreen(
    vm: BoardViewModel,
    date: AppDate,
    onDateChange: (AppDate) -> Unit,
    onTrainClick: (String) -> Unit,
    historyStore: QueryHistoryStore<StationQuery> = koinInject(qualifier = named("history_statie")),
    todayProvider: Today = koinInject(qualifier = named("today")),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val loadedStation by vm.loadedStation.collectAsStateWithLifecycle()
    var selectedStation by remember { mutableStateOf(vm.loadedStation.value) }
    var recentItems by remember { mutableStateOf(historyStore.recent()) }
    LaunchedEffect(loadedStation) { loadedStation?.let { selectedStation = it } }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val listState = rememberLazyListState()

    // content fits entirely on screen (nothing to scroll)
    val contentFits by remember { derivedStateOf { !listState.canScrollForward && !listState.canScrollBackward } }
    // user has scrolled to the bottom of a scrollable list
    val atBottomScrolled by remember { derivedStateOf { !listState.canScrollForward && listState.canScrollBackward } }

    LaunchedEffect(contentFits, atBottomScrolled, state) {
        val s = state as? BoardUiState.Success ?: return@LaunchedEffect
        if (s.loadingMore || !s.canLoadMore) return@LaunchedEffect
        when {
            contentFits && s.sections.size < EAGER_AUTO_LOAD_DAYS -> vm.loadMore() // eager, capped at 2
            atBottomScrolled -> vm.loadMore()                                       // lazy, on scroll
        }
    }

    val todayDate = remember { todayProvider() }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            StationPickerField(
                label = "Stație",
                onPicked = { station ->
                    selectedStation = station
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    vm.load(station, date)
                    historyStore.add(StationQuery(station, kind))
                    recentItems = historyStore.recent()
                },
                modifier = Modifier.padding(top = 16.dp),
                selected = selectedStation,
            )
        }
        item {
            DatePickerField(
                date = date,
                onDateChange = { newDate ->
                    onDateChange(newDate)
                    selectedStation?.let { vm.load(it, newDate) }
                },
            )
        }
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = kind == BoardKind.DEPARTURES,
                    onClick = { vm.setKind(BoardKind.DEPARTURES) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Plecări") }
                SegmentedButton(
                    selected = kind == BoardKind.ARRIVALS,
                    onClick = { vm.setKind(BoardKind.ARRIVALS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Sosiri") }
            }
        }

        if (recentItems.isNotEmpty()) {
            item {
                Text("Recente", style = MaterialTheme.typography.labelSmall)
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    recentItems.forEach { entry ->
                        AssistChip(
                            // No submit button on this tab — a history tap auto-submits,
                            // restoring the saved station AND its Plecări/Sosiri kind.
                            onClick = {
                                selectedStation = entry.station
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                vm.setKind(entry.kind)
                                vm.load(entry.station, date)
                                historyStore.add(StationQuery(entry.station, entry.kind))
                                recentItems = historyStore.recent()
                            },
                            label = { Text(entry.station.name) },
                        )
                    }
                }
            }
        }

        when (val s = state) {
            BoardUiState.Idle ->
                item { EmptyState("Alege o stație pentru a vedea plecările/sosirile.") }
            BoardUiState.Loading ->
                item { LoadingState() }
            BoardUiState.Empty ->
                item { EmptyState("Nu există trenuri înregistrate.") }
            is BoardUiState.Error ->
                item { ErrorState(s.message) }
            is BoardUiState.Success -> {
                s.sections.forEach { day ->
                    // Day separator header
                    item(key = "header_${day.date.year}_${day.date.month}_${day.date.day}") {
                        Text(
                            text = dayLabel(day.date, todayDate),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    if (day.entries.isEmpty()) {
                        item(key = "empty_${day.date.year}_${day.date.month}_${day.date.day}") {
                            Text(
                                text = "Niciun tren disponibil.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    } else {
                        items(
                            items = day.entries,
                            key = { entry -> "${day.date.year}_${day.date.month}_${day.date.day}_${entry.trainNumber}_${entry.scheduledTime}" },
                        ) { entry ->
                            BoardEntryRow(entry, kind, onTrainClick)
                        }
                    }
                }

                // Load-more sentinel / spinner
                item(key = "load_more_sentinel") {
                    when {
                        s.loadingMore ->
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        !s.canLoadMore -> { /* nothing — reached cap */ }
                        else -> { /* nothing — lazy scroll triggers loadMore */ }
                    }
                }
            }
        }

        item { /* bottom padding */ }
    }
}

@Composable
private fun BoardEntryRow(
    entry: BoardEntry,
    kind: BoardKind,
    onTrainClick: (String) -> Unit,
) {
    val estimatedTime = estimatedTimeOrNull(entry.scheduledTime, entry.delayMinutes)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.scheduledTime,
                style = MaterialTheme.typography.bodyMedium,
            )
            // Alignment column: always render the parenthetical when live data is present.
            // on-time (delayMinutes == 0) → green "(la timp)"
            // delayed (delayMinutes > 0) → red "(HH:MM)"
            // no live data (delayMinutes == null) → nothing
            when {
                entry.delayMinutes == null -> { /* no live data — muted badge shown elsewhere */ }
                estimatedTime != null ->
                    Text(
                        text = " ($estimatedTime)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DelayRed,
                    )
                else ->
                    Text(
                        text = " (la timp)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DelayGreen,
                    )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = entry.category.name,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = entry.trainNumber,
            modifier = Modifier.clickable { onTrainClick(entry.trainNumber) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        val prefix = if (kind == BoardKind.DEPARTURES) "spre " else "dinspre "
        Text(
            text = prefix + entry.counterpartStation,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
        when {
            entry.delayMinutes == null -> Unit
            entry.delayMinutes == 0 ->
                Text("la timp", style = MaterialTheme.typography.labelSmall, color = DelayGreen)
            else ->
                Text("+${entry.delayMinutes} min", style = MaterialTheme.typography.labelSmall, color = DelayRed)
        }
        entry.track?.let {
            Text("linia $it", style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
}
